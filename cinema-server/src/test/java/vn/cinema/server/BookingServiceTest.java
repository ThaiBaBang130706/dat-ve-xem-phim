package vn.cinema.server;

import com.google.gson.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.Json;
import static org.junit.jupiter.api.Assertions.*;
import static vn.cinema.server.Database.*;

class BookingServiceTest {
 @TempDir Path dir;
 Database db;BookingService service;AuthService auth;AdminService admin;MutableClock clock;
 Session a=new Session(2,"client-a"),b=new Session(3,"client-b"),root=new Session(1,"admin");
 @BeforeEach void setup()throws Exception {
  clock=new MutableClock(Instant.parse("2026-09-14T00:00:00Z").toEpochMilli());
  db=new Database(dir.resolve("cinema.db"));db.seed(clock);
  service=new BookingService(db,clock);auth=new AuthService(db,clock);admin=new AdminService(db,clock,service);
 }
 @Test void onlyOneClientCanHoldAndConfirmTheSameSeat()throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(12);CountDownLatch start=new CountDownLatch(1);
  try{
   List<Future<Session>> results=new ArrayList<>();
   for(int i=0;i<12;i++){
    Session contender=new Session(i%2==0?2:3,"client-"+i);
    results.add(pool.submit(()->{start.await();try{service.hold(contender,1,List.of("B1"));return contender;}catch(IllegalArgumentException conflict){return null;}}));
   }
   start.countDown();List<Session> winners=new ArrayList<>();
   for(Future<Session> result:results){Session winner=result.get(15,TimeUnit.SECONDS);if(winner!=null)winners.add(winner);}
   assertEquals(1,winners.size());
   Session winner=winners.get(0);
   JsonObject first=service.confirm(winner,1,List.of("B1"),"payment-0001");
   JsonObject retry=service.confirm(winner,1,List.of("B1"),"payment-0001");
   assertEquals(first.get("id"),retry.get("id"));
   assertEquals(1L,db.read(c->one(c,"SELECT COUNT(*) AS n FROM tickets WHERE show_id=1 AND seat_label='B1' AND status='ACTIVE'").get("n").getAsLong()).longValue());
   assertThrows(IllegalArgumentException.class,()->service.hold(a,1,List.of("B1")));
  }finally{pool.shutdownNow();}
 }
 @Test void failedMultiSeatHoldDoesNotLosePreviousSelection()throws Exception {
  service.hold(a,1,List.of("B1"));service.hold(b,1,List.of("B2"));
  assertThrows(IllegalArgumentException.class,()->service.hold(a,1,List.of("B3","B2")));
  assertEquals("AVAILABLE",status("B3"));assertEquals("HELD",status("B1"));
  service.confirm(a,1,List.of("B1"),"original-hold");
 }
 @Test void expiryAndDisconnectFreeSeatsAndNotify()throws Exception {
  List<String> expired=new ArrayList<>();
  service.setEvents(new BookingService.Events(){public void seatsChanged(long s){}public void holdExpired(String id,long s){expired.add(id);}});
  service.hold(a,1,List.of("B1"));clock.advance(BookingService.HOLD_MILLIS);
  service.expireAll();assertEquals("AVAILABLE",status("B1"));assertEquals(List.of("client-a"),expired);
  assertThrows(IllegalArgumentException.class,()->service.confirm(a,1,List.of("B1"),"expired-hold"));
  service.hold(b,1,List.of("B1"));service.disconnect(b);assertEquals("AVAILABLE",status("B1"));
 }
 @Test void holdsBelongToConnectionAndTicketBelongsToUser()throws Exception {
  service.hold(a,1,List.of("B1"));
  assertThrows(IllegalArgumentException.class,()->service.confirm(new Session(2,"other-device"),1,List.of("B1"),"stolen-hold"));
  JsonObject bought=service.confirm(a,1,List.of("B1"),"valid-payment");
  long id=bought.get("id").getAsLong();
  assertThrows(IllegalArgumentException.class,()->service.cancel(b,id));
  assertThrows(IllegalArgumentException.class,()->service.ticket(b,id));
  service.cancel(a,id);assertEquals("AVAILABLE",status("B1"));
  service.hold(b,1,List.of("B1"));service.confirm(b,1,List.of("B1"),"next-payment");
  assertEquals("SOLD",status("B1"));
 }
 @Test void adminValidationAndLockedUsersCannotMutate()throws Exception {
  assertThrows(IllegalArgumentException.class,()->admin.handle(a,"ADMIN_LIST_USERS",Json.obj()));
  JsonObject show=db.read(c->one(c,"SELECT * FROM showtimes WHERE id=1"));
  assertThrows(IllegalArgumentException.class,()->admin.handle(root,"ADMIN_SAVE_SHOW",Json.obj("movie_id",2,"room_id",1,"starts_at",show.get("starts_at"),"price_vnd",75000)));
  service.hold(a,1,List.of("B1"));
  admin.handle(root,"ADMIN_SET_USER_STATUS",Json.obj("id",2,"status","LOCKED"));
  assertEquals("AVAILABLE",status("B1"));
  assertThrows(IllegalArgumentException.class,()->service.hold(a,1,List.of("B1")));
 }
 @Test void passwordsAreHashedAndSqlInputCannotBypassLogin()throws Exception {
  JsonObject created=auth.register(Json.obj("username","new_student","password","abc12345","displayName","Bạn mới"));
  assertFalse(created.has("password_hash"));
  JsonObject user=auth.login(Json.obj("username","new_student","password","abc12345"),false);
  assertEquals("USER",Json.str(user,"role",""));
  assertThrows(IllegalArgumentException.class,()->auth.login(Json.obj("username","admin' OR '1'='1","password","wrong1234"),false));
  String hash=db.read(c->one(c,"SELECT password_hash FROM users WHERE username='new_student'").get("password_hash").getAsString());
  assertNotEquals("abc12345",hash);assertTrue(Passwords.verify("abc12345",hash));assertFalse(Passwords.verify("wrong",hash));
 }
 String status(String seat)throws Exception {return db.read(c->one(c,"SELECT status FROM seats_state WHERE show_id=1 AND seat_label=?",seat).get("status").getAsString());}
 static final class MutableClock extends Clock {
  final AtomicLong now;MutableClock(long millis){now=new AtomicLong(millis);}
  void advance(long millis){now.addAndGet(millis);}
  @Override public ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
  @Override public Instant instant(){return Instant.ofEpochMilli(now.get());}
  @Override public long millis(){return now.get();}
 }
}
