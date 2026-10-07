import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { loadHelpDefinition } from '../src/help-file-loader.mjs';
import { buildRegistry, findRegisteredAction, publicModules } from '../src/help-registry.mjs';
import { validateParameters } from '../src/parameter-validator.mjs';
import { resolveGradleTestRun } from '../src/runner-resolver.mjs';
import { executionOutcome, prepareReportDirectory, executeGradleTest, isFreshReportAvailable } from '../src/gradle-test-runner.mjs';
import { resolveContainedPath } from '../src/path-safety.mjs';
import { createHelpServer } from '../src/server.mjs';

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'help-plan-v2-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}

function writeDefinition(projectDir, value) {
  fs.writeFileSync(path.join(projectDir, 'help.json'), JSON.stringify(value));
}

function definition() {
  return {
    schemaVersion: 1,
    module: { title: 'Migration', description: 'Integration test', icon: 'database' },
    tests: [{
      id: 'migration-system-integration', title: 'Migration Integration',
      runner: { type: 'gradle-test', testFilter: 'sample.IntegrationTest.runs' },
      parameters: [
        { id: 'db-name', label: 'Database', type: 'text', required: true, secret: false, validation: { notBlank: true }, binding: { type: 'gradle-project-property', name: 'migration.test.db.name' } },
        { id: 'db-password', label: 'Password', type: 'password', required: true, allowEmpty: true, secret: true, binding: { type: 'gradle-project-property', name: 'migration.test.db.password' } },
        { id: 'refresh', label: 'Refresh', type: 'boolean', required: true, default: false, secret: false, binding: { type: 'gradle-project-property', name: 'migration.test.db.refresh' } },
      ],
      report: { type: 'gradle-html', path: 'build/reports/tests/test' },
    }],
  };
}

function runtimeContext() {
  return {
    purpose: 'Test the runtime contract', scenario: 'One observed step', preconditions: [],
    expectedWorkflow: [{ id: 'prepare-config', label: 'Prepare configuration' }],
    expectedMigrations: [{ id: 'create_users', order: 1 }], verificationCategories: ['history'],
    runtime: { type: 'test-runtime-json', schemaVersion: 1 },
  };
}

function runtimeArtifact(runId, projectPath = ':fixture', testId = 'migration-system-integration') {
  return {
    schemaVersion: 1, projectPath, testId, runId,
    startedAt: '2026-10-03T10:00:00.000Z', completedAt: '2026-10-03T10:00:01.000Z', runtimeStatus: 'complete',
    steps: [{ id: 'prepare-config', order: 1, status: 'succeeded' }],
    migrations: [{ migrationId: 'create_users', registrationOrder: 1, operationType: 'not_collected', target: 'not_collected', executionStatus: 'succeeded', historyStatus: 'recorded', batch: 1 }],
    historyVerification: { rows: [{ migrationId: 'create_users', batch: 1 }] },
  };
}

test('loads declarative definition and preserves exact Gradle property binding names', (t) => {
  const projectDir = fixture(t);
  writeDefinition(projectDir, definition());
  const loaded = loadHelpDefinition({ projectPath: ':data_base:migration', projectDir });
  assert.equal(loaded.projectPath, ':data_base:migration');
  assert.equal(loaded.module.title, 'Migration');
  assert.equal(loaded.tests[0].parameters[0].binding.name, 'migration.test.db.name');
  assert.equal(loaded.tests[0].parameters[1].binding.name, 'migration.test.db.password');
});

test('loads the real migration Help Definition without changing its Gradle test contract', () => {
  const repositoryRoot = path.resolve(import.meta.dirname, '../..');
  const loaded = loadHelpDefinition({ projectPath: ':data_base:migration', projectDir: path.join(repositoryRoot, 'data_base', 'migration') });
  assert.equal(loaded.tests[0].id, 'migration-system-integration');
  assert.equal(loaded.tests[0].runner.testFilter, 'gog.my_project.data_base.migration.integration.MigrationSystemIntegrationTest.runsTheMigrationSubsystemLifecycleAgainstMySql');
  assert.equal(loaded.tests[0].parameters.find((item) => item.id === 'refresh-database').binding.name, 'migration.test.db.refresh');
  assert.equal(loaded.schemaVersion, 2);
  assert.equal(loaded.tests[0].context.runtime.type, 'test-runtime-json');
  assert.equal(loaded.tests[1].context.expectedMigrations.length, 7);
});

