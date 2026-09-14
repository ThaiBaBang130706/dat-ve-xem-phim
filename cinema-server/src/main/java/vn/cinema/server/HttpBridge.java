package vn.cinema.server;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import vn.cinema.common.Json;

public final class HttpBridge implements AutoCloseable {
 private final HttpServer server;
 private final ExecutorService pool=Executors.newFixedThreadPool(8);
 private final TcpServer tcp;
 private final byte[] expectedKey;
 public HttpBridge(TcpServer tcp,int port,Path keyFile)throws Exception{
  this.tcp=tcp;expectedKey=loadKey(keyFile).getBytes(StandardCharsets.UTF_8);
  server=HttpServer.create(new InetSocketAddress("0.0.0.0",port),32);
  server.setExecutor(pool);server.createContext("/api/",this::handle);
 }
 public void start(){server.start();}
 public int port(){return server.getAddress().getPort();}
 public static String loadKey(Path file)throws IOException {
  Files.createDirectories(file.toAbsolutePath().getParent());
  if(!Files.exists(file)){
   byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
   try{Files.writeString(file,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),StandardOpenOption.CREATE_NEW);}
   catch(FileAlreadyExistsException ignored){}
   try{Files.setPosixFilePermissions(file,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));}
   catch(UnsupportedOperationException ignored){}
  }
  String key=Files.readString(file).strip();
  if(key.length()<32)throw new IOException("dashboard.key quá ngắn; cần ít nhất 32 ký tự.");
  return key;
 }
 private void handle(HttpExchange exchange)throws IOException {
  try{
   String authorization=exchange.getRequestHeaders().getFirst("Authorization");
   String supplied=authorization!=null && authorization.startsWith("Bearer ")?authorization.substring(7):"";
   if(!MessageDigest.isEqual(expectedKey,supplied.getBytes(StandardCharsets.UTF_8))){respond(exchange,401,Json.obj("message","Thiếu khoá kết nối dashboard."));return;}
   String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
   Object result;
   if(path.equals("/api/auth") || path.equals("/api/auth/check")){
    if(!method.equals("POST")){respond(exchange,405,Json.obj("message","Cần POST."));return;}
    byte[] body=exchange.getRequestBody().readNBytes(16_385);
    if(body.length>16_384){respond(exchange,413,Json.obj("message","Yêu cầu quá lớn."));return;}
    JsonObject data=Json.GSON.fromJson(new String(body,StandardCharsets.UTF_8),JsonObject.class);
    if(path.equals("/api/auth"))result=tcp.auth().login(data,true);
    else {tcp.admin().checkAdmin(Validation.number(data,"userId",1,Long.MAX_VALUE));result=Json.obj("active",true);}
   }else{
    if(!method.equals("GET")){respond(exchange,405,Json.obj("message","Dashboard chỉ đọc dữ liệu."));return;}
    result=switch(path){
     case "/api/stats" -> {JsonObject stats=tcp.admin().stats();stats.addProperty("connectedClients",tcp.clientCount());yield stats;}
     case "/api/shows" -> tcp.admin().shows();
     case "/api/bookings" -> tcp.admin().bookings();
     case "/api/logs" -> tcp.admin().logs();
     default -> null;
    };
    if(result==null){respond(exchange,404,Json.obj("message","Không tìm thấy API."));return;}
   }
   respond(exchange,200,result);
  }catch(IllegalArgumentException|IllegalStateException e){respond(exchange,403,Json.obj("message",e.getMessage()==null?"Yêu cầu không hợp lệ.":e.getMessage()));}
  catch(Exception e){org.slf4j.LoggerFactory.getLogger(HttpBridge.class).warn("Lỗi HTTP bridge",e);respond(exchange,500,Json.obj("message","Server tạm thời chưa xử lý được."));}
  finally{exchange.close();}
 }
 private static void respond(HttpExchange e,int status,Object body)throws IOException{
  byte[] bytes=Json.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
  e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
  e.getResponseHeaders().set("Cache-Control","no-store");
  e.getResponseHeaders().set("X-Content-Type-Options","nosniff");
  e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);
 }
 @Override public void close(){server.stop(0);pool.shutdownNow();}
}
