import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const MODULE_DIR = path.dirname(fileURLToPath(import.meta.url));
const DISCOVERY_SCRIPT = path.resolve(MODULE_DIR, '../gradle/project-discovery.init.gradle');

function javaExecutable() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  return process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', executable) : executable;
}

function runProcess(command, args, options, timeoutMs) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { ...options, shell: false, windowsHide: true });
    let stderr = '';
    let settled = false;
    const timer = setTimeout(() => {
      child.kill();
      finish(new Error(`Gradle discovery timed out after ${timeoutMs} ms`));
    }, timeoutMs);
    const finish = (error, result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      error ? reject(error) : resolve(result);
    };
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', (chunk) => { stderr = (stderr + chunk).slice(-16_000); });
    child.on('error', (error) => finish(new Error(`Could not start Gradle discovery: ${error.message}`)));
    child.on('close', (code, signal) => {
      if (code !== 0) {
        finish(new Error(`Gradle discovery exited with code ${code ?? `signal ${signal}`}${stderr ? `: ${stderr.trim()}` : ''}`));
      } else finish(null, { code });
    });
  });
}

export async function discoverGradleProjects({ buildRoot, timeoutMs = 180_000 }) {
  const root = fs.realpathSync(buildRoot);
  const wrapperJar = path.join(root, 'gradle', 'wrapper', 'gradle-wrapper.jar');
  const wrapperProperties = path.join(root, 'gradle', 'wrapper', 'gradle-wrapper.properties');
  if (!fs.existsSync(wrapperJar) || !fs.existsSync(wrapperProperties)) {
    throw new Error(`Gradle Wrapper files were not found under ${root}`);
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'help-gradle-discovery-'));
  const outputPath = path.join(tempDir, 'projects.json');
  try {
    const args = [
      '-jar', wrapperJar,
      '--init-script', DISCOVERY_SCRIPT,
      '--no-daemon',
      '--console=plain',
      '--dry-run',
      'help',
    ];
    await runProcess(javaExecutable(), args, {
      cwd: root,
      env: { ...process.env, HELP_DISCOVERY_BUILD_ROOT: root, HELP_DISCOVERY_OUTPUT: outputPath },
      stdio: ['ignore', 'ignore', 'pipe'],
    }, timeoutMs);
    if (!fs.existsSync(outputPath)) throw new Error('Gradle discovery completed without emitting the Project graph');
    const payload = JSON.parse(fs.readFileSync(outputPath, 'utf8'));
    if (!payload || !Array.isArray(payload.projects) || payload.projects.length === 0) {
      throw new Error('Gradle discovery emitted an invalid or empty Project graph');
    }
    const paths = new Set();
    return payload.projects.map((project) => {
      if (typeof project?.path !== 'string' || !/^:(?:[^:/\\\u0000-\u001f]+(?::[^:/\\\u0000-\u001f]+)*)?$/.test(project.path)) {
        throw new Error('Gradle discovery emitted an invalid Project path');
      }
      if (paths.has(project.path)) throw new Error(`Gradle discovery emitted duplicate Project path '${project.path}'`);
      paths.add(project.path);
      if (typeof project.projectDir !== 'string' || !path.isAbsolute(project.projectDir)) {
        throw new Error(`Gradle discovery emitted an invalid projectDir for '${project.path}'`);
      }
      if (typeof project.hasBuildTask !== 'boolean' || typeof project.hasRunTask !== 'boolean') {
        throw new Error(`Gradle discovery emitted invalid task capabilities for '${project.path}'`);
      }
      return {
        projectPath: project.path,
        projectDir: fs.realpathSync(project.projectDir),
        hasBuildTask: project.hasBuildTask,
        hasRunTask: project.hasRunTask,
      };
    });
  } catch (error) {
    throw new Error(`Gradle Project discovery failed: ${error.message}`, { cause: error });
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}
