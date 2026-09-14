const $ = id => document.getElementById(id);
const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value || 0);
const date = value => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(value));
let socket, revenueChart, seatChart;

async function api(path, body) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) })
  });
  const value = await response.json();
  if (!response.ok) throw new Error(value.message || 'Không tải được dữ liệu.');
  return value;
}
function loggedOut(message = '') {
  socket?.disconnect();socket = null;
  $('dashboard').hidden = true;$('login-panel').hidden = false;$('logout').hidden = true;
  $('username').textContent = '';$('login-error').textContent = message;
}
async function loggedIn(user) {
  $('login-panel').hidden = true;$('dashboard').hidden = false;$('logout').hidden = false;
  $('username').textContent = user.display_name;
  socket?.disconnect();
  socket = io({ reconnection: true });
  socket.on('dashboard:update', render);
  socket.on('dashboard:status', value => { $('status').textContent = value.message;$('status').className = 'offline'; });
  socket.on('connect_error', error => { $('status').textContent = error.message;$('status').className = 'offline'; });
  socket.on('disconnect', reason => {
    $('status').textContent = 'Đã ngắt cập nhật trực tiếp.';$('status').className = 'offline';
    if (reason === 'io server disconnect') loggedOut('Phiên đã hết hạn hoặc tài khoản không còn quyền truy cập.');
  });
  try { render(await api('/api/dashboard')); }
  catch (error) { $('status').textContent = error.message;$('status').className = 'offline'; }
}
$('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget, button = form.querySelector('button');
  button.disabled = true;$('login-error').textContent = '';
  try {
    const fields = new FormData(form);
    const result = await api('/api/login', { username: fields.get('username'), password: fields.get('password') });
    form.reset();await loggedIn(result.user);
  } catch (error) { $('login-error').textContent = error.message; }
  finally { button.disabled = false; }
});
$('logout').addEventListener('click', async () => {
  try { await api('/api/logout', {});loggedOut(); }
  catch (error) { $('status').textContent = error.message; }
});
function rows(target, values) {
  const body = $(target);body.replaceChildren();
  for (const valuesOfRow of values) {
    const tr = document.createElement('tr');
    for (const value of valuesOfRow) { const td = document.createElement('td');td.textContent = value ?? '';tr.append(td); }
    body.append(tr);
  }
  if (!values.length) {
    const tr = document.createElement('tr'), td = document.createElement('td');
    td.colSpan = 8;td.textContent = 'Chưa có dữ liệu.';tr.append(td);body.append(tr);
  }
}
function render(data) {
  $('status').textContent = '● Đang cập nhật từ Java server';$('status').className = 'online';
  $('updated').textContent = 'Cập nhật ' + date(data.updatedAt);
  $('revenue').textContent = money(data.stats.revenue);
  $('tickets').textContent = data.stats.tickets;
  $('held').textContent = data.stats.heldSeats;
  $('clients').textContent = data.stats.connectedClients;
  const shows = data.shows.filter(show => show.status === 'OPEN' && show.starts_at > data.stats.serverTime).sort((a, b) => a.starts_at - b.starts_at);
  $('show-count').textContent = shows.length + ' suất';
  rows('shows-body', shows.map(s => [s.title, s.room_name, date(s.starts_at), money(s.price_vnd), s.sold + '/' + s.capacity, s.held, Math.round(s.sold / Math.max(1, s.capacity) * 100) + '%']));
  rows('bookings-body', data.bookings.slice(0, 30).map(b => [b.code, b.username, b.title + ' · ' + b.seats, money(b.total_vnd), b.status === 'CONFIRMED' ? 'Đã xác nhận' : 'Đã huỷ', date(b.created_at)]));
  rows('logs-body', data.logs.slice(0, 40).map(l => [date(l.created_at), l.username, l.action, l.detail]));
  const dayFormat = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });
  const days = Array.from({ length: 7 }, (_, i) => dayFormat.format(new Date(data.stats.serverTime - (6 - i) * 86400000)));
  const daily = new Map(data.stats.daily.map(d => [d.day, d.revenue]));
  const revenueData = { labels: days.map(d => d.slice(5).split('-').reverse().join('/')), datasets: [{ label: 'Doanh thu demo (VND)', data: days.map(d => daily.get(d) || 0), backgroundColor: '#da8540', borderRadius: 5 }] };
  const next = shows.slice(0, 6);
  const seatData = {
    labels: next.map(s => s.room_name + ' · ' + date(s.starts_at)),
    datasets: [{ label: 'Đã bán', data: next.map(s => s.sold), backgroundColor: '#ba655e' }, { label: 'Đang giữ', data: next.map(s => s.held), backgroundColor: '#e4bc58' }]
  };
  if (!revenueChart) revenueChart = new Chart($('revenue-chart'), { type: 'bar', data: revenueData, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } } });
  else { revenueChart.data = revenueData;revenueChart.update('none'); }
  if (!seatChart) seatChart = new Chart($('seat-chart'), { type: 'bar', data: seatData, options: { indexAxis: 'y', responsive: true, maintainAspectRatio: false, scales: { x: { stacked: true, beginAtZero: true, ticks: { precision: 0 } }, y: { stacked: true } } } });
  else { seatChart.data = seatData;seatChart.update('none'); }
}
api('/api/session').then(result => loggedIn(result.user)).catch(() => loggedOut());
