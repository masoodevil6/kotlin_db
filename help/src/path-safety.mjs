import fs from 'node:fs';
import path from 'node:path';

export function isWithin(parent, candidate) {
  const relative = path.relative(parent, candidate);
  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}

export function canonicalProjectRoot(projectDir) {
  const root = fs.realpathSync(projectDir);
  if (!fs.statSync(root).isDirectory()) throw new Error(`Gradle project directory is not a directory: ${projectDir}`);
  return root;
}

export function resolveContainedPath(root, relativePath, { allowMissing = false, requireNonRoot = false } = {}) {
  if (typeof relativePath !== 'string' || relativePath.length === 0 || relativePath.includes('\0')) {
    throw new Error('Path must be a non-empty relative path');
  }
  if (path.isAbsolute(relativePath) || path.win32.isAbsolute(relativePath)) {
    throw new Error('Absolute paths are not allowed');
  }
  const segments = relativePath.split(/[\\/]+/);
  if (segments.some((segment) => segment === '..' || segment === '')) {
    throw new Error('Path traversal or empty path segment is not allowed');
  }

  const candidate = path.resolve(root, ...segments);
  if (!isWithin(root, candidate) || (requireNonRoot && candidate === root)) {
    throw new Error('Path must resolve below its allowed root');
  }

  let existing = candidate;
  while (!fs.existsSync(existing)) {
    const parent = path.dirname(existing);
    if (parent === existing) throw new Error('Could not find an existing path ancestor');
    existing = parent;
  }
  const canonicalExisting = fs.realpathSync(existing);
  if (!isWithin(root, canonicalExisting)) throw new Error('Path resolves outside its allowed root');

  if (!allowMissing && !fs.existsSync(candidate)) throw new Error(`Path does not exist: ${relativePath}`);
  if (fs.existsSync(candidate)) {
    const canonicalCandidate = fs.realpathSync(candidate);
    if (!isWithin(root, canonicalCandidate) || (requireNonRoot && canonicalCandidate === root)) {
      throw new Error('Path resolves outside its allowed root');
    }
    return canonicalCandidate;
  }
  return candidate;
}

export function resolveReportDirectory(projectRoot, relativePath) {
  return resolveContainedPath(projectRoot, relativePath, { allowMissing: true, requireNonRoot: true });
}
