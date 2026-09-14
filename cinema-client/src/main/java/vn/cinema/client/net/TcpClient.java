package vn.cinema.client.net;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import vn.cinema.common.*;

public final class TcpClient implements AutoCloseable {
 private final Socket socket=new Socket();
 private final ConcurrentHashMap<String,CompletableFuture<JsonElement>> pending=new ConcurrentHashMap<>();
 private final ExecutorService writer=Executors.newSingleThreadExecutor(r->daemon(r,"client-writer"));
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"client-timer"));
 private final AtomicBoolean closed=new AtomicBoolean();
 private volatile String token;
 private volatile Consumer<Response> events=r->{};
 private volatile Consumer<String> disconnect=message->{};
 private final String host;
 public TcpClient(String host,int port)throws IOException {
  this.host=host;
  try{socket.connect(new InetSocketAddress(host,port),5000);socket.setTcpNoDelay(true);socket.setSoTimeout(65_000);}
  catch(IOException e){socket.close();writer.shutdownNow();timer.shutdownNow();throw e;}
  daemon(this::read,"client-reader").start();
  timer.scheduleAtFixedRate(()->request("PING",Json.obj()).exceptionally(e->{close();return null;}),20,20,TimeUnit.SECONDS);
 }
 public String host(){return host;}
 public void onEvent(Consumer<Response> listener){events=listener;}
 public void onDisconnect(Consumer<String> listener){disconnect=listener;}
 public CompletableFuture<JsonElement> request(String type,JsonObject data){
  if(closed.get())return CompletableFuture.failedFuture(new IOException("Kết nối đã đóng."));
  if(pending.size()>=64)return CompletableFuture.failedFuture(new IOException("Đang xử lý quá nhiều yêu cầu."));
  String id=UUID.randomUUID().toString();
  CompletableFuture<JsonElement> future=new CompletableFuture<>();pending.put(id,future);
  try{
   ScheduledFuture<?> timeout=timer.schedule(()->{
    CompletableFuture<JsonElement> waiting=pending.remove(id);
    if(waiting!=null)waiting.completeExceptionally(new IOException("Chưa nhận phản hồi sau 15 giây. Với thanh toán, bấm lại Xác nhận hoặc kiểm tra Vé của tôi."));
   },15,TimeUnit.SECONDS);
   future.whenComplete((r,e)->timeout.cancel(false));
   Request frame=new Request(id,type,token,data);
   writer.execute(()->{try{JsonLineCodec.write(socket.getOutputStream(),frame);}catch(IOException e){close();}});
  }catch(RejectedExecutionException e){pending.remove(id);future.completeExceptionally(new IOException("Kết nối đã đóng."));}
  return future;
 }
 private void read(){
  try{
   String line;
   while((line=JsonLineCodec.read(socket.getInputStream()))!=null){
    Response response=Json.GSON.fromJson(line,Response.class);
    if(response.id()==null){events.accept(response);continue;}
    CompletableFuture<JsonElement> future=pending.remove(response.id());
    if(future==null)continue;
    if(response.success()){
     if("LOGIN".equals(response.type()))token=response.data().getAsJsonObject().get("token").getAsString();
     if("LOGOUT".equals(response.type()))token=null;
     future.complete(response.data());
    }else future.completeExceptionally(new IllegalArgumentException(response.message()));
   }
  }catch(Exception e){if(!closed.get())disconnect.accept("Mất kết nối tới server. Ghế đang giữ sẽ được trả lại.");}
  finally{close();}
 }
 @Override public void close(){
  if(!closed.compareAndSet(false,true))return;
  try{socket.close();}catch(IOException ignored){}
  writer.shutdownNow();timer.shutdownNow();
  pending.forEach((id,f)->f.completeExceptionally(new IOException("Kết nối tới server đã đóng.")));pending.clear();
  disconnect.accept("Đã ngắt kết nối tới server.");
 }
 private static Thread daemon(Runnable r,String name){Thread t=new Thread(r,name);t.setDaemon(true);return t;}
}
