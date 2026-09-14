package vn.cinema.server;
import com.google.gson.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.cinema.common.Json;

public final class Database {
 private final String url;
 public Database(Path file) throws Exception {
  Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");
  url="jdbc:sqlite:"+file.toAbsolutePath();
  try(Connection c=open();Statement s=c.createStatement()) {
   s.execute("PRAGMA journal_mode=WAL");
   String schema=new String(Objects.requireNonNull(getClass().getResourceAsStream("/schema.sql")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   for(String sql:schema.split(";"))if(!sql.isBlank())s.execute(sql);
   // A previous process cannot retain ownership of a hold.
   s.executeUpdate("UPDATE seats_state SET status='AVAILABLE',held_by=NULL,hold_session=NULL,hold_until=NULL WHERE status='HELD'");
  }
 }
 public Connection open() throws SQLException {
  Connection c=DriverManager.getConnection(url);
  try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=10000");}
  return c;
 }
 @FunctionalInterface public interface Work<T>{T run(Connection c)throws Exception;}
 public <T>T read(Work<T> w)throws Exception{try(Connection c=open()){return w.run(c);}}
 // SQLite has one writer. This gate avoids DEFERRED-transaction upgrade races within this process.
 public synchronized <T>T write(Work<T>w)throws Exception{
  try(Connection c=open()){
   c.setAutoCommit(false);
   try{T value=w.run(c);c.commit();return value;}
   catch(Exception e){c.rollback();throw e;}
  }
 }
 public static JsonArray rows(Connection c,String sql,Object... args)throws SQLException{
  try(PreparedStatement p=c.prepareStatement(sql)){
   bind(p,args);try(ResultSet rs=p.executeQuery()){
    JsonArray a=new JsonArray();ResultSetMetaData md=rs.getMetaData();
    while(rs.next()){
     JsonObject o=new JsonObject();
     for(int i=1;i<=md.getColumnCount();i++)o.add(md.getColumnLabel(i),Json.GSON.toJsonTree(rs.getObject(i)));
     a.add(o);
    }return a;
   }
  }
 }
 public static JsonObject one(Connection c,String sql,Object...args)throws SQLException{
  JsonArray a=rows(c,sql,args);return a.isEmpty()?null:a.get(0).getAsJsonObject();
 }
 public static int exec(Connection c,String sql,Object...args)throws SQLException{
  try(PreparedStatement p=c.prepareStatement(sql)){bind(p,args);return p.executeUpdate();}
 }
 public static long insert(Connection c,String sql,Object...args)throws SQLException{
  exec(c,sql,args);return one(c,"SELECT last_insert_rowid() AS id").get("id").getAsLong();
 }
 private static void bind(PreparedStatement p,Object[]args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
 public void log(Connection c,Long user,String action,String detail,long now)throws SQLException{
  exec(c,"INSERT INTO logs(user_id,action,detail,created_at) VALUES(?,?,?,?)",user,action,detail.substring(0,Math.min(300,detail.length())),now);
 }
 public void seed(Clock clock)throws Exception{
  String adminHash=Passwords.hash("Admin@123"), userHash=Passwords.hash("User@1234");
  write(c->{
   if(one(c,"SELECT id FROM users LIMIT 1")!=null)return null;
   long now=clock.millis();
   exec(c,"INSERT INTO users(username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?)","admin",adminHash,"Quản trị rạp","ADMIN",now);
   exec(c,"INSERT INTO users(username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?)","user1",userHash,"Sinh viên A","USER",now);
   exec(c,"INSERT INTO users(username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?)","user2",userHash,"Sinh viên B","USER",now);
   String[] titles={"Hẹn Nhau Ở Huế","Chuyến Tàu Bình Minh","Mật Mã Đại Dương","Mùa Hè Của Chúng Ta","Ngôi Nhà Cuối Phố","Hành Trình Sao Hoả","Bức Thư Chưa Gửi","Đội Bóng Xóm Nhỏ","Bên Kia Cầu Vồng","Một Ngày Thật Khác"};
   String catalog=new String(Objects.requireNonNull(getClass().getResourceAsStream("/seed.sql")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   try(Statement statement=c.createStatement()){for(String sql:catalog.split(";"))if(!sql.isBlank())statement.execute(sql);}
   ZonedDateTime base=ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"));
   for(int day=0;day<2;day++)for(int room=1;room<=3;room++)for(int slot=0;slot<4;slot++){
    int movie=(day*12+(room-1)*4+slot)%10+1;
    long start=base.plusDays(day).plusHours(10+slot*3).toInstant().toEpochMilli();
    long show=insert(c,"INSERT INTO showtimes(movie_id,room_id,starts_at,ends_at,price_vnd) VALUES(?,?,?,?,?)",movie,room,start,start+(105+(movie-1)*2)*60_000L,room==3?120000:75000);
    createSeats(c,show,room+5,room==3?10:8);
    if(slot==0){
     long booking=insert(c,"INSERT INTO bookings(user_id,show_id,request_id,total_vnd,status,payment_method,created_at) VALUES(2,?,?,150000,'CONFIRMED','DEMO',?)",show,"seed-"+show,now);
     exec(c,"UPDATE bookings SET code=?,total_vnd=? WHERE id=?",code(booking,now),2*(room==3?120000:75000),booking);
     for(String seat:List.of("A1","A2")){
      exec(c,"UPDATE seats_state SET status='SOLD',booking_id=? WHERE show_id=? AND seat_label=?",booking,show,seat);
      exec(c,"INSERT INTO tickets(booking_id,show_id,seat_label,movie_title,room_name,starts_at,price_vnd) VALUES(?,?,?,?,?,?,?)",booking,show,seat,titles[movie-1],room==3?"IMAX":"P"+room,start,room==3?120000:75000);
     }
    }
   }
   log(c,1L,"SEED","Khởi tạo dữ liệu demo",now);return null;
  });
 }
 public static void createSeats(Connection c,long show,int rows,int cols)throws SQLException{
  for(int r=0;r<rows;r++)for(int col=1;col<=cols;col++)exec(c,"INSERT INTO seats_state(show_id,seat_label) VALUES(?,?)",show,""+(char)('A'+r)+col);
 }
 public static String code(long id,long now){
  String date=Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
  return "CB-"+date+"-"+String.format("%04d",id);
 }
}
