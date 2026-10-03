import fs from 'node:fs';
import path from 'node:path';
import { isWithin } from './path-safety.mjs';
import { reportRelativeFile } from './runner-resolver.mjs';

const CONTENT_TYPES = new Map([
  ['.html', 'text/html; charset=utf-8'], ['.css', 'text/css; charset=utf-8'], ['.js', 'text/javascript; charset=utf-8'],
  ['.png', 'image/png'], ['.jpg', 'image/jpeg'], ['.jpeg', 'image/jpeg'], ['.gif', 'image/gif'], ['.svg', 'image/svg+xml'],
  ['.woff', 'font/woff'], ['.woff2', 'font/woff2'], ['.ttf', 'font/ttf'], ['.json', 'application/json; charset=utf-8'],
]);

export function serveReportFile(response, reportDirectory, filePath) {
  try {
    const candidate = reportRelativeFile(reportDirectory, filePath);
    const canonical = fs.realpathSync(candidate);
    if (!isWithin(reportDirectory, canonical) || !fs.statSync(canonical).isFile()) throw new Error('Report file is not available');
    response.writeHead(200, { 'Content-Type': CONTENT_TYPES.get(path.extname(canonical).toLowerCase()) ?? 'application/octet-stream', 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-store' });
    fs.createReadStream(canonical).pipe(response);
  } catch {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
    response.end('Report file not found');
  }
}
