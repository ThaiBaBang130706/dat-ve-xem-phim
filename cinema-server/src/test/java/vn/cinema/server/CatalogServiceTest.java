package vn.cinema.server;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.cinema.server.Database.*;

class CatalogServiceTest {
 @TempDir Path dir;
 final Clock clock=Clock.fixed(Instant.parse("2026-09-22T03:00:00Z"),ZoneOffset.UTC);
 final Session admin=new Session(1,"admin"),user=new Session(2,"customer");
 Database db;CatalogService catalog;AdminService service;
 @BeforeEach void setup()throws Exception{db=new Database(dir.resolve("cinema.db"));db.seed(clock);catalog=new CatalogService(db,clock);service=new AdminService(db,clock,new BookingService(db,clock));}
 JsonObject movie(){return Json.obj("title","Phim kiểm thử","genre_ids",List.of(1,2),"duration_minutes",110,"age_rating","P","description","Nội dung phim","poster_url","https://example.org/poster.jpg","banner_url","https://example.org/banner.jpg","trailer_url","https://example.org/trailer","release_date","2026-09-22","end_date","2026-10-22","director","Đạo diễn A","cast_names","Diễn viên A, B","country","Việt Nam","language","Tiếng Việt","presentation","Lồng tiếng Việt");}
 @Test void metadataManyGenresAndRenameRoundTripWithoutLosingMedia()throws Exception {
  JsonObject saved=catalog.saveMovie(admin,movie());long id=Json.num(saved,"id",0);
  catalog.saveLookup(admin,Json.obj("id",1,"name","Thể loại đổi tên"),"genres");
  JsonObject read=find(catalog.movies(false),id);assertEquals(2,read.getAsJsonArray("genres").size());assertTrue(Json.str(read,"genre","").contains("Thể loại đổi tên"));assertEquals("Đạo diễn A",Json.str(read,"director",""));
  JsonObject legacy=Json.obj("id",id,"title","Tên mới","genre",Json.str(read,"genre",""),"duration_minutes",110,"age_rating","P","description","");
  JsonObject updated=catalog.saveMovie(admin,legacy);assertEquals(saved.get("poster_url"),updated.get("poster_url"));assertEquals(saved.get("trailer_url"),updated.get("trailer_url"));
  assertThrows(IllegalArgumentException.class,()->catalog.saveMovie(user,movie()));
 }
 @Test void dateBoundsProtectExistingSchedulesAndClassifyRelease()throws Exception {
  JsonObject invalid=movie();invalid.addProperty("end_date","2026-09-01");assertThrows(IllegalArgumentException.class,()->catalog.saveMovie(admin,invalid));
  invalid.addProperty("end_date","2026-02-30");assertThrows(IllegalArgumentException.class,()->catalog.saveMovie(admin,invalid));
  JsonObject existing=find(catalog.movies(false),1);existing.addProperty("release_date","2026-10-01");assertThrows(IllegalArgumentException.class,()->catalog.saveMovie(admin,existing));
  JsonObject later=movie();later.addProperty("release_date","2026-10-01");JsonObject saved=catalog.saveMovie(admin,later);long id=Json.num(saved,"id",0);
  assertEquals("UPCOMING",Json.str(find(catalog.movies(false),id),"screening_status",""));
  long start=LocalDate.of(2026,9,29).atTime(10,0).atZone(CatalogService.ZONE).toInstant().toEpochMilli();
  assertThrows(IllegalArgumentException.class,()->service.handle(admin,"ADMIN_SAVE_SHOW",Json.obj("movie_id",id,"room_id",1,"starts_at",start,"price_vnd",75000)));
  assertEquals("NOW_SHOWING",Json.str(find(catalog.movies(false),1),"screening_status",""));
  assertEquals("UPCOMING",Json.str(find(catalog.movies(false),9),"screening_status",""));
  JsonObject ended=movie();ended.addProperty("release_date","2026-08-01");ended.addProperty("end_date","2026-09-21");long endedId=Json.num(catalog.saveMovie(admin,ended),"id",0);assertEquals("ENDED",Json.str(find(catalog.movies(false),endedId),"screening_status",""));
 }
 @Test void filtersCombineMovieAreaCinemaAndVietnamDate()throws Exception {
  JsonArray shows=catalog.shows(Json.obj("areaId",2,"cinemaId",2,"date","2026-09-23"));assertEquals(4,shows.size());
  for(JsonElement e:shows){JsonObject s=e.getAsJsonObject();assertEquals(2,Json.num(s,"cinema_id",0));assertEquals("Đà Nẵng",Json.str(s,"area_name",""));}
  assertTrue(catalog.shows(Json.obj("areaId",1,"cinemaId",2)).isEmpty());
  assertTrue(catalog.shows(Json.obj("date","2026-10-20")).isEmpty());
  assertThrows(IllegalArgumentException.class,()->catalog.shows(Json.obj("date","tomorrow")));
 }
 @Test void cinemasAndRoomsCannotOrphanExistingShows()throws Exception {
  assertThrows(IllegalArgumentException.class,()->catalog.archive(admin,Json.obj("id",1),"cinemas"));
  assertThrows(IllegalArgumentException.class,()->catalog.archive(admin,Json.obj("id",1),"areas"));
  assertThrows(IllegalArgumentException.class,()->service.handle(admin,"ADMIN_SAVE_ROOM",Json.obj("id",1,"name","P1","rows_count",6,"cols_count",8,"cinema_id",2)));
  long area=Json.num(catalog.saveLookup(admin,Json.obj("name","Khu vực mới"),"areas"),"id",0);
  long cinema=Json.num(catalog.saveCinema(admin,Json.obj("name","Rạp mới","area_id",area,"address","Đường A","phone","0900000000")),"id",0);
  JsonObject room=service.handle(admin,"ADMIN_SAVE_ROOM",Json.obj("name","Mới P1","rows_count",6,"cols_count",8,"cinema_id",cinema)).getAsJsonObject();assertEquals(cinema,Json.num(room,"cinema_id",0));
 }
 @Test void oldDatabaseUpgradesTwiceWithIdsAndBookingHistoryIntact()throws Exception {
  Path old=dir.resolve("old.db");
  try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+old);Statement s=c.createStatement()){
   String schema=new String(getClass().getResourceAsStream("/schema.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);for(String sql:schema.split(";"))if(!sql.isBlank())s.execute(sql);
   exec(c,"INSERT INTO users(id,username,password_hash,display_name,role,created_at) VALUES(44,'legacy','hash','Tên cũ','USER',1)");
   exec(c,"INSERT INTO movies(id,title,genre,duration_minutes,poster_url) VALUES(77,'Phim cũ','Hài, Gia đình',100,'https://example.org/old.jpg')");
   exec(c,"INSERT INTO rooms(id,name,rows_count,cols_count) VALUES(88,'Phòng cũ',6,8)");
   exec(c,"INSERT INTO showtimes(id,movie_id,room_id,starts_at,ends_at,price_vnd) VALUES(99,77,88,1,2,75000)");
   exec(c,"INSERT INTO bookings(id,user_id,show_id,request_id,total_vnd,status,payment_method,created_at) VALUES(66,44,99,'old-booking',75000,'CONFIRMED','DEMO',1)");
   exec(c,"INSERT INTO tickets(booking_id,show_id,seat_label,movie_title,room_name,starts_at,price_vnd) VALUES(66,99,'B1','Phim cũ','Phòng cũ',1,75000)");
   exec(c,"INSERT INTO seats_state(show_id,seat_label,status,booking_id) VALUES(99,'B1','SOLD',66)");
  }
  new Database(old);Database upgraded=new Database(old);
  upgraded.read(c->{assertEquals(2,rows(c,"SELECT * FROM movie_genres WHERE movie_id=77").size());assertEquals("SOLD",Json.str(one(c,"SELECT * FROM seats_state WHERE show_id=99"),"status",""));assertEquals(66,Json.num(one(c,"SELECT * FROM tickets"),"booking_id",0));assertEquals("https://example.org/old.jpg",Json.str(one(c,"SELECT * FROM movies WHERE id=77"),"poster_url",""));assertEquals(1,rows(c,"SELECT * FROM cinemas").size());return null;});
 }
 @Test void uploadedImageTravelsFromAdminSocketToAnotherClientAndPersists()throws Exception {
  var bitmap=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(bitmap,"png",out);String encoded=Base64.getEncoder().encodeToString(out.toByteArray());String url;
  try(TcpServer server=new TcpServer(db,clock,0)){server.start();try(var adminWire=new TcpServerTest.Wire(server.port());var customer=new TcpServerTest.Wire(server.port())){
   assertFalse(customer.request("GET_IMAGE",Json.obj("id","0".repeat(64))).success());
   adminWire.request("LOGIN",Json.obj("username","admin","password","Admin@123"));customer.request("LOGIN",Json.obj("username","user1","password","User@1234"));
   assertFalse(customer.request("ADMIN_UPLOAD_IMAGE",Json.obj("data",encoded)).success());
   Response upload=adminWire.request("ADMIN_UPLOAD_IMAGE",Json.obj("data",encoded));assertTrue(upload.success(),upload.toString());url=Json.str(upload.data().getAsJsonObject(),"url","");
   Response image=customer.request("GET_IMAGE",Json.obj("id",url.substring(6)));assertTrue(image.success());assertEquals(encoded,Json.str(image.data().getAsJsonObject(),"data",""));
   assertTrue(customer.request("GET_CINEMAS",Json.obj()).success());assertTrue(customer.request("GET_GENRES",Json.obj()).success());
   assertFalse(adminWire.request("ADMIN_UPLOAD_IMAGE",Json.obj("data","bm90LWFuLWltYWdl")).success());
  }}
  MediaService reopened=new MediaService(new Database(dir.resolve("cinema.db")),clock);assertEquals(encoded,Json.str(reopened.image(url.substring(6)),"data",""));
  assertThrows(IllegalArgumentException.class,()->MediaService.url("file:///C:/poster.jpg",true));assertThrows(IllegalArgumentException.class,()->MediaService.url("javascript:alert(1)",false));
 }
 static JsonObject find(JsonArray values,long id){for(JsonElement e:values)if(Json.num(e.getAsJsonObject(),"id",0)==id)return e.getAsJsonObject();throw new AssertionError("Missing "+id);}
}
