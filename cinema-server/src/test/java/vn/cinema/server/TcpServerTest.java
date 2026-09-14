package vn.cinema.server;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpServerTest {
 @TempDir Path dir;
 @Test void twoRealSocketsReceiveUpdatesAndDisconnectReleasesHold()throws Exception {
  Database db=new Database(dir.resolve("cinema.db"));db.seed(Clock.systemUTC());
  try(TcpServer server=new TcpServer(db,Clock.systemUTC(),0)){
   server.start();
   try(Wire a=new Wire(server.port());Wire b=new Wire(server.port())){
    assertTrue(a.request("LOGIN",Json.obj("username","user1","password","User@1234")).success());
    assertTrue(b.request("LOGIN",Json.obj("username","user2","password","User@1234")).success());
    assertFalse(a.request("ADMIN_LIST_USERS",Json.obj()).success());
    a.request("SUBSCRIBE_SHOW",Json.obj("showId",1));b.request("SUBSCRIBE_SHOW",Json.obj("showId",1));
    assertTrue(a.request("HOLD_SEATS",Json.obj("showId",1,"seats",List.of("B4"))).success());
    Response event=b.nextEvent("EVENT_SEAT_UPDATE");
    assertEquals("HELD",seat(event.data().getAsJsonObject(),"B4").get("status").getAsString());
    assertEquals(0,seat(event.data().getAsJsonObject(),"B4").get("is_mine").getAsInt());
    assertFalse(b.request("HOLD_SEATS",Json.obj("showId",1,"seats",List.of("B4"))).success());
    a.close();
    Response released=b.nextEvent("EVENT_SEAT_UPDATE");
    assertEquals("AVAILABLE",seat(released.data().getAsJsonObject(),"B4").get("status").getAsString());
    assertEquals("PONG",b.request("PING",Json.obj()).type());
   }
  }
 }
 static JsonObject seat(JsonObject map,String label){for(JsonElement e:map.getAsJsonArray("seats"))if(e.getAsJsonObject().get("seat_label").getAsString().equals(label))return e.getAsJsonObject();throw new AssertionError("Missing seat");}
 static class Wire implements AutoCloseable{
  final Socket socket;String token;int sequence;final Deque<Response> events=new ArrayDeque<>();
  Wire(int port)throws Exception{socket=new Socket("127.0.0.1",port);socket.setSoTimeout(10_000);}
  Response read()throws Exception{return Json.GSON.fromJson(JsonLineCodec.read(socket.getInputStream()),Response.class);}
  Response request(String type,JsonObject data)throws Exception{
   String id="test-"+(++sequence);byte[] frame=(Json.GSON.toJson(new Request(id,type,token,data))+"\n").getBytes(StandardCharsets.UTF_8);
   // Deliberately split the JSON frame across writes.
   socket.getOutputStream().write(frame,0,3);socket.getOutputStream().flush();
   socket.getOutputStream().write(frame,3,frame.length-3);socket.getOutputStream().flush();
   while(true){Response r=read();if(id.equals(r.id())){if(type.equals("LOGIN") && r.success())token=r.data().getAsJsonObject().get("token").getAsString();return r;}events.add(r);}
  }
  Response nextEvent(String type)throws Exception{
   while(true){
    while(!events.isEmpty()){Response r=events.removeFirst();if(type.equals(r.type()))return r;}
    Response r=read();if(type.equals(r.type()))return r;
   }
  }
  public void close()throws Exception{socket.close();}
 }
}
