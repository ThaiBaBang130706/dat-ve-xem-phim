import express from 'express';
import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { Server } from 'socket.io';

const require = createRequire(import.meta.url);
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SESSION_MS = 4 * 60 * 60 * 1000;

function cookieId(header = '') {
  return header.split(';').map(part => part.trim()).find(part => part.startsWith('cinema_session='))?.slice(15) || '';
}
function sameOrigin(req) {
  if (!req.headers.origin) return true;
  try { return new URL(req.headers.origin).host === req.headers.host; }
  catch { return false; }
}

export function createDashboard({ bridgeUrl, bridgeKey, pollMs = 2000, secureCookie = false }) {
  if (!bridgeKey || bridgeKey.length < 32) throw new Error('Cần dashboard.key do Java server tạo.');
  const app = express();
  const http = createServer(app);
  const sessions = new Map();
  const attempts = new Map();
  const io = new Server(http, {
    maxHttpBufferSize: 16 * 1024,
    allowRequest: (req, done) => done(null, sameOrigin(req))
  });
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    res.set({
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'",
      'Cache-Control': 'no-store'
    });
    if (!sameOrigin(req)) return res.status(403).json({ message: 'Nguồn yêu cầu không hợp lệ.' });
    if (req.method === 'POST' && !req.is('application/json')) return res.status(415).json({ message: 'Cần JSON.' });
    next();
  });
  app.use(express.json({ limit: '16kb' }));

  async function bridge(path, body) {
    const response = await fetch(new URL(path, bridgeUrl), {
      method: body === undefined ? 'GET' : 'POST',
      headers: { Authorization: 'Bearer ' + bridgeKey, 'Content-Type': 'application/json' },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(5000)
    });
    const value = await response.json();
    if (!response.ok) {
      const error = new Error(value.message || 'Java server từ chối yêu cầu.');
      error.status = response.status;
      throw error;
    }
    return value;
  }
  function revoke(id) {
    sessions.delete(id);
    io.in('session:' + id).disconnectSockets(true);
  }
  async function authenticate(id) {
    const session = sessions.get(id);
    if (!session || session.expires <= Date.now()) { revoke(id); return null; }
    try { await bridge('/api/auth/check', { userId: session.user.id }); }
    catch (error) { if (error.status === 403 || error.status === 401) { revoke(id); return null; } throw error; }
    return session;
  }
  const asyncRoute = fn => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
  const protectedRoute = fn => asyncRoute(async (req, res) => {
    const id = cookieId(req.headers.cookie);
    const session = await authenticate(id);
    if (!session) return res.status(401).json({ message: 'Vui lòng đăng nhập quản trị.' });
    await fn(req, res, session, id);
  });
  function setCookie(res, id, maxAge) {
    res.cookie('cinema_session', id, { httpOnly: true, sameSite: 'strict', secure: secureCookie, maxAge, path: '/' });
  }
  app.post('/api/login', asyncRoute(async (req, res) => {
    const ip = req.socket.remoteAddress;
    const now = Date.now();
    let rate = attempts.get(ip);
    if (!rate || rate.until < now) { rate = { count: 0, until: now + 60_000 }; attempts.set(ip, rate); }
    if (++rate.count > 10) return res.status(429).json({ message: 'Thử quá nhiều lần. Chờ 1 phút rồi đăng nhập lại.' });
    if (typeof req.body?.username !== 'string' || typeof req.body?.password !== 'string') return res.status(400).json({ message: 'Nhập tên đăng nhập và mật khẩu.' });
    const user = await bridge('/api/auth', { username: req.body.username, password: req.body.password });
    if (user.role !== 'ADMIN' || user.status !== 'ACTIVE') return res.status(403).json({ message: 'Cần tài khoản quản trị đang hoạt động.' });
    revoke(cookieId(req.headers.cookie));
    if (sessions.size >= 100) return res.status(503).json({ message: 'Dashboard đã đủ phiên. Vui lòng thử lại sau.' });
    const id = randomBytes(32).toString('base64url');
    sessions.set(id, { user, expires: now + SESSION_MS });
    setCookie(res, id, SESSION_MS);
    res.json({ user });
  }));
  app.post('/api/logout', (req, res) => {
    revoke(cookieId(req.headers.cookie));setCookie(res, '', 0);res.json({ success: true });
  });
  app.get('/api/session', protectedRoute(async (req, res, session) => res.json({ user: session.user })));
  async function snapshot() {
    const [stats, shows, bookings, logs] = await Promise.all(['/api/stats', '/api/shows', '/api/bookings', '/api/logs'].map(path => bridge(path)));
    return { stats, shows, bookings, logs, updatedAt: Date.now() };
  }
  app.get('/api/dashboard', protectedRoute(async (req, res) => res.json(await snapshot())));
  app.use('/api', (req, res) => res.status(404).json({ message: 'Dashboard chỉ hỗ trợ các API đọc đã công bố.' }));
  app.get('/vendor/chart.js', (req, res) => res.sendFile(resolve(dirname(require.resolve('chart.js')), 'chart.umd.js')));
  app.use(express.static(resolve(root, 'public'), { etag: false }));
  app.use((error, req, res, next) => {
    if (res.headersSent) return next(error);
    const status = error.status === 401 || error.status === 403 ? 403 : error.status === 413 ? 413 : error.type === 'entity.parse.failed' ? 400 : 502;
    res.status(status).json({ message: status === 502 ? 'Chưa kết nối được Java server. Kiểm tra server và cổng 5001.' : error.message });
  });
  io.use(async (socket, next) => {
    try {
      const id = cookieId(socket.request.headers.cookie);
      const session = await authenticate(id);
      if (!session) return next(new Error('Vui lòng đăng nhập quản trị.'));
      socket.data.sessionId = id;next();
    } catch { next(new Error('Java server chưa sẵn sàng.')); }
  });
  io.on('connection', socket => {
    socket.join('admins');
    socket.join('session:' + socket.data.sessionId);
    poll();
  });
  let polling = false;
  async function poll() {
    if (polling || io.engine.clientsCount === 0) return;
    polling = true;
    try {
      for (const [id, session] of sessions) {
        if (session.expires <= Date.now()) revoke(id);
        else await authenticate(id);
      }
      const data = await snapshot();
      io.to('admins').emit('dashboard:update', data);
    } catch {
      io.to('admins').emit('dashboard:status', { online: false, message: 'Java server đang mất kết nối; số liệu hiển thị là lần cập nhật trước.' });
    } finally { polling = false; }
  }
  const interval = setInterval(() => {
    for (const [id, session] of sessions) if (session.expires <= Date.now()) revoke(id);
    for (const [ip, rate] of attempts) if (rate.until <= Date.now()) attempts.delete(ip);
    void poll();
  }, pollMs);
  interval.unref();
  return {
    app, http, io,
    async close() { clearInterval(interval); await new Promise(resolveClose => io.close(resolveClose)); }
  };
}
