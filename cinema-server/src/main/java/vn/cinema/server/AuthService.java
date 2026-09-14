package vn.cinema.server;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.time.Clock;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

public final class AuthService {
 private final Database db;
 private final Clock clock;
 public AuthService(Database db,Clock clock){this.db=db;this.clock=clock;}
 public JsonObject register(JsonObject data)throws Exception {
  String username=text(data,"username",3,32);
  require(username.matches("[a-zA-Z0-9_]{3,32}"),"Tên đăng nhập chỉ gồm chữ, số và dấu gạch dưới.");
  String password=password(data,"password"), name=text(data,"displayName",2,60);
  String hash=Passwords.hash(password);
  return db.write(c->{
   require(one(c,"SELECT id FROM users WHERE username=?",username)==null,"Tên đăng nhập đã tồn tại.");
   long id=insert(c,"INSERT INTO users(username,password_hash,display_name,role,created_at) VALUES(?,?,?,'USER',?)",username,hash,name,clock.millis());
   db.log(c,id,"REGISTER","Tạo tài khoản",clock.millis());
   return publicUser(c,id);
  });
 }
 public JsonObject login(JsonObject data,boolean adminOnly)throws Exception {
  String username=text(data,"username",3,32), password=password(data,"password");
  JsonObject user=db.read(c->one(c,"SELECT * FROM users WHERE username=?",username));
  boolean valid=user!=null && Passwords.verify(password,Json.str(user,"password_hash",""));
  require(valid,"Tên đăng nhập hoặc mật khẩu chưa đúng.");
  require("ACTIVE".equals(Json.str(user,"status","")),"Tài khoản đã bị khoá.");
  require(!adminOnly || "ADMIN".equals(Json.str(user,"role","")),"Chỉ quản trị viên được mở dashboard.");
  return db.write(c->{
   long id=user.get("id").getAsLong();
   JsonObject current=publicUser(c,id);
   require("ACTIVE".equals(Json.str(current,"status","")),"Tài khoản đã bị khoá.");
   db.log(c,id,"LOGIN",adminOnly?"Dashboard":"Java TCP",clock.millis());
   return current;
  });
 }
 public JsonObject profile(Session session)throws Exception{return db.read(c->check(c,session,false));}
 public JsonObject update(Session session,JsonObject data)throws Exception {
  String name=text(data,"displayName",2,60);
  return db.write(c->{check(c,session,false);exec(c,"UPDATE users SET display_name=? WHERE id=?",name,session.userId());return publicUser(c,session.userId());});
 }
 public JsonObject changePassword(Session session,JsonObject data)throws Exception {
  String old=password(data,"oldPassword"), next=password(data,"newPassword");
  String hash=Passwords.hash(next);
  return db.write(c->{
   check(c,session,false);
   JsonObject user=one(c,"SELECT password_hash FROM users WHERE id=?",session.userId());
   require(Passwords.verify(old,user.get("password_hash").getAsString()),"Mật khẩu hiện tại chưa đúng.");
   exec(c,"UPDATE users SET password_hash=? WHERE id=?",hash,session.userId());
   db.log(c,session.userId(),"CHANGE_PASSWORD","Đổi mật khẩu",clock.millis());
   return Json.obj();
  });
 }
 public static JsonObject check(Connection c,Session session,boolean admin)throws Exception {
  require(session!=null,"Vui lòng đăng nhập.");
  JsonObject user=publicUser(c,session.userId());
  require(user!=null && "ACTIVE".equals(Json.str(user,"status","")),"Phiên đăng nhập không còn hợp lệ.");
  require(!admin || "ADMIN".equals(Json.str(user,"role","")),"Chức năng dành cho quản trị viên.");
  return user;
 }
 private static JsonObject publicUser(Connection c,long id)throws Exception {
  return one(c,"SELECT id,username,display_name,role,status,created_at FROM users WHERE id=?",id);
 }
 private static String password(JsonObject data,String field) {
  String password=Json.str(data,field,"");
  require(password.length()>=8 && password.length()<=128,"Mật khẩu cần 8–128 ký tự.");
  return password;
 }
}
