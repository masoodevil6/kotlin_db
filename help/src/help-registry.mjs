import fs from 'node:fs';
import path from 'node:path';
import { loadHelpDefinition } from './help-file-loader.mjs';

export function buildRegistry(projects) {
  const modules = new Map();
  const paths = new Set();
  for (const project of projects) {
    if (paths.has(project.projectPath)) throw new Error(`Duplicate Gradle Project path '${project.projectPath}'`);
    paths.add(project.projectPath);
    const definitionPath = path.join(project.projectDir, 'help.json');
    if (!fs.existsSync(definitionPath)) continue;
    const definition = loadHelpDefinition(project);
    modules.set(project.projectPath, definition);
  }
  return modules;
}

export function publicModules(registry) {
  return [...registry.values()].map((module) => ({
    projectPath: module.projectPath,
    module: module.module,
    hasTestsCapability: module.hasTestsCapability,
    tests: module.tests.map(({ id, title, description, parameters, context }) => ({ id, title, description, parameters, context })),
  }));
}

export function findRegisteredTest(registry, projectPath, testId) {
  const module = registry.get(projectPath);
  if (!module) return null;
  const test = module.tests.find((candidate) => candidate.id === testId);
  return test ? { module, test } : null;
}
