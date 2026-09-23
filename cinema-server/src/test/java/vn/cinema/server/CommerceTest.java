package vn.cinema.server;
import com.google.gson.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static org.junit.jupiter.api.Assertions.*;
class CommerceTest {
 @TempDir Path dir;Database db;BookingService booking;Commerce commerce;Clock clock;Session user=new Session(2,"a"),other=new Session(3,"b"),admin=new Session(1,"root");
 @BeforeEach void setup()throws Exception{clock=Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"),ZoneOffset.UTC);db=new Database(dir.resolve("cinema.db"));db.seed(clock);booking=new BookingService(db,clock);commerce=new Commerce(db,clock);}
 void promo()throws Exception{commerce.save(admin,Json.obj("code","SALE10","percent",10,"max_discount",50000,"min_total",0,"starts_at",clock.millis()-1,"ends_at",clock.millis()+86400000,"quota",1,"per_user",1,"active",1));}
 @Test void serverPricesQuotaAndLoyaltyAreAtomicAndIdempotent()throws Exception{
  promo();booking.hold(user,1,List.of("B1","B2"));JsonObject b=booking.confirm(user,1,List.of("B1","B2"),"checkout-one","sale10");assertEquals(135000,Json.num(b,"total_vnd",0));assertEquals(13,Json.num(commerce.loyalty(user),"points",0));assertEquals(b,booking.confirm(user,1,List.of("B1","B2"),"checkout-one","SALE10"));assertEquals(13,Json.num(commerce.loyalty(user),"points",0));
  booking.hold(other,1,List.of("B3"));assertThrows(IllegalArgumentException.class,()->booking.confirm(other,1,List.of("B3"),"checkout-two","SALE10"));
  booking.cancel(user,Json.num(b,"id",0));booking.cancel(user,Json.num(b,"id",0));assertEquals(0,Json.num(commerce.loyalty(user),"points",0));
  assertThrows(IllegalArgumentException.class,()->commerce.save(user,Json.obj("code","X10","percent",10,"max_discount",100,"min_total",0,"starts_at",1,"ends_at",9,"quota",1,"per_user",1,"active",1)));
 }
 @Test void migrationsRetainPointsAndAreRepeatable()throws Exception{booking.hold(user,1,List.of("B1"));booking.confirm(user,1,List.of("B1"),"repeat-upgrade");new Database(dir.resolve("cinema.db"));assertEquals(7,Json.num(commerce.loyalty(user),"points",0));}
 static class FakePayOS extends PayOS {JsonObject data;boolean paid;FakePayOS(){super(Map.of());}public boolean enabled(){return true;}public JsonObject create(long id,long amount,long expiry){data=Json.obj("orderCode",id,"amount",amount,"status","PENDING","checkoutUrl","https://pay.payos.vn/web/test","qrCode","TEST");return data;}public JsonObject get(long id){JsonObject v=data.deepCopy();v.addProperty("status",paid?"PAID":"PENDING");v.addProperty("amountPaid",paid?Json.num(v,"amount",0):0);return v;}}
 @Test void paidQrSurvivesDisconnectRestartAndNeverAwardsTwice()throws Exception{
  FakePayOS provider=new FakePayOS();PaymentService payments=new PaymentService(db,clock,booking,provider);booking.hold(user,1,List.of("B1"));JsonObject p=payments.create(user,1,List.of("B1"),"payment-00001","");long id=Json.num(p,"id",0);booking.disconnect(user);new Database(dir.resolve("cinema.db"));assertThrows(IllegalArgumentException.class,()->booking.hold(other,1,List.of("B1")));assertThrows(IllegalArgumentException.class,()->payments.status(other,id));
  provider.paid=true;payments.reconcile(id);payments.reconcile(id);JsonObject paid=payments.status(user,id);assertEquals("PAID",Json.str(paid,"status",""));assertEquals(7,Json.num(commerce.loyalty(user),"points",0));assertThrows(IllegalArgumentException.class,()->booking.cancel(user,Json.num(paid,"booking_id",0)));
 }
 @Test void amountMismatchAndLatePaymentCannotIssueTickets()throws Exception{
  FakePayOS provider=new FakePayOS();PaymentService payments=new PaymentService(db,clock,booking,provider);booking.hold(user,1,List.of("B1"));long id=Json.num(payments.create(user,1,List.of("B1"),"payment-late", ""),"id",0);provider.paid=true;
  db.write(c->{exec(c,"UPDATE seats_state SET status='AVAILABLE',held_by=NULL,hold_session=NULL,hold_until=NULL WHERE hold_session=?","payment:"+id);return null;});booking.hold(other,1,List.of("B1"));booking.confirm(other,1,List.of("B1"),"other-booking");payments.reconcile(id);assertEquals("REVIEW",Json.str(payments.status(user,id),"status",""));assertEquals(0,Json.num(commerce.loyalty(user),"points",0));
 }
 @Test void pendingPaymentReservesPromotionQuota()throws Exception{promo();FakePayOS provider=new FakePayOS();PaymentService payments=new PaymentService(db,clock,booking,provider);booking.hold(user,1,List.of("B1"));payments.create(user,1,List.of("B1"),"reserve-promo","SALE10");assertThrows(IllegalArgumentException.class,()->booking.quote(other,1,List.of("B2"),"SALE10"));}
 @Test void operatingHoursSupportOvernightAndRejectOutsideWindow(){JsonObject hours=Json.obj("opens_at","20:00","closes_at","02:00");long start=LocalDateTime.parse("2026-09-15T00:30").atZone(CatalogService.ZONE).toInstant().toEpochMilli();assertDoesNotThrow(()->CatalogService.checkHours(hours,start,start+3600000));assertThrows(IllegalArgumentException.class,()->CatalogService.checkHours(hours,start,start+7200000));}
}
