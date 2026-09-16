package vn.cinema.server;

import com.google.gson.JsonObject;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Clock;
import vn.cinema.common.Json;

/** Owns one database/process lock and closes all listeners after a failed start. */
public final class ServerRuntime implements AutoCloseable {
 private TcpServer tcp;
 private HttpBridge http;
 private FileChannel channel;
 private FileLock lock;
 private long started;
 private Path database;
 public synchronized void start(Path file,int tcpPort,int httpPort,boolean seed)throws Exception {
  if(tcp!=null)throw new IllegalStateException("Server đang chạy.");
  if(tcpPort<0 || tcpPort>65535 || httpPort<0 || httpPort>65535 || tcpPort>0 && tcpPort==httpPort)throw new IllegalArgumentException("Cổng TCP và HTTP phải khác nhau và hợp lệ.");
  database=file.toAbsolutePath().normalize();Files.createDirectories(database.getParent());
  try{
   channel=FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
   try{lock=channel.tryLock();}catch(OverlappingFileLockException e){throw new IllegalStateException("Database này đang được một server khác sử dụng.");}
   if(lock==null)throw new IllegalStateException("Database này đang được một server khác sử dụng.");
   Clock clock=Clock.systemUTC();Database db=new Database(database);if(seed)db.seed(clock);
   tcp=new TcpServer(db,clock,tcpPort);http=new HttpBridge(tcp,httpPort,database.getParent().resolve("dashboard.key"));
   tcp.start();http.start();started=clock.millis();
  }catch(Exception e){close();throw e;}
 }
 public synchronized boolean running(){return tcp!=null;}
 public synchronized int tcpPort(){return tcp==null?0:tcp.port();}
 public synchronized int httpPort(){return http==null?0:http.port();}
 public synchronized JsonObject snapshot()throws Exception {
  if(tcp==null)return Json.obj("running",false);
  JsonObject result=tcp.operations();result.addProperty("running",true);result.addProperty("tcpPort",tcp.port());result.addProperty("httpPort",http.port());result.addProperty("started",started);result.addProperty("database",database.toString());
  Runtime vm=Runtime.getRuntime();result.addProperty("heapUsed",vm.totalMemory()-vm.freeMemory());result.addProperty("heapMax",vm.maxMemory());
  result.add("stats",tcp.admin().stats());return result;
 }
 @Override public synchronized void close(){
  if(http!=null){http.close();http=null;}
  if(tcp!=null){tcp.close();tcp=null;}
  try{if(lock!=null)lock.release();}catch(Exception ignored){}finally{lock=null;}
  try{if(channel!=null)channel.close();}catch(Exception ignored){}finally{channel=null;}
 }
}
