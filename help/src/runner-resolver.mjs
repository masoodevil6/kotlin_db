import path from 'node:path';
import { resolveReportDirectory } from './path-safety.mjs';

export function resolveGradleTestRun(module, test) {
  if (test.runner.type !== 'gradle-test') throw new Error('Unsupported runner capability');
  const taskPath = module.projectPath === ':' ? ':test' : `${module.projectPath}:test`;
  const reportDirectory = resolveReportDirectory(module.helpProjectRoot, test.report.path);
  const testResultsDirectory = resolveReportDirectory(module.helpProjectRoot, 'build/test-results/test');
  return { cwd: null, taskPath, testFilter: test.runner.testFilter, reportDirectory, testResultsDirectory, projectRoot: module.helpProjectRoot };
}

export function reportRelativeFile(reportDirectory, filePath) {
  if (typeof filePath !== 'string' || filePath.length === 0 || filePath.includes('\0')) throw new Error('Invalid report file path');
  const normalized = filePath.replaceAll('\\', '/');
  if (normalized.startsWith('/') || /^[A-Za-z]:/.test(normalized) || normalized.split('/').some((segment) => segment === '..' || segment === '')) {
    throw new Error('Invalid report file path');
  }
  const resolved = path.resolve(reportDirectory, normalized);
  const relative = path.relative(reportDirectory, resolved);
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) throw new Error('Report file path escapes its report directory');
  return resolved;
}
