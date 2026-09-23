package vn.cinema.server;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;

/** Additive, transactional upgrade: existing IDs, bookings and seat maps are retained. */
final class CatalogMigration {
 private CatalogMigration(){}
 static void migrate(Connection c)throws Exception {
  c.setAutoCommit(false);
  try {
   exec(c,"CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY)");
   if(one(c,"SELECT version FROM schema_migrations WHERE version=1")==null){
    exec(c,"CREATE TABLE areas(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE COLLATE NOCASE,active INTEGER NOT NULL DEFAULT 1)");
    exec(c,"CREATE TABLE cinemas(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,address TEXT NOT NULL DEFAULT '',area_id INTEGER NOT NULL REFERENCES areas(id),image_url TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,UNIQUE(area_id,name))");
    exec(c,"CREATE TABLE genres(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE COLLATE NOCASE,active INTEGER NOT NULL DEFAULT 1)");
    exec(c,"CREATE TABLE movie_genres(movie_id INTEGER NOT NULL REFERENCES movies(id),genre_id INTEGER NOT NULL REFERENCES genres(id),PRIMARY KEY(movie_id,genre_id))");
    exec(c,"CREATE TABLE media_assets(id TEXT PRIMARY KEY,mime_type TEXT NOT NULL,data TEXT NOT NULL,created_at INTEGER NOT NULL)");
    exec(c,"INSERT INTO areas(id,name) VALUES(1,'Chưa phân khu')");
    exec(c,"INSERT INTO cinemas(id,name,area_id) VALUES(1,'Rạp hiện tại',1)");
    // SQLite cannot add a REFERENCES column with a non-NULL default to populated tables.
    exec(c,"ALTER TABLE rooms ADD COLUMN cinema_id INTEGER REFERENCES cinemas(id)");
    exec(c,"UPDATE rooms SET cinema_id=1");
    exec(c,"CREATE TRIGGER room_requires_cinema_insert BEFORE INSERT ON rooms WHEN NEW.cinema_id IS NULL BEGIN SELECT RAISE(ABORT,'Room requires a cinema'); END");
    exec(c,"CREATE TRIGGER room_requires_cinema_update BEFORE UPDATE OF cinema_id ON rooms WHEN NEW.cinema_id IS NULL BEGIN SELECT RAISE(ABORT,'Room requires a cinema'); END");
    for(String key:List.of("banner_url","trailer_url","release_date","end_date","director","cast_names","country","language","presentation"))
     exec(c,"ALTER TABLE movies ADD COLUMN "+key+" TEXT NOT NULL DEFAULT ''");
    exec(c,"CREATE INDEX movie_genre_lookup ON movie_genres(genre_id,movie_id)");
    exec(c,"CREATE INDEX room_cinema_lookup ON rooms(cinema_id)");
    importGenres(c);
    exec(c,"INSERT INTO schema_migrations(version) VALUES(1)");
   }
   if(one(c,"SELECT version FROM schema_migrations WHERE version=2")==null){
    DemoTrailerMigration.populate(c);
    exec(c,"INSERT INTO schema_migrations(version) VALUES(2)");
   }
   c.commit();
  }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
 }
 static void importGenres(Connection c)throws Exception {
  for(JsonElement entry:rows(c,"SELECT id,genre FROM movies")){
   JsonObject movie=entry.getAsJsonObject();
   for(String part:Json.str(movie,"genre","").split("[,;/|]")){
    String name=part.strip();if(name.isEmpty())continue;
    exec(c,"INSERT OR IGNORE INTO genres(name) VALUES(?)",name);
    long genre=one(c,"SELECT id FROM genres WHERE name=?",name).get("id").getAsLong();
    exec(c,"INSERT OR IGNORE INTO movie_genres(movie_id,genre_id) VALUES(?,?)",movie.get("id").getAsLong(),genre);
   }
  }
 }
}
