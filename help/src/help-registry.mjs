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
    const definition = fs.existsSync(definitionPath)
      ? loadHelpDefinition(project)
      : createDefaultDefinition(project);
    const declaredActions = new Map(definition.module.actions.map((action) => [action.id, action]));
    const actions = [];
    if (project.hasBuildTask ?? true) {
      actions.push(declaredActions.get('build') ?? defaultTaskAction('build'));
    }
    if (project.hasRunTask === true) {
      actions.push(declaredActions.get('run') ?? defaultTaskAction('run'));
    }
    definition.module.actions = actions;
    modules.set(project.projectPath, definition);
  }
  return modules;
}

function createDefaultDefinition(project) {
  const projectName = project.projectPath.split(':').filter(Boolean).at(-1) ?? 'Gradle';
  return {
    projectPath: project.projectPath,
    schemaVersion: 1,
    helpProjectRoot: fs.realpathSync(project.projectDir),
    module: {
      title: projectName,
      description: 'ماژول Gradle شناسایی‌شده در پروژه.',
      icon: 'module',
      actions: [],
      projectDependencies: [],
    },
    hasTestsCapability: false,
    tests: [],
  };
}

function defaultTaskAction(id) {
  return id === 'build'
    ? { id, title: 'ساخت ماژول', description: 'اجرای task استاندارد build برای این Gradle project.', task: id }
    : { id, title: 'اجرای ماژول', description: 'اجرای task استاندارد run برای این Gradle project.', task: id };
}

export function publicModules(registry) {
  return [...registry.values()].map((module) => ({
    projectPath: module.projectPath,
    module: module.module,
    hasTestsCapability: module.hasTestsCapability,
    actions: module.module.actions,
    tests: module.tests.map(({ id, title, description, parameters, context }) => ({ id, title, description, parameters, context })),
  }));
}

export function findRegisteredAction(registry, projectPath, actionId) {
  const module = registry.get(projectPath);
  if (!module) return null;
  const action = module.module.actions.find((candidate) => candidate.id === actionId);
  return action ? { module, action } : null;
}

export function findRegisteredTest(registry, projectPath, testId) {
  const module = registry.get(projectPath);
  if (!module) return null;
  const test = module.tests.find((candidate) => candidate.id === testId);
  return test ? { module, test } : null;
}
