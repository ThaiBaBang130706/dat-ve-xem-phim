package vn.cinema.server;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import vn.cinema.common.*;
import static vn.cinema.server.Validation.*;

public final class TcpServer implements AutoCloseable,BookingService.Events {
 private static final Logger LOG=LoggerFactory.getLogger(TcpServer.class);
 private final AuthService auth;
 private final BookingService booking;
 private final AdminService admin;
 private final CatalogService catalog;private final MediaService media;
 private final ServerSocket listener;
 private final Set<Client> clients=ConcurrentHashMap.newKeySet();
 private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"cinema-maintenance"));
 private volatile boolean running=true;
 private final java.util.concurrent.atomic.AtomicLong requests=new java.util.concurrent.atomic.AtomicLong(), errors=new java.util.concurrent.atomic.AtomicLong();
 private final Deque<JsonObject> events=new ArrayDeque<>();
 private synchronized void record(String action,String detail){
  events.addFirst(Json.obj("created_at",System.currentTimeMillis(),"action",action,"detail",detail));
  while(events.size()>200)events.removeLast();
 }
 public synchronized JsonObject operations(){
  JsonArray connections=new JsonArray(), journal=new JsonArray();
  for(Client c:clients)connections.add(Json.obj("connection",c.connectionId.substring(0,8),"address",c.socket.getRemoteSocketAddress().toString(),"username",c.username,"role",c.role,"connected_at",c.connectedAt,"last_seen_at",c.lastSeen,"show",c.subscribedShow));
  events.forEach(e->journal.add(e.deepCopy()));
  return Json.obj("clients",connections,"events",journal,"requests",requests.get(),"errors",errors.get());
 }
 public TcpServer(Database db,Clock clock,int port)throws IOException {
  auth=new AuthService(db,clock);booking=new BookingService(db,clock);admin=new AdminService(db,clock,booking);catalog=new CatalogService(db,clock);media=new MediaService(db,clock);
  booking.setEvents(this);
  listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress("0.0.0.0",port),64);
 }
 public int port(){return listener.getLocalPort();}
 public AuthService auth(){return auth;}
 public AdminService admin(){return admin;}
 public BookingService bookings(){return booking;}
 public int clientCount(){return clients.size();}
 public void start(){
  daemon(this::accept,"cinema-accept").start();
  scheduler.scheduleAtFixedRate(()->{
   try{booking.expireAll();}catch(Exception e){LOG.error("Không thể dọn ghế hết hạn",e);}
   long now=System.currentTimeMillis();
   clients.forEach(c->{if(now-c.lastSeen>60_000)c.close();});
  },5,5,TimeUnit.SECONDS);
  LOG.info("TCP đang nghe cổng {}",port());
 }
 private void accept(){
  while(running)try{
   Socket socket=listener.accept();
   if(clients.size()>=64){socket.close();continue;}
   socket.setTcpNoDelay(true);socket.setSoTimeout(60_000);
   Client client=new Client(socket);clients.add(client);client.start();record("CONNECT",socket.getRemoteSocketAddress().toString());
  }catch(IOException e){if(running)LOG.warn("Lỗi nhận kết nối",e);}
 }
 @Override public void seatsChanged(long show){
  for(Client client:clients)if(client.subscribedShow==show && client.session!=null)try{
   client.send(Response.event("EVENT_SEAT_UPDATE",booking.snapshot(client.session,show)));
  }catch(Exception e){LOG.warn("Không thể phát sơ đồ ghế suất {}",show,e);}
 }
 @Override public void holdExpired(String connectionId,long show){
  for(Client c:clients)if(c.connectionId.equals(connectionId))c.send(Response.event("EVENT_HOLD_EXPIRED",Json.obj("showId",show,"message","Đã hết 5 phút giữ ghế. Vui lòng chọn lại.")));
 }
 private void showUpdated(){for(Client c:clients)if(c.session!=null)c.send(Response.event("EVENT_SHOW_UPDATED",Json.obj("message","Lịch chiếu vừa được cập nhật.")));}
 @Override public void close(){
  running=false;
  try{listener.close();}catch(IOException ignored){}
  scheduler.shutdownNow();
  for(Client c:List.copyOf(clients))c.close();
 }
 private static Thread daemon(Runnable task,String name){Thread t=new Thread(task,name);t.setDaemon(true);return t;}

 private final class Client {
  private final Socket socket;
  private final String connectionId=UUID.randomUUID().toString();
  private final BlockingQueue<Response> outgoing=new ArrayBlockingQueue<>(128);
  private final AtomicBoolean closed=new AtomicBoolean();
  private volatile Session session;
  private volatile String username="Chưa đăng nhập",role="—";
  private final long connectedAt=System.currentTimeMillis();
  private volatile String token;
  private volatile long subscribedShow,lastSeen=System.currentTimeMillis();
  private Thread writer;
  private long authWindow=System.currentTimeMillis();
  private int authAttempts;
  Client(Socket socket){this.socket=socket;}
  void start(){
   writer=daemon(()->{
    try{OutputStream out=socket.getOutputStream();while(!closed.get())JsonLineCodec.write(out,outgoing.take());}
    catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(IOException e){if(!closed.get())LOG.debug("Kết thúc luồng gửi: {}",e.getMessage());}
    finally{close();}
   },"cinema-writer");
   writer.start();
   send(Response.event("EVENT_SYSTEM",Json.obj("message","Đã kết nối CinemaBooking","heartbeatSeconds",20,"holdSeconds",300)));
   daemon(this::read,"cinema-client").start();
  }
  void read(){
   try{
    InputStream in=new BufferedInputStream(socket.getInputStream());String line;
    while(!closed.get() && (line=JsonLineCodec.read(in))!=null){
     lastSeen=System.currentTimeMillis();
     requests.incrementAndGet();
     Request request=null;
     try{
      request=Json.GSON.fromJson(line,Request.class);
      require(request!=null && request.id()!=null && request.id().length()>=1 && request.id().length()<=64,"Thiếu mã yêu cầu.");
      require(request.type()!=null && request.type().length()<=64,"Thiếu loại yêu cầu.");
      Object result=handle(request);
      send(Response.ok(request,result));
      if(!request.type().equals("PING"))record(request.type(),username+" · OK");
     }catch(IllegalArgumentException|IllegalStateException e){
      errors.incrementAndGet();record("REJECT",username+" · "+(request==null?"INVALID_JSON":request.type()));
      send(Response.error(request==null?null:request.id(),request==null?"ERROR":request.type(),e.getMessage()==null?"JSON không hợp lệ.":e.getMessage()));
     }catch(Exception e){
      errors.incrementAndGet();record("ERROR",username+" · lỗi xử lý yêu cầu");
      LOG.error("Không xử lý được yêu cầu {}",request==null?"UNKNOWN":request.type(),e);
      send(Response.error(request==null?null:request.id(),request==null?"ERROR":request.type(),"Server chưa xử lý được yêu cầu. Vui lòng thử lại."));
     }
    }
   }catch(IOException e){LOG.debug("Đóng kết nối: {}",e.getMessage());}
   finally{close();}
  }
  synchronized Object handle(Request request)throws Exception{
   require(!closed.get(),"Kết nối đã đóng.");
   String type=request.type();JsonObject d=request.data()==null?Json.obj():request.data();
   if(type.equals("PING"))return Json.obj("serverTime",System.currentTimeMillis());
   if(type.equals("LOGIN") || type.equals("REGISTER")){
    require(session==null,"Hãy đăng xuất trước khi đổi tài khoản.");
    long now=System.currentTimeMillis();
    if(now-authWindow>60_000){authAttempts=0;authWindow=now;}
    require(++authAttempts<=10,"Thử đăng nhập quá nhiều. Chờ 1 phút rồi thử lại.");
    if(type.equals("REGISTER"))return auth.register(d);
    JsonObject user=auth.login(d,false);
    username=Json.str(user,"username","");role=Json.str(user,"role","");
    session=new Session(user.get("id").getAsLong(),connectionId);
    token=UUID.randomUUID().toString();
    return Json.obj("token",token,"user",user);
   }
   require(session!=null && token!=null && token.equals(request.token()),"Vui lòng đăng nhập lại.");
   auth.profile(session);
   if(type.startsWith("ADMIN_")){
    JsonElement result=admin.handle(session,type,d);
    if(type.startsWith("ADMIN_SAVE_") || type.startsWith("ADMIN_DELETE_"))showUpdated();
    return result;
   }
   return switch(type){
    case "LOGOUT" -> {booking.disconnect(session);session=null;token=null;subscribedShow=0;username="Chưa đăng nhập";role="—";yield Json.obj();}
    case "GET_PROFILE" -> auth.profile(session);
    case "UPDATE_PROFILE" -> auth.update(session,d);
    case "CHANGE_PASSWORD" -> auth.changePassword(session,d);
    case "GET_MOVIES" -> booking.movies();
    case "GET_MOVIE_DETAIL" -> {
     long id=number(d,"movieId",1,Long.MAX_VALUE);
     JsonObject movie=null;
     for(JsonElement e:booking.movies())if(e.getAsJsonObject().get("id").getAsLong()==id)movie=e.getAsJsonObject();
     require(movie!=null,"Không tìm thấy phim.");yield movie;
    }
    case "GET_SHOWTIMES" -> catalog.shows(d);
    case "GET_GENRES" -> catalog.genres(false);
    case "GET_AREAS" -> catalog.areas(false);
    case "GET_CINEMAS" -> catalog.cinemas(false,Json.num(d,"areaId",0));
    case "GET_IMAGE" -> media.image(Validation.text(d,"id",64,64));
    case "GET_SEATMAP" -> booking.seatMap(session,number(d,"showId",1,Long.MAX_VALUE));
    case "SUBSCRIBE_SHOW" -> {
     long show=number(d,"showId",1,Long.MAX_VALUE);
     JsonObject map=booking.seatMap(session,show);subscribedShow=show;
     // Read once more after subscribing, closing the response/event registration gap.
     yield booking.snapshot(session,show);
    }
    case "UNSUBSCRIBE_SHOW" -> {subscribedShow=0;yield Json.obj();}
    case "HOLD_SEATS" -> booking.hold(session,number(d,"showId",1,Long.MAX_VALUE),seatList(d));
    case "RELEASE_SEATS" -> booking.release(session,number(d,"showId",1,Long.MAX_VALUE));
    case "CONFIRM_BOOKING" -> booking.confirm(session,number(d,"showId",1,Long.MAX_VALUE),seatList(d),Validation.text(d,"requestId",8,64));
    case "GET_MY_TICKETS" -> booking.myBookings(session);
    case "GET_TICKET" -> booking.ticket(session,number(d,"bookingId",1,Long.MAX_VALUE));
    case "CANCEL_BOOKING" -> booking.cancel(session,number(d,"bookingId",1,Long.MAX_VALUE));
    default -> throw new IllegalArgumentException("Lệnh không được hỗ trợ: "+type);
   };
  }
  List<String> seatList(JsonObject d){
   require(d.has("seats") && d.get("seats").isJsonArray(),"Danh sách ghế không hợp lệ.");
   List<String> result=new ArrayList<>();for(JsonElement e:d.getAsJsonArray("seats"))result.add(e.getAsString());return result;
  }
  void send(Response response){
   if(!closed.get() && !outgoing.offer(response)){
    // Only interrupt I/O here. Cleanup runs in the connection threads, avoiding
    // cross-client monitor deadlocks when two slow clients overflow together.
    try{socket.close();}catch(IOException ignored){}
   }
  }
  void close(){
   if(!closed.compareAndSet(false,true))return;
   clients.remove(this);record("DISCONNECT",username+" · "+socket.getRemoteSocketAddress());
   try{socket.close();}catch(IOException ignored){}
   if(writer!=null)writer.interrupt();
   synchronized(this){try{booking.disconnect(session);}catch(Exception e){LOG.error("Không thể trả ghế khi ngắt kết nối",e);}}
  }
 }
}

