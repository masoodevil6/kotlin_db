import fs from 'node:fs';
import { canonicalProjectRoot, isWithin, resolveContainedPath } from './path-safety.mjs';

const ROOT_FIELDS = new Set(['schemaVersion', 'module', 'tests']);
const ICONS = new Set(['database', 'module', 'code', 'test', 'tools']);
const PROPERTY_PATTERN = /^[A-Za-z_][A-Za-z0-9_.-]*$/;

function fail(where, message) { throw new Error(`${where}: ${message}`); }
function object(value, where) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(where, 'must be an object');
  return value;
}
function exactFields(value, allowed, where) {
  for (const key of Object.keys(value)) if (!allowed.has(key)) fail(where, `unknown field '${key}'`);
}
function nonBlank(value, where) {
  if (typeof value !== 'string' || value.trim().length === 0) fail(where, 'must be a non-blank string');
  return value;
}

function loadParameter(raw, index, ownerLabel, allowSelect = false) {
  const where = `${ownerLabel} parameter[${index}]`;
  object(raw, where);
  exactFields(raw, new Set(['id', 'label', 'description', 'type', 'required', 'allowEmpty', 'default', 'secret', 'minimum', 'maximum', 'validation', 'options', 'binding']), where);
  const id = nonBlank(raw.id, `${where}.id`);
  if (/[\u0000-\u001f\u007f]/.test(id)) fail(`${where}.id`, 'must not contain control characters');
  const label = nonBlank(raw.label, `${where}.label`);
  const description = raw.description === undefined ? undefined : nonBlank(raw.description, `${where}.description`);
  const supportedTypes = allowSelect
    ? ['text', 'password', 'number', 'boolean', 'select']
    : ['text', 'password', 'number', 'boolean'];
  if (!supportedTypes.includes(raw.type)) fail(`${where}.type`, 'unsupported parameter type');
  if (typeof raw.required !== 'boolean' || typeof raw.secret !== 'boolean') fail(where, 'required and secret must be booleans');
  if (raw.type === 'password' && raw.secret !== true) fail(where, 'password parameters must be marked secret');
  if (raw.type !== 'password' && raw.secret) fail(where, 'only password parameters may be secret');
  if (raw.allowEmpty !== undefined && typeof raw.allowEmpty !== 'boolean') fail(`${where}.allowEmpty`, 'must be boolean');
  if (!['text', 'password'].includes(raw.type) && raw.allowEmpty !== undefined) fail(`${where}.allowEmpty`, 'is supported only for text and password parameters');
  if (raw.default !== undefined) {
    if (raw.type === 'password') fail(`${where}.default`, 'password defaults are not allowed');
    const matches = raw.type === 'boolean' ? typeof raw.default === 'boolean'
      : raw.type === 'number' ? Number.isSafeInteger(raw.default)
        : typeof raw.default === 'string';
    if (!matches) fail(`${where}.default`, 'does not match the parameter type');
  }
  for (const field of ['minimum', 'maximum']) {
    if (raw[field] !== undefined && (!Number.isSafeInteger(raw[field]) || raw.type !== 'number')) fail(`${where}.${field}`, 'must be an integer bound for a number parameter');
  }
  if (raw.minimum !== undefined && raw.maximum !== undefined && raw.minimum > raw.maximum) fail(where, 'minimum must not exceed maximum');

  let options;
  if (raw.type === 'select') {
    if (!Array.isArray(raw.options) || raw.options.length === 0) fail(`${where}.options`, 'must be a non-empty array for select parameters');
    options = raw.options.map((option, optionIndex) => {
      const optionWhere = `${where}.options[${optionIndex}]`;
      object(option, optionWhere);
      exactFields(option, new Set(['value', 'label']), optionWhere);
      const value = nonBlank(option.value, `${optionWhere}.value`);
      if (/[\u0000-\u001f\u007f]/.test(value)) fail(`${optionWhere}.value`, 'must not contain control characters');
      return { value, label: nonBlank(option.label, `${optionWhere}.label`) };
    });
    if (new Set(options.map(({ value }) => value)).size !== options.length) fail(`${where}.options`, 'contains duplicate values');
    if (raw.default !== undefined && !options.some(({ value }) => value === raw.default)) fail(`${where}.default`, 'must match one of the select option values');
  } else if (raw.options !== undefined) {
    fail(`${where}.options`, 'is supported only for select parameters');
  }

  let validation;
  if (raw.validation !== undefined) {
    object(raw.validation, `${where}.validation`);
    exactFields(raw.validation, new Set(['pattern', 'notBlank']), `${where}.validation`);
    validation = {};
    if (raw.validation.pattern !== undefined) {
      if (!['text', 'password'].includes(raw.type)) fail(`${where}.validation.pattern`, 'is supported only for text and password parameters');
      if (typeof raw.validation.pattern !== 'string' || raw.validation.pattern.length > 256) fail(`${where}.validation.pattern`, 'must be a string of at most 256 characters');
      try { new RegExp(raw.validation.pattern); } catch { fail(`${where}.validation.pattern`, 'is not a valid regular expression'); }
      validation.pattern = raw.validation.pattern;
    }
    if (raw.validation.notBlank !== undefined) {
      if (!['text', 'password'].includes(raw.type)) fail(`${where}.validation.notBlank`, 'is supported only for text and password parameters');
      if (typeof raw.validation.notBlank !== 'boolean') fail(`${where}.validation.notBlank`, 'must be boolean');
      validation.notBlank = raw.validation.notBlank;
    }
  }

  object(raw.binding, `${where}.binding`);
  exactFields(raw.binding, new Set(['type', 'name']), `${where}.binding`);
  if (raw.binding.type !== 'gradle-project-property') fail(`${where}.binding.type`, 'only gradle-project-property is supported');
  if (typeof raw.binding.name !== 'string' || !PROPERTY_PATTERN.test(raw.binding.name)) fail(`${where}.binding.name`, 'is not a valid Gradle project property name');
  return { id, label, description, type: raw.type, required: raw.required, allowEmpty: raw.allowEmpty ?? false, default: raw.default, secret: raw.secret, minimum: raw.minimum, maximum: raw.maximum, validation, options, binding: { type: raw.binding.type, name: raw.binding.name } };
}

