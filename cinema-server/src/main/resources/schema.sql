PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS users(
 id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE,
 password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN('USER','ADMIN')),
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','LOCKED')), created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS movies(
 id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, genre TEXT NOT NULL, duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 30 AND 300),
 age_rating TEXT NOT NULL DEFAULT 'P', description TEXT NOT NULL DEFAULT '', poster_url TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS rooms(
 id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, rows_count INTEGER NOT NULL CHECK(rows_count BETWEEN 1 AND 12),
 cols_count INTEGER NOT NULL CHECK(cols_count BETWEEN 1 AND 16), active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS showtimes(
 id INTEGER PRIMARY KEY AUTOINCREMENT, movie_id INTEGER NOT NULL REFERENCES movies(id), room_id INTEGER NOT NULL REFERENCES rooms(id),
 starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL, price_vnd INTEGER NOT NULL CHECK(price_vnd BETWEEN 1000 AND 10000000),
 status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','CANCELLED')), UNIQUE(room_id,starts_at)
);
CREATE TABLE IF NOT EXISTS bookings(
 id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE, user_id INTEGER NOT NULL REFERENCES users(id),
 show_id INTEGER NOT NULL REFERENCES showtimes(id), request_id TEXT NOT NULL,
 total_vnd INTEGER NOT NULL CHECK(total_vnd>=0), status TEXT NOT NULL CHECK(status IN('CONFIRMED','CANCELLED')),
 payment_method TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(user_id,request_id)
);
CREATE TABLE IF NOT EXISTS seats_state(
 show_id INTEGER NOT NULL REFERENCES showtimes(id), seat_label TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK(status IN('AVAILABLE','HELD','SOLD','UNAVAILABLE')),
 held_by INTEGER REFERENCES users(id), hold_session TEXT, hold_until INTEGER, booking_id INTEGER REFERENCES bookings(id),
 PRIMARY KEY(show_id,seat_label),
 CHECK((status='HELD' AND held_by IS NOT NULL AND hold_session IS NOT NULL AND hold_until IS NOT NULL AND booking_id IS NULL)
 OR (status='SOLD' AND booking_id IS NOT NULL AND held_by IS NULL AND hold_session IS NULL AND hold_until IS NULL)
 OR (status IN('AVAILABLE','UNAVAILABLE') AND booking_id IS NULL AND held_by IS NULL AND hold_session IS NULL AND hold_until IS NULL))
);
CREATE TABLE IF NOT EXISTS tickets(
 id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id INTEGER NOT NULL REFERENCES bookings(id),
 show_id INTEGER NOT NULL REFERENCES showtimes(id), seat_label TEXT NOT NULL, movie_title TEXT NOT NULL,
 room_name TEXT NOT NULL, starts_at INTEGER NOT NULL, price_vnd INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS unique_active_seat ON tickets(show_id,seat_label) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS hold_expiry ON seats_state(status,hold_until);
CREATE INDEX IF NOT EXISTS booking_user ON bookings(user_id,created_at);
CREATE INDEX IF NOT EXISTS show_date ON showtimes(starts_at);
CREATE TABLE IF NOT EXISTS logs(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER REFERENCES users(id),action TEXT NOT NULL,detail TEXT NOT NULL,created_at INTEGER NOT NULL);