test('keeps schema v1 compatible and validates schema v2 Context strictly', (t) => {
  const projectDir = fixture(t);
  writeDefinition(projectDir, definition());
  assert.equal(loadHelpDefinition({ projectPath: ':fixture', projectDir }).schemaVersion, 1);
  const v2 = definition(); v2.schemaVersion = 2; v2.tests[0].context = runtimeContext();
  writeDefinition(projectDir, v2);
  assert.equal(loadHelpDefinition({ projectPath: ':fixture', projectDir }).tests[0].context.expectedWorkflow[0].id, 'prepare-config');
  v2.tests[0].context.unexpected = true;
  writeDefinition(projectDir, v2);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /unknown field 'unexpected'/);
  delete v2.tests[0].context.unexpected; v2.tests[0].context.expectedWorkflow.push(v2.tests[0].context.expectedWorkflow[0]);
  writeDefinition(projectDir, v2);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /duplicate workflow step/);
});

test('strict schema rejects unknown fields, duplicate ids and duplicate bindings', (t) => {
  const projectDir = fixture(t);
  const unknown = definition(); unknown.unexpected = true;
  writeDefinition(projectDir, unknown);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /unknown field 'unexpected'/);

  const duplicate = definition(); duplicate.tests.push(duplicate.tests[0]);
  writeDefinition(projectDir, duplicate);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /duplicate Test ID/);

  const duplicateBinding = definition();
  duplicateBinding.tests[0].parameters[1].binding.name = duplicateBinding.tests[0].parameters[0].binding.name;
  writeDefinition(projectDir, duplicateBinding);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /duplicate parameter binding/);
});

test('rejects missing, unreadable and escaping help definitions', (t) => {
  const projectDir = fixture(t);
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /does not exist/);
  fs.writeFileSync(path.join(projectDir, 'help.json'), '{');
  assert.throws(() => loadHelpDefinition({ projectPath: ':fixture', projectDir }), /cannot be read or parsed/);
  assert.throws(() => resolveContainedPath(projectDir, '../outside.json'), /Path traversal/);
  assert.throws(() => resolveContainedPath(projectDir, path.resolve(projectDir, 'absolute')), /Absolute paths/);
});

test('registry exposes build/run actions for projects without help.json and keeps test IDs project-scoped', (t) => {
  const root = fixture(t);
  const one = path.join(root, 'one'); const two = path.join(root, 'two'); const noHelp = path.join(root, 'no-help');
  for (const directory of [one, two, noHelp]) fs.mkdirSync(directory);
  writeDefinition(one, definition()); writeDefinition(two, definition());
  const registry = buildRegistry([
    { projectPath: ':one', projectDir: one, hasBuildTask: true, hasRunTask: true },
    { projectPath: ':two', projectDir: two, hasBuildTask: true, hasRunTask: false },
    { projectPath: ':opt-out', projectDir: noHelp, hasBuildTask: true, hasRunTask: false },
  ]);
  assert.deepEqual([...registry.keys()], [':one', ':two', ':opt-out']);
  const publishedModules = publicModules(registry);
  assert.equal(publishedModules.length, 3);
  assert.deepEqual(publishedModules.find(({ projectPath }) => projectPath === ':opt-out').actions.map(({ id }) => id), ['build']);
  assert.deepEqual(registry.get(':opt-out').module.actions.map(({ id }) => id), ['build']);
  assert.deepEqual(registry.get(':one').module.actions.map(({ id }) => id), ['build', 'run']);
  assert.equal(registry.get(':opt-out').tests.length, 0);
  assert.equal(findRegisteredAction(registry, ':opt-out', 'build').action.task, 'build');
  assert.equal(findRegisteredAction(registry, ':opt-out', 'run'), null);
  assert.throws(() => buildRegistry([{ projectPath: ':same', projectDir: one }, { projectPath: ':same', projectDir: two }]), /Duplicate Gradle Project path/);
});

test('HTTP action endpoint runs the generated build action for a project without help.json', async (t) => {
  const projectDir = fixture(t);
  const registry = buildRegistry([
    { projectPath: ':without-help', projectDir, hasBuildTask: true, hasRunTask: false },
  ]);
  let executedAction;
  const server = createHelpServer({
    registry,
    runGradleAction: async (input) => {
      executedAction = input;
      return { status: 'Passed', exitCode: 0, taskPath: ':without-help:build' };
    },
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  t.after(() => { server.closeAllConnections(); server.close(); });

  const response = await fetch(`http://127.0.0.1:${server.address().port}/api/actions`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ projectPath: ':without-help', actionId: 'build' }),
  });
  const payload = await response.json();

  assert.equal(response.status, 200);
  assert.equal(payload.executionStatus, 'Passed');
  assert.equal(executedAction.module.projectPath, ':without-help');
  assert.equal(executedAction.action.task, 'build');
});

