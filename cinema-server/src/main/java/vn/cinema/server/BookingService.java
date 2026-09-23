package vn.cinema.server;

import com.google.gson.*;
import java.sql.Connection;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

/** Every seat mutation is transactional; each show also has a business lock. */
public final class BookingService {
 public static final long HOLD_MILLIS=300_000;
 public interface Events {
  void seatsChanged(long showId);
  void holdExpired(String connectionId,long showId);
 }
 private final Database db;
 private final Clock clock;
 private final ConcurrentHashMap<Long,ReentrantLock> locks=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<Long,Long> revisions=new ConcurrentHashMap<>();
 private volatile Events events=new Events(){public void seatsChanged(long id){} public void holdExpired(String session,long id){}};
 public BookingService(Database db,Clock clock){this.db=db;this.clock=clock;}
 public void setEvents(Events events){this.events=Objects.requireNonNull(events);}
 private ReentrantLock lock(long show){return locks.computeIfAbsent(show,id->new ReentrantLock());}
 private void changed(long show){events.seatsChanged(show);}
 private void bump(long show){revisions.merge(show,1L,Long::sum);}
 public void publish(long show){ReentrantLock l=lock(show);l.lock();try{bump(show);}finally{l.unlock();}changed(show);}

 public JsonArray movies()throws Exception {return new CatalogService(db,clock).movies(false);}
 public JsonArray shows(long movie)throws Exception {return new CatalogService(db,clock).shows(Json.obj("movieId",movie));}
 public JsonObject seatMap(Session session,long show)throws Exception {expire(show);return snapshot(session,show);}
 public JsonObject snapshot(Session session,long show)throws Exception {
  ReentrantLock l=lock(show);l.lock();
  try{return db.read(c->{
   JsonObject info=one(c,"SELECT s.*,m.title,ci.name AS cinema_name,ci.address,a.name AS area_name,r.name AS room_name,r.rows_count,r.cols_count FROM showtimes s JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id JOIN cinemas ci ON ci.id=r.cinema_id JOIN areas a ON a.id=ci.area_id WHERE s.id=?",show);
   require(info!=null,"Không tìm thấy suất chiếu.");
   JsonArray seats=rows(c,"SELECT seat_label,status,hold_until,CASE WHEN hold_session=? THEN 1 ELSE 0 END AS is_mine FROM seats_state WHERE show_id=? ORDER BY substr(seat_label,1,1),CAST(substr(seat_label,2) AS INTEGER)",session==null?"":session.connectionId(),show);
   return Json.obj("show",info,"seats",seats,"serverTime",clock.millis(),"revision",revisions.getOrDefault(show,0L));
  });}finally{l.unlock();}
 }
 public JsonObject hold(Session session,long show,List<String> requested)throws Exception {
  List<String> seats=validSeats(requested);
  expire(show);
  ReentrantLock l=lock(show);l.lock();
  try {
   db.write(c->{
    AuthService.check(c,session,false);openShow(c,show);
    for(String seat:seats){
     JsonObject state=one(c,"SELECT * FROM seats_state WHERE show_id=? AND seat_label=?",show,seat);
     require(state!=null,"Ghế "+seat+" không tồn tại.");
     String status=Json.str(state,"status","");
     require(status.equals("AVAILABLE") || status.equals("HELD") && session.connectionId().equals(Json.str(state,"hold_session","")),"Ghế "+seat+" vừa được người khác chọn. Vui lòng chọn ghế khác.");
    }
    releaseIn(c,"hold_session=? AND show_id=?",session.connectionId(),show);
    long until=clock.millis()+HOLD_MILLIS;
    for(String seat:seats)exec(c,"UPDATE seats_state SET status='HELD',held_by=?,hold_session=?,hold_until=? WHERE show_id=? AND seat_label=?",session.userId(),session.connectionId(),until,show,seat);
    db.log(c,session.userId(),"HOLD_SEATS","Suất "+show+" / "+String.join(", ",seats),clock.millis());
    return null;
   });
   bump(show);
  }finally{l.unlock();}
  changed(show);
  return snapshot(session,show);
 }
 public JsonObject release(Session session,long show)throws Exception {
  ReentrantLock l=lock(show);l.lock();
  try {db.write(c->{AuthService.check(c,session,false);releaseIn(c,"hold_session=? AND show_id=?",session.connectionId(),show);return null;});bump(show);}
  finally{l.unlock();}
  changed(show);return snapshot(session,show);
 }
 public JsonObject confirm(Session session,long show,List<String> requested,String requestId)throws Exception {
  List<String> seats=validSeats(requested);
  require(requestId!=null && requestId.length()>=8 && requestId.length()<=64,"Mã yêu cầu thanh toán không hợp lệ.");
  expire(show);
  JsonObject result;
  ReentrantLock l=lock(show);l.lock();
  try {
   result=db.write(c->{
    AuthService.check(c,session,false);
    JsonObject previous=one(c,"SELECT * FROM bookings WHERE user_id=? AND request_id=?",session.userId(),requestId);
    if(previous!=null){
     require(previous.get("show_id").getAsLong()==show,"Mã thanh toán đã dùng cho suất chiếu khác.");
     JsonArray oldSeats=rows(c,"SELECT seat_label FROM tickets WHERE booking_id=?",previous.get("id").getAsLong());
     Set<String> expected=new HashSet<>();oldSeats.forEach(e->expected.add(e.getAsJsonObject().get("seat_label").getAsString()));
     require(expected.equals(new HashSet<>(seats)),"Mã thanh toán đã dùng cho lựa chọn ghế khác.");
     return booking(c,previous.get("id").getAsLong());
    }
    JsonObject info=openShow(c,show);
    long now=clock.millis();
    for(String seat:seats){
     JsonObject state=one(c,"SELECT * FROM seats_state WHERE show_id=? AND seat_label=?",show,seat);
     require(state!=null && "HELD".equals(Json.str(state,"status","")) && session.connectionId().equals(Json.str(state,"hold_session","")) && Json.num(state,"hold_until",0)>now,"Ghế "+seat+" không còn được giữ bởi phiên của bạn.");
    }
    long price=info.get("price_vnd").getAsLong(), total=price*seats.size();
    long id=insert(c,"INSERT INTO bookings(user_id,show_id,request_id,total_vnd,status,payment_method,created_at) VALUES(?,?,?,?,'CONFIRMED','DEMO',?)",session.userId(),show,requestId,total,now);
    exec(c,"UPDATE bookings SET code=? WHERE id=?",Database.code(id,now),id);
    for(String seat:seats){
     exec(c,"UPDATE seats_state SET status='SOLD',held_by=NULL,hold_session=NULL,hold_until=NULL,booking_id=? WHERE show_id=? AND seat_label=?",id,show,seat);
     exec(c,"INSERT INTO tickets(booking_id,show_id,seat_label,movie_title,room_name,starts_at,price_vnd) VALUES(?,?,?,?,?,?,?)",id,show,seat,info.get("title").getAsString(),info.get("cinema_name").getAsString()+" / "+info.get("room_name").getAsString(),info.get("starts_at").getAsLong(),price);
    }
    releaseIn(c,"hold_session=? AND show_id=?",session.connectionId(),show);
    db.log(c,session.userId(),"CONFIRM_BOOKING","Vé "+Database.code(id,now)+" / "+total+" VND (demo)",now);
    return booking(c,id);
   });
   bump(show);
  }finally{l.unlock();}
  changed(show);return result;
 }
 public JsonArray myBookings(Session session)throws Exception {
  return db.read(c->{AuthService.check(c,session,false);return rows(c,"SELECT b.*,m.title,r.name AS room_name,s.starts_at,(SELECT group_concat(t.seat_label, ', ') FROM tickets t WHERE t.booking_id=b.id) AS seats FROM bookings b JOIN showtimes s ON s.id=b.show_id JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id WHERE b.user_id=? ORDER BY b.id DESC LIMIT 500",session.userId());});
 }
 public JsonObject ticket(Session session,long id)throws Exception {
  return db.read(c->{
   JsonObject user=AuthService.check(c,session,false), result=booking(c,id);
   require(result.get("user_id").getAsLong()==session.userId() || "ADMIN".equals(Json.str(user,"role","")),"Bạn không có quyền xem vé này.");
   return result;
  });
 }
 public JsonObject cancel(Session session,long id)throws Exception {
  long show=db.read(c->{JsonObject b=one(c,"SELECT show_id FROM bookings WHERE id=?",id);require(b!=null,"Không tìm thấy vé.");return b.get("show_id").getAsLong();});
  ReentrantLock l=lock(show);l.lock();
  JsonObject result;
  try{
   result=db.write(c->{
    JsonObject user=AuthService.check(c,session,false),b=booking(c,id);
    require(b.get("user_id").getAsLong()==session.userId() || "ADMIN".equals(Json.str(user,"role","")),"Bạn không có quyền huỷ vé này.");
    if("CANCELLED".equals(Json.str(b,"status","")))return b;
    require(Json.num(b,"starts_at",0)>clock.millis(),"Suất chiếu đã bắt đầu, không thể huỷ vé.");
    exec(c,"UPDATE tickets SET status='CANCELLED' WHERE booking_id=?",id);
    exec(c,"UPDATE bookings SET status='CANCELLED' WHERE id=?",id);
    exec(c,"UPDATE seats_state SET status='AVAILABLE',booking_id=NULL WHERE booking_id=?",id);
    db.log(c,session.userId(),"CANCEL_BOOKING","Huỷ vé "+Json.str(b,"code","")+" (demo)",clock.millis());
    return booking(c,id);
   });bump(show);
  }finally{l.unlock();}
  changed(show);return result;
 }
 public void disconnect(Session session)throws Exception {
  if(session==null)return;
  releaseMatching("hold_session=?",session.connectionId());
 }
 public void releaseUser(long user)throws Exception{releaseMatching("held_by=?",user);}
 private void releaseMatching(String clause,Object value)throws Exception {
  JsonArray shows=db.read(c->rows(c,"SELECT DISTINCT show_id FROM seats_state WHERE status='HELD' AND "+clause,value));
  for(JsonElement e:shows){
   long show=e.getAsJsonObject().get("show_id").getAsLong();
   ReentrantLock l=lock(show);l.lock();
   try{db.write(c->{releaseIn(c,clause+" AND show_id=?",value,show);return null;});bump(show);}finally{l.unlock();}
   changed(show);
  }
 }
 public void expireAll()throws Exception {
  JsonArray shows=db.read(c->rows(c,"SELECT DISTINCT show_id FROM seats_state WHERE status='HELD' AND hold_until<=?",clock.millis()));
  for(JsonElement e:shows)expire(e.getAsJsonObject().get("show_id").getAsLong());
 }
 private void expire(long show)throws Exception {
  ReentrantLock l=lock(show);l.lock();JsonArray owners;
  try{
   owners=db.write(c->{
    long now=clock.millis();
    JsonArray found=rows(c,"SELECT DISTINCT hold_session FROM seats_state WHERE show_id=? AND status='HELD' AND hold_until<=?",show,now);
    if(!found.isEmpty()){releaseIn(c,"show_id=? AND hold_until<=?",show,now);db.log(c,null,"HOLD_EXPIRED","Suất "+show,now);}
    return found;
   });
   if(!owners.isEmpty())bump(show);
  }finally{l.unlock();}
  if(!owners.isEmpty()){
   changed(show);
   for(JsonElement owner:owners)events.holdExpired(owner.getAsJsonObject().get("hold_session").getAsString(),show);
  }
 }
 private JsonObject openShow(Connection c,long show)throws Exception {
  JsonObject info=one(c,"SELECT s.*,m.title,m.release_date,m.end_date,r.name AS room_name,ci.name AS cinema_name,ci.active AS cinema_active,a.active AS area_active,m.active AS movie_active,r.active AS room_active FROM showtimes s JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id JOIN cinemas ci ON ci.id=r.cinema_id JOIN areas a ON a.id=ci.area_id WHERE s.id=?",show);
  require(info!=null && "OPEN".equals(Json.str(info,"status","")) && Json.num(info,"starts_at",0)>clock.millis() && Json.num(info,"movie_active",0)==1 && Json.num(info,"room_active",0)==1 && Json.num(info,"cinema_active",0)==1 && Json.num(info,"area_active",0)==1,"Suất chiếu không còn mở bán.");
  CatalogService.checkShowDate(info,Json.num(info,"starts_at",0));
  return info;
 }
 private static int releaseIn(Connection c,String where,Object...args)throws Exception {
  return exec(c,"UPDATE seats_state SET status='AVAILABLE',held_by=NULL,hold_session=NULL,hold_until=NULL WHERE status='HELD' AND "+where,args);
 }
 private static List<String> validSeats(List<String> seats) {
  require(seats!=null && !seats.isEmpty() && seats.size()<=8,"Mỗi lần đặt từ 1 đến 8 ghế.");
  require(new HashSet<>(seats).size()==seats.size(),"Danh sách ghế bị trùng.");
  for(String s:seats)require(s!=null && s.matches("[A-L]([1-9]|1[0-6])"),"Mã ghế không hợp lệ.");
  return List.copyOf(seats);
 }
 private static JsonObject booking(Connection c,long id)throws Exception {
  JsonObject b=one(c,"SELECT b.*,m.title,r.name AS room_name,s.starts_at,u.username FROM bookings b JOIN showtimes s ON s.id=b.show_id JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id JOIN users u ON u.id=b.user_id WHERE b.id=?",id);
  require(b!=null,"Không tìm thấy vé.");
  b.add("tickets",rows(c,"SELECT * FROM tickets WHERE booking_id=? ORDER BY id",id));
  b.addProperty("qrText","CINEMA-DEMO:"+Json.str(b,"code",""));
  return b;
 }
}
