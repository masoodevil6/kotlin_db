const modulePath = document.querySelector('#module-path');
const actionSelect = document.querySelector('#action');
const actionChoices = document.querySelector('#action-choices');
const testSelect = document.querySelector('#test');
const moduleDescription = document.querySelector('#module-description');
const testDescription = document.querySelector('#test-description');
const testPicker = document.querySelector('#test-picker');
const parameterForm = document.querySelector('#parameters');
const runParameterForm = document.querySelector('#run-parameters');
const runButton = document.querySelector('#run');
const result = document.querySelector('#result');
const reportPanel = document.querySelector('#report-panel');
const reportFrame = document.querySelector('#report');
const runSummary = document.querySelector('#run-summary');
const copyAiReportButton = document.querySelector('#copy-ai-report');
const copyAiReportStatus = document.querySelector('#copy-ai-report-status');
const aiReportFallback = document.querySelector('#ai-report-text');
const connection = document.querySelector('#connection');
const workflowNav = document.querySelector('#workflow-nav');
const workflowSteps = document.querySelector('#workflow-steps');
const workflowPanels = [...document.querySelectorAll('[data-step-panel]')];
const moduleNext = document.querySelector('#module-next');
const actionNext = document.querySelector('#action-next');
const actionModuleSummary = document.querySelector('#action-module-summary');
const testSelectionSummary = document.querySelector('#test-selection-summary');
const moduleUses = document.querySelector('#module-uses');
const runModuleSummary = document.querySelector('#run-module-summary');
const buildModuleSummary = document.querySelector('#build-module-summary');
let modules = [];
let projectPaths = [];
let moduleTree = { children: new Map() };
let selectedPathSegments = [];
let currentStep = 'module';
let latestOutcome = null;
const MODULE_TEST_ACTION = 'module-test';
const MODULE_TASK_PREFIX = 'module-task:';
const WORKFLOW_ORDER = ['test', 'results'];

function option(text, value) { const item = document.createElement('option'); item.textContent = text; item.value = value; return item; }

async function loadModules() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);
  let response;
  try {
    response = await fetch('/api/modules', { signal: controller.signal, cache: 'no-store' });
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('سرور Help در ۱۵ ثانیه به درخواست فهرست پروژه‌ها پاسخ نداد.');
    throw new Error(`ارتباط با سرور Help برقرار نشد: ${error.message}`);
  } finally {
    clearTimeout(timeout);
  }
  if (!response.ok) {
    let detail = '';
    try { detail = (await response.json()).error ?? ''; } catch { /* The server may return a non-JSON error page. */ }
    throw new Error(detail || `سرور Help درخواست فهرست پروژه‌ها را با وضعیت ${response.status} رد کرد.`);
  }
  const payload = await response.json();
  if (!Array.isArray(payload.projects) || !Array.isArray(payload.modules)) {
    throw new Error('پاسخ سرور Help از نسخهٔ قدیمی است. فرایند قبلی Help را متوقف کنید و سرور را دوباره با کد فعلی اجرا کنید.');
  }
  modules = payload.modules;
  projectPaths = payload.projects;
  moduleTree = { children: new Map() };
  for (const projectPath of projectPaths) {
    let node = moduleTree;
    for (const segment of projectPath.split(':').filter(Boolean)) {
      if (!node.children.has(segment)) node.children.set(segment, { children: new Map() });
      node = node.children.get(segment);
    }
    node.projectPath = projectPath;
  }
  renderModulePath();
  restoreSelectionFromUrl();
  connection.className = 'badge rounded-pill text-bg-success';
  connection.textContent = 'متصل';
}

function selectedProjectPath() {
  if (selectedPathSegments.length === 0) return undefined;
  return `:${selectedPathSegments.join(':')}`;
}

function selectedModule() {
  const projectPath = selectedProjectPath();
  return modules.find((item) => item.projectPath === projectPath);
}

function selectedModuleTaskAction(module = selectedModule()) {
  const actionId = actionSelect.value.startsWith(MODULE_TASK_PREFIX)
    ? actionSelect.value.slice(MODULE_TASK_PREFIX.length)
    : undefined;
  return module?.actions?.find((action) => action.id === actionId);
}

function selectedModuleAction(module = selectedModule()) {
  return actionSelect.value === MODULE_TEST_ACTION
    ? { title: 'تست ماژول', description: 'اجرای تست انتخاب‌شده برای این ماژول.' }
    : selectedModuleTaskAction(module);
}

function canOpenWorkflowStep(step) {
  if (step === 'module') return true;
  if (step === 'action') return Boolean(selectedProjectPath());
  if (step === 'test') return Boolean(selectedProjectPath()) && actionSelect.value === MODULE_TEST_ACTION && Boolean(selectedModule()?.tests.length);
  if (step === 'run' || step === 'build') return Boolean(selectedProjectPath()) && selectedModuleTaskAction()?.id === step;
  if (step === 'results') return actionSelect.value === MODULE_TEST_ACTION && Boolean(latestOutcome);
  return false;
}

function renderWorkflow() {
  const module = selectedModule();
  const projectPath = selectedProjectPath();
  actionModuleSummary.textContent = module ? `${module.module.title} · ${projectPath}` : (projectPath ?? '');
  const selectedAction = selectedModuleAction(module);
  testSelectionSummary.textContent = module && selectedAction ? `${module.module.title} · ${selectedAction.title}` : '';

  const actionFlowActive = currentStep === 'test' || currentStep === 'results';
  workflowNav.hidden = !actionFlowActive;
  if (module) {
    runModuleSummary.textContent = `${module.module.title} · ${module.projectPath} · Gradle task: ${module.projectPath === ':' ? ':run' : `${module.projectPath}:run`}`;
    buildModuleSummary.textContent = `${module.module.title} · ${module.projectPath} · Gradle task: ${module.projectPath === ':' ? ':build' : `${module.projectPath}:build`}`;
  } else {
    runModuleSummary.textContent = '';
    buildModuleSummary.textContent = '';
  }
  for (const panel of workflowPanels) panel.hidden = panel.dataset.stepPanel !== currentStep;
  for (const button of workflowSteps.querySelectorAll('[data-step-target]')) {
    const step = button.dataset.stepTarget;
    const current = step === currentStep;
    button.disabled = !canOpenWorkflowStep(step);
    if (current) button.setAttribute('aria-current', 'step');
    else button.removeAttribute('aria-current');
    button.closest('.breadcrumb-item').classList.toggle('active', current);
    button.closest('.breadcrumb-item').classList.toggle('completed', !current && canOpenWorkflowStep(step)
      && WORKFLOW_ORDER.indexOf(step) < WORKFLOW_ORDER.indexOf(currentStep));
  }
  moduleNext.disabled = !projectPath;
  const taskAction = selectedModuleTaskAction(module);
  const destination = taskAction?.id ?? 'test';
  actionNext.disabled = !canOpenWorkflowStep(destination);
  actionNext.textContent = taskAction?.id === 'run'
    ? 'رفتن به نمای اجرای برنامه ←'
    : taskAction?.id === 'build' ? 'رفتن به نمای ساخت ماژول ←' : 'ادامه به انتخاب تست ←';
}

function goToWorkflowStep(step) {
  if (!canOpenWorkflowStep(step)) return;
  currentStep = step;
  renderWorkflow();
}

function updateSelectionUrl(method = 'push') {
  const url = new URL(window.location.href);
  const projectPath = selectedProjectPath();
  const module = selectedModule();
  const action = actionSelect.value;
  const test = action === MODULE_TEST_ACTION ? module?.tests.find((item) => item.id === testSelect.value) : undefined;
  if (projectPath) url.searchParams.set('module', projectPath);
  else url.searchParams.delete('module');
  if (action) url.searchParams.set('action', action);
  else url.searchParams.delete('action');
  if (test) url.searchParams.set('test', test.id);
  else url.searchParams.delete('test');
  const nextUrl = `${url.pathname}${url.search}${url.hash}`;
  if (nextUrl === `${window.location.pathname}${window.location.search}${window.location.hash}`) return;
  if (method === 'replace') window.history.replaceState(null, '', nextUrl);
  else window.history.pushState(null, '', nextUrl);
}

function restoreSelectionFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const requestedPath = params.get('module');
  const requestedProjectPath = projectPaths.includes(requestedPath) && requestedPath !== ':' ? requestedPath : undefined;
  selectedPathSegments = requestedProjectPath ? requestedProjectPath.split(':').filter(Boolean) : [];
  renderModulePath();
  renderActions();
  const module = selectedModule();
  const requestedAction = params.get('action');
  const requestedTest = params.get('test');
  const requestedTestIsValid = module?.tests.some((item) => item.id === requestedTest) ?? false;
  const requestedTaskActionIsValid = module?.actions?.some((item) => item.id === requestedAction?.slice(MODULE_TASK_PREFIX.length))
    && requestedAction?.startsWith(MODULE_TASK_PREFIX);
  if ((requestedAction === MODULE_TEST_ACTION && module?.tests.length) || requestedTaskActionIsValid || (!requestedAction && requestedTestIsValid)) {
    actionSelect.value = MODULE_TEST_ACTION;
    if (requestedTaskActionIsValid) actionSelect.value = `${MODULE_TASK_PREFIX}${requestedAction.slice(MODULE_TASK_PREFIX.length)}`;
  }
  renderTests();
  if (actionSelect.value === MODULE_TEST_ACTION && requestedTestIsValid) testSelect.value = requestedTest;
  renderParameters();

  const isTaskActionSelected = Boolean(selectedModuleTaskAction(module));
  const hasInvalidSelection = (requestedPath !== null && !requestedProjectPath)
    || (params.has('action') && actionSelect.value !== requestedAction)
    || (params.has('test') && (actionSelect.value !== MODULE_TEST_ACTION || !requestedTestIsValid));
  currentStep = actionSelect.value === MODULE_TEST_ACTION
    ? (requestedTestIsValid ? 'test' : 'action')
    : isTaskActionSelected ? selectedModuleTaskAction(module).id : 'module';
  renderWorkflow();
  if (hasInvalidSelection) updateSelectionUrl('replace');
}

function rememberedValuesKey(module, test) {
  return `help:last-inputs:${encodeURIComponent(module.projectPath)}:${encodeURIComponent(test.id)}`;
}

function readRememberedValues(module, test) {
  try {
    const saved = JSON.parse(localStorage.getItem(rememberedValuesKey(module, test)) ?? '{}');
    if (!saved || typeof saved !== 'object' || Array.isArray(saved)) return {};
    const safeParameters = new Map(test.parameters.filter((parameter) => !parameter.secret).map((parameter) => [parameter.id, parameter]));
    return Object.fromEntries(Object.entries(saved).filter(([id, value]) => {
      const parameter = safeParameters.get(id);
      if (!parameter) return false;
      if (parameter.type === 'boolean') return typeof value === 'boolean';
      if (parameter.type === 'number') return Number.isSafeInteger(value);
      return typeof value === 'string' && !(value === '' && parameter.default !== undefined);
    }));
  } catch {
    return {};
  }
}

function rememberValue(module, test, parameter, value) {
  if (parameter.secret) return;
  const values = readRememberedValues(module, test);
  if (value === undefined || (value === '' && parameter.default !== undefined)) delete values[parameter.id];
  else values[parameter.id] = value;
  try { localStorage.setItem(rememberedValuesKey(module, test), JSON.stringify(values)); }
  catch { /* Storage may be disabled or unavailable; the form still works. */ }
}

function renderModulePath() {
  modulePath.replaceChildren();
  if (projectPaths.length === 0) {
    const empty = document.createElement('p'); empty.className = 'muted'; empty.textContent = 'ماژولی برای نمایش ثبت نشده است.';
    modulePath.append(empty);
    return;
  }

  let node = moduleTree;
  for (let level = 0; node.children.size > 0; level += 1) {
    const wrapper = document.createElement('div'); wrapper.className = 'module-level';
    const label = document.createElement('label'); label.htmlFor = `module-level-${level}`;
    label.textContent = level === 0 ? 'پروژهٔ اصلی' : `زیرماژول ${level}`;
    label.className = 'form-label fw-semibold mb-2';
    const select = document.createElement('select'); select.id = `module-level-${level}`;
    select.className = 'form-select';
    select.setAttribute('aria-label', label.textContent);
    select.append(option(level === 0 ? 'انتخاب پروژه' : 'انتخاب زیرماژول', ''));
    for (const segment of node.children.keys()) select.append(option(segment, segment));

    const selectedSegment = selectedPathSegments[level] ?? '';
    select.value = node.children.has(selectedSegment) ? selectedSegment : '';
    select.addEventListener('change', () => {
      selectedPathSegments = selectedPathSegments.slice(0, level);
      if (select.value) selectedPathSegments.push(select.value);
      renderModulePath();
      renderActions();
      renderTests();
      currentStep = 'module';
      renderWorkflow();
      updateSelectionUrl();
    });
    wrapper.append(label, select);
    modulePath.append(wrapper);

    if (!select.value) break;
    node = node.children.get(select.value);
  }
}

function renderActions() {
  const projectPath = selectedProjectPath();
  const module = selectedModule();
  actionChoices.replaceChildren();
  const hasActions = Boolean(module && (module.tests.length || module.actions?.length));
  actionSelect.replaceChildren(option(projectPath
    ? (hasActions ? 'انتخاب اکشن' : 'برای این ماژول اکشنی تعریف نشده است')
    : 'ابتدا ماژول را انتخاب کنید', ''));
  if (module?.tests.length) actionSelect.append(option('تست‌های ماژول', MODULE_TEST_ACTION));
  for (const action of module?.actions ?? []) actionSelect.append(option(action.title, `${MODULE_TASK_PREFIX}${action.id}`));
  actionSelect.disabled = !hasActions;
  actionSelect.value = '';

  if (!projectPath || !module) {
    const emptyState = document.createElement('p');
    emptyState.className = 'action-empty-state';
    emptyState.textContent = projectPath ? 'برای این ماژول هنوز اکشنی تعریف نشده است.' : 'ابتدا ماژول را انتخاب کنید.';
    actionChoices.append(emptyState);
    return;
  }

  const definitions = [
    ...(module.tests.length ? [{ id: MODULE_TEST_ACTION, title: 'تست‌های ماژول', description: `اجرای یکی از ${new Intl.NumberFormat('fa-IR').format(module.tests.length)} تست ثبت‌شده برای این ماژول.`, icon: '✓' }] : []),
    ...(module.actions ?? []).map((action) => ({ ...action, id: `${MODULE_TASK_PREFIX}${action.id}`, icon: action.id === 'build' ? 'B' : '▶' })),
  ];
  for (const action of definitions) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'action-card';
    card.dataset.actionValue = action.id;
    card.setAttribute('role', 'radio');
    card.setAttribute('aria-checked', 'false');
    const icon = document.createElement('span');
    icon.className = 'action-card-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = action.icon;
    const content = document.createElement('span');
    content.className = 'action-card-content';
    const title = document.createElement('span');
    title.className = 'action-card-title';
    title.textContent = action.title;
    const description = document.createElement('span');
    description.className = 'action-card-description';
    description.textContent = action.description;
    content.append(title, description);
    const marker = document.createElement('span');
    marker.className = 'action-card-marker';
    marker.setAttribute('aria-hidden', 'true');
    card.append(icon, content, marker);
    actionChoices.append(card);
  }
}

