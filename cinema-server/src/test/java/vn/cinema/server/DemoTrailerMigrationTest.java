package vn.cinema.server;

import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.cinema.server.Database.*;

class DemoTrailerMigrationTest {
 @TempDir Path dir;
 final Clock clock=Clock.fixed(Instant.parse("2026-09-22T03:00:00Z"),ZoneOffset.UTC);
 @Test void freshCatalogExposesLabelledTrailersForAllTenMovies()throws Exception {
  Database db=new Database(dir.resolve("fresh.db"));db.seed(clock);
  var movies=new CatalogService(db,clock).movies(false);assertEquals(10,movies.size());
  for(var entry:movies){
   String url=Json.str(entry.getAsJsonObject(),"trailer_url","");
   assertFalse(DemoTrailers.notice(url).isEmpty());assertEquals(url,MediaService.url(url,false));
  }
  assertEquals("",DemoTrailers.notice("https://example.org/custom.mp4"));
 }
 @Test void upgradePreservesCustomDataAndDoesNotRestoreRemovedTrailers()throws Exception {
  Path file=dir.resolve("existing.db");Database db=new Database(file);db.seed(clock);
  String before=db.read(c->rows(c,"SELECT * FROM tickets ORDER BY id").toString());
  // Recreate the previous catalogue version, including administrator edits.
  db.write(c->{
   exec(c,"DELETE FROM schema_migrations WHERE version=2");
   exec(c,"UPDATE movies SET trailer_url=''");
   exec(c,"UPDATE movies SET trailer_url='https://example.org/custom.mp4' WHERE id=1");
   exec(c,"UPDATE movies SET description='Nội dung quản trị viên đã sửa' WHERE id=2");
   exec(c,"UPDATE movies SET title='Phim riêng của rạp' WHERE id=3");
   exec(c,"INSERT INTO movies(title,description,genre,duration_minutes) VALUES('Hẹn Nhau Ở Huế','Phim thật do admin nhập','Hài',100)");
   return null;
  });
  Database upgraded=new Database(file);
  upgraded.read(c->{
   assertEquals("https://example.org/custom.mp4",Json.str(one(c,"SELECT * FROM movies WHERE id=1"),"trailer_url",""));
   assertEquals(7,rows(c,"SELECT id FROM movies WHERE trailer_url IN (?,?)",DemoTrailers.BUNNY,DemoTrailers.SINTEL).size());
   assertEquals(3,rows(c,"SELECT id FROM movies WHERE trailer_url='' ").size());
   assertEquals(before,rows(c,"SELECT * FROM tickets ORDER BY id").toString());return null;
  });
  upgraded.write(c->{exec(c,"UPDATE movies SET trailer_url='' WHERE id=4");return null;});
  Database reopened=new Database(file);reopened.seed(clock);
  reopened.read(c->{assertEquals("",Json.str(one(c,"SELECT * FROM movies WHERE id=4"),"trailer_url",""));assertEquals(1,rows(c,"SELECT version FROM schema_migrations WHERE version=2").size());return null;});
 }
}
