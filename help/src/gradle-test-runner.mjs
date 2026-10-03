import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { isWithin, resolveContainedPath } from './path-safety.mjs';
import { resolveGradleTestRun } from './runner-resolver.mjs';

function javaExecutable() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  return process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', executable) : executable;
}

function runChild(command, args, options) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { ...options, shell: false, windowsHide: true, stdio: ['ignore', 'ignore', 'ignore'] });
    child.on('error', reject);
    child.on('close', (code, signal) => resolve({ code, signal }));
  });
}

export function prepareReportDirectory(projectRoot, reportDirectory) {
  const validated = resolveContainedPath(projectRoot, path.relative(projectRoot, reportDirectory), { allowMissing: true, requireNonRoot: true });
  fs.rmSync(validated, { recursive: true, force: true });
  fs.mkdirSync(validated, { recursive: true });
  const prepared = fs.realpathSync(validated);
  if (!isWithin(projectRoot, prepared) || prepared === projectRoot) throw new Error('Prepared report directory escaped its Project root');
  return prepared;
}

function xmlAttributeMap(source) {
  const attributes = new Map();
  for (const match of source.matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)) {
    attributes.set(match[1], decodeXml(match[2] ?? match[3] ?? ''));
  }
  return attributes;
}

function decodeXml(value) {
  return value.replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (entity, value) => {
    if (value[0] === '#') {
      const codePoint = value[1].toLowerCase() === 'x' ? Number.parseInt(value.slice(2), 16) : Number.parseInt(value.slice(1), 10);
      return Number.isFinite(codePoint) && codePoint <= 0x10ffff ? String.fromCodePoint(codePoint) : entity;
    }
    return ({ amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" })[value.toLowerCase()] ?? entity;
  });
}

function xmlChildText(xml, tag) {
  const match = xml.match(new RegExp(`<${tag}\\b([^>]*)>([\\s\\S]*?)<\\/${tag}>|<${tag}\\b([^>]*)\\s*/>`));
  if (!match) return undefined;
  const attributes = xmlAttributeMap(match[1] ?? match[3] ?? '');
  const body = match[2]?.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  return (attributes.get('message') || body || '').slice(0, 1200) || `${tag} reported without a message`;
}

export function readGradleTestResults(testResultsDirectory, projectRoot) {
  let files;
  try { files = fs.readdirSync(testResultsDirectory).filter((file) => file.toLowerCase().endsWith('.xml')).sort(); }
  catch { return null; }

  const summary = { source: 'Gradle JUnit XML', total: 0, passed: 0, failed: 0, skipped: 0, durationMs: 0, suites: [], failures: [] };
  let parsedFiles = 0;
  for (const file of files) {
    try {
      const filePath = path.join(testResultsDirectory, file);
      const canonical = fs.realpathSync(filePath);
      if (!isWithin(testResultsDirectory, canonical) || !isWithin(projectRoot, canonical) || !fs.statSync(canonical).isFile()) continue;
      const xml = fs.readFileSync(canonical, 'utf8');
      const suiteTags = [...xml.matchAll(/<testsuite\b([^>]*)>/g)];
      if (suiteTags.length === 0) continue;
      parsedFiles += 1;
      for (const suiteTag of suiteTags) {
        const attributes = xmlAttributeMap(suiteTag[1]);
        const tests = Number.parseInt(attributes.get('tests') ?? '0', 10) || 0;
        const failures = Number.parseInt(attributes.get('failures') ?? '0', 10) || 0;
        const errors = Number.parseInt(attributes.get('errors') ?? '0', 10) || 0;
        const skipped = Number.parseInt(attributes.get('skipped') ?? '0', 10) || 0;
        const durationMs = Math.round((Number.parseFloat(attributes.get('time') ?? '0') || 0) * 1000);
        summary.total += tests;
        summary.failed += failures + errors;
        summary.skipped += skipped;
        summary.durationMs += durationMs;
        summary.suites.push({ name: attributes.get('name') ?? file, tests, failures: failures + errors, skipped, durationMs });
      }
      for (const testCase of xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
        const attributes = xmlAttributeMap(testCase[1]);
        const body = testCase[2] ?? '';
        const failureTag = body.match(/<(failure|error)\b/);
        const skipped = /<skipped\b/.test(body);
        if (failureTag) {
          const failureBody = body.slice(failureTag.index);
          summary.failures.push({
            className: attributes.get('classname') ?? '',
            name: attributes.get('name') ?? 'Unknown test',
            type: failureTag[1],
            message: xmlChildText(failureBody, failureTag[1]) ?? `${failureTag[1]} reported without a message`,
          });
        } else if (!skipped) summary.passed += 1;
      }
    } catch { /* A malformed/unreadable XML file must not be treated as test evidence. */ }
  }
  if (parsedFiles === 0) return null;
  summary.failed = Math.max(summary.failed, summary.failures.length);
  summary.passed = Math.max(summary.passed, summary.total - summary.failed - summary.skipped);
  summary.failures = summary.failures.slice(0, 30);
  return summary;
}

export function executionOutcome(exitCode, reportAvailable) {
  return { status: exitCode === 0 ? 'Passed' : 'Failed', reportAvailable: Boolean(reportAvailable) };
}

export function isFreshReportAvailable(reportDirectory, projectRoot) {
  try {
    const indexPath = path.join(reportDirectory, 'index.html');
    if (!fs.statSync(indexPath).isFile()) return false;
    const canonicalIndex = fs.realpathSync(indexPath);
    return isWithin(reportDirectory, canonicalIndex) && isWithin(projectRoot, canonicalIndex);
  } catch { return false; }
}

export async function executeGradleTest({ buildRoot, module, test, bindings, launchProcess = runChild }) {
  let run;
  let reportDirectory;
  let testResultsDirectory;
  const runId = randomUUID();
  let runtimeDirectory;
  let runtimePath;
  try {
    run = resolveGradleTestRun(module, test);
    reportDirectory = prepareReportDirectory(run.projectRoot, run.reportDirectory);
    testResultsDirectory = prepareReportDirectory(run.projectRoot, run.testResultsDirectory);
    if (test.context?.runtime) {
      runtimeDirectory = prepareRuntimeDirectory(run.projectRoot, runId);
      runtimePath = path.join(runtimeDirectory, 'runtime.json');
    }
  } catch (error) {
    return { status: 'Failed', reportAvailable: false, reportToken: null, runId, runtimeStatus: 'unavailable', runtime: null, preparationFailure: true, message: `Report preparation failed: ${error.message}` };
  }

  const wrapperJar = path.join(buildRoot, 'gradle', 'wrapper', 'gradle-wrapper.jar');
  const args = [
    '-jar', wrapperJar,
    `--project-dir=${buildRoot}`,
    '--no-daemon',
    '--console=plain',
    run.taskPath,
    '--tests',
    run.testFilter,
  ];
  const env = { ...process.env };
  for (const [name, value] of Object.entries(bindings)) env[`ORG_GRADLE_PROJECT_${name}`] = value;
  if (runtimePath) {
    env['ORG_GRADLE_PROJECT_help.runtime.runId'] = runId;
    env['ORG_GRADLE_PROJECT_help.runtime.path'] = runtimePath;
  }
  let processResult;
  const startedAt = Date.now();
  try {
    processResult = await launchProcess(javaExecutable(), args, { cwd: buildRoot, env });
  } catch (error) {
    cleanupRuntimeDirectory(runtimeDirectory, run.projectRoot);
    return { status: 'Failed', reportAvailable: false, reportToken: null, runId, runtimeStatus: 'unavailable', runtime: null, preparationFailure: false, message: `Could not start Gradle Test process: ${error.message}` };
  }

  const reportAvailable = isFreshReportAvailable(reportDirectory, run.projectRoot);
  const testResults = readGradleTestResults(testResultsDirectory, run.projectRoot);
  const outcome = executionOutcome(processResult.code, reportAvailable);
  const runtime = runtimePath ? readRuntimeArtifact({ projectRoot: run.projectRoot, runtimePath, expectedRunId: runId, expectedProjectPath: module.projectPath, expectedTestId: test.id, expectedRuntimeSchemaVersion: test.context.runtime.schemaVersion, expectedSteps: test.context.expectedWorkflow, expectedMigrations: test.context.expectedMigrations }) : null;
  cleanupRuntimeDirectory(runtimeDirectory, run.projectRoot);
  return {
    status: outcome.status,
    exitCode: processResult.code,
    reportAvailable: outcome.reportAvailable,
    reportToken: outcome.reportAvailable ? randomUUID() : null,
    reportDirectory: outcome.reportAvailable ? reportDirectory : null,
    taskPath: run.taskPath,
    testFilter: run.testFilter,
    processDurationMs: Date.now() - startedAt,
    testResults,
    runId,
    runtimeStatus: runtime?.runtimeStatus ?? 'unavailable',
    runtime: runtime?.runtimeStatus === 'complete' || runtime?.runtimeStatus === 'incomplete' ? runtime : null,
    runtimeValidationError: runtime?.runtimeStatus === 'invalid' ? String(runtime.validationError ?? 'Runtime artifact validation failed').slice(0, 300) : null,
    preparationFailure: false,
    message: outcome.status === 'Passed' ? undefined : `Gradle Test failed with exit code ${processResult.code ?? `signal ${processResult.signal}`}`,
  };
}

function prepareRuntimeDirectory(projectRoot, runId) {
  const relative = path.join('build', 'reports', 'help', 'runtime', runId);
  const target = resolveContainedPath(projectRoot, relative, { allowMissing: true, requireNonRoot: true });
  if (fs.existsSync(target)) throw new Error('Runtime artifact directory already exists for this run');
  const parent = path.dirname(target);
  fs.mkdirSync(parent, { recursive: true });
  const canonicalParent = fs.realpathSync(parent);
  if (!isWithin(projectRoot, canonicalParent)) throw new Error('Runtime artifact parent escaped its Project root');
  fs.mkdirSync(target);
  const canonical = fs.realpathSync(target);
  if (!isWithin(projectRoot, canonical) || canonical === projectRoot) throw new Error('Runtime artifact directory escaped its Project root');
  return canonical;
}

function cleanupRuntimeDirectory(directory, projectRoot) {
  if (!directory) return;
  try {
    if (!isWithin(projectRoot, directory) || directory === projectRoot) return;
    fs.rmSync(directory, { recursive: true, force: true });
  } catch { /* Runtime cleanup is diagnostic-only and never changes the Test result. */ }
}

const STEP_STATUSES = new Set(['not_started', 'in_progress', 'succeeded', 'failed', 'not_executed', 'not_collected']);
const EXECUTION_STATUSES = new Set(['succeeded', 'failed', 'not_executed', 'unknown']);
const HISTORY_STATUSES = new Set(['recorded', 'not_recorded', 'recording_failed', 'failed', 'not_applicable', 'unknown']);
const BLOCKED_KEYS = /password|credential|user(?:name)?|jdbc(?:url|property)|rawsql|environment|resultset|statement|secret/i;

function validateSafeTree(value, depth = 0) {
  if (depth > 8) throw new Error('Runtime payload nesting is too deep');
  if (value === null || typeof value === 'boolean' || typeof value === 'number') {
    if (typeof value === 'number' && !Number.isFinite(value)) throw new Error('Runtime payload contains a non-finite number');
    return;
  }
  if (typeof value === 'string') {
    if (value.length > 2000) throw new Error('Runtime payload contains an oversized string');
    if (/^\s*(?:jdbc:|select\b|insert\b|update\b|delete\b|create\s+|alter\s+|drop\s+)/i.test(value)) throw new Error('Runtime payload contains a prohibited raw connection or SQL string');
    return;
  }
  if (Array.isArray(value)) {
    if (value.length > 500) throw new Error('Runtime payload contains an oversized array');
    for (const item of value) validateSafeTree(item, depth + 1);
    return;
  }
  if (!value || typeof value !== 'object') throw new Error('Runtime payload contains an unsupported value');
  const keys = Object.keys(value);
  if (keys.length > 80) throw new Error('Runtime payload contains too many fields');
  for (const key of keys) {
    if (BLOCKED_KEYS.test(key)) throw new Error(`Runtime payload contains a prohibited field '${key}'`);
    validateSafeTree(value[key], depth + 1);
  }
}

function readRuntimeArtifact({ projectRoot, runtimePath, expectedRunId, expectedProjectPath, expectedTestId, expectedRuntimeSchemaVersion, expectedSteps, expectedMigrations }) {
  try {
    if (!fs.existsSync(runtimePath)) return null;
    const relative = path.relative(projectRoot, runtimePath);
    const validatedPath = resolveContainedPath(projectRoot, relative, { allowMissing: false, requireNonRoot: true });
    if (!fs.statSync(validatedPath).isFile()) throw new Error('Runtime artifact is not a regular file');
    const runtime = JSON.parse(fs.readFileSync(validatedPath, 'utf8'));
    if (!runtime || typeof runtime !== 'object' || Array.isArray(runtime)) throw new Error('Runtime payload must be an object');
    const allowed = new Set(['schemaVersion', 'projectPath', 'testId', 'runId', 'startedAt', 'completedAt', 'runtimeStatus', 'steps', 'migrations', 'schemaVerification', 'historyVerification', 'finalState']);
    for (const key of Object.keys(runtime)) if (!allowed.has(key)) throw new Error(`Unknown runtime field '${key}'`);
    if (runtime.schemaVersion !== expectedRuntimeSchemaVersion || runtime.projectPath !== expectedProjectPath || runtime.testId !== expectedTestId || runtime.runId !== expectedRunId) throw new Error('Runtime artifact identity does not match this execution');
    if (!['complete', 'incomplete'].includes(runtime.runtimeStatus)) throw new Error('Invalid Runtime status');
    if (typeof runtime.startedAt !== 'string' || !Number.isFinite(Date.parse(runtime.startedAt))) throw new Error('Invalid Runtime start time');
    if (runtime.completedAt !== undefined && (typeof runtime.completedAt !== 'string' || !Number.isFinite(Date.parse(runtime.completedAt)))) throw new Error('Invalid Runtime completion time');
    const expectedRuntimeSteps = expectedSteps.filter((step) => step.kind !== 'group');
    if (!Array.isArray(runtime.steps) || runtime.steps.length !== expectedRuntimeSteps.length || !Array.isArray(runtime.migrations ?? [])) throw new Error('Runtime steps and migrations must match the declared contract');
    const expectedById = new Map(expectedRuntimeSteps.map((step, index) => [step.id, { ...step, order: index + 1 }]));
    const observedIds = new Set();
    for (const [index, step] of runtime.steps.entries()) {
      if (!step || typeof step !== 'object' || !expectedById.has(step.id) || observedIds.has(step.id)) throw new Error('Runtime contains an unknown or duplicate workflow step');
      const expected = expectedById.get(step.id);
      if (step.id !== expectedRuntimeSteps[index]?.id || step.order !== expected.order || step.parentId !== expected.parentId || !STEP_STATUSES.has(step.status)) throw new Error('Runtime workflow step does not match its declared order or parent');
      for (const key of Object.keys(step)) if (!['id', 'parentId', 'order', 'status', 'startedAt', 'completedAt', 'durationMs', 'data'].includes(key)) throw new Error(`Unknown Runtime step field '${key}'`);
      observedIds.add(step.id);
    }
    for (const migration of runtime.migrations ?? []) {
      if (!migration || typeof migration !== 'object' || typeof migration.migrationId !== 'string') throw new Error('Invalid Runtime migration entry');
      if (!expectedMigrations.some((expected) => expected.id === migration.migrationId)) throw new Error('Runtime contains an undeclared Migration identity');
      if (!EXECUTION_STATUSES.has(migration.executionStatus) || !HISTORY_STATUSES.has(migration.historyStatus)) throw new Error('Invalid Runtime migration status');
      if (!Number.isSafeInteger(migration.registrationOrder) || migration.registrationOrder < 1) throw new Error('Invalid Runtime migration order');
      if (migration.batch !== undefined && (!Number.isSafeInteger(migration.batch) || migration.batch < 1)) throw new Error('Invalid Runtime migration batch');
      for (const field of ['operationType', 'target']) {
        const value = migration[field];
        if (value !== undefined && value !== 'not_collected' && (
          !value || typeof value !== 'object' || Array.isArray(value) ||
          Object.keys(value).some((key) => !['value', 'provenance'].includes(key)) ||
          typeof value.value !== 'string' || !value.value.trim() ||
          !['declared', 'observed'].includes(value.provenance)
        )) throw new Error(`Invalid Runtime migration ${field}`);
      }
      for (const key of Object.keys(migration)) if (!['migrationId', 'registrationOrder', 'operationType', 'target', 'executionStatus', 'historyStatus', 'batch'].includes(key)) throw new Error(`Unknown Runtime migration field '${key}'`);
    }
    if (runtime.historyVerification !== undefined) {
      if (!runtime.historyVerification || !Array.isArray(runtime.historyVerification.rows)) throw new Error('Invalid Runtime history verification');
      for (const row of runtime.historyVerification.rows) {
        if (!row || typeof row.migrationId !== 'string' || !expectedMigrations.some((expected) => expected.id === row.migrationId) || !Number.isSafeInteger(row.batch) || row.batch < 1) throw new Error('Runtime history contains an undeclared or invalid Migration row');
      }
    }
    if (runtime.runtimeStatus === 'complete' && runtime.steps.some((step) => ['not_started', 'in_progress', 'failed'].includes(step.status))) throw new Error('Complete Runtime contains unfinished or failed workflow steps');
    validateSafeTree(runtime.steps); validateSafeTree(runtime.migrations ?? []);
    for (const key of ['schemaVerification', 'historyVerification', 'finalState']) if (runtime[key] !== undefined) validateSafeTree(runtime[key]);
    return runtime;
  } catch (error) {
    return { runtimeStatus: 'invalid', validationError: error.message };
  }
}