test('parameters validate by declared type and preserve submitted strings exactly', (t) => {
  const projectDir = fixture(t); writeDefinition(projectDir, definition());
  const loaded = loadHelpDefinition({ projectPath: ':migration', projectDir });
  const values = validateParameters(loaded.tests[0], { 'db-name': ' test_db ', 'db-password': '', refresh: true });
  assert.equal(values['migration.test.db.name'], ' test_db ');
  assert.equal(values['migration.test.db.password'], '');
  assert.equal(values['migration.test.db.refresh'], 'true');
  assert.throws(() => validateParameters(loaded.tests[0], { 'db-name': ' ', refresh: false }), /must not be blank/);
  assert.throws(() => validateParameters(loaded.tests[0], { 'db-name': 'db', 'db-password': '', refresh: 'true' }), /must be boolean/);
  assert.throws(() => validateParameters(loaded.tests[0], { 'db-name': 'db', 'db-password': '', refresh: false, extra: 1 }), /unknown parameter/);
});

test('runner derives only the standard test task and resolves reports beneath Project root', (t) => {
  const projectRoot = fixture(t); const config = definition().tests[0];
  const module = { projectPath: ':', helpProjectRoot: projectRoot };
  assert.equal(resolveGradleTestRun(module, config).taskPath, ':test');
  assert.equal(resolveGradleTestRun({ ...module, projectPath: ':data_base:migration' }, config).taskPath, ':data_base:migration:test');
  assert.equal(resolveGradleTestRun(module, config).testFilter, 'sample.IntegrationTest.runs');
  assert.throws(() => resolveGradleTestRun(module, { ...config, report: { path: '../escape', type: 'gradle-html' } }), /traversal/);
});

test('report refresh removes only the validated report directory and preserves unrelated files', (t) => {
  const projectRoot = fixture(t); const reports = path.join(projectRoot, 'build', 'reports', 'tests', 'test');
  const unrelated = path.join(projectRoot, 'build', 'reports', 'unrelated.txt');
  fs.mkdirSync(reports, { recursive: true }); fs.writeFileSync(path.join(reports, 'index.html'), 'old');
  fs.writeFileSync(unrelated, 'keep');
  const prepared = prepareReportDirectory(projectRoot, reports);
  assert.equal(fs.existsSync(path.join(prepared, 'index.html')), false);
  assert.equal(fs.readFileSync(unrelated, 'utf8'), 'keep');
});

test('Gradle run reports all four status/report combinations from current-run files', async (t) => {
  const projectRoot = fixture(t); const config = definition().tests[0];
  const module = { projectPath: ':fixture', helpProjectRoot: projectRoot };
  const cases = [
    { exitCode: 0, fresh: true, status: 'Passed', available: true },
    { exitCode: 0, fresh: false, status: 'Passed', available: false },
    { exitCode: 1, fresh: true, status: 'Failed', available: true },
    { exitCode: 1, fresh: false, status: 'Failed', available: false },
  ];
  for (const scenario of cases) {
    const reportDirectory = path.join(projectRoot, 'build', 'reports', 'tests', 'test');
    fs.mkdirSync(reportDirectory, { recursive: true });
    fs.writeFileSync(path.join(reportDirectory, 'index.html'), 'stale report');
    const result = await executeGradleTest({
      buildRoot: projectRoot, module, test: config,
      bindings: { 'migration.test.db.name': 'fixture_db', 'migration.test.db.password': 'never-in-argv' },
      launchProcess: async (command, args, options) => {
        assert.equal(options.cwd, projectRoot);
        assert.ok(args.includes(':fixture:test'));
        assert.deepEqual(args.slice(args.indexOf('--tests'), args.indexOf('--tests') + 2), ['--tests', config.runner.testFilter]);
        assert.equal(args.includes('never-in-argv'), false);
        assert.equal(options.env['ORG_GRADLE_PROJECT_migration.test.db.name'], 'fixture_db');
        assert.equal(options.env['ORG_GRADLE_PROJECT_migration.test.db.password'], 'never-in-argv');
        assert.equal(fs.existsSync(path.join(reportDirectory, 'index.html')), false, 'the old report must be removed before Gradle starts');
        if (scenario.fresh) fs.writeFileSync(path.join(reportDirectory, 'index.html'), 'fresh report');
        return { code: scenario.exitCode, signal: null };
      },
    });
    assert.equal(result.status, scenario.status);
    assert.equal(result.reportAvailable, scenario.available);
    assert.equal(Boolean(result.reportToken), scenario.available);
    assert.equal(isFreshReportAvailable(reportDirectory, projectRoot), scenario.fresh);
  }
});