function loadContext(raw, where) {
  object(raw, where);
  exactFields(raw, new Set(['purpose', 'scenario', 'preconditions', 'expectedWorkflow', 'expectedMigrations', 'verificationCategories', 'runtime']), where);
  const purpose = nonBlank(raw.purpose, `${where}.purpose`);
  const scenario = nonBlank(raw.scenario, `${where}.scenario`);
  if (!Array.isArray(raw.preconditions) || !Array.isArray(raw.expectedWorkflow) || !Array.isArray(raw.expectedMigrations) || !Array.isArray(raw.verificationCategories)) {
    fail(where, 'preconditions, expectedWorkflow, expectedMigrations and verificationCategories must be arrays');
  }
  const preconditions = raw.preconditions.map((value, i) => nonBlank(value, `${where}.preconditions[${i}]`));
  const expectedWorkflow = raw.expectedWorkflow.map((item, i) => {
    const itemWhere = `${where}.expectedWorkflow[${i}]`;
    object(item, itemWhere);
    exactFields(item, new Set(['id', 'label', 'kind', 'parentId']), itemWhere);
    const id = nonBlank(item.id, `${itemWhere}.id`);
    const kind = item.kind ?? 'step';
    if (!['group', 'step'].includes(kind)) fail(`${itemWhere}.kind`, "must be 'group' or 'step'");
    const parentId = item.parentId === undefined ? undefined : nonBlank(item.parentId, `${itemWhere}.parentId`);
    return { id, label: nonBlank(item.label, `${itemWhere}.label`), kind, parentId };
  });
  const workflowIds = new Set();
  const workflowById = new Map();
  for (const step of expectedWorkflow) {
    if (workflowIds.has(step.id)) fail(where, `duplicate workflow step '${step.id}'`);
    workflowIds.add(step.id);
    workflowById.set(step.id, step);
  }
  for (const [index, node] of expectedWorkflow.entries()) {
    if (node.parentId === undefined) continue;
    const parent = workflowById.get(node.parentId);
    if (!parent) fail(`${where}.expectedWorkflow[${index}].parentId`, `references unknown workflow node '${node.parentId}'`);
    if (parent.kind !== 'group') fail(`${where}.expectedWorkflow[${index}].parentId`, 'must reference a group node');
    if (node.parentId === node.id) fail(`${where}.expectedWorkflow[${index}].parentId`, 'cannot reference itself');
  }
  for (const node of expectedWorkflow) {
    const ancestors = new Set([node.id]);
    let parentId = node.parentId;
    while (parentId !== undefined) {
      if (ancestors.has(parentId)) fail(where, `workflow tree contains a cycle at '${parentId}'`);
      ancestors.add(parentId);
      parentId = workflowById.get(parentId)?.parentId;
    }
  }
  const hasStepDescendant = (groupId) => expectedWorkflow.some((node) => {
    if (node.kind !== 'step') return false;
    let parentId = node.parentId;
    while (parentId !== undefined) {
      if (parentId === groupId) return true;
      parentId = workflowById.get(parentId)?.parentId;
    }
    return false;
  });
  for (const node of expectedWorkflow) {
    if (node.kind === 'group' && !hasStepDescendant(node.id)) fail(where, `workflow group '${node.id}' has no step descendants`);
  }
  const expectedMigrations = raw.expectedMigrations.map((item, i) => {
    const itemWhere = `${where}.expectedMigrations[${i}]`;
    object(item, itemWhere);
    exactFields(item, new Set(['id', 'order', 'group', 'operationType', 'target']), itemWhere);
    if (!Number.isSafeInteger(item.order) || item.order < 1) fail(`${itemWhere}.order`, 'must be a positive integer');
    return {
      id: nonBlank(item.id, `${itemWhere}.id`), order: item.order,
      group: item.group === undefined ? undefined : nonBlank(item.group, `${itemWhere}.group`),
      operationType: item.operationType === undefined ? undefined : nonBlank(item.operationType, `${itemWhere}.operationType`),
      target: item.target === undefined ? undefined : nonBlank(item.target, `${itemWhere}.target`),
    };
  });
  const migrationIds = new Set();
  for (const migration of expectedMigrations) {
    if (migrationIds.has(migration.id)) fail(where, `duplicate expected migration '${migration.id}'`);
    migrationIds.add(migration.id);
  }
  const verificationCategories = raw.verificationCategories.map((value, i) => nonBlank(value, `${where}.verificationCategories[${i}]`));
  object(raw.runtime, `${where}.runtime`);
  exactFields(raw.runtime, new Set(['type', 'schemaVersion']), `${where}.runtime`);
  if (raw.runtime.type !== 'test-runtime-json' || ![1, 2].includes(raw.runtime.schemaVersion)) fail(`${where}.runtime`, 'unsupported runtime descriptor');
  return { purpose, scenario, preconditions, expectedWorkflow, expectedMigrations, verificationCategories, runtime: { type: raw.runtime.type, schemaVersion: raw.runtime.schemaVersion } };
}

