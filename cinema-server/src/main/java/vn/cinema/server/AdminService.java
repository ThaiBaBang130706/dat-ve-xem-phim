package vn.cinema.server;

import com.google.gson.*;
import java.sql.Connection;
import java.time.*;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

public final class AdminService {
 private final Database db;
 private final Clock clock;
 private final BookingService booking;
 public AdminService(Database db,Clock clock,BookingService booking){this.db=db;this.clock=clock;this.booking=booking;}
 public JsonElement handle(Session session,String type,JsonObject data)throws Exception {
  db.read(c->AuthService.check(c,session,true));
  return switch(type){
   case "ADMIN_GET_STATS" -> stats();
   case "ADMIN_LIST_MOVIES" -> db.read(c->rows(c,"SELECT * FROM movies ORDER BY id DESC"));
   case "ADMIN_LIST_ROOMS" -> db.read(c->rows(c,"SELECT * FROM rooms ORDER BY id"));
   case "ADMIN_LIST_SHOWS" -> shows();
   case "ADMIN_LIST_USERS" -> db.read(c->rows(c,"SELECT id,username,display_name,role,status,created_at FROM users ORDER BY id"));
   case "ADMIN_LIST_BOOKINGS" -> bookings();
   case "ADMIN_LIST_LOGS" -> logs();
   case "ADMIN_SAVE_MOVIE" -> saveMovie(session,data);
   case "ADMIN_DELETE_MOVIE" -> archive(session,data,"movies","movie_id");
   case "ADMIN_SAVE_ROOM" -> saveRoom(session,data);
   case "ADMIN_DELETE_ROOM" -> archive(session,data,"rooms","room_id");
   case "ADMIN_SAVE_SHOW" -> saveShow(session,data);
   case "ADMIN_DELETE_SHOW" -> cancelShow(session,data);
   case "ADMIN_SET_USER_STATUS" -> setUserStatus(session,data);
   case "ADMIN_SET_SEAT_STATUS" -> setSeatStatus(session,data);
   default -> throw new IllegalArgumentException("Lệnh quản trị không hợp lệ.");
  };
 }
 public JsonObject stats()throws Exception {
  return db.read(c->{
   JsonObject stats=one(c,"SELECT (SELECT COUNT(*) FROM users WHERE role='USER') AS users,(SELECT COUNT(*) FROM bookings WHERE status='CONFIRMED') AS bookings,(SELECT COUNT(*) FROM tickets WHERE status='ACTIVE') AS tickets,(SELECT COALESCE(SUM(total_vnd),0) FROM bookings WHERE status='CONFIRMED') AS revenue,(SELECT COUNT(*) FROM seats_state WHERE status='HELD') AS heldSeats");
   long from=LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).minusDays(6).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
   stats.add("daily",rows(c,"SELECT date(created_at/1000,'unixepoch','+7 hours') AS day,SUM(total_vnd) AS revenue,COUNT(*) AS bookings FROM bookings WHERE status='CONFIRMED' AND created_at>=? GROUP BY day ORDER BY day",from));
   stats.addProperty("serverTime",clock.millis());return stats;
  });
 }
 public JsonArray shows()throws Exception {
  return db.read(c->rows(c,"SELECT s.*,m.title,r.name AS room_name,r.rows_count,r.cols_count,(SELECT COUNT(*) FROM seats_state ss WHERE ss.show_id=s.id) AS capacity,(SELECT COUNT(*) FROM seats_state ss WHERE ss.show_id=s.id AND ss.status='SOLD') AS sold,(SELECT COUNT(*) FROM seats_state ss WHERE ss.show_id=s.id AND ss.status='HELD') AS held FROM showtimes s JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id ORDER BY s.starts_at DESC LIMIT 500"));
 }
 public JsonArray bookings()throws Exception {
  return db.read(c->rows(c,"SELECT b.*,u.username,m.title,r.name AS room_name,s.starts_at,(SELECT group_concat(t.seat_label, ', ') FROM tickets t WHERE t.booking_id=b.id) AS seats FROM bookings b JOIN users u ON u.id=b.user_id JOIN showtimes s ON s.id=b.show_id JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id ORDER BY b.id DESC LIMIT 500"));
 }
 public JsonArray logs()throws Exception {
  return db.read(c->rows(c,"SELECT l.id,l.created_at,COALESCE(u.username,'system') AS username,l.action,l.detail FROM logs l LEFT JOIN users u ON u.id=l.user_id ORDER BY l.id DESC LIMIT 200"));
 }
 public void checkAdmin(long userId)throws Exception {db.read(c->AuthService.check(c,new Session(userId,"http-dashboard"),true));}
 private JsonObject saveMovie(Session session,JsonObject d)throws Exception {
  long id=Json.num(d,"id",0),duration=number(d,"duration_minutes",30,300);
  String title=text(d,"title",1,100),genre=text(d,"genre",1,40),rating=text(d,"age_rating",1,6),description=text(d,"description",0,2000);
  require(java.util.Set.of("P","K","T13","T16","T18").contains(rating),"Phân loại tuổi: P, K, T13, T16 hoặc T18.");
  return db.write(c->{
   AuthService.check(c,session,true);
   long saved=id;
   if(id==0)saved=insert(c,"INSERT INTO movies(title,genre,duration_minutes,age_rating,description) VALUES(?,?,?,?,?)",title,genre,duration,rating,description);
   else{
    JsonObject old=one(c,"SELECT * FROM movies WHERE id=?",id);require(old!=null,"Phim không tồn tại.");
    if(Json.num(old,"duration_minutes",0)!=duration)require(one(c,"SELECT id FROM showtimes WHERE movie_id=? AND status='OPEN' AND starts_at>?",id,clock.millis())==null,"Phim đã có suất chiếu sắp tới. Giữ nguyên thời lượng hoặc huỷ suất trước.");
    exec(c,"UPDATE movies SET title=?,genre=?,duration_minutes=?,age_rating=?,description=?,active=1 WHERE id=?",title,genre,duration,rating,description,id);
   }
   db.log(c,session.userId(),"SAVE_MOVIE","Phim "+saved+" / "+title,clock.millis());
   return one(c,"SELECT * FROM movies WHERE id=?",saved);
  });
 }
 private JsonObject saveRoom(Session session,JsonObject d)throws Exception {
  long id=Json.num(d,"id",0),rowCount=number(d,"rows_count",1,12),colCount=number(d,"cols_count",1,16);
  String name=text(d,"name",1,30);
  return db.write(c->{
   AuthService.check(c,session,true);
   require(one(c,"SELECT id FROM rooms WHERE name=? AND id<>?",name,id)==null,"Tên phòng đã tồn tại.");
   long saved=id;
   if(id==0)saved=insert(c,"INSERT INTO rooms(name,rows_count,cols_count) VALUES(?,?,?)",name,rowCount,colCount);
   else {
    JsonObject old=one(c,"SELECT * FROM rooms WHERE id=?",id);require(old!=null,"Phòng không tồn tại.");
    if(Json.num(old,"rows_count",0)!=rowCount || Json.num(old,"cols_count",0)!=colCount)require(one(c,"SELECT id FROM showtimes WHERE room_id=?",id)==null,"Phòng đã có lịch chiếu. Tạo phòng mới nếu cần đổi sơ đồ ghế.");
    exec(c,"UPDATE rooms SET name=?,rows_count=?,cols_count=?,active=1 WHERE id=?",name,rowCount,colCount,id);
   }
   db.log(c,session.userId(),"SAVE_ROOM","Phòng "+saved+" / "+name,clock.millis());
   return one(c,"SELECT * FROM rooms WHERE id=?",saved);
  });
 }
 private JsonObject archive(Session session,JsonObject d,String table,String column)throws Exception {
  long id=number(d,"id",1,Long.MAX_VALUE);
  return db.write(c->{
   AuthService.check(c,session,true);
   require(one(c,"SELECT id FROM "+table+" WHERE id=?",id)!=null,"Không tìm thấy dữ liệu.");
   require(one(c,"SELECT id FROM showtimes WHERE "+column+"=? AND status='OPEN' AND starts_at>?",id,clock.millis())==null,"Còn suất chiếu sắp tới. Huỷ các suất đó trước khi ngừng sử dụng.");
   exec(c,"UPDATE "+table+" SET active=0 WHERE id=?",id);
   db.log(c,session.userId(),"ARCHIVE_"+table.toUpperCase(),"Mã "+id,clock.millis());
   return Json.obj("id",id);
  });
 }
 private JsonObject saveShow(Session session,JsonObject d)throws Exception {
  long id=Json.num(d,"id",0),movie=number(d,"movie_id",1,Long.MAX_VALUE),room=number(d,"room_id",1,Long.MAX_VALUE);
  long start=number(d,"starts_at",clock.millis()+60_000,clock.millis()+366L*86_400_000),price=number(d,"price_vnd",1000,10_000_000);
  JsonObject result=db.write(c->{
   AuthService.check(c,session,true);
   JsonObject m=one(c,"SELECT * FROM movies WHERE id=? AND active=1",movie),r=one(c,"SELECT * FROM rooms WHERE id=? AND active=1",room);
   require(m!=null && r!=null,"Phim hoặc phòng không còn hoạt động.");
   long end=start+Json.num(m,"duration_minutes",0)*60_000;
   require(one(c,"SELECT id FROM showtimes WHERE room_id=? AND id<>? AND status='OPEN' AND starts_at<? AND ends_at>?",room,id,end+900_000,start-900_000)==null,"Lịch chiếu bị trùng; cần 15 phút dọn phòng giữa hai suất.");
   long saved=id;
   if(id>0){
    require(one(c,"SELECT id FROM showtimes WHERE id=?",id)!=null,"Suất chiếu không tồn tại.");
    require(one(c,"SELECT seat_label FROM seats_state WHERE show_id=? AND status IN('SOLD','HELD') LIMIT 1",id)==null,"Suất chiếu có ghế đã bán hoặc đang giữ, chưa thể sửa.");
    exec(c,"DELETE FROM seats_state WHERE show_id=?",id);
    exec(c,"UPDATE showtimes SET movie_id=?,room_id=?,starts_at=?,ends_at=?,price_vnd=?,status='OPEN' WHERE id=?",movie,room,start,end,price,id);
   }else saved=insert(c,"INSERT INTO showtimes(movie_id,room_id,starts_at,ends_at,price_vnd) VALUES(?,?,?,?,?)",movie,room,start,end,price);
   Database.createSeats(c,saved,(int)Json.num(r,"rows_count",0),(int)Json.num(r,"cols_count",0));
   db.log(c,session.userId(),"SAVE_SHOW","Suất "+saved,clock.millis());
   return one(c,"SELECT * FROM showtimes WHERE id=?",saved);
  });
  booking.publish(result.get("id").getAsLong());return result;
 }
 private JsonObject cancelShow(Session session,JsonObject d)throws Exception {
  long id=number(d,"id",1,Long.MAX_VALUE);
  db.write(c->{
   AuthService.check(c,session,true);
   require(one(c,"SELECT id FROM showtimes WHERE id=?",id)!=null,"Suất chiếu không tồn tại.");
   require(one(c,"SELECT id FROM bookings WHERE show_id=? AND status='CONFIRMED' LIMIT 1",id)==null,"Suất đã có vé bán. Huỷ vé trước khi huỷ suất.");
   exec(c,"UPDATE seats_state SET status='AVAILABLE',held_by=NULL,hold_session=NULL,hold_until=NULL WHERE show_id=? AND status='HELD'",id);
   exec(c,"UPDATE showtimes SET status='CANCELLED' WHERE id=?",id);
   db.log(c,session.userId(),"CANCEL_SHOW","Suất "+id,clock.millis());return null;
  });
  booking.publish(id);return Json.obj("id",id);
 }
 private JsonObject setUserStatus(Session session,JsonObject d)throws Exception {
  long id=number(d,"id",1,Long.MAX_VALUE);String status=text(d,"status",6,6);
  require(status.equals("ACTIVE") || status.equals("LOCKED"),"Trạng thái không hợp lệ.");
  JsonObject result=db.write(c->{
   AuthService.check(c,session,true);require(id!=session.userId(),"Không được tự khoá tài khoản đang dùng.");
   JsonObject user=one(c,"SELECT id,role FROM users WHERE id=?",id);require(user!=null,"Tài khoản không tồn tại.");
   if(status.equals("LOCKED") && "ADMIN".equals(Json.str(user,"role","")))require(one(c,"SELECT id FROM users WHERE role='ADMIN' AND status='ACTIVE' AND id<>?",id)!=null,"Cần giữ ít nhất một quản trị viên.");
   exec(c,"UPDATE users SET status=? WHERE id=?",status,id);
   db.log(c,session.userId(),"SET_USER_STATUS","Tài khoản "+id+" / "+status,clock.millis());
   return Json.obj("id",id,"status",status);
  });
  if(status.equals("LOCKED"))booking.releaseUser(id);
  return result;
 }
 private JsonObject setSeatStatus(Session session,JsonObject d)throws Exception {
  long show=number(d,"showId",1,Long.MAX_VALUE);String seat=text(d,"seat",2,3),status=text(d,"status",9,11);
  require(status.equals("AVAILABLE") || status.equals("UNAVAILABLE"),"Chỉ được mở hoặc khoá ghế trống.");
  db.write(c->{
   AuthService.check(c,session,true);
   require(exec(c,"UPDATE seats_state SET status=? WHERE show_id=? AND seat_label=? AND status IN('AVAILABLE','UNAVAILABLE')",status,show,seat)==1,"Ghế không tồn tại hoặc đang được giữ/đã bán.");
   db.log(c,session.userId(),"SET_SEAT_STATUS",show+"/"+seat+"/"+status,clock.millis());return null;
  });
  booking.publish(show);return Json.obj();
 }
}