test('preparation failure fails before starting Gradle and prior report is not available', async (t) => {
  const projectRoot = fixture(t); const outside = fixture(t);
  const link = path.join(projectRoot, 'report-link');
  try { fs.symlinkSync(outside, link, 'junction'); } catch { t.skip('Symlink creation is unavailable in this environment'); return; }
  const module = { projectPath: ':fixture', helpProjectRoot: projectRoot };
  const testDefinition = { ...definition().tests[0], report: { type: 'gradle-html', path: 'report-link' } };
  let gradleStarted = false;
  const result = await executeGradleTest({ buildRoot: projectRoot, module, test: testDefinition, bindings: {}, launchProcess: async () => { gradleStarted = true; return { code: 0 }; } });
  assert.equal(result.status, 'Failed');
  assert.equal(result.reportAvailable, false);
  assert.equal(result.preparationFailure, true);
  assert.equal(gradleStarted, false);
});

test('execution status and fresh report availability form the four independent outcomes', () => {
  assert.deepEqual(executionOutcome(0, true), { status: 'Passed', reportAvailable: true });
  assert.deepEqual(executionOutcome(0, false), { status: 'Passed', reportAvailable: false });
  assert.deepEqual(executionOutcome(1, true), { status: 'Failed', reportAvailable: true });
  assert.deepEqual(executionOutcome(1, false), { status: 'Failed', reportAvailable: false });
});

test('Runtime artifact is run-scoped, validated and removed after API-side read', async (t) => {
  const projectDir = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext(); writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const result = await executeGradleTest({
    buildRoot: projectDir, module, test: module.tests[0], bindings: {},
    launchProcess: async (_command, _args, options) => {
      const runId = options.env['ORG_GRADLE_PROJECT_help.runtime.runId'];
      const runtimePath = options.env['ORG_GRADLE_PROJECT_help.runtime.path'];
      assert.match(runId, /^[0-9a-f-]{36}$/i);
      assert.ok(runtimePath.startsWith(path.join(projectDir, 'build', 'reports', 'help', 'runtime')));
      fs.writeFileSync(runtimePath, JSON.stringify(runtimeArtifact(runId)));
      return { code: 1, signal: null };
    },
  });
  assert.equal(result.status, 'Failed');
  assert.equal(result.runtimeStatus, 'complete');
  assert.equal(result.runtime.runId, result.runId);
  assert.equal(result.runtime.historyVerification.rows[0].batch, 1);
  assert.equal(fs.existsSync(path.join(projectDir, 'build', 'reports', 'help', 'runtime', result.runId)), false);
});

test('missing and mismatched Runtime artifacts remain unavailable or invalid', async (t) => {
  const projectDir = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext(); writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const missing = await executeGradleTest({ buildRoot: projectDir, module, test: module.tests[0], bindings: {}, launchProcess: async () => ({ code: 0, signal: null }) });
  assert.equal(missing.runtimeStatus, 'unavailable');
  assert.equal(missing.runtime, null);

  const mismatch = await executeGradleTest({
    buildRoot: projectDir, module, test: module.tests[0], bindings: {},
    launchProcess: async (_command, _args, options) => {
      const runId = options.env['ORG_GRADLE_PROJECT_help.runtime.runId'];
      fs.writeFileSync(options.env['ORG_GRADLE_PROJECT_help.runtime.path'], JSON.stringify(runtimeArtifact('00000000-0000-0000-0000-000000000000')));
      return { code: 0, signal: null };
    },
  });
  assert.equal(mismatch.runtimeStatus, 'invalid');
  assert.equal(mismatch.runtime, null);
});

