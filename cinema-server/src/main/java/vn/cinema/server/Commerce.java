package vn.cinema.server;

import com.google.gson.*;
import java.sql.Connection;
import java.time.Clock;
import java.util.Locale;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

/** Prices, promotion quotas and loyalty are authoritative at the database writer. */
public final class Commerce {
 private final Database db; private final Clock clock;
 public Commerce(Database db,Clock clock){this.db=db;this.clock=clock;}
 static void migrate(Connection c)throws Exception {
  c.setAutoCommit(false);
  try {
   if(one(c,"SELECT version FROM schema_migrations WHERE version=2")==null){
    exec(c,"CREATE TABLE promotions(code TEXT PRIMARY KEY COLLATE NOCASE,percent INTEGER NOT NULL CHECK(percent BETWEEN 1 AND 100),max_discount INTEGER NOT NULL,min_total INTEGER NOT NULL,starts_at INTEGER NOT NULL,ends_at INTEGER NOT NULL,quota INTEGER NOT NULL,per_user INTEGER NOT NULL,active INTEGER NOT NULL DEFAULT 1)");
    exec(c,"ALTER TABLE bookings ADD COLUMN discount_vnd INTEGER NOT NULL DEFAULT 0");
    exec(c,"ALTER TABLE bookings ADD COLUMN promotion_code TEXT NOT NULL DEFAULT ''");
    exec(c,"CREATE TABLE loyalty_ledger(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id),booking_id INTEGER NOT NULL REFERENCES bookings(id),points INTEGER NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL,UNIQUE(booking_id,reason))");
    exec(c,"CREATE TABLE payment_intents(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id),show_id INTEGER NOT NULL REFERENCES showtimes(id),request_id TEXT NOT NULL,seats TEXT NOT NULL,amount INTEGER NOT NULL,discount INTEGER NOT NULL,promotion_code TEXT NOT NULL,status TEXT NOT NULL,expires_at INTEGER NOT NULL,created_at INTEGER NOT NULL,provider_data TEXT NOT NULL DEFAULT '{}',booking_id INTEGER REFERENCES bookings(id),checked_at INTEGER NOT NULL DEFAULT 0,UNIQUE(user_id,request_id))");
    exec(c,"ALTER TABLE cinemas ADD COLUMN opens_at TEXT NOT NULL DEFAULT '08:00'");
    exec(c,"ALTER TABLE cinemas ADD COLUMN closes_at TEXT NOT NULL DEFAULT '23:59'");
    exec(c,"CREATE INDEX payment_pending ON payment_intents(status,checked_at)");
    exec(c,"INSERT INTO schema_migrations VALUES(2)");
   }
   c.commit();
  }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
 }
 static String code(String value){String s=value.strip().toUpperCase(Locale.ROOT);require(s.isEmpty()||s.matches("[A-Z0-9_-]{3,32}"),"Mã giảm giá không hợp lệ.");return s;}
 static JsonObject quote(Connection c,long user,long gross,String coupon,long now)throws Exception {
  String code=code(coupon);long discount=0;
  if(!code.isEmpty()){
   JsonObject p=one(c,"SELECT * FROM promotions WHERE code=? AND active=1 AND starts_at<=? AND ends_at>?",code,now,now);
   require(p!=null,"Mã giảm giá không tồn tại hoặc đã hết hạn.");
   require(gross>=Json.num(p,"min_total",0),"Đơn chưa đạt giá trị tối thiểu của mã.");
   long used=Json.num(one(c,"SELECT (SELECT COUNT(*) FROM bookings WHERE promotion_code=? AND status='CONFIRMED')+(SELECT COUNT(*) FROM payment_intents WHERE promotion_code=? AND status IN('CREATING','PENDING') AND expires_at+60000>?) AS n",code,code,now),"n",0);
   long personal=Json.num(one(c,"SELECT (SELECT COUNT(*) FROM bookings WHERE promotion_code=? AND user_id=? AND status='CONFIRMED')+(SELECT COUNT(*) FROM payment_intents WHERE promotion_code=? AND user_id=? AND status IN('CREATING','PENDING') AND expires_at+60000>?) AS n",code,user,code,user,now),"n",0);
   require(used<Json.num(p,"quota",0) && personal<Json.num(p,"per_user",0),"Mã đã hết lượt sử dụng.");
   discount=Math.min(gross*Json.num(p,"percent",0)/100,Json.num(p,"max_discount",0));
  }
  return Json.obj("subtotal",gross,"discount",discount,"total",gross-discount,"promotionCode",code,"pointsEarned",(gross-discount)/10000);
 }
 static void award(Connection c,long user,long booking,long amount,long now)throws Exception {
  exec(c,"INSERT OR IGNORE INTO loyalty_ledger(user_id,booking_id,points,reason,created_at) VALUES(?,?,?,'EARN',?)",user,booking,amount/10000,now);
 }
 static void reverse(Connection c,long booking,long now)throws Exception {
  exec(c,"INSERT OR IGNORE INTO loyalty_ledger(user_id,booking_id,points,reason,created_at) SELECT user_id,booking_id,-points,'CANCEL',? FROM loyalty_ledger WHERE booking_id=? AND reason='EARN'",now,booking);
 }
 public JsonObject loyalty(Session s)throws Exception{return db.read(c->{AuthService.check(c,s,false);JsonObject o=one(c,"SELECT COALESCE(SUM(points),0) AS points FROM loyalty_ledger WHERE user_id=?",s.userId());o.add("history",rows(c,"SELECT l.*,b.code FROM loyalty_ledger l JOIN bookings b ON b.id=l.booking_id WHERE l.user_id=? ORDER BY l.id DESC LIMIT 100",s.userId()));return o;});}
 public JsonArray promotions(boolean all)throws Exception{return db.read(c->rows(c,"SELECT * FROM promotions"+(all?"":" WHERE active=1 AND starts_at<="+clock.millis()+" AND ends_at>"+clock.millis())+" ORDER BY ends_at"));}
 public JsonObject save(Session s,JsonObject d)throws Exception {
  String code=code(text(d,"code",3,32));long percent=number(d,"percent",1,100),max=number(d,"max_discount",1,80_000_000),min=number(d,"min_total",0,80_000_000),start=number(d,"starts_at",0,Long.MAX_VALUE),end=number(d,"ends_at",start+1,Long.MAX_VALUE),quota=number(d,"quota",1,1_000_000),per=number(d,"per_user",1,quota),active=number(d,"active",0,1);
  return db.write(c->{AuthService.check(c,s,true);exec(c,"INSERT INTO promotions VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET percent=excluded.percent,max_discount=excluded.max_discount,min_total=excluded.min_total,starts_at=excluded.starts_at,ends_at=excluded.ends_at,quota=excluded.quota,per_user=excluded.per_user,active=excluded.active",code,percent,max,min,start,end,quota,per,active);db.log(c,s.userId(),"SAVE_PROMOTION",code,clock.millis());return one(c,"SELECT * FROM promotions WHERE code=?",code);});
 }
}
