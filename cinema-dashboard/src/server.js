import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createDashboard } from './app.js';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const keyPath = process.env.JAVA_BRIDGE_KEY_FILE || resolve(root, '../data/dashboard.key');
let key;
try { key = (await readFile(keyPath, 'utf8')).trim(); }
catch { console.error('Chưa thấy dashboard.key. Chạy Java server trước, hoặc đặt JAVA_BRIDGE_KEY_FILE trỏ tới file khoá.'); process.exit(1); }

const dashboard = createDashboard({
  bridgeUrl: process.env.JAVA_BRIDGE_URL || 'http://127.0.0.1:5001',
  bridgeKey: key,
  secureCookie: process.env.COOKIE_SECURE === 'true'
});
const port = Number(process.env.PORT || 3000);
dashboard.http.listen(port, '0.0.0.0', () => console.log('Cinema dashboard: http://localhost:' + port));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await dashboard.close(); process.exit(0); });
