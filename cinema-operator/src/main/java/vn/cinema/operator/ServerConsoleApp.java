package vn.cinema.operator;

import com.google.gson.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import vn.cinema.client.Ui;
import vn.cinema.common.Json;
import vn.cinema.server.ServerRuntime;

/** Local operator GUI. Data comes from the same running TCP server, never fixtures. */
public final class ServerConsoleApp extends Application {
 private final ServerRuntime runtime=new ServerRuntime();
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"operator-worker");t.setDaemon(true);return t;});
 private final AtomicBoolean closing=new AtomicBoolean();
 private volatile boolean busy;
 private TextField tcpPort,httpPort,database;
 private CheckBox seed;
 private Button start,stop;
 private Label health,status,connections,requests,errors,uptime,heap,held,revenue;
 private TableView<JsonObject> clients,events;
 @Override public void start(Stage stage){
  stage.setTitle("NOIR Cinema · Vận hành server");stage.setMinWidth(1080);stage.setMinHeight(740);
  BorderPane root=new BorderPane();root.getStyleClass().add("page");
  VBox brand=new VBox(12,Ui.label("NOIR / SERVER","brand"),Ui.label("Vận hành rạp phim","section-title"),Ui.label("Java TCP · Mạng LAN","muted"));brand.setPadding(new Insets(25));
  health=Ui.label("● Đã dừng","status");brand.getChildren().add(health);root.setTop(brand);
  tcpPort=Ui.field("5000","TCP");httpPort=Ui.field("5001","HTTP bridge");database=Ui.field(defaultDatabase().toString(),"Đường dẫn SQLite");
  tcpPort.setId("tcpPort");httpPort.setId("httpPort");database.setId("database");
  seed=new CheckBox("Tạo dữ liệu mẫu nếu database còn trống");seed.setSelected(true);seed.setWrapText(true);
  start=Ui.button("Khởi động server",this::startServer,"primary");start.setId("startServer");start.setMaxWidth(Double.MAX_VALUE);
  stop=Ui.button("Dừng server",this::stopServer,"danger");stop.setId("stopServer");stop.setMaxWidth(Double.MAX_VALUE);stop.setDisable(true);
  VBox settings=new VBox(12,Ui.label("CẤU HÌNH","eyebrow"),Ui.label("Cổng TCP",null),tcpPort,Ui.label("Cổng HTTP dashboard",null),httpPort,Ui.label("Database dùng chung",null),database,seed,start,stop,Ui.label("Kết nối client bằng một trong các IP LAN của máy này:","muted"),Ui.label(addresses(),"light-text"),Ui.label("Máy này: 127.0.0.1\nKhông chạy thêm JAR server khi cửa sổ này đã khởi động server.","muted"));
  settings.getStyleClass().add("sidebar");settings.setPadding(new Insets(24));settings.setPrefWidth(285);root.setLeft(settings);
  connections=metric();requests=metric();errors=metric();uptime=metric();heap=Ui.label("JVM heap: —","muted");held=Ui.label("Ghế đang giữ: —","muted");revenue=Ui.label("Doanh thu demo: —","muted");
  HBox metrics=new HBox(16,card("KẾT NỐI TCP",connections),card("YÊU CẦU",requests),card("YÊU CẦU LỖI",errors),card("UPTIME",uptime));
  clients=Ui.table(new JsonArray(),"connection","Phiên","address","Địa chỉ","username","Tài khoản","role","Quyền","connected_at","Kết nối lúc","show","Suất đang xem");clients.setId("clientsTable");
  events=Ui.table(new JsonArray(),"created_at","Thời gian","action","Sự kiện","detail","Chi tiết");events.setId("eventsTable");
  TabPane tabs=new TabPane(tab("Kết nối hiện tại",clients),tab("Nhật ký TCP · 200 gần nhất",events));VBox.setVgrow(tabs,Priority.ALWAYS);
  status=Ui.label("Nhập cấu hình rồi bấm Khởi động server.","muted");status.setId("runtimeStatus");
  VBox center=new VBox(18,metrics,new HBox(24,heap,held,revenue),tabs,status);center.setPadding(new Insets(24));root.setCenter(center);
  Scene scene=new Scene(root,1320,850);scene.getStylesheets().add(Ui.class.getResource("/vn/cinema/client/styles.css").toExternalForm());stage.setScene(scene);
  stage.setOnCloseRequest(e->{e.consume();if(!runtime.running()||Ui.confirm("Đóng server? Các client sẽ bị ngắt kết nối và ghế giữ được trả lại."))shutdown(stage);});stage.show();
  worker.scheduleWithFixedDelay(()->{if(!busy&&!closing.get())try{JsonObject snapshot=runtime.snapshot();Platform.runLater(()->{if(!closing.get())display(snapshot);});}catch(Exception e){Platform.runLater(()->status.setText("Chưa đọc được dữ liệu: "+Ui.message(e)));}},1,2,TimeUnit.SECONDS);
 }
 private static Label metric(){return Ui.label("—","metric-value");}
 private static VBox card(String title,Label value){VBox box=new VBox(8,Ui.label(title,"eyebrow"),value);box.getStyleClass().add("card");HBox.setHgrow(box,Priority.ALWAYS);box.setMaxWidth(Double.MAX_VALUE);return box;}
 private static Tab tab(String name,javafx.scene.Node body){Tab tab=new Tab(name,body);tab.setClosable(false);return tab;}
 private void startServer(){
  if(busy||runtime.running())return;int tcp,http;Path file;
  try{tcp=Integer.parseInt(tcpPort.getText().strip());http=Integer.parseInt(httpPort.getText().strip());if(tcp<1||tcp>65535||http<1||http>65535||tcp==http)throw new IllegalArgumentException("Nhập hai cổng khác nhau từ 1 đến 65535.");file=Path.of(database.getText().strip());if(database.getText().isBlank())throw new IllegalArgumentException("Nhập đường dẫn database.");}
  catch(Exception e){Ui.error(Ui.message(e));return;}
  boolean sample=seed.isSelected();busy=true;controls();status.setText("Đang khởi động server…");
  worker.execute(()->{try{runtime.start(file,tcp,http,sample);JsonObject snapshot=runtime.snapshot();Platform.runLater(()->{busy=false;display(snapshot);controls();status.setText("Server đang chạy · client kết nối cổng "+tcp+" · tự cập nhật mỗi 2 giây.");});}
   catch(Exception e){Platform.runLater(()->{busy=false;controls();health.setText("● Khởi động thất bại");status.setText("Không khởi động được: "+Ui.message(e));Ui.error("Không khởi động được server. "+Ui.message(e));});}});
 }
 private void stopServer(){if(busy||!Ui.confirm("Dừng server? Tất cả client sẽ mất kết nối; ghế đang giữ sẽ được giải phóng."))return;busy=true;controls();status.setText("Đang dừng và trả ghế…");worker.execute(()->{runtime.close();Platform.runLater(()->{busy=false;display(Json.obj("running",false));controls();status.setText("Đã dừng server. Có thể khởi động lại với cùng dữ liệu.");});});}
 private void controls(){boolean running=runtime.running();start.setDisable(busy||running);stop.setDisable(busy||!running);tcpPort.setDisable(busy||running);httpPort.setDisable(busy||running);database.setDisable(busy||running);seed.setDisable(busy||running);}
 private void display(JsonObject s){
  boolean running=s.get("running").getAsBoolean();health.setText(running?"● Đang nghe TCP "+Json.num(s,"tcpPort",0):"● Đã dừng");health.getStyleClass().setAll(running?"legend-available":"muted");
  if(!running){connections.setText("0");uptime.setText("—");clients.getItems().clear();return;}
  clients.getItems().setAll(objects(s.getAsJsonArray("clients")));events.getItems().setAll(objects(s.getAsJsonArray("events")));
  connections.setText(""+clients.getItems().size());requests.setText(""+Json.num(s,"requests",0));errors.setText(""+Json.num(s,"errors",0));
  long seconds=(System.currentTimeMillis()-Json.num(s,"started",0))/1000;uptime.setText(String.format("%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));
  heap.setText("JVM heap: "+Json.num(s,"heapUsed",0)/1048576+" / "+Json.num(s,"heapMax",0)/1048576+" MB");JsonObject stats=s.getAsJsonObject("stats");held.setText("Ghế đang giữ: "+Json.num(stats,"heldSeats",0));revenue.setText("Doanh thu demo: "+Ui.money(Json.num(stats,"revenue",0)));
 }
 private static List<JsonObject> objects(JsonArray array){List<JsonObject> result=new ArrayList<>();array.forEach(e->result.add(e.getAsJsonObject()));return result;}
 private void shutdown(Stage stage){if(!closing.compareAndSet(false,true))return;stage.getScene().getRoot().setDisable(true);worker.execute(()->{runtime.close();worker.shutdown();Platform.runLater(()->{stage.hide();Platform.exit();});});}
 @Override public void stop(){closing.set(true);runtime.close();worker.shutdownNow();}
 static Path defaultDatabase(){Path dir=Path.of("").toAbsolutePath();for(Path p=dir;p!=null;p=p.getParent())if(Files.isDirectory(p.resolve("cinema-server"))&&Files.isRegularFile(p.resolve("pom.xml")))return p.resolve("data/cinema.db");return dir.resolve("data/cinema.db");}
 private static String addresses(){List<String> result=new ArrayList<>();try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))if(n.isUp()&&!n.isLoopback())for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address)result.add(a.getHostAddress());}catch(Exception ignored){}return result.isEmpty()?"Chưa tìm thấy IPv4 LAN":String.join("\n",result);}
}