function loadTest(raw, index, projectPath, schemaVersion) {
  const where = `Project '${projectPath}' test[${index}]`;
  object(raw, where);
  exactFields(raw, new Set(['id', 'title', 'description', 'runner', 'parameters', 'report', ...(schemaVersion >= 2 ? ['context'] : [])]), where);
  const id = nonBlank(raw.id, `${where}.id`);
  if (/[\u0000-\u001f\u007f]/.test(id)) fail(`${where}.id`, 'must not contain control characters');
  const title = nonBlank(raw.title, `${where}.title`);
  if (raw.description !== undefined && typeof raw.description !== 'string') fail(`${where}.description`, 'must be a string');
  object(raw.runner, `${where}.runner`);
  exactFields(raw.runner, new Set(['type', 'testFilter']), `${where}.runner`);
  if (raw.runner.type !== 'gradle-test') fail(`${where}.runner.type`, 'only gradle-test is supported');
  const testFilter = nonBlank(raw.runner.testFilter, `${where}.runner.testFilter`);
  if (testFilter.length > 512 || /[\u0000-\u001f]/.test(testFilter)) fail(`${where}.runner.testFilter`, 'contains unsupported characters');
  if (!Array.isArray(raw.parameters)) fail(`${where}.parameters`, 'must be an array');
  const parameters = raw.parameters.map((parameter, parameterIndex) => loadParameter(parameter, parameterIndex, `test '${id}'`));
  const parameterIds = new Set();
  const bindings = new Set();
  for (const parameter of parameters) {
    if (parameterIds.has(parameter.id)) fail(where, `duplicate parameter id '${parameter.id}'`);
    if (bindings.has(parameter.binding.name)) fail(where, `duplicate parameter binding '${parameter.binding.name}'`);
    parameterIds.add(parameter.id);
    bindings.add(parameter.binding.name);
  }
  object(raw.report, `${where}.report`);
  exactFields(raw.report, new Set(['type', 'path']), `${where}.report`);
  if (raw.report.type !== 'gradle-html') fail(`${where}.report.type`, 'only gradle-html is supported');
  const reportPath = nonBlank(raw.report.path, `${where}.report.path`);
  const context = schemaVersion >= 2 ? loadContext(raw.context, `${where}.context`) : undefined;
  return { id, title, description: raw.description, runner: { type: 'gradle-test', testFilter }, parameters, report: { type: 'gradle-html', path: reportPath }, context };
}

