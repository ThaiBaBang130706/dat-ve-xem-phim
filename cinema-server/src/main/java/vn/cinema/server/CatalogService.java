package vn.cinema.server;

import com.google.gson.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

/** Shared catalogue rules for customer discovery and administrator edits. */
public final class CatalogService {
 public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
 private final Database db;private final Clock clock;
 public CatalogService(Database db,Clock clock){this.db=db;this.clock=clock;}
 public JsonArray movies(boolean all)throws Exception {
  return db.read(c->{
   JsonArray movies=rows(c,"SELECT * FROM movies"+(all?"":" WHERE active=1")+" ORDER BY id");
   for(JsonElement entry:movies){JsonObject movie=entry.getAsJsonObject();long id=Json.num(movie,"id",0);
    movie.add("genres",rows(c,"SELECT g.* FROM genres g JOIN movie_genres mg ON mg.genre_id=g.id WHERE mg.movie_id=? ORDER BY g.name",id));
    JsonObject next=one(c,"SELECT MIN(s.starts_at) AS first_show FROM showtimes s JOIN rooms r ON r.id=s.room_id JOIN cinemas ci ON ci.id=r.cinema_id JOIN areas a ON a.id=ci.area_id WHERE s.movie_id=? AND s.status='OPEN' AND s.ends_at>? AND r.active=1 AND ci.active=1 AND a.active=1 AND (?='' OR date(s.starts_at/1000,'unixepoch','+7 hours')>=?) AND (?='' OR date(s.starts_at/1000,'unixepoch','+7 hours')<=?)",id,clock.millis(),Json.str(movie,"release_date",""),Json.str(movie,"release_date",""),Json.str(movie,"end_date",""),Json.str(movie,"end_date",""));
    Long first=next.get("first_show").isJsonNull()?null:next.get("first_show").getAsLong();
    String status=status(movie,first,LocalDate.now(clock.withZone(ZONE)));
    String release=Json.str(movie,"release_date","");
    if(!release.isEmpty()){long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(release),LocalDate.now(clock.withZone(ZONE)));movie.addProperty("release_label",days<0?"Khởi chiếu sau "+(-days)+" ngày":days==0?"Khởi chiếu hôm nay":"Đã ra mắt "+days+" ngày");}
    movie.addProperty("screening_status",status);movie.addProperty("screening_label",switch(status){case "NOW_SHOWING"->"Đang chiếu";case "UPCOMING"->"Sắp chiếu";case "ENDED"->"Đã ngừng chiếu";case "INACTIVE"->"Ngừng sử dụng";default->"Chưa có lịch chiếu";});
   }return movies;
  });
 }
 static String status(JsonObject movie,Long first,LocalDate today){
  if(Json.num(movie,"active",1)==0)return "INACTIVE";
  String release=Json.str(movie,"release_date",""),end=Json.str(movie,"end_date","");
  if(!end.isEmpty() && LocalDate.parse(end).isBefore(today))return "ENDED";
  if(!release.isEmpty() && LocalDate.parse(release).isAfter(today))return "UPCOMING";
  if(first==null)return "UNSCHEDULED";
  if(release.isEmpty() && Instant.ofEpochMilli(first).atZone(ZONE).toLocalDate().isAfter(today))return "UPCOMING";
  return "NOW_SHOWING";
 }
 public JsonArray areas(boolean all)throws Exception{return db.read(c->rows(c,"SELECT * FROM areas"+(all?"":" WHERE active=1")+" ORDER BY name"));}
 public JsonArray genres(boolean all)throws Exception{return db.read(c->rows(c,"SELECT * FROM genres"+(all?"":" WHERE active=1")+" ORDER BY name"));}
 public JsonArray cinemas(boolean all,long area)throws Exception {
  return db.read(c->rows(c,"SELECT ci.*,a.name AS area_name FROM cinemas ci JOIN areas a ON a.id=ci.area_id WHERE (?=0 OR ci.area_id=?)"+(all?"":" AND ci.active=1 AND a.active=1")+" ORDER BY a.name,ci.name",area,area));
 }
 public JsonArray rooms()throws Exception{return db.read(c->rows(c,"SELECT r.*,ci.name AS cinema_name,ci.name || ' / ' || r.name AS room_label,ci.area_id,a.name AS area_name FROM rooms r JOIN cinemas ci ON ci.id=r.cinema_id JOIN areas a ON a.id=ci.area_id ORDER BY ci.name,r.name"));}
 public JsonArray shows(JsonObject filter)throws Exception {
  long movie=Json.num(filter,"movieId",0),cinema=Json.num(filter,"cinemaId",0),area=Json.num(filter,"areaId",0);
  String day=date(Json.str(filter,"date",""));
  return db.read(c->rows(c,"SELECT s.*,m.title,m.genre,m.age_rating,m.duration_minutes,r.name AS room_name,r.rows_count,r.cols_count,ci.id AS cinema_id,ci.name AS cinema_name,ci.address,ci.phone,ci.opens_at,ci.closes_at,ci.image_url AS cinema_image_url,ci.area_id,a.name AS area_name FROM showtimes s JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id JOIN cinemas ci ON ci.id=r.cinema_id JOIN areas a ON a.id=ci.area_id WHERE s.status='OPEN' AND m.active=1 AND r.active=1 AND ci.active=1 AND a.active=1 AND s.starts_at>? AND (?=0 OR s.movie_id=?) AND (?=0 OR ci.id=?) AND (?=0 OR a.id=?) AND (?='' OR date(s.starts_at/1000,'unixepoch','+7 hours')=?) AND (m.release_date='' OR date(s.starts_at/1000,'unixepoch','+7 hours')>=m.release_date) AND (m.end_date='' OR date(s.starts_at/1000,'unixepoch','+7 hours')<=m.end_date) ORDER BY s.starts_at,ci.name,s.id",clock.millis(),movie,movie,cinema,cinema,area,area,day,day));
 }
 static String date(String input){
  String value=input.strip();if(value.isEmpty())return value;
  try{require(value.matches("\\d{4}-\\d{2}-\\d{2}"),"Ngày phải có dạng yyyy-MM-dd.");return LocalDate.parse(value).toString();}
  catch(java.time.DateTimeException e){throw new IllegalArgumentException("Ngày không hợp lệ; dùng yyyy-MM-dd.");}
 }
 static void checkShowDate(JsonObject movie,long start){
  String day=Instant.ofEpochMilli(start).atZone(ZONE).toLocalDate().toString();
  String release=Json.str(movie,"release_date",""),end=Json.str(movie,"end_date","");
  require((release.isEmpty() || day.compareTo(release)>=0) && (end.isEmpty() || day.compareTo(end)<=0),"Suất chiếu phải nằm trong khoảng ngày khởi chiếu và ngày ngừng chiếu.");
 }
 static void checkHours(JsonObject cinema,long start,long end){
  ZonedDateTime s=Instant.ofEpochMilli(start).atZone(ZONE),e=Instant.ofEpochMilli(end).atZone(ZONE);
  LocalTime open=LocalTime.parse(Json.str(cinema,"opens_at","08:00")),close=LocalTime.parse(Json.str(cinema,"closes_at","23:59"));
  if(open.equals(close))return; // explicitly configured 24-hour operation
  LocalDate businessDay=s.toLocalDate();if(close.isBefore(open)&&s.toLocalTime().isBefore(close))businessDay=businessDay.minusDays(1);
  ZonedDateTime from=businessDay.atTime(open).atZone(ZONE),to=businessDay.plusDays(close.isBefore(open)?1:0).atTime(close).atZone(ZONE);
  require(!s.isBefore(from)&&!e.isAfter(to),"Suất chiếu nằm ngoài giờ hoạt động của rạp.");
 }
 public JsonObject saveMovie(Session session,JsonObject d)throws Exception {
  long id=Json.num(d,"id",0),duration=number(d,"duration_minutes",30,300);
  String title=text(d,"title",1,100),rating=text(d,"age_rating",1,6),description=text(d,"description",0,2000);
  require(Set.of("P","K","T13","T16","T18").contains(rating),"Phân loại tuổi: P, K, T13, T16 hoặc T18.");
  return db.write(c->{
   AuthService.check(c,session,true);
   JsonObject old=id==0?Json.obj():one(c,"SELECT * FROM movies WHERE id=?",id);require(old!=null,"Phim không tồn tại.");
   Map<String,String> values=new LinkedHashMap<>();
   for(String key:List.of("poster_url","banner_url","trailer_url","release_date","end_date","director","cast_names","country","language","presentation")){
    String value=Json.str(d,key,Json.str(old,key,"")).strip();require(value.length()<=2000,"Thông tin phim quá dài.");
    if(key.endsWith("_url"))value=MediaService.url(value,!key.equals("trailer_url"));
    if(key.endsWith("_date"))value=date(value);values.put(key,value);
   }
   String release=values.get("release_date"),end=values.get("end_date");
   require(release.isEmpty() || end.isEmpty() || end.compareTo(release)>=0,"Ngày ngừng chiếu phải từ ngày khởi chiếu trở đi.");
   if(id>0){
    if(Json.num(old,"duration_minutes",0)!=duration)require(one(c,"SELECT id FROM showtimes WHERE movie_id=? AND status='OPEN' AND ends_at>?",id,clock.millis())==null,"Phim đã có suất chiếu. Giữ thời lượng hoặc huỷ suất trước.");
    JsonObject proposed=Json.obj("release_date",release,"end_date",end);
    for(JsonElement e:rows(c,"SELECT starts_at FROM showtimes WHERE movie_id=? AND status='OPEN' AND ends_at>?",id,clock.millis()))checkShowDate(proposed,Json.num(e.getAsJsonObject(),"starts_at",0));
   }
   LinkedHashSet<Long> genreIds=new LinkedHashSet<>();
   if(d.has("genre_ids")){
    require(d.get("genre_ids").isJsonArray(),"Chọn ít nhất một thể loại.");
    for(JsonElement e:d.getAsJsonArray("genre_ids")){long g=e.getAsLong();require(one(c,"SELECT id FROM genres WHERE id=? AND active=1",g)!=null,"Thể loại không còn hoạt động.");genreIds.add(g);}
   }else{
    // Older clients still submit the legacy genre label.
    for(String part:Json.str(d,"genre",Json.str(old,"genre","")).split("[,;/|]")){
     String name=part.strip();if(name.isEmpty())continue;require(name.length()<=60,"Tên thể loại tối đa 60 ký tự.");
     exec(c,"INSERT OR IGNORE INTO genres(name) VALUES(?)",name);
     JsonObject genre=one(c,"SELECT * FROM genres WHERE name=?",name);require(Json.num(genre,"active",0)==1,"Thể loại đã ngừng sử dụng.");genreIds.add(Json.num(genre,"id",0));
    }
   }
   require(!genreIds.isEmpty() && genreIds.size()<=10,"Mỗi phim cần từ 1 đến 10 thể loại.");
   long saved=id;
   if(id==0)saved=insert(c,"INSERT INTO movies(title,genre,duration_minutes,age_rating,description) VALUES(?,'',?,?,?)",title,duration,rating,description);
   else exec(c,"UPDATE movies SET title=?,duration_minutes=?,age_rating=?,description=?,active=1 WHERE id=?",title,duration,rating,description,id);
   for(var v:values.entrySet())exec(c,"UPDATE movies SET "+v.getKey()+"=? WHERE id=?",v.getValue(),saved);
   exec(c,"DELETE FROM movie_genres WHERE movie_id=?",saved);
   for(long g:genreIds)exec(c,"INSERT INTO movie_genres(movie_id,genre_id) VALUES(?,?)",saved,g);
   refreshGenreLabels(c);
   db.log(c,session.userId(),"SAVE_MOVIE","Phim "+saved+" / "+title,clock.millis());return one(c,"SELECT * FROM movies WHERE id=?",saved);
  });
 }
 private static void refreshGenreLabels(Connection c)throws Exception {
  exec(c,"UPDATE movies SET genre=COALESCE((SELECT group_concat(name, ', ') FROM (SELECT g.name FROM genres g JOIN movie_genres mg ON mg.genre_id=g.id WHERE mg.movie_id=movies.id ORDER BY g.name)),'')");
 }
 public JsonObject saveLookup(Session session,JsonObject d,String table)throws Exception {
  require(Set.of("areas","genres").contains(table),"Danh mục không hợp lệ.");
  long id=Json.num(d,"id",0);String name=text(d,"name",1,60);
  return db.write(c->{AuthService.check(c,session,true);
   require(one(c,"SELECT id FROM "+table+" WHERE name=? AND id<>?",name,id)==null,"Tên đã tồn tại.");long saved=id;
   if(id==0)saved=insert(c,"INSERT INTO "+table+"(name) VALUES(?)",name);
   else require(exec(c,"UPDATE "+table+" SET name=?,active=1 WHERE id=?",name,id)==1,"Không tìm thấy danh mục.");
   if(table.equals("genres"))refreshGenreLabels(c);
   db.log(c,session.userId(),"SAVE_"+table.toUpperCase(),name,clock.millis());return one(c,"SELECT * FROM "+table+" WHERE id=?",saved);
  });
 }
 public JsonObject saveCinema(Session session,JsonObject d)throws Exception {
  long id=Json.num(d,"id",0),area=number(d,"area_id",1,Long.MAX_VALUE);String name=text(d,"name",1,100),address=text(d,"address",1,300),phone=text(d,"phone",0,30),image=MediaService.url(Json.str(d,"image_url",""),true);
  return db.write(c->{AuthService.check(c,session,true);
   require(one(c,"SELECT id FROM areas WHERE id=? AND active=1",area)!=null,"Chọn khu vực đang hoạt động.");
   require(one(c,"SELECT id FROM cinemas WHERE name=? AND area_id=? AND id<>?",name,area,id)==null,"Tên rạp đã tồn tại trong khu vực.");
   long saved=id;
   if(id==0)saved=insert(c,"INSERT INTO cinemas(name,address,phone,image_url,area_id) VALUES(?,?,?,?,?)",name,address,phone,image,area);
   else {
    JsonObject old=one(c,"SELECT * FROM cinemas WHERE id=?",id);require(old!=null,"Không tìm thấy rạp.");
    if(Json.num(old,"area_id",0)!=area || !Json.str(old,"address","").equals(address))require(one(c,"SELECT s.id FROM showtimes s JOIN rooms r ON r.id=s.room_id WHERE r.cinema_id=? AND s.status='OPEN' AND s.ends_at>?",id,clock.millis())==null,"Rạp còn lịch chiếu; huỷ lịch trước khi chuyển địa điểm.");
    exec(c,"UPDATE cinemas SET name=?,address=?,phone=?,image_url=?,area_id=?,active=1 WHERE id=?",name,address,phone,image,area,id);
   }
   JsonObject current=one(c,"SELECT * FROM cinemas WHERE id=?",saved);
   String opens=Json.str(d,"opens_at",Json.str(current,"opens_at","08:00")),closes=Json.str(d,"closes_at",Json.str(current,"closes_at","23:59"));
   require(opens.matches("[0-2][0-9]:[0-5][0-9]")&&closes.matches("[0-2][0-9]:[0-5][0-9]"),"Giờ hoạt động dạng HH:mm.");
   try{LocalTime.parse(opens);LocalTime.parse(closes);}catch(Exception e){throw new IllegalArgumentException("Giờ hoạt động không hợp lệ.");}
   JsonObject hours=Json.obj("opens_at",opens,"closes_at",closes);
   for(JsonElement e:rows(c,"SELECT s.starts_at,s.ends_at FROM showtimes s JOIN rooms r ON r.id=s.room_id WHERE r.cinema_id=? AND s.status='OPEN' AND s.ends_at>?",saved,clock.millis())){JsonObject t=e.getAsJsonObject();checkHours(hours,Json.num(t,"starts_at",0),Json.num(t,"ends_at",0));}
   exec(c,"UPDATE cinemas SET opens_at=?,closes_at=? WHERE id=?",opens,closes,saved);
   db.log(c,session.userId(),"SAVE_CINEMA",name,clock.millis());return one(c,"SELECT * FROM cinemas WHERE id=?",saved);
  });
 }
 public JsonObject archive(Session session,JsonObject d,String table)throws Exception {
  require(Set.of("areas","genres","cinemas").contains(table),"Danh mục không hợp lệ.");long id=number(d,"id",1,Long.MAX_VALUE);
  return db.write(c->{AuthService.check(c,session,true);
   String dependency=switch(table){case "areas"->"SELECT id FROM cinemas WHERE area_id=? AND active=1";case "genres"->"SELECT m.id FROM movies m JOIN movie_genres mg ON mg.movie_id=m.id WHERE mg.genre_id=? AND m.active=1";default->"SELECT id FROM rooms WHERE cinema_id=? AND active=1";};
   require(one(c,dependency,id)==null,"Mục này còn dữ liệu đang sử dụng. Chuyển hoặc ngừng các mục liên quan trước.");
   require(exec(c,"UPDATE "+table+" SET active=0 WHERE id=?",id)==1,"Không tìm thấy dữ liệu.");db.log(c,session.userId(),"ARCHIVE_"+table.toUpperCase(),"Mã "+id,clock.millis());return Json.obj("id",id);
  });
 }
}