function renderTests() {
  const projectPath = selectedProjectPath();
  const module = selectedModule();
  const actionSelected = actionSelect.value === MODULE_TEST_ACTION;
  const dependencies = module?.module.projectDependencies ?? [];
  moduleUses.replaceChildren();
  moduleUses.hidden = dependencies.length === 0;
  if (dependencies.length) {
    const heading = document.createElement('strong');
    heading.textContent = 'ماژول‌های پروژه‌ای مورد استفاده: ';
    const list = document.createElement('span');
    list.textContent = dependencies.map((item) => `${item.path} (${item.scope})`).join('، ');
    moduleUses.append(heading, list);
  }

  testSelect.replaceChildren(option(!projectPath
    ? 'ابتدا ماژول را انتخاب کنید'
    : !actionSelected
      ? 'ابتدا اکشن را انتخاب کنید'
      : (module?.tests.length ? 'انتخاب تست' : 'برای این ماژول تستی تعریف نشده است'), ''));
  for (const test of (actionSelected ? module?.tests ?? [] : [])) testSelect.append(option(test.title, test.id));
  testSelect.disabled = !actionSelected || !module || module.tests.length === 0;
  moduleDescription.textContent = projectPath
    ? (module ? (module.module.description ?? 'برای این ماژول توضیحی ثبت نشده است.') : 'این Gradle Project در Help ثبت نشده است.')
    : '';
  testDescription.textContent = '';
  parameterForm.replaceChildren();
  renderRunActionParameters(module);
  runButton.textContent = 'اجرای تست';
  runButton.disabled = !actionSelected || !testSelect.value;
  latestOutcome = null;
  runSummary.replaceChildren();
  reportPanel.hidden = true;
  reportFrame.removeAttribute('src');
}

function renderRunActionParameters(module) {
  runParameterForm.replaceChildren();
  const runAction = module?.actions?.find((action) => action.id === 'run');
  for (const parameter of runAction?.parameters ?? []) {
    const field = document.createElement('div');
    field.className = parameter.type === 'boolean' ? 'parameter form-check' : 'parameter-field';
    const label = document.createElement('label');
    label.htmlFor = `run-param-${parameter.id}`;
    label.textContent = parameter.label;
    let input;
    if (parameter.type === 'boolean') {
      label.className = 'form-check-label';
      input = document.createElement('input');
      input.type = 'checkbox';
      input.className = 'form-check-input';
      input.checked = parameter.default ?? false;
      field.append(input, label);
    } else {
      label.className = 'form-label';
      input = document.createElement('select');
      input.className = 'form-select';
      input.required = parameter.required;
      input.append(option('', ''));
      for (const item of parameter.options ?? []) input.append(option(item.label, item.value));
      if (parameter.default !== undefined) input.value = parameter.default;
      field.append(label, input);
    }
    input.id = `run-param-${parameter.id}`;
    input.name = parameter.id;
    if (parameter.description) field.append(summaryElement('p', 'form-text mb-0', parameter.description));
    runParameterForm.append(field);
  }
}function faNumber(value) {
  return new Intl.NumberFormat('fa-IR').format(value ?? 0);
}

function formatDuration(milliseconds) {
  const value = Math.max(0, milliseconds ?? 0);
  if (value < 1000) return `${faNumber(value)} میلی‌ثانیه`;
  return `${new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 2 }).format(value / 1000)} ثانیه`;
}

function summaryElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function markdownInline(value) {
  return String(value ?? '').replace(/`/g, '\\`').replace(/\r?\n/g, '\\n').replace(/\|/g, '\\|');
}

function markdownFence(value) {
  const longest = Math.max(0, ...[...String(value ?? '').matchAll(/`+/g)].map((match) => match[0].length));
  return '`'.repeat(Math.max(3, longest + 1));
}

function redactSecretValues(value, secrets) {
  if (typeof value === 'string') return secrets.reduce((text, secret) => text.replaceAll(secret, '[محرمانه حذف شد]'), value);
  if (Array.isArray(value)) return value.map((item) => redactSecretValues(item, secrets));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, redactSecretValues(item, secrets)]));
  return value;
}

function createAiHandoffMarkdown(outcome) {
  const data = outcome.testResults;
  const lines = [
    outcome.actionInfo ? '# گزارش اجرای اکشن Gradle' : '# گزارش اجرای تست',
    '',
    `- زمان شروع: ${outcome.startedAt ?? 'نامشخص'}`,
    `- زمان گزارش: ${outcome.completedAt ?? new Date().toISOString()}`,
    `- وضعیت: ${outcome.executionStatus === 'Passed' ? 'موفق' : 'ناموفق'}`,
    `- ماژول: ${outcome.moduleInfo?.title ?? 'نامشخص'} (${outcome.moduleInfo?.projectPath ?? 'نامشخص'})`,
    `- ${outcome.actionInfo ? 'اکشن' : 'تست'}: ${outcome.actionInfo?.title ?? outcome.testInfo?.title ?? 'نامشخص'} (${outcome.actionInfo?.id ?? outcome.testInfo?.id ?? 'نامشخص'})`,
  ];

  if (outcome.taskPath) lines.push(`- Gradle task: \`${markdownInline(outcome.taskPath)}\``);
  if (outcome.testFilter) lines.push(`- فیلتر Gradle: \`${markdownInline(outcome.testFilter)}\``);
  if (outcome.exitCode !== undefined && outcome.exitCode !== null) lines.push(`- کد خروج Gradle: ${outcome.exitCode}`);
  if (outcome.actionInfo && (outcome.stdout || outcome.stderr)) {
    lines.push('', '## خروجی واقعی فرایند');
    if (outcome.stdout) lines.push('', '### stdout', '```text', outcome.stdout, '```');
    if (outcome.stderr) lines.push('', '### stderr', '```text', outcome.stderr, '```');
    if (outcome.outputTruncated) lines.push('', 'خروجی برای محدود کردن حجم بریده شده است.');
  }

  lines.push('', '## داده‌های ورودی');
  for (const parameter of outcome.inputParameters ?? []) {
    const value = parameter.secret ? '[محرمانه؛ برای امنیت درج نشده]' : (parameter.value === '' ? '[خالی]' : String(parameter.value ?? '[وارد نشده]'));
    lines.push(`- ${parameter.label} (${parameter.id}): \`${markdownInline(value)}\``);
  }

  lines.push('', '## نتیجهٔ Gradle');
  if (data) {
    lines.push(
      `- منبع آمار: ${data.source}`,
      `- کل: ${data.total}`,
      `- موفق: ${data.passed}`,
      `- ناموفق: ${data.failed}`,
      `- ردشده (Skipped): ${data.skipped}`,
      `- زمان تست‌ها: ${data.durationMs} ms`,
      `- زمان کل اجرای Gradle: ${outcome.processDurationMs ?? 0} ms`,
    );
  } else {
    lines.push('- Gradle فایل نتیجهٔ JUnit XML برای این اجرا تولید نکرد.');
  }
  if (outcome.message) lines.push(`- پیام اجرا: ${markdownInline(outcome.message)}`);

  if (data?.failures?.length) {
    lines.push('', '## خطاهای گزارش‌شده');
    for (const failure of data.failures) {
      lines.push('', `### ${markdownInline(failure.name)}${failure.className ? ` — \`${markdownInline(failure.className)}\`` : ''}`);
      if (failure.type) lines.push(`- نوع: \`${markdownInline(failure.type)}\``);
      const fence = markdownFence(failure.message);
      lines.push('', fence, failure.message ?? 'بدون پیام خطا', fence);
    }
    if (data.failed > data.failures.length) lines.push('', `بقیهٔ خطاها (${data.failed - data.failures.length}) در گزارش کامل Gradle هستند.`);
  }

  lines.push('', '## Context مورد انتظار');
  const context = outcome.context;
  if (context) {
    lines.push(`- هدف: ${markdownInline(context.purpose)}`, `- سناریو: ${markdownInline(context.scenario)}`);
    for (const item of context.expectedMigrations ?? []) lines.push(`- Migration مورد انتظار (${item.order}): \`${markdownInline(item.id)}\`${item.target ? ` → \`${markdownInline(item.target)}\`` : ''}`);
  } else lines.push('- Context برای این تست تعریف نشده است.');

  lines.push('', '## Runtime مشاهده‌شده');
  lines.push(`- شناسهٔ اجرا: \`${markdownInline(outcome.runId ?? 'ناموجود')}\``, `- وضعیت Runtime: ${markdownInline(outcome.runtimeStatus ?? 'unavailable')}`);
  if (outcome.runtime) {
    for (const step of outcome.runtime.steps ?? []) lines.push(`- گام ${step.order} (${markdownInline(step.id)}): ${markdownInline(step.status)}`);
    for (const migration of outcome.runtime.migrations ?? []) lines.push(`- Migration \`${markdownInline(migration.migrationId)}\`: اجرا=${markdownInline(migration.executionStatus)}، history=${markdownInline(migration.historyStatus)}${migration.batch == null ? '' : `، batch=${migration.batch}`}، operation=${markdownInline(migration.operationType ?? 'not_collected')}، target=${markdownInline(migration.target ?? 'not_collected')}`);
    for (const field of ['schemaVerification', 'historyVerification', 'finalState']) {
      if (outcome.runtime[field] !== undefined) {
        lines.push('', `### ${field}`, '```json', JSON.stringify(outcome.runtime[field], null, 2), '```');
      }
    }
  } else lines.push('- Runtime artifact معتبر برای این اجرا در دسترس نیست.');

  if (outcome.reportAvailable) lines.push('', 'گزارش HTML همان اجرا نیز در صفحهٔ Help موجود است.');
  return lines.join('\n');
}

