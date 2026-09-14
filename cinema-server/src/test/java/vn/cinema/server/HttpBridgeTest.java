package vn.cinema.server;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Clock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.cinema.common.Json;
import static org.junit.jupiter.api.Assertions.*;

class HttpBridgeTest {
 @TempDir Path dir;
 @Test void bridgeRequiresKeyAndAdminCredentialsAndRejectsWrites()throws Exception {
  Database db=new Database(dir.resolve("cinema.db"));db.seed(Clock.systemUTC());
  try(TcpServer tcp=new TcpServer(db,Clock.systemUTC(),0);
      HttpBridge bridge=new HttpBridge(tcp,0,dir.resolve("dashboard.key"))){
   tcp.start();bridge.start();
   HttpClient client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+bridge.port();
   String key=Files.readString(dir.resolve("dashboard.key"));
   assertTrue(key.length()>=32);
   assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(base+"/api/stats")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
   var stats=client.send(request(base,key,"/api/stats",null),HttpResponse.BodyHandlers.ofString());
   assertEquals(200,stats.statusCode());assertTrue(stats.body().contains("connectedClients"));
   assertEquals(405,client.send(request(base,key,"/api/bookings","{}"),HttpResponse.BodyHandlers.ofString()).statusCode());
   assertEquals(403,client.send(request(base,key,"/api/auth",Json.GSON.toJson(Json.obj("username","user1","password","User@1234"))),HttpResponse.BodyHandlers.ofString()).statusCode());
   var admin=client.send(request(base,key,"/api/auth",Json.GSON.toJson(Json.obj("username","admin","password","Admin@123"))),HttpResponse.BodyHandlers.ofString());
   assertEquals(200,admin.statusCode());assertFalse(admin.body().contains("password"));
   assertEquals(200,client.send(request(base,key,"/api/auth/check","{\"userId\":1}"),HttpResponse.BodyHandlers.ofString()).statusCode());
   db.write(c->{Database.exec(c,"UPDATE users SET status='LOCKED' WHERE id=1");return null;});
   assertEquals(403,client.send(request(base,key,"/api/auth/check","{\"userId\":1}"),HttpResponse.BodyHandlers.ofString()).statusCode());
  }
 }
 private HttpRequest request(String base,String key,String path,String body){
  var b=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+key).header("Content-Type","application/json");
  return body==null?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
 }
}