test('rejects reordered workflow and credential fields in Runtime artifacts', async (t) => {
  const projectDir = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext();
  raw.tests[0].context.expectedWorkflow.push({ id: 'final-state', label: 'Final state' });
  writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const result = await executeGradleTest({
    buildRoot: projectDir, module, test: module.tests[0], bindings: {},
    launchProcess: async (_command, _args, options) => {
      const payload = runtimeArtifact(options.env['ORG_GRADLE_PROJECT_help.runtime.runId']);
      payload.steps = [
        { id: 'final-state', order: 2, status: 'succeeded' },
        { id: 'prepare-config', order: 1, status: 'succeeded' },
      ];
      fs.writeFileSync(options.env['ORG_GRADLE_PROJECT_help.runtime.path'], JSON.stringify(payload));
      return { code: 0, signal: null };
    },
  });
  assert.equal(result.runtimeStatus, 'invalid');
  assert.equal(result.runtime, null);

  const credentialProject = fixture(t); const credentialDefinition = definition();
  credentialDefinition.schemaVersion = 2; credentialDefinition.tests[0].context = runtimeContext();
  writeDefinition(credentialProject, credentialDefinition);
  const credentialModule = loadHelpDefinition({ projectPath: ':fixture', projectDir: credentialProject });
  const credentialResult = await executeGradleTest({
    buildRoot: credentialProject, module: credentialModule, test: credentialModule.tests[0], bindings: {},
    launchProcess: async (_command, _args, options) => {
      const payload = runtimeArtifact(options.env['ORG_GRADLE_PROJECT_help.runtime.runId']);
      payload.finalState = { username: 'should-not-be-published' };
      fs.writeFileSync(options.env['ORG_GRADLE_PROJECT_help.runtime.path'], JSON.stringify(payload));
      return { code: 0, signal: null };
    },
  });
  assert.equal(credentialResult.runtimeStatus, 'invalid');
  assert.equal(credentialResult.runtime, null);
});

test('cleans the run-scoped Runtime directory when Gradle cannot start', async (t) => {
  const projectDir = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext(); writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const result = await executeGradleTest({
    buildRoot: projectDir, module, test: module.tests[0], bindings: {},
    launchProcess: async () => { throw new Error('simulated process launch failure'); },
  });
  assert.equal(result.status, 'Failed');
  assert.equal(result.runtimeStatus, 'unavailable');
  assert.equal(fs.existsSync(path.join(projectDir, 'build', 'reports', 'help', 'runtime', result.runId)), false);
});

test('Runtime path preparation rejects a symlink that escapes the project root before Gradle starts', async (t) => {
  const projectDir = fixture(t); const outside = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext(); writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const link = path.join(projectDir, 'build', 'reports', 'help');
  fs.mkdirSync(path.dirname(link), { recursive: true });
  try { fs.symlinkSync(outside, link, 'junction'); } catch { t.skip('Symlink creation is unavailable in this environment'); return; }
  let started = false;
  const result = await executeGradleTest({ buildRoot: projectDir, module, test: module.tests[0], bindings: {}, launchProcess: async () => { started = true; return { code: 0 }; } });
  assert.equal(result.preparationFailure, true);
  assert.equal(result.runtimeStatus, 'unavailable');
  assert.equal(started, false);
});

test('HTTP API lists registered metadata, returns Runtime and rejects task injection from a request', async (t) => {
  const projectDir = fixture(t); const raw = definition(); raw.schemaVersion = 2; raw.tests[0].context = runtimeContext(); writeDefinition(projectDir, raw);
  const module = loadHelpDefinition({ projectPath: ':fixture', projectDir });
  const apiRuntime = runtimeArtifact('11111111-1111-1111-1111-111111111111');
  const server = createHelpServer({
    registry: new Map([[':fixture', module]]),
    runGradleTest: async () => ({ status: 'Passed', exitCode: 0, reportAvailable: false, reportToken: null, runId: apiRuntime.runId, runtimeStatus: 'complete', runtime: apiRuntime }),
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  t.after(() => { server.closeAllConnections(); server.close(); });
  const address = server.address(); const base = `http://127.0.0.1:${address.port}`;
  const listResponse = await fetch(`${base}/api/modules`);
  assert.equal(listResponse.status, 200);
  const listed = await listResponse.json();
  assert.equal(listed.modules[0].tests[0].parameters[0].binding.name, 'migration.test.db.name');
  assert.equal(listed.modules[0].tests[0].context.purpose, module.tests[0].context.purpose);
  const runResponse = await fetch(`${base}/api/runs`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      projectPath: ':fixture', testId: 'migration-system-integration',
      parameters: { 'db-name': 'fixture_db', 'db-password': '', refresh: false },
    }),
  });
  const runPayload = await runResponse.json();
  assert.equal(runResponse.status, 200);
  assert.equal(runPayload.runId, apiRuntime.runId);
  assert.equal(runPayload.runtimeStatus, 'complete');
  assert.equal(runPayload.runtime.historyVerification.rows[0].migrationId, 'create_users');
  assert.equal(runPayload.executionStatus, 'Passed');
  const injectionResponse = await fetch(`${base}/api/runs`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ projectPath: ':fixture', testId: 'migration-system-integration', task: ':other:clean', parameters: {} }),
  });
  assert.equal(injectionResponse.status, 400);
  assert.match((await injectionResponse.json()).error, /Unknown request field 'task'/);
});