async function copyActionOutput(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const fallback = document.createElement('textarea');
  fallback.value = text;
  fallback.setAttribute('readonly', '');
  fallback.style.position = 'fixed';
  fallback.style.opacity = '0';
  document.body.append(fallback);
  fallback.select();
  const copied = document.execCommand('copy');
  fallback.remove();
  if (!copied) throw new Error('Clipboard copy failed');
}

function createActionOutputStream(name, value = '') {
  const details = document.createElement('details');
  details.open = true;
  details.className = 'action-output-stream';
  const summary = document.createElement('summary');
  summary.append(summaryElement('span', 'action-output-label', name));

  const controls = summaryElement('span', 'action-output-controls');
  const copyButton = document.createElement('button');
  copyButton.type = 'button';
  copyButton.className = 'action-output-copy';
  copyButton.title = `کپی ${name}`;
  copyButton.setAttribute('aria-label', `کپی ${name}`);
  copyButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="8" y="8" width="12" height="12" rx="2"></rect><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"></path></svg>';
  const copyStatus = summaryElement('span', 'action-output-copy-status');
  copyStatus.setAttribute('role', 'status');
  copyStatus.setAttribute('aria-live', 'polite');
  copyButton.addEventListener('click', async (event) => {
    event.preventDefault();
    event.stopPropagation();
    try {
      await copyActionOutput(pre.textContent);
      copyStatus.textContent = 'کپی شد';
    } catch {
      copyStatus.textContent = 'کپی ناموفق بود';
    }
  });
  controls.append(copyStatus, copyButton);
  summary.append(controls);

  const pre = summaryElement('pre', 'action-output-text', value);
  details.append(summary, pre);
  return { details, pre };
}

function createLiveActionOutputView(outputContainer) {
  outputContainer.replaceChildren();
  const outputSection = summaryElement('section', 'action-process-output');
  outputSection.append(summaryElement('h4', 'h6 mb-2', 'خروجی زندهٔ فرایند'));
  const streams = {};
  for (const name of ['stdout', 'stderr']) {
    const stream = createActionOutputStream(name);
    outputSection.append(stream.details);
    streams[name] = stream.pre;
  }
  outputContainer.append(outputSection);
  return streams;
}

function appendLiveActionOutput(streams, stream, data) {
  const target = streams[stream];
  if (!target || !data) return;
  target.append(document.createTextNode(data));
  target.scrollTop = target.scrollHeight;
}

function renderRunSummary(outcome, test) {
  runSummary.replaceChildren();
  const status = outcome.executionStatus ?? 'Failed';
  runSummary.dataset.state = status;

  const overview = summaryElement('div', 'run-overview');
  const statusBadge = summaryElement('span', `run-status-badge ${status === 'Passed' ? 'is-passed' : 'is-failed'}`,
    status === 'Passed' ? 'موفق' : 'ناموفق');
  const title = summaryElement('h3', 'h5 mb-1', outcome.actionInfo?.title ?? test?.title ?? 'اجرای تست');
  const subtitle = summaryElement('p', 'mb-0 text-secondary', status === 'Passed'
    ? 'Gradle اجرای task را با موفقیت به پایان رساند.'
    : (outcome.message ?? 'Gradle اجرای task را با خطا به پایان رساند.'));
  const titleGroup = summaryElement('div', 'run-overview-title');
  titleGroup.append(title, subtitle);
  overview.append(statusBadge, titleGroup);
  runSummary.append(overview);

  const data = outcome.testResults;
  if (data) {
    const metrics = summaryElement('div', 'run-metrics');
    const values = [
      ['کل تست‌ها', data.total], ['موفق', data.passed], ['ناموفق', data.failed],
      ['ردشده / Skip', data.skipped], ['زمان تست‌ها', formatDuration(data.durationMs)],
      ['زمان کل اجرا', formatDuration(outcome.processDurationMs)],
    ];
    for (const [label, value] of values) {
      const metric = summaryElement('div', 'run-metric');
      metric.append(summaryElement('span', 'run-metric-label', label), summaryElement('strong', 'run-metric-value', typeof value === 'number' ? faNumber(value) : value));
      metrics.append(metric);
    }
    runSummary.append(metrics);
    runSummary.append(summaryElement('p', 'run-data-source', `جزئیات از ${data.source} خوانده شد؛ این آمار متعلق به اجرای فعلی است.`));

    if (data.failures?.length) {
      const failureSection = summaryElement('section', 'run-failures');
      failureSection.append(summaryElement('h4', 'h6 mb-3', 'تست‌های ناموفق'));
      for (const failure of data.failures) {
        const item = summaryElement('details', 'run-failure');
        const heading = summaryElement('summary', '', `${failure.name}${failure.className ? ` — ${failure.className}` : ''}`);
        item.append(heading);
        if (failure.type) item.append(summaryElement('span', 'run-failure-type', failure.type));
        item.append(summaryElement('pre', 'run-failure-message', failure.message));
        failureSection.append(item);
      }
      if (data.failed > data.failures.length) failureSection.append(summaryElement('p', 'small text-secondary', `${faNumber(data.failed - data.failures.length)} مورد دیگر در گزارش کامل Gradle موجود است.`));
      runSummary.append(failureSection);
    } else if (data.total === 0) {
      runSummary.append(summaryElement('p', 'run-data-source', 'Gradle فایل نتیجه تولید کرد، اما مورد تستی در آن ثبت نشده است.'));
    }
  } else {
    const message = outcome.actionInfo
      ? 'این اکشن Gradle گزارش JUnit تولید نمی‌کند؛ وضعیت بالا نتیجهٔ خود task است.'
      : 'Gradle برای این اجرا فایل نتیجهٔ JUnit XML تولید نکرد؛ ممکن است task پیش از شروع تست‌ها متوقف شده باشد.';
    runSummary.append(summaryElement('p', 'run-data-source', message));
  }

  const execution = summaryElement('dl', 'run-execution-details');
  const details = [
    ['Gradle task', outcome.taskPath],
    ['فیلتر تست', outcome.testFilter],
    ['کد خروج', outcome.exitCode === undefined || outcome.exitCode === null ? undefined : String(outcome.exitCode)],
  ].filter(([, value]) => value !== undefined);
  for (const [label, value] of details) {
    execution.append(summaryElement('dt', '', label), summaryElement('dd', '', value));
  }
  if (details.length) runSummary.append(execution);
  if (outcome.actionInfo) {
    const outputSection = summaryElement('section', 'action-process-output');
    outputSection.append(summaryElement('h4', 'h6 mb-2', 'خروجی واقعی فرایند'));
    if (outcome.outputTruncated) outputSection.append(summaryElement('p', 'small text-warning', 'بخشی از خروجی به علت محدودیت حجم ذخیره نشده است.'));
    for (const [label, value] of [['stdout', outcome.stdout], ['stderr', outcome.stderr]]) {
      if (!value) continue;
      outputSection.append(createActionOutputStream(label, value).details);
    }
    if (!outcome.stdout && !outcome.stderr) {
      outputSection.append(summaryElement('p', 'small text-secondary mb-0', 'فرایند هیچ متنی در stdout یا stderr تولید نکرد.'));
    }
    runSummary.append(outputSection);
  }
  const context = outcome.context ?? test?.context;
  if (context) runSummary.append(renderContextSection(context));
  runSummary.append(renderWorkflowSection(outcome.runtimeStatus, outcome.runtime, context, outcome.runtimeValidationError));
  runSummary.append(renderExpectedActualSection(context, outcome.runtime));
  if (outcome.reportAvailable && outcome.reportUrl) {
    reportFrame.src = outcome.reportUrl;
    reportPanel.hidden = false;
  } else {
    reportPanel.hidden = true;
    reportFrame.removeAttribute('src');
  }
}