export function loadHelpDefinition(project, icons = ICONS) {
  const projectRoot = canonicalProjectRoot(project.projectDir);
  const candidate = resolveContainedPath(projectRoot, 'help.json');
  if (!isWithin(projectRoot, candidate)) fail(project.projectPath, 'help.json resolves outside its Project directory');
  let raw;
  try { raw = JSON.parse(fs.readFileSync(candidate, 'utf8')); }
  catch (error) { fail(`${project.projectPath}/help.json`, `cannot be read or parsed (${error.message})`); }
  object(raw, `${project.projectPath}/help.json`);
  exactFields(raw, ROOT_FIELDS, `${project.projectPath}/help.json`);
  if (![1, 2].includes(raw.schemaVersion)) fail(`${project.projectPath}/help.json.schemaVersion`, 'only schema versions 1 and 2 are supported');
  object(raw.module, `${project.projectPath}/help.json.module`);
  exactFields(raw.module, new Set(['title', 'description', 'icon', 'actions', 'projectDependencies']), `${project.projectPath}/help.json.module`);
  const title = nonBlank(raw.module.title, `${project.projectPath}/help.json.module.title`);
  if (raw.module.description !== undefined && typeof raw.module.description !== 'string') fail(`${project.projectPath}/help.json.module.description`, 'must be a string');
  if (raw.module.icon !== undefined && (!icons.has(raw.module.icon))) fail(`${project.projectPath}/help.json.module.icon`, 'unknown icon identifier');
  if (raw.module.actions !== undefined && !Array.isArray(raw.module.actions)) fail(`${project.projectPath}/help.json.module.actions`, 'must be an array');
  const actions = (raw.module.actions ?? []).map((action, index) => {
    const where = `${project.projectPath}/help.json.module.actions[${index}]`;
    object(action, where);
    exactFields(action, new Set(['id', 'title', 'description', 'task', 'parameters']), where);
    const id = nonBlank(action.id, `${where}.id`);
    if (!['build', 'run'].includes(id)) fail(`${where}.id`, "only 'build' and 'run' actions are supported");
    if (action.task !== id) fail(`${where}.task`, 'must match the allow-listed action id');
    const rawParameters = action.parameters ?? [];
    if (!Array.isArray(rawParameters)) fail(`${where}.parameters`, 'must be an array');
    if (id !== 'run' && rawParameters.length > 0) fail(`${where}.parameters`, 'parameters are supported only for the run action');
    const parameters = rawParameters.map((parameter, parameterIndex) => loadParameter(parameter, parameterIndex, `action '${id}'`, id === 'run'));
    if (parameters.some((parameter) => !['select', 'boolean'].includes(parameter.type))) fail(`${where}.parameters`, 'run action supports select and boolean parameters only');
    if (new Set(parameters.map(({ id: parameterId }) => parameterId)).size !== parameters.length) fail(`${where}.parameters`, 'contains duplicate parameter ids');
    if (new Set(parameters.map(({ binding }) => binding.name)).size !== parameters.length) fail(`${where}.parameters`, 'contains duplicate parameter bindings');
    return { id, title: nonBlank(action.title, `${where}.title`), description: nonBlank(action.description, `${where}.description`), task: action.task, parameters };
  });
  if (new Set(actions.map((action) => action.id)).size !== actions.length) fail(`${project.projectPath}/help.json.module.actions`, 'contains duplicate action ids');
  if (raw.module.projectDependencies !== undefined && !Array.isArray(raw.module.projectDependencies)) fail(`${project.projectPath}/help.json.module.projectDependencies`, 'must be an array');
  const projectDependencies = (raw.module.projectDependencies ?? []).map((dependency, index) => {
    const where = `${project.projectPath}/help.json.module.projectDependencies[${index}]`;
    object(dependency, where);
    exactFields(dependency, new Set(['path', 'scope']), where);
    const dependencyPath = nonBlank(dependency.path, `${where}.path`);
    if (!/^:[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*$/.test(dependencyPath)) fail(`${where}.path`, 'must be a Gradle project path');
    if (!['implementation', 'testImplementation'].includes(dependency.scope)) fail(`${where}.scope`, "must be 'implementation' or 'testImplementation'");
    return { path: dependencyPath, scope: dependency.scope };
  });
  if (new Set(projectDependencies.map(({ path }) => path)).size !== projectDependencies.length) fail(`${project.projectPath}/help.json.module.projectDependencies`, 'contains duplicate project paths');
  if (raw.tests !== undefined && !Array.isArray(raw.tests)) fail(`${project.projectPath}/help.json.tests`, 'must be an array');
  const tests = (raw.tests ?? []).map((test, index) => loadTest(test, index, project.projectPath, raw.schemaVersion));
  const testIds = new Set();
  for (const test of tests) {
    if (testIds.has(test.id)) fail(project.projectPath, `duplicate Test ID '${test.id}'`);
    testIds.add(test.id);
  }
  return {
    projectPath: project.projectPath,
    schemaVersion: raw.schemaVersion,
    helpProjectRoot: projectRoot,
    module: { title, description: raw.module.description, icon: raw.module.icon, actions, projectDependencies },
    hasTestsCapability: raw.tests !== undefined,
    tests,
  };
}

export { ICONS };
