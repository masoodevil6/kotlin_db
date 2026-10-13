import path from 'node:path';
import { spawn } from 'node:child_process';

const MAX_OUTPUT_CHARS_PER_STREAM = 512 * 1024;
const CONSOLE_MODES = new Set(['plain', 'auto', 'rich', 'verbose']);
const GRADLE_OPTION_FIELDS = new Set(['rerunTasks', 'noConfigurationCache', 'info', 'console']);

export function validateGradleActionOptions(actionId, rawOptions) {
  if (rawOptions === undefined) {
    return actionId === 'test'
      ? { rerunTasks: false, noConfigurationCache: false, info: false, console: 'plain' }
      : undefined;
  }
  if (actionId !== 'test') throw new Error('Gradle options are supported only for module test actions');
  if (!rawOptions || typeof rawOptions !== 'object' || Array.isArray(rawOptions)) throw new Error('gradleOptions must be an object');
  for (const key of Object.keys(rawOptions)) if (!GRADLE_OPTION_FIELDS.has(key)) throw new Error(`Unknown Gradle option '${key}'`);
  for (const field of ['rerunTasks', 'noConfigurationCache', 'info']) {
    if (typeof rawOptions[field] !== 'boolean') throw new Error(`gradleOptions.${field} must be boolean`);
  }
  if (!CONSOLE_MODES.has(rawOptions.console)) throw new Error('gradleOptions.console is not supported');
  return {
    rerunTasks: rawOptions.rerunTasks,
    noConfigurationCache: rawOptions.noConfigurationCache,
    info: rawOptions.info,
    console: rawOptions.console,
  };
}

function javaExecutable() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  return process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', executable) : executable;
}

function runChild(command, args, { onOutput, ...options }) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { ...options, shell: false, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    const output = { stdout: '', stderr: '', truncated: false };
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    const collect = (stream, name) => {
      stream.on('data', (chunk) => {
        const remaining = MAX_OUTPUT_CHARS_PER_STREAM - output[name].length;
        const captured = remaining > 0 ? chunk.slice(0, remaining) : '';
        if (captured) {
          output[name] += captured;
          try { onOutput?.(name, captured); } catch { /* A disconnected output consumer must not stop Gradle. */ }
        }
        if (captured.length < chunk.length) output.truncated = true;
      });
    };
    collect(child.stdout, 'stdout');
    collect(child.stderr, 'stderr');
    child.on('error', reject);
    child.on('close', (code, signal) => resolve({
      code,
      signal,
      stdout: output.stdout,
      stderr: output.stderr,
      outputTruncated: output.truncated,
    }));
  });
}

export async function executeGradleAction({ buildRoot, module, action, bindings = {}, gradleOptions: rawGradleOptions, onOutput, launchProcess = runChild }) {
  if (!['build', 'run', 'test'].includes(action.id) || action.task !== action.id) throw new Error('Unsupported Gradle action');
  const gradleOptions = validateGradleActionOptions(action.id, rawGradleOptions);
  const taskPath = module.projectPath === ':' ? `:${action.task}` : `${module.projectPath}:${action.task}`;
  const args = [
    '-jar', path.join(buildRoot, 'gradle', 'wrapper', 'gradle-wrapper.jar'),
    `--project-dir=${buildRoot}`,
    '--no-daemon',
  ];
  if (action.id === 'test') {
    if (gradleOptions.rerunTasks) args.push('--rerun-tasks');
    if (gradleOptions.noConfigurationCache) args.push('--no-configuration-cache');
    args.push(`--console=${gradleOptions.console}`);
    if (gradleOptions.info) args.push('--info');
  } else {
    args.push('--console=plain');
  }
  args.push(taskPath);
  const startedAt = Date.now();
  try {
    const env = { ...process.env };
    for (const [name, value] of Object.entries(bindings)) env[`ORG_GRADLE_PROJECT_${name}`] = String(value);
    const result = await launchProcess(javaExecutable(), args, { cwd: buildRoot, env, onOutput });
    return {
      status: result.code === 0 ? 'Passed' : 'Failed',
      exitCode: result.code,
      taskPath,
      processDurationMs: Date.now() - startedAt,
      stdout: result.stdout ?? '',
      stderr: result.stderr ?? '',
      outputTruncated: result.outputTruncated ?? false,
      message: result.code === 0 ? undefined : `Gradle task failed with exit code ${result.code ?? `signal ${result.signal}`}`,
    };
  } catch (error) {
    return { status: 'Failed', exitCode: null, taskPath, processDurationMs: Date.now() - startedAt, stdout: '', stderr: '', outputTruncated: false, message: `Could not start Gradle task: ${error.message}` };
  }
}