const WORKFLOW_STATUS_LABELS = {
  not_started: 'هنوز شروع نشده', in_progress: 'در حال اجرا', succeeded: 'موفق',
  failed: 'ناموفق', not_executed: 'اجرا نشده (با شواهد)', not_collected: 'داده‌ای جمع‌آوری نشد',
};
const RUNTIME_STATUS_LABELS = { complete: 'کامل', incomplete: 'ناقص؛ آخرین checkpoint معتبر', unavailable: 'در دسترس نیست', invalid: 'نامعتبر' };
const WORKFLOW_STATUS_COLORS = {
  not_started: '#aab5c5', in_progress: '#4c69e8', succeeded: '#168765',
  failed: '#cf4057', not_executed: '#8995a8', not_collected: '#c28a1c',
};

function renderContextSection(context) {
  const section = summaryElement('section', 'runtime-card context-card');
  section.append(summaryElement('h4', 'h6 mb-2', 'Test Context — انتظار ثابت'));
  section.append(summaryElement('p', 'runtime-copy', context.purpose));
  section.append(summaryElement('p', 'runtime-copy', context.scenario));
  if (context.preconditions?.length) {
    section.append(summaryElement('h5', 'runtime-subheading', 'پیش‌شرط‌ها'));
    const list = document.createElement('ul');
    for (const item of context.preconditions) list.append(summaryElement('li', '', item));
    section.append(list);
  }
  if (context.expectedWorkflow?.length) {
    section.append(summaryElement('h5', 'runtime-subheading', 'مسیر مورد انتظار'));
    const list = document.createElement('ol');
    list.className = 'runtime-workflow-context-list';
    const appendChildren = (container, parentId) => {
      for (const node of context.expectedWorkflow.filter((item) => item.parentId === parentId)) {
        const item = document.createElement('li');
        item.append(summaryElement('span', node.kind === 'group' ? 'runtime-workflow-context-group' : '', node.label));
        const children = context.expectedWorkflow.filter((candidate) => candidate.parentId === node.id);
        if (children.length) {
          const nested = document.createElement(node.kind === 'group' ? 'ol' : 'ul');
          appendChildren(nested, node.id);
          item.append(nested);
        }
        container.append(item);
      }
    };
    appendChildren(list, undefined);
    section.append(list);
  }
  return section;
}

function renderWorkflowSection(runtimeStatus, runtime, context, runtimeValidationError) {
  const section = summaryElement('section', 'runtime-card workflow-runtime-card');
  section.append(summaryElement('h4', 'h6 mb-2', 'Observed Workflow — اجرای واقعی'));
  section.append(summaryElement('p', 'runtime-copy', `وضعیت Runtime: ${RUNTIME_STATUS_LABELS[runtimeStatus] ?? 'در دسترس نیست'}`));
  if (!runtime) {
    const detail = runtimeStatus === 'invalid' && runtimeValidationError
      ? `اعتبارسنجی دادهٔ workflow ناموفق بود: ${runtimeValidationError}`
      : 'جزئیات گام‌ها از روی JUnit یا خروجی Gradle حدس زده نمی‌شوند.';
    section.append(summaryElement('p', 'small text-secondary mb-0', detail));
    return section;
  }
  const steps = runtime.steps ?? [];
  const counts = steps.reduce((result, step) => {
    const status = Object.hasOwn(WORKFLOW_STATUS_LABELS, step.status) ? step.status : 'not_collected';
    result[status] = (result[status] ?? 0) + 1;
    return result;
  }, {});
  const monitor = document.createElement('div'); monitor.className = 'workflow-monitor-summary';
  monitor.setAttribute('aria-label', 'خلاصهٔ وضعیت مراحل workflow');
  for (const status of ['succeeded', 'in_progress', 'failed', 'not_executed', 'not_started', 'not_collected']) {
    if (!counts[status]) continue;
    const metric = document.createElement('div'); metric.className = `workflow-monitor-metric status-${status}`;
    metric.append(summaryElement('strong', '', faNumber(counts[status])), summaryElement('span', '', WORKFLOW_STATUS_LABELS[status]));
    monitor.append(metric);
  }
  section.append(monitor, renderWorkflowSvg(steps, context?.expectedWorkflow ?? []));
  const migrations = runtime.migrations ?? [];
  if (migrations.length) {
    section.append(summaryElement('h5', 'runtime-subheading', 'Migration execution و history'));
    const table = document.createElement('div'); table.className = 'runtime-migration-list';
    for (const migration of migrations) {
      const row = summaryElement('div', 'runtime-migration-row');
      row.append(summaryElement('code', 'runtime-migration-id', migration.migrationId));
      row.append(summaryElement('span', '', `اجرا: ${migration.executionStatus}`));
      row.append(summaryElement('span', '', `history: ${migration.historyStatus}`));
      row.append(summaryElement('span', '', `operation: ${migration.operationType ?? 'not_collected'}`));
      row.append(summaryElement('span', '', `target: ${migration.target ?? 'not_collected'}`));
      if (migration.batch !== undefined) row.append(summaryElement('span', '', `batch ${faNumber(migration.batch)}`));
      table.append(row);
    }
    section.append(table);
  }
  return section;
}

