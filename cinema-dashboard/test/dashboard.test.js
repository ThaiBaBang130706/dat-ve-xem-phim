import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { io as connect } from 'socket.io-client';
import { createDashboard } from '../src/app.js';

test('protects HTTP and Socket.IO, forwards data, revokes locked/logged-out admins', { timeout: 15000 }, async () => {
  let locked = false;
  const key = 'test-only-key-'.repeat(4);
  const bridge = createServer(async (req, res) => {
    assert.equal(req.headers.authorization, 'Bearer ' + key);
    let raw = '';for await (const part of req) raw += part;
    const body = raw ? JSON.parse(raw) : {};
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/api/auth') {
      if (body.username !== 'admin' || body.password !== 'Admin@123') { res.writeHead(403);res.end(JSON.stringify({ message: 'Sai tài khoản.' }));return; }
      res.end(JSON.stringify({ id: 1, username: 'admin', display_name: 'Quản trị', role: 'ADMIN', status: 'ACTIVE' }));
    } else if (req.url === '/api/auth/check') {
      if (locked) { res.writeHead(403);res.end(JSON.stringify({ message: 'Đã khoá.' }));return; }
      res.end('{"active":true}');
    } else if (req.url === '/api/stats') res.end('{"revenue":150000,"tickets":2,"heldSeats":1,"connectedClients":2,"serverTime":1790000000000,"daily":[]}');
    else res.end('[]');
  });
  bridge.listen(0, '127.0.0.1');await once(bridge, 'listening');
  const dash = createDashboard({ bridgeUrl: 'http://127.0.0.1:' + bridge.address().port, bridgeKey: key, pollMs: 50 });
  dash.http.listen(0, '127.0.0.1');await once(dash.http, 'listening');
  const url = 'http://127.0.0.1:' + dash.http.address().port;
  const sockets = [];
  const post = (path, data, cookie) => fetch(url + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(data) });
  try {
    assert.equal((await fetch(url + '/api/dashboard')).status, 401);
    assert.equal((await post('/api/login', { username: 'user1', password: 'User@1234' })).status, 403);
    const rejected = connect(url, { transports: ['websocket'], reconnection: false });sockets.push(rejected);
    const denied = await Promise.race([once(rejected, 'connect_error'), new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('Socket auth timeout')), 5000); timer.unref(); })]);
    assert.match(denied[0].message, /đăng nhập/);
    const login = await post('/api/login', { username: 'admin', password: 'Admin@123' });
    assert.equal(login.status, 200);
    const setCookie = login.headers.get('set-cookie');assert.match(setCookie, /HttpOnly/);assert.match(setCookie, /SameSite=Strict/i);
    const cookie = setCookie.split(';')[0];
    const stats = await fetch(url + '/api/dashboard', { headers: { Cookie: cookie } });
    assert.equal(stats.status, 200);assert.equal((await stats.json()).stats.revenue, 150000);
    const authorized = connect(url, { transports: ['websocket'], reconnection: false, extraHeaders: { Cookie: cookie } });sockets.push(authorized);
    const update = await once(authorized, 'dashboard:update');
    assert.equal(update[0].stats.tickets, 2);
    assert.equal((await fetch(url + '/vendor/chart.js')).status, 200);
    assert.equal((await post('/api/bookings', {}, cookie)).status, 404);
    assert.equal((await fetch(url + '/api/session', { headers: { Cookie: cookie, Origin: 'http://evil.example' } })).status, 403);
    const disconnected = once(authorized, 'disconnect');
    assert.equal((await post('/api/logout', {}, cookie)).status, 200);await disconnected;
    assert.equal((await fetch(url + '/api/dashboard', { headers: { Cookie: cookie } })).status, 401);
    const again = await post('/api/login', { username: 'admin', password: 'Admin@123' });
    const nextCookie = again.headers.get('set-cookie').split(';')[0];
    locked = true;
    assert.equal((await fetch(url + '/api/dashboard', { headers: { Cookie: nextCookie } })).status, 401);
  } finally {
    sockets.forEach(socket => socket.disconnect());
    await dash.close();await new Promise(resolve => bridge.close(resolve));
  }
});
