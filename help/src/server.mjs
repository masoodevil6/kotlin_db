import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { discoverGradleProjects } from './gradle-project-discovery.mjs';
import { buildRegistry, findRegisteredAction, findRegisteredTest, publicModules } from './help-registry.mjs';
import { validateParameters } from './parameter-validator.mjs';
import { executeGradleTest } from './gradle-test-runner.mjs';
import { executeGradleAction } from './gradle-action-runner.mjs';
import { serveReportFile } from './report-server.mjs';

const moduleDir = path.dirname(fileURLToPath(import.meta.url));
const helpRoot = path.resolve(moduleDir, '..');
const buildRoot = path.resolve(helpRoot, '..');
const publicRoot = path.join(helpRoot, 'public');
const PUBLIC_FILES = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
  ['/vendor/bootstrap.rtl.min.css', ['vendor/bootstrap.rtl.min.css', 'text/css; charset=utf-8']],
  ['/font/Vazir-Regular.ttf', ['../font/Vazir-Regular.ttf', 'font/ttf']],
]);

function sendJson(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  response.end(JSON.stringify(body));
}

async function readJson(request, maximumBytes = 64 * 1024) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > maximumBytes) throw new Error('Request body is too large');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function assertLocalRequest(request) {
  const host = request.headers.host ?? '';
  if (!/^(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?$/i.test(host)) throw new Error('Only loopback requests are accepted');
  const origin = request.headers.origin;
  if (origin !== undefined) {
    let parsed;
    try { parsed = new URL(origin); } catch { throw new Error('Invalid request Origin'); }
    if (!['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname) || parsed.host !== host) throw new Error('Cross-origin requests are not accepted');
  }
  if (request.method === 'POST' && !/^application\/json(?:\s*;|$)/i.test(String(request.headers['content-type'] ?? ''))) {
    throw new Error('POST requests must use application/json');
  }
}

function servePublic(response, pathname) {
  const target = PUBLIC_FILES.get(pathname);
  if (!target) return false;
  const [file, contentType] = target;
  const content = fs.readFileSync(path.join(publicRoot, file));
  response.writeHead(200, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-src 'self'; object-src 'none'; base-uri 'none'",
  });
  response.end(content);
  return true;
}

export function createHelpServer({ registry, projects = [...registry.values()], buildRoot: root = buildRoot, runGradleTest = executeGradleTest, runGradleAction = executeGradleAction }) {
  const reportSessions = new Map();
  const server = http.createServer(async (request, response) => {
    try {
      assertLocalRequest(request);
      const url = new URL(request.url ?? '/', `http://${request.headers.host}`);
      if (request.method === 'GET' && url.pathname === '/api/modules') {
        return sendJson(response, 200, {
          projects: projects.map((project) => project.projectPath),
          modules: publicModules(registry),
        });
      }
      if (request.method === 'GET' && url.pathname === '/api/health') return sendJson(response, 200, { status: 'ok' });
      if (request.method === 'POST' && url.pathname === '/api/runs') {
        const body = await readJson(request);
        if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('Request body must be an object');
        for (const key of Object.keys(body)) if (!['projectPath', 'testId', 'parameters'].includes(key)) throw new Error(`Unknown request field '${key}'`);
        if (typeof body.projectPath !== 'string' || typeof body.testId !== 'string') throw new Error('projectPath and testId are required');
        const selected = findRegisteredTest(registry, body.projectPath, body.testId);
        if (!selected) return sendJson(response, 404, { error: 'Unknown Module or Test' });
        const bindings = validateParameters(selected.test, body.parameters ?? {});
        const sessionKey = `${body.projectPath}\0${body.testId}`;
        for (const [token, session] of reportSessions) if (session.key === sessionKey) reportSessions.delete(token);
        const result = await runGradleTest({ buildRoot: root, module: selected.module, test: selected.test, bindings });
        if (result.reportToken) reportSessions.set(result.reportToken, { key: sessionKey, directory: result.reportDirectory });
        return sendJson(response, 200, {
          executionStatus: result.status,
          exitCode: result.exitCode,
          reportAvailable: result.reportAvailable,
          reportUrl: result.reportToken ? `/reports/${result.reportToken}/index.html` : null,
          runId: result.runId,
          runtimeStatus: result.runtimeStatus ?? 'unavailable',
          runtime: result.runtime ?? null,
          runtimeValidationError: result.runtimeValidationError ?? null,
          context: selected.test.context ?? null,
          taskPath: result.taskPath,
          testFilter: result.testFilter,
          processDurationMs: result.processDurationMs,
          testResults: result.testResults,
          message: result.message,
        });
      }
      if (request.method === 'POST' && url.pathname === '/api/actions') {
        const body = await readJson(request);
        if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('Request body must be an object');
        for (const key of Object.keys(body)) if (!['projectPath', 'actionId', 'parameters'].includes(key)) throw new Error(`Unknown request field '${key}'`);
        if (typeof body.projectPath !== 'string' || typeof body.actionId !== 'string') throw new Error('projectPath and actionId are required');
        const selected = findRegisteredAction(registry, body.projectPath, body.actionId);
        if (!selected) return sendJson(response, 404, { error: 'Unknown Module or Action' });
        const bindings = validateParameters({ parameters: selected.action.parameters ?? [] }, body.parameters ?? {});
        const wantsStream = String(request.headers.accept ?? '').includes('text/event-stream');
        const sendEvent = (event, payload) => {
          if (!response.destroyed) response.write(`event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`);
        };
        if (wantsStream) {
          response.writeHead(200, {
            'Content-Type': 'text/event-stream; charset=utf-8',
            'Cache-Control': 'no-cache, no-transform',
            'X-Accel-Buffering': 'no',
            'X-Content-Type-Options': 'nosniff',
          });
          response.flushHeaders?.();
          sendEvent('started', { taskPath: selected.action.task === selected.action.id
            ? (selected.module.projectPath === ':' ? `:${selected.action.task}` : `${selected.module.projectPath}:${selected.action.task}`)
            : null });
        }
        const result = await runGradleAction({
          buildRoot: root,
          module: selected.module,
          action: selected.action,
          bindings,
          onOutput: wantsStream ? (stream, data) => sendEvent('output', { stream, data }) : undefined,
        });
        const payload = {
          executionStatus: result.status,
          exitCode: result.exitCode,
          taskPath: result.taskPath,
          processDurationMs: result.processDurationMs,
          stdout: result.stdout ?? '',
          stderr: result.stderr ?? '',
          outputTruncated: result.outputTruncated ?? false,
          message: result.message,
        };
        if (wantsStream) {
          sendEvent('completed', payload);
          response.end();
          return;
        }
        return sendJson(response, 200, payload);
      }
      if (request.method === 'GET' && url.pathname.startsWith('/reports/')) {
        const [, , token, ...fileParts] = url.pathname.split('/');
        const session = reportSessions.get(token);
        if (!session) {
          response.writeHead(404); response.end('Report not found'); return;
        }
        let filePath;
        try { filePath = fileParts.map((part) => decodeURIComponent(part)).join('/') || 'index.html'; }
        catch { response.writeHead(404); response.end('Report not found'); return; }
        return serveReportFile(response, session.directory, filePath);
      }
      if (request.method === 'GET' && servePublic(response, url.pathname)) return;
      sendJson(response, 404, { error: 'Not found' });
    } catch (error) {
      const status = /too large/i.test(error.message) ? 413 : /Unknown Module or (Test|Action)/.test(error.message) ? 404 : 400;
      sendJson(response, status, { error: error.message });
    }
  });
  return server;
}

export async function startHelp({ port = Number(process.env.HELP_PORT ?? 4173), host = '127.0.0.1' } = {}) {
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('HELP_PORT must be an integer between 1 and 65535');
  const projects = await discoverGradleProjects({ buildRoot });
  const registry = buildRegistry(projects);
  const server = createHelpServer({ registry, projects });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, resolve);
  });
  return { server, registry, address: server.address() };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  startHelp().then(({ address }) => {
    process.stdout.write(`Help available at http://${address.address}:${address.port}\n`);
  }).catch((error) => {
    process.stderr.write(`Help startup failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