function renderWorkflowSvg(steps, expectedWorkflow = []) {
  const viewport = document.createElement('div'); viewport.className = 'workflow-svg-viewport';
  if (!steps.length) {
    viewport.append(summaryElement('p', 'workflow-empty-state', 'در این اجرا گام قابل مشاهده‌ای ثبت نشده است.'));
    return viewport;
  }

  const runtimeById = new Map(steps.map((step) => [step.id, step]));
  const definitions = expectedWorkflow.length
    ? expectedWorkflow
    : steps.map((step) => ({ id: step.id, label: step.id, kind: 'step', parentId: step.parentId }));
  const nodesById = new Map(definitions.map((definition) => [definition.id, {
    ...definition,
    kind: definition.kind ?? 'step',
    children: [],
    runtime: runtimeById.get(definition.id),
  }]));
  const roots = [];
  for (const node of nodesById.values()) {
    const parent = node.parentId ? nodesById.get(node.parentId) : null;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }

  const treeRows = [];
  const flattenTree = (node, depth = 0, parent = null) => {
    node.depth = depth;
    node.parent = parent;
    treeRows.push(node);
    for (const child of node.children) flattenTree(child, depth + 1, node);
  };
  for (const root of roots) flattenTree(root);

  const namespace = 'http://www.w3.org/2000/svg';
  const width = 1000;
  const rowHeight = 118;
  const height = 34 + treeRows.length * rowHeight;
  const svg = document.createElementNS(namespace, 'svg');
  svg.setAttribute('class', 'workflow-svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-labelledby', 'runtime-workflow-title runtime-workflow-description');
  const title = svgText(namespace, 'title', 'runtime-workflow-title', 'نمودار مراحل اجرای واقعی تست');
  const description = svgText(namespace, 'desc', 'runtime-workflow-description', `درخت workflow شامل ${faNumber(steps.length)} گام ثبت‌شده و ${faNumber(treeRows.length - steps.length)} گروه ساختاری است.`);
  svg.append(title, description);

  const cardHeight = 92;
  treeRows.forEach((node, index) => {
    node.y = 18 + index * rowHeight;
    node.cardX = 126 + node.depth * 58;
    node.cardWidth = width - node.cardX - 32;
    node.railX = 76 + node.depth * 58;
    node.clipId = `runtime-workflow-content-clip-${index}`;
  });

  // Each depth has a different badge lane, so clip text to that card's own
  // content area instead of letting nested labels overlap the status badge.
  const defs = svgNode(namespace, 'defs', {});
  for (const node of treeRows) {
    const contentLeft = node.cardX + 190;
    const contentRight = node.cardX + node.cardWidth - 24;
    const clip = svgNode(namespace, 'clipPath', { id: node.clipId, clipPathUnits: 'userSpaceOnUse' });
    clip.append(svgNode(namespace, 'rect', { x: contentLeft, y: 0, width: Math.max(1, contentRight - contentLeft), height }));
    defs.append(clip);
  }
  svg.append(defs);

  for (const node of treeRows) {
    if (!node.parent) continue;
    const childStatus = workflowNodeStatus(node);
    const color = WORKFLOW_STATUS_COLORS[childStatus];
    const parentCenterY = node.parent.y + cardHeight / 2;
    const centerY = node.y + cardHeight / 2;
    const arrowBaseX = node.railX - 31;
    svg.append(svgNode(namespace, 'path', {
      d: `M ${node.parent.railX} ${parentCenterY + 23} V ${centerY} H ${arrowBaseX}`,
      class: `workflow-svg-connector${childStatus === 'not_executed' ? ' is-not-executed' : ''}`,
      stroke: color,
    }));
    svg.append(svgNode(namespace, 'path', {
      d: `M ${arrowBaseX} ${centerY - 5} L ${node.railX - 23} ${centerY} L ${arrowBaseX} ${centerY + 5} Z`,
      fill: color,
      class: 'workflow-svg-tree-arrow',
    }));
  }

  treeRows.forEach((node) => {
    const isGroup = node.kind === 'group';
    const step = node.runtime;
    const status = workflowNodeStatus(node);
    const color = WORKFLOW_STATUS_COLORS[status];
    const y = node.y;
    const centerY = y + cardHeight / 2;
    const group = svgNode(namespace, 'g', { class: `workflow-svg-step${isGroup ? ' is-group' : ''} status-${status}`, tabindex: '0', role: 'group' });
    const label = node.label ?? node.id;
    const statusLabel = WORKFLOW_STATUS_LABELS[status];
    group.setAttribute('aria-label', `${isGroup ? 'گروه' : `گام ${step?.order ?? ''}`}: ${label}؛ ${statusLabel}`);
    group.append(svgNode(namespace, 'rect', { x: node.cardX, y, width: node.cardWidth, height: cardHeight, rx: 15, class: 'workflow-svg-card' }));
    group.append(svgNode(namespace, 'rect', { x: node.cardX, y: y + 13, width: 4, height: cardHeight - 26, rx: 2, fill: color }));
    group.append(svgNode(namespace, 'circle', { cx: node.railX, cy: centerY, r: 23, class: `workflow-svg-node${isGroup ? ' workflow-svg-group-node' : ''}`, stroke: color }));
    const order = svgText(namespace, 'text', '', isGroup ? 'گ' : faNumber(step?.order ?? ''));
    order.setAttribute('x', node.railX); order.setAttribute('y', centerY + 5); order.setAttribute('text-anchor', 'middle'); order.setAttribute('class', 'workflow-svg-order');
    group.append(order);

    const titleText = svgText(namespace, 'text', '', label);
    titleText.setAttribute('x', node.cardX + node.cardWidth - 24); titleText.setAttribute('y', y + 34);
    // For RTL text, SVG's logical "start" anchor is the visual right edge.
    // Using "end" here moved the text to the right of x=944 and outside the card.
    titleText.setAttribute('text-anchor', 'start'); titleText.setAttribute('class', 'workflow-svg-label');
    titleText.setAttribute('direction', 'rtl'); titleText.setAttribute('unicode-bidi', 'plaintext');
    titleText.setAttribute('clip-path', `url(#${node.clipId})`);
    titleText.setAttribute('title', label);
    group.append(titleText);

    const idText = svgText(namespace, 'text', '', node.id);
    idText.setAttribute('x', node.cardX + node.cardWidth - 24); idText.setAttribute('y', y + 53);
    idText.setAttribute('text-anchor', 'end'); idText.setAttribute('class', 'workflow-svg-id'); idText.setAttribute('direction', 'ltr');
    idText.setAttribute('clip-path', `url(#${node.clipId})`);
    idText.setAttribute('title', node.id);
    group.append(idText);

    const meta = isGroup ? workflowGroupMeta(node) : workflowStepMeta(step ?? {});
    if (meta) {
      const metaText = svgText(namespace, 'text', '', meta);
      metaText.setAttribute('x', node.cardX + node.cardWidth - 24); metaText.setAttribute('y', y + 76);
      metaText.setAttribute('text-anchor', 'start'); metaText.setAttribute('class', 'workflow-svg-meta');
      metaText.setAttribute('direction', 'rtl'); metaText.setAttribute('unicode-bidi', 'plaintext');
      metaText.setAttribute('clip-path', `url(#${node.clipId})`);
      metaText.setAttribute('title', meta);
      group.append(metaText);
    }

    const pillX = node.cardX + 17;
    group.append(svgNode(namespace, 'rect', { x: pillX, y: y + 15, width: 156, height: 27, rx: 13.5, class: 'workflow-svg-status-pill', fill: color }));
    const statusText = svgText(namespace, 'text', '', statusLabel);
    statusText.setAttribute('x', pillX + 78); statusText.setAttribute('y', y + 33);
    statusText.setAttribute('text-anchor', 'middle'); statusText.setAttribute('class', 'workflow-svg-status-text');
    group.append(statusText);

    const fullData = isGroup ? meta : workflowStepMeta(step ?? {});
    if (fullData) {
      const tooltip = svgText(namespace, 'title', '', `${label} — ${statusLabel} — ${fullData}`);
      group.append(tooltip);
    }
    svg.append(group);
  });
  viewport.append(svg);
  return viewport;
}

function workflowNodeStatus(node) {
  if (node.kind !== 'group') {
    const status = node.runtime?.status ?? 'not_collected';
    return Object.hasOwn(WORKFLOW_STATUS_LABELS, status) ? status : 'not_collected';
  }
  const statuses = node.children.flatMap((child) => workflowLeafStatuses(child));
  if (!statuses.length) return 'not_collected';
  if (statuses.includes('failed')) return 'failed';
  if (statuses.includes('in_progress')) return 'in_progress';
  if (statuses.every((status) => status === 'succeeded')) return 'succeeded';
  if (statuses.every((status) => status === 'not_executed')) return 'not_executed';
  if (statuses.includes('not_collected')) return 'not_collected';
  if (statuses.every((status) => status === 'not_started')) return 'not_started';
  return 'in_progress';
}

function workflowLeafStatuses(node) {
  if (node.kind !== 'group') return [workflowNodeStatus(node)];
  return node.children.flatMap((child) => workflowLeafStatuses(child));
}

function workflowGroupMeta(node) {
  const statuses = workflowLeafStatuses(node);
  const succeeded = statuses.filter((status) => status === 'succeeded').length;
  const failed = statuses.filter((status) => status === 'failed').length;
  const pending = statuses.filter((status) => ['not_started', 'not_executed', 'not_collected'].includes(status)).length;
  const parts = [`${faNumber(statuses.length)} گام`];
  if (succeeded) parts.push(`${faNumber(succeeded)} موفق`);
  if (failed) parts.push(`${faNumber(failed)} ناموفق`);
  if (pending) parts.push(`${faNumber(pending)} در انتظار`);
  return parts.join('  ·  ');
}

