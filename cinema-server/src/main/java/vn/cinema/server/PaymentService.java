package vn.cinema.server;

import com.google.gson.*;
import java.time.Clock;
import java.util.*;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

/** Durable payment reservations. Only a provider lookup can issue paid tickets. */
public final class PaymentService {
 private final Database db;private final Clock clock;private final BookingService booking;private final PayOS provider;
 public PaymentService(Database db,Clock clock,BookingService booking){this(db,clock,booking,new PayOS());}
 PaymentService(Database db,Clock clock,BookingService booking,PayOS provider){this.db=db;this.clock=clock;this.booking=booking;this.provider=provider;}
 public boolean enabled(){return provider.enabled();}
 public synchronized JsonObject create(Session s,long show,List<String> requested,String request,String coupon)throws Exception {
  require(enabled(),"Chưa cấu hình payOS. Quản trị cần đặt khoá thanh toán trên máy chủ.");
  List<String> seats=BookingService.validSeats(requested);Collections.sort(seats=new ArrayList<>(seats));
  String labels=String.join(",",seats);require(request.matches("[A-Za-z0-9_-]{8,64}"),"Mã yêu cầu không hợp lệ.");
  JsonObject intent=db.write(c->{
   AuthService.check(c,s,false);
   JsonObject old=one(c,"SELECT * FROM payment_intents WHERE user_id=? AND request_id=?",s.userId(),request);
   if(old!=null){require(Json.num(old,"show_id",0)==show&&labels.equals(Json.str(old,"seats",""))&&Commerce.code(coupon).equals(Json.str(old,"promotion_code","")),"Mã yêu cầu đã dùng cho lựa chọn khác.");return old;}
   JsonObject info=booking.openShow(c,show);long now=clock.millis();
   require(one(c,"SELECT id FROM payment_intents WHERE user_id=? AND status IN('CREATING','PENDING') AND expires_at>?",s.userId(),now)==null,"Bạn còn giao dịch đang chờ. Mở Thanh toán của tôi để kiểm tra.");
   for(String seat:labels.split(","))require(one(c,"SELECT seat_label FROM seats_state WHERE show_id=? AND seat_label=? AND status='HELD' AND hold_session=? AND hold_until>?",show,seat,s.connectionId(),now)!=null,"Ghế không còn được giữ bởi bạn.");
   JsonObject q=Commerce.quote(c,s.userId(),Json.num(info,"price_vnd",0)*labels.split(",").length,coupon,now);
   long total=Json.num(q,"total",0);require(total>=2000,"QR yêu cầu số tiền từ 2.000đ; chọn mã giảm giá khác.");
   long expiry=Math.min(now+300_000,Json.num(info,"starts_at",now)-1000);require(expiry>now+30_000,"Suất chiếu sắp bắt đầu.");
   long last=Json.num(one(c,"SELECT COALESCE(MAX(id),0) AS id FROM payment_intents"),"id",0);long id=Math.max(now,last+1);
   exec(c,"INSERT INTO payment_intents(id,user_id,show_id,request_id,seats,amount,discount,promotion_code,status,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,'CREATING',?,?)",id,s.userId(),show,request,labels,total,Json.num(q,"discount",0),Json.str(q,"promotionCode",""),expiry,now);
   for(String seat:labels.split(","))exec(c,"UPDATE seats_state SET hold_session=?,hold_until=? WHERE show_id=? AND seat_label=?","payment:"+id,expiry+60_000,show,seat);
   return one(c,"SELECT * FROM payment_intents WHERE id=?",id);
  });
  long id=Json.num(intent,"id",0);booking.publish(show);
  if("CREATING".equals(Json.str(intent,"status",""))&&clock.millis()<Json.num(intent,"expires_at",0)){
   JsonObject data;
   try{data=provider.create(id,Json.num(intent,"amount",0),Json.num(intent,"expires_at",0));}
   catch(Exception e){try{data=provider.get(id);}catch(Exception ignored){throw e;}}
   require(Json.num(data,"orderCode",0)==id&&Json.num(data,"amount",-1)==Json.num(intent,"amount",0),"Thông tin trả về của payOS không khớp.");
   String serialized=Json.GSON.toJson(data);
   db.write(c->{exec(c,"UPDATE payment_intents SET provider_data=?,status='PENDING' WHERE id=? AND status='CREATING'",serialized,id);return null;});
  }
  return status(s,id);
 }
 public synchronized JsonObject status(Session s,long id)throws Exception {
  JsonObject row=db.read(c->{AuthService.check(c,s,false);JsonObject p=one(c,"SELECT * FROM payment_intents WHERE id=? AND user_id=?",id,s.userId());require(p!=null,"Không tìm thấy giao dịch của bạn.");return p;});
  if(enabled()&&Set.of("CREATING","PENDING","EXPIRED").contains(Json.str(row,"status",""))&&clock.millis()-Json.num(row,"checked_at",0)>=5000)reconcile(id);
  return db.read(c->publicData(one(c,"SELECT * FROM payment_intents WHERE id=?",id)));
 }
 public JsonArray list(Session s,boolean admin)throws Exception{return db.read(c->{AuthService.check(c,s,admin);JsonArray out=new JsonArray();for(JsonElement e:rows(c,"SELECT * FROM payment_intents"+(admin?"":" WHERE user_id="+s.userId())+" ORDER BY created_at DESC LIMIT 100"))out.add(publicData(e.getAsJsonObject()));return out;});}
 private JsonObject publicData(JsonObject p){
  JsonObject out=p.deepCopy(),d=Json.GSON.fromJson(Json.str(p,"provider_data","{}"),JsonObject.class);out.remove("provider_data");out.remove("request_id");
  String link=Json.str(d,"checkoutUrl","");String providerId=Json.str(d,"paymentLinkId",Json.str(d,"id",""));
  if(link.isEmpty()&&providerId.matches("[a-fA-F0-9]{32}"))link="https://pay.payos.vn/web/"+providerId;
  out.addProperty("checkoutUrl",link);out.addProperty("qrCode",Json.str(d,"qrCode",""));return out;
 }
 synchronized void reconcile(long id)throws Exception {
  db.write(c->{exec(c,"UPDATE payment_intents SET checked_at=? WHERE id=?",clock.millis(),id);return null;});
  JsonObject remote=provider.get(id);
  long show=db.write(c->{
   JsonObject p=one(c,"SELECT * FROM payment_intents WHERE id=?",id);if(p==null||Set.of("PAID","REVIEW","CANCELLED").contains(Json.str(p,"status","")))return 0L;
   long showId=Json.num(p,"show_id",0),amount=Json.num(p,"amount",0),now=clock.millis();
   require(Json.num(remote,"orderCode",0)==id&&Json.num(remote,"amount",-1)==amount,"Giao dịch payOS không khớp đơn.");
   if("PAID".equals(Json.str(remote,"status",""))){
    boolean valid=Json.num(remote,"amountPaid",-1)==amount;JsonObject info=null;
    try{info=booking.openShow(c,showId);}catch(IllegalArgumentException e){valid=false;}
    for(String seat:Json.str(p,"seats","").split(","))if(one(c,"SELECT seat_label FROM seats_state WHERE show_id=? AND seat_label=? AND status='HELD' AND hold_session=? AND hold_until>?",showId,seat,"payment:"+id,now)==null)valid=false;
    if(!valid){exec(c,"UPDATE payment_intents SET status='REVIEW' WHERE id=?",id);db.log(c,Json.num(p,"user_id",0),"PAYMENT_REVIEW","Cần đối soát/hoàn tiền đơn "+id,now);}
    else {
     long user=Json.num(p,"user_id",0),bid=insert(c,"INSERT INTO bookings(user_id,show_id,request_id,total_vnd,status,payment_method,created_at,discount_vnd,promotion_code) VALUES(?,?,?,?,'CONFIRMED','PAYOS',?,?,?)",user,showId,"payos-"+id,amount,now,Json.num(p,"discount",0),Json.str(p,"promotion_code",""));
     exec(c,"UPDATE bookings SET code=? WHERE id=?",Database.code(bid,now),bid);
     String[] seats=Json.str(p,"seats","").split(",");
     for(int i=0;i<seats.length;i++){
      exec(c,"UPDATE seats_state SET status='SOLD',held_by=NULL,hold_session=NULL,hold_until=NULL,booking_id=? WHERE show_id=? AND seat_label=?",bid,showId,seats[i]);
      exec(c,"INSERT INTO tickets(booking_id,show_id,seat_label,movie_title,room_name,starts_at,price_vnd) VALUES(?,?,?,?,?,?,?)",bid,showId,seats[i],Json.str(info,"title",""),Json.str(info,"cinema_name","")+" / "+Json.str(info,"room_name",""),Json.num(info,"starts_at",0),amount/seats.length+(i<amount%seats.length?1:0));
     }
     Commerce.award(c,user,bid,amount,now);exec(c,"UPDATE payment_intents SET status='PAID',booking_id=? WHERE id=?",bid,id);db.log(c,user,"PAYMENT_PAID","payOS "+id,now);
    }
   }else if(Set.of("CANCELLED","EXPIRED").contains(Json.str(remote,"status",""))||now>Json.num(p,"expires_at",0)+60_000){exec(c,"UPDATE payment_intents SET status=? WHERE id=?","CANCELLED".equals(Json.str(remote,"status",""))?"CANCELLED":"EXPIRED",id);}
   if(!"PENDING".equals(Json.str(remote,"status",""))||now>Json.num(p,"expires_at",0)+60_000)exec(c,"UPDATE seats_state SET status='AVAILABLE',held_by=NULL,hold_session=NULL,hold_until=NULL WHERE status='HELD' AND hold_session=?","payment:"+id);
   return showId;
  });
  if(show>0)booking.publish(show);
 }
 public void poll(){
  if(!enabled())return;
  try{JsonArray pending=db.read(c->rows(c,"SELECT id FROM payment_intents WHERE status IN('CREATING','PENDING','EXPIRED') AND created_at>? AND checked_at<? ORDER BY checked_at LIMIT 20",clock.millis()-7L*86400000,clock.millis()-15000));for(JsonElement e:pending)try{reconcile(Json.num(e.getAsJsonObject(),"id",0));}catch(Exception ignored){/* Retry; no payment is inferred from a timeout. */}}catch(Exception e){org.slf4j.LoggerFactory.getLogger(PaymentService.class).warn("Không thể đọc danh sách đối soát thanh toán");}
 }
}
