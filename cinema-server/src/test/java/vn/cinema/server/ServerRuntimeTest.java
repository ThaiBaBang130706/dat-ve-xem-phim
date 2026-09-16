package vn.cinema.server;
import com.google.gson.*;
import java.net.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import vn.cinema.common.*;

class ServerRuntimeTest {
 @TempDir Path dir;
 @Test void startStopRestartAndRollbackPortsOnHttpFailure()throws Exception {
  Path db=dir.resolve("cinema.db");
  try(ServerRuntime runtime=new ServerRuntime()){
   runtime.start(db,0,0,true);int tcp=runtime.tcpPort(),http=runtime.httpPort();
   assertTrue(runtime.snapshot().get("running").getAsBoolean());
   try(ServerRuntime duplicate=new ServerRuntime()){assertThrows(IllegalStateException.class,()->duplicate.start(db,0,0,true));}
   runtime.close();assertFalse(runtime.running());
   try(ServerSocket occupiedHttp=new ServerSocket(http)){
    assertThrows(Exception.class,()->runtime.start(db,tcp,http,false));assertFalse(runtime.running());
    try(ServerSocket releasedTcp=new ServerSocket(tcp)){assertEquals(tcp,releasedTcp.getLocalPort());}
   }
   runtime.start(db,tcp,http,false);assertEquals(tcp,runtime.tcpPort());
  }
 }
 @Test void metricsContainRealConnectionsAndNeverTokensOrPasswords()throws Exception {
  try(ServerRuntime runtime=new ServerRuntime()){
   runtime.start(dir.resolve("cinema.db"),0,0,true);
   try(Socket socket=new Socket("127.0.0.1",runtime.tcpPort())){
    socket.setSoTimeout(5000);JsonLineCodec.read(socket.getInputStream());
    JsonLineCodec.write(socket.getOutputStream(),new Request("login","LOGIN",null,Json.obj("username","admin","password","Admin@123")));
    Response response=Json.GSON.fromJson(JsonLineCodec.read(socket.getInputStream()),Response.class);assertTrue(response.success());
    JsonObject snapshot=runtime.snapshot();assertEquals(1,snapshot.getAsJsonArray("clients").size());assertTrue(snapshot.get("requests").getAsLong()>=1);
    assertEquals("admin",snapshot.getAsJsonArray("clients").get(0).getAsJsonObject().get("username").getAsString());
    assertFalse(snapshot.toString().contains("Admin@123"));assertFalse(snapshot.toString().contains(response.data().getAsJsonObject().get("token").getAsString()));
   }
  }
 }
}