function svgNode(namespace, tag, attributes) {
  const element = document.createElementNS(namespace, tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
  return element;
}

function svgText(namespace, tag, id, value) {
  const element = document.createElementNS(namespace, tag);
  if (id) element.setAttribute('id', id);
  element.textContent = String(value ?? '');
  return element;
}

function workflowStepMeta(step) {
  const pieces = [];
  if (step.startedAt) pieces.push(`شروع ${formatWorkflowTime(step.startedAt)}`);
  if (step.completedAt) pieces.push(`پایان ${formatWorkflowTime(step.completedAt)}`);
  if (step.durationMs !== undefined) pieces.push(formatDuration(step.durationMs));
  if (step.data && typeof step.data === 'object') {
    pieces.push(...Object.entries(step.data).map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`));
  }
  const summary = pieces.join('  ·  ');
  return summary.length > 132 ? `${summary.slice(0, 129)}…` : summary;
}

function formatWorkflowTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return 'زمان نامعتبر';
  return new Intl.DateTimeFormat('fa-IR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date);
}

function runtimeContextStep(stepId) {
  return latestOutcome?.context?.expectedWorkflow?.find((step) => step.id === stepId);
}

function renderExpectedActualSection(context, runtime) {
  const section = summaryElement('section', 'runtime-card expected-actual-card');
  section.append(summaryElement('h4', 'h6 mb-2', 'Expected vs Actual'));
  const expected = document.createElement('div'); expected.className = 'expected-actual-column';
  expected.append(summaryElement('h5', 'runtime-subheading', 'Expected'));
  const expectedList = document.createElement('ul');
  for (const migration of context?.expectedMigrations ?? []) {
    expectedList.append(summaryElement('li', '', `${migration.order}. ${migration.id}${migration.target ? ` → ${migration.target}` : ''}`));
  }
  if (!expectedList.children.length) expectedList.append(summaryElement('li', '', 'تعریفی در Context ثبت نشده است.'));
  expected.append(expectedList);
  const actual = document.createElement('div'); actual.className = 'expected-actual-column';
  actual.append(summaryElement('h5', 'runtime-subheading', 'Actual observations'));
  const observation = runtime ? {
    history: runtime.historyVerification,
    schema: runtime.schemaVerification,
    finalState: runtime.finalState,
  } : null;
  if (observation && Object.values(observation).some((value) => value !== undefined)) {
    const pre = summaryElement('pre', 'runtime-json');
    pre.textContent = JSON.stringify(observation, null, 2);
    actual.append(pre);
  } else actual.append(summaryElement('p', 'small text-secondary', 'برای این اجرا observation معتبر جمع‌آوری نشده است.'));
  section.append(expected, actual);
  return section;
}

function renderParameters() {
  const module = selectedModule();
  testPicker.hidden = false;
  parameterForm.hidden = false;
  runButton.textContent = 'اجرای تست';
  const test = module?.tests.find((item) => item.id === testSelect.value);
  parameterForm.replaceChildren();
  testDescription.textContent = test?.description ?? '';
  runButton.disabled = !test;
  if (!test) return;
  const rememberedValues = readRememberedValues(module, test);
  for (const parameter of test.parameters) {
    const wrapper = document.createElement('div'); wrapper.className = parameter.type === 'boolean' ? 'parameter form-check' : 'parameter';
    const label = document.createElement('label'); label.htmlFor = `param-${parameter.id}`; label.textContent = `${parameter.label}${parameter.required ? ' *' : ''}`;
    label.className = parameter.type === 'boolean' ? 'form-check-label' : 'form-label';
    let tooltip;
    if (parameter.description) {
      tooltip = document.createElement('button');
      tooltip.type = 'button';
      tooltip.className = 'parameter-tooltip';
      tooltip.textContent = '؟';
      tooltip.dataset.tooltip = parameter.description;
      tooltip.setAttribute('aria-label', `توضیحات ${parameter.label}: ${parameter.description}`);
    }
    let reset;
    if (parameter.default !== undefined) {
      reset = document.createElement('button');
      reset.type = 'button';
      reset.className = 'parameter-reset';
      reset.textContent = '↺';
      reset.title = 'بازگرداندن مقدار پیش‌فرض';
      reset.setAttribute('aria-label', `بازگرداندن ${parameter.label} به مقدار پیش‌فرض`);
    }
    let actions;
    if (tooltip || reset) {
      actions = document.createElement('span');
      actions.className = 'parameter-actions';
      if (tooltip) actions.append(tooltip);
      if (reset) actions.append(reset);
    }
    let input;
    if (parameter.type === 'boolean') {
      input = document.createElement('input'); input.type = 'checkbox';
      input.className = 'form-check-input';
      input.checked = rememberedValues[parameter.id] ?? parameter.default ?? false;
    } else {
      input = document.createElement('input'); input.type = parameter.type === 'password' ? 'password' : parameter.type === 'number' ? 'number' : 'text';
      input.className = 'form-control';
      if (parameter.minimum !== undefined) input.min = String(parameter.minimum);
      if (parameter.maximum !== undefined) input.max = String(parameter.maximum);
      const initialValue = rememberedValues[parameter.id] ?? parameter.default;
      if (initialValue !== undefined) input.value = String(initialValue);
      if (parameter.required && !(parameter.type === 'password' && parameter.allowEmpty)) input.required = true;
    }
    input.id = `param-${parameter.id}`; input.name = parameter.id;
    if (parameter.type === 'password') input.autocomplete = 'current-password';
    const saveCurrentValue = () => {
      if (parameter.type === 'boolean') rememberValue(module, test, parameter, input.checked);
      else if (parameter.type === 'number') {
        const number = input.value === '' ? undefined : Number(input.value);
        rememberValue(module, test, parameter, Number.isSafeInteger(number) ? number : undefined);
      } else rememberValue(module, test, parameter, input.value);
    };
    input.addEventListener(parameter.type === 'boolean' ? 'change' : 'input', saveCurrentValue);
    reset?.addEventListener('click', () => {
      if (parameter.type === 'boolean') input.checked = parameter.default;
      else input.value = String(parameter.default);
      saveCurrentValue();
    });
    if (parameter.type === 'boolean') {
      wrapper.append(input, label);
      if (actions) wrapper.append(actions);
    } else {
      const labelGroup = document.createElement('div'); labelGroup.className = 'parameter-label-group';
      labelGroup.append(label);
      if (actions) labelGroup.append(actions);
      wrapper.append(labelGroup, input);
    }
    parameterForm.append(wrapper);
  }
}

actionSelect.addEventListener('change', () => {
  currentStep = 'action';
  latestOutcome = null;
  document.querySelector('#run-output').replaceChildren();
  document.querySelector('#build-output').replaceChildren();
  document.querySelector('#run-status').textContent = '';
  document.querySelector('#build-status').textContent = '';
  for (const card of actionChoices.querySelectorAll('[data-action-value]')) {
    const selected = card.dataset.actionValue === actionSelect.value;
    card.classList.toggle('selected', selected);
    card.setAttribute('aria-checked', String(selected));
  }
  renderTests();
  renderParameters();
  renderWorkflow();
  updateSelectionUrl();
});

actionChoices.addEventListener('click', (event) => {
  const card = event.target.closest('[data-action-value]');
  if (!card || actionSelect.disabled) return;
  actionSelect.value = card.dataset.actionValue;
  actionSelect.dispatchEvent(new Event('change', { bubbles: true }));
});

moduleNext.addEventListener('click', () => goToWorkflowStep('action'));
actionNext.addEventListener('click', () => {
  const taskAction = selectedModuleTaskAction();
  goToWorkflowStep(taskAction?.id ?? 'test');
});
document.querySelector('#action-back').addEventListener('click', () => goToWorkflowStep('module'));
document.querySelector('#test-back').addEventListener('click', () => goToWorkflowStep('action'));
document.querySelector('#run-back').addEventListener('click', () => goToWorkflowStep('action'));
document.querySelector('#build-back').addEventListener('click', () => goToWorkflowStep('action'));
document.querySelector('#run-app').addEventListener('click', () => runModuleAction('run'));
document.querySelector('#build-module').addEventListener('click', () => runModuleAction('build'));
workflowSteps.addEventListener('click', (event) => {
  const button = event.target.closest('[data-step-target]');
  if (button && !button.disabled) goToWorkflowStep(button.dataset.stepTarget);
});

testSelect.addEventListener('change', () => {
  latestOutcome = null;
  runSummary.replaceChildren();
  reportPanel.hidden = true;
  reportFrame.removeAttribute('src');
  result.removeAttribute('data-state');
  result.textContent = '';
  copyAiReportStatus.textContent = '';
  aiReportFallback.hidden = true;
  renderParameters();
  renderWorkflow();
  updateSelectionUrl();
});
window.addEventListener('popstate', restoreSelectionFromUrl);
parameterForm.addEventListener('submit', (event) => event.preventDefault());

async function runModuleAction(actionId) {
  const module = selectedModule();
  const action = module?.actions?.find((item) => item.id === actionId);
  if (!module || !action) return;

  if (actionId === 'run' && !runParameterForm.reportValidity()) return;
  const parameters = Object.fromEntries([...(action.parameters ?? [])]
    .map((parameter) => {
      const input = runParameterForm.elements.namedItem(parameter.id);
      return [parameter.id, parameter.type === 'boolean' ? input.checked : input.value];
    })
    .filter(([parameterId, value]) => {
      const parameter = action.parameters.find((item) => item.id === parameterId);
      return parameter.type === 'boolean' || value !== '';
    }));
  currentStep = actionId;
  renderWorkflow();
  const button = document.querySelector(actionId === 'run' ? '#run-app' : '#build-module');
  const status = document.querySelector(actionId === 'run' ? '#run-status' : '#build-status');
  const outputContainer = document.querySelector(actionId === 'run' ? '#run-output' : '#build-output');
  const outputStreams = createLiveActionOutputView(outputContainer);
  const liveOutput = { stdout: '', stderr: '' };
  const taskPath = module.projectPath === ':' ? `:${action.task}` : `${module.projectPath}:${action.task}`;

  button.disabled = true;
  status.removeAttribute('data-state');
  status.textContent = actionId === 'run' ? 'برنامه در حال اجراست…' : 'ساخت ماژول در حال اجراست…';
  try {
    const response = await fetch('/api/actions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ projectPath: module.projectPath, actionId, parameters }),
    });
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error ?? 'Gradle action request failed');
    }

    let outcome;
    const contentType = response.headers.get('content-type') ?? '';
    if (contentType.includes('text/event-stream') && response.body) {
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let pending = '';
      const consumeEvent = (block) => {
        let eventName = 'message';
        const dataLines = [];
        for (const line of block.split(/\r?\n/)) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim();
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
        }
        if (!dataLines.length) return;
        const payload = JSON.parse(dataLines.join('\n'));
        if (eventName === 'output') {
          if (Object.hasOwn(liveOutput, payload.stream)) liveOutput[payload.stream] += payload.data;
          appendLiveActionOutput(outputStreams, payload.stream, payload.data);
        } else if (eventName === 'completed') outcome = payload;
      };

      while (true) {
        const { value, done } = await reader.read();
        pending += decoder.decode(value, { stream: !done });
        let boundary;
        while ((boundary = pending.indexOf('\n\n')) >= 0) {
          consumeEvent(pending.slice(0, boundary));
          pending = pending.slice(boundary + 2);
        }
        if (done) break;
      }
      if (pending.trim()) consumeEvent(pending);
    } else {
      outcome = await response.json();
      for (const stream of ['stdout', 'stderr']) {
        liveOutput[stream] = outcome[stream] ?? '';
        appendLiveActionOutput(outputStreams, stream, liveOutput[stream]);
      }
    }
    if (!outcome) throw new Error('Gradle stream ended before the final result arrived');
    const succeeded = outcome.executionStatus === 'Passed';
    status.dataset.state = outcome.executionStatus;
    status.textContent = `${succeeded ? 'اجرا موفق بود' : 'اجرا ناموفق بود'} · ${taskPath} · exit ${outcome.exitCode ?? 'ناموجود'} · ${formatDuration(outcome.processDurationMs)}`;
    if (outcome.outputTruncated) {
      outputContainer.append(summaryElement('p', 'small text-warning mt-2', 'بخشی از خروجی به علت محدودیت حجم ذخیره نشده است.'));
    }
    if (!liveOutput.stdout && !liveOutput.stderr) {
      outputContainer.append(summaryElement('p', 'small text-secondary mt-2', 'فرایند هیچ متنی در stdout یا stderr تولید نکرد.'));
    }
  } catch (error) {
    status.dataset.state = 'Failed';
    status.textContent = `اجرای ${taskPath} ناموفق بود: ${error.message}`;
    if (!liveOutput.stdout && !liveOutput.stderr) {
      outputContainer.append(summaryElement('p', 'small text-danger mt-2', error.message));
    }
  } finally {
    button.disabled = false;
  }
}

runButton.addEventListener('click', async () => {
  const module = selectedModule();
  const test = module?.tests.find((item) => item.id === testSelect.value);
  if (!module || !test) return;
  const inputParameters = test.parameters.map((parameter) => {
    const input = parameterForm.elements.namedItem(parameter.id);
    const value = parameter.type === 'boolean' ? input.checked : input.value;
    return { id: parameter.id, label: parameter.label, value: parameter.secret ? undefined : value, secret: parameter.secret };
  });
  const secretValues = test.parameters.filter((parameter) => parameter.secret)
    .map((parameter) => parameterForm.elements.namedItem(parameter.id).value).filter(Boolean);
  const runMetadata = {
    moduleInfo: { title: module.module.title, projectPath: module.projectPath },
    testInfo: { title: test.title, id: test.id },
    inputParameters,
    startedAt: new Date().toISOString(),
  };
  const values = {};
  for (const parameter of test.parameters) {
    const input = parameterForm.elements.namedItem(parameter.id);
    if (parameter.type === 'boolean') values[parameter.id] = input.checked;
    else if (parameter.type === 'number') values[parameter.id] = input.value === '' ? undefined : Number(input.value);
    else if (input.value !== '' || parameter.required) values[parameter.id] = input.value;
  }
  runButton.disabled = true;
  result.removeAttribute('data-state'); result.textContent = 'تست در حال اجراست…';
  reportPanel.hidden = true; reportFrame.removeAttribute('src');
  latestOutcome = null;
  runSummary.replaceChildren();
  try {
    const response = await fetch('/api/runs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectPath: module.projectPath, testId: test.id, parameters: values }) });
    const outcome = await response.json();
    if (!response.ok) throw new Error(outcome.error ?? 'Test request failed');
    latestOutcome = { ...redactSecretValues(outcome, secretValues), ...runMetadata, completedAt: new Date().toISOString() };
    result.dataset.state = outcome.executionStatus;
    result.textContent = 'جزئیات اجرای Gradle در مرحلهٔ گزارش نتیجه نمایش داده می‌شود.';
    renderRunSummary(latestOutcome, test);
    currentStep = 'results';
    renderWorkflow();
  } catch (error) {
    latestOutcome = { ...runMetadata, executionStatus: 'Failed', message: redactSecretValues(error.message, secretValues), completedAt: new Date().toISOString() };
    result.dataset.state = 'Failed';
    result.textContent = 'اجرای درخواست ناموفق بود؛ جزئیات در مرحلهٔ گزارش نتیجه آمده است.';
    renderRunSummary(latestOutcome, test);
    currentStep = 'results';
    renderWorkflow();
  } finally {
    for (const input of parameterForm.querySelectorAll('input[type="password"]')) input.value = '';
    runButton.disabled = false;
  }
});

copyAiReportButton.addEventListener('click', async () => {
  if (!latestOutcome) return;
  const markdown = createAiHandoffMarkdown(latestOutcome);
  aiReportFallback.value = markdown;
  copyAiReportStatus.textContent = '';
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard API unavailable');
    await navigator.clipboard.writeText(markdown);
    aiReportFallback.hidden = true;
    copyAiReportStatus.textContent = 'گزارش Markdown کپی شد و آمادهٔ الصاق برای AI است.';
  } catch {
    aiReportFallback.hidden = false;
    aiReportFallback.focus();
    aiReportFallback.select();
    copyAiReportStatus.textContent = 'کپی خودکار در دسترس نیست؛ متن گزارش انتخاب شده و می‌توانید آن را کپی کنید.';
  }
});

function showModuleLoadError(error) {
  connection.className = 'badge rounded-pill text-bg-danger';
  connection.textContent = 'خطا در بارگذاری';
  modulePath.replaceChildren();

  const message = document.createElement('p');
  message.className = 'text-danger mb-2';
  message.textContent = 'فهرست پروژه‌های Gradle بارگذاری نشد.';
  const detail = document.createElement('p');
  detail.className = 'small text-secondary';
  detail.textContent = error.message;
  const retry = document.createElement('button');
  retry.type = 'button';
  retry.className = 'btn btn-outline-primary';
  retry.textContent = 'تلاش دوباره';
  retry.addEventListener('click', async () => {
    retry.disabled = true;
    retry.textContent = 'در حال تلاش…';
    try { await loadModules(); }
    catch (retryError) { showModuleLoadError(retryError); }
  });
  modulePath.append(message, detail, retry);
}

loadModules().catch(showModuleLoadError);
