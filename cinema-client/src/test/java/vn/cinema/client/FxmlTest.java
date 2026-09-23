package vn.cinema.client;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FxmlTest {
 @BeforeAll static void start()throws Exception {
  CountDownLatch ready=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});
  assertTrue(ready.await(10,TimeUnit.SECONDS));
 }
 @Test void allScreensAndStylesLoadOnJavaFxThread()throws Exception {
  CompletableFuture<Void> done=new CompletableFuture<>();
  Platform.runLater(()->{
   try{
    for(String file:new String[]{"login.fxml","main.fxml","admin.fxml","admin-shell.fxml"}){
     Parent root=FXMLLoader.load(getClass().getResource("/vn/cinema/client/"+file));
     Scene scene=new Scene(root,1180,780);scene.getStylesheets().add(getClass().getResource("/vn/cinema/client/styles.css").toExternalForm());
     root.applyCss();root.layout();assertNotNull(root);
    }
    done.complete(null);
   }catch(Throwable e){done.completeExceptionally(e);}
  });
  done.get(20,TimeUnit.SECONDS);
 }
 @Test void loginRoutesAdminAndCustomerIntoSeparateWorkspaces()throws Exception {
  for(String role:new String[]{"ADMIN","USER"}){
   try(MockServer server=new MockServer()){
    vn.cinema.client.net.TcpClient client=new vn.cinema.client.net.TcpClient("127.0.0.1",server.port());
    CompletableFuture<Void> done=new CompletableFuture<>();
    Platform.runLater(()->{
     javafx.stage.Stage stage=new javafx.stage.Stage();CinemaApp app=new CinemaApp();
     try{
      app.start(stage);
      app.loggedIn(client,vn.cinema.common.Json.obj("id",1,"username",role.toLowerCase(),"display_name","Test","role",role));
      Parent root=stage.getScene().getRoot();root.applyCss();root.layout();
      java.util.Set<String> buttons=new java.util.HashSet<>();
      collectButtons(root,buttons);
      if(role.equals("ADMIN")){
       assertTrue(stage.getTitle().contains("Quản trị"));
       assertFalse(buttons.contains("Phim đang chiếu"));assertFalse(buttons.contains("Vé của tôi"));
       javafx.scene.control.TabPane tabs=(javafx.scene.control.TabPane)root.lookup("#tabs");
       assertNotNull(tabs);
       assertEquals(java.util.List.of("Tổng quan","Phim","Phòng chiếu","Suất chiếu","Khách hàng","Đơn đặt vé","Vé theo ghế","Nhật ký","Thể loại","Khu vực","Rạp"),tabs.getTabs().stream().map(javafx.scene.control.Tab::getText).toList());
      }else{
       assertTrue(buttons.contains("Phim đang chiếu"));assertTrue(buttons.contains("Vé của tôi"));
       assertFalse(buttons.contains("Quản lý rạp"));assertNull(root.lookup("#tabs"));
      }
      done.complete(null);
     }catch(Throwable e){done.completeExceptionally(e);}
     finally{app.stop();stage.close();}
    });
    done.get(20,TimeUnit.SECONDS);
   }
  }
 }
 private static void collectButtons(javafx.scene.Node node,java.util.Set<String> texts){
  if(node instanceof javafx.scene.control.Button button)texts.add(button.getText());
  if(node instanceof Parent parent)parent.getChildrenUnmodifiable().forEach(child->collectButtons(child,texts));
 }
 private static final class MockServer implements AutoCloseable{
  private final java.net.ServerSocket listener=new java.net.ServerSocket(0);
  private volatile java.net.Socket socket;
  MockServer()throws Exception{
   Thread worker=new Thread(()->{
    try(java.net.Socket accepted=listener.accept()){
     socket=accepted;java.io.InputStream input=new java.io.BufferedInputStream(accepted.getInputStream());String line;
     while((line=vn.cinema.common.JsonLineCodec.read(input))!=null){
      vn.cinema.common.Request request=vn.cinema.common.Json.GSON.fromJson(line,vn.cinema.common.Request.class);
      Object data=request.type().equals("ADMIN_GET_STATS")?vn.cinema.common.Json.obj("daily",new com.google.gson.JsonArray()):new com.google.gson.JsonArray();
      vn.cinema.common.JsonLineCodec.write(accepted.getOutputStream(),vn.cinema.common.Response.ok(request,data));
     }
    }catch(java.io.IOException ignored){}
   },"test-ui-server");worker.setDaemon(true);worker.start();
  }
  int port(){return listener.getLocalPort();}
  public void close()throws Exception{listener.close();if(socket!=null)socket.close();}
 }
 @Test void realTcpCustomerJourneyAndAdminWorkspace()throws Exception {
  java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("noir-ui-");
  vn.cinema.server.ServerRuntime server=new vn.cinema.server.ServerRuntime();
  server.start(folder.resolve("cinema.db"),0,0,true);
  CinemaApp app=new CinemaApp();javafx.stage.Stage stage=fx(()->new javafx.stage.Stage());
  try(vn.cinema.client.net.TcpClient customer=new vn.cinema.client.net.TcpClient("127.0.0.1",server.tcpPort());vn.cinema.client.net.TcpClient other=new vn.cinema.client.net.TcpClient("127.0.0.1",server.tcpPort());vn.cinema.client.net.TcpClient admin=new vn.cinema.client.net.TcpClient("127.0.0.1",server.tcpPort())){
   com.google.gson.JsonObject manager=admin.request("LOGIN",vn.cinema.common.Json.obj("username","admin","password","Admin@123")).get(5,TimeUnit.SECONDS).getAsJsonObject().getAsJsonObject("user");
   java.awt.image.BufferedImage poster=new java.awt.image.BufferedImage(80,120,java.awt.image.BufferedImage.TYPE_INT_RGB);
   java.awt.Graphics2D paint=poster.createGraphics();paint.setColor(java.awt.Color.ORANGE);paint.fillRect(0,0,80,120);paint.dispose();
   java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(poster,"png",encoded);
   String media=admin.request("ADMIN_UPLOAD_IMAGE",vn.cinema.common.Json.obj("data",java.util.Base64.getEncoder().encodeToString(encoded.toByteArray()))).get(5,TimeUnit.SECONDS).getAsJsonObject().get("url").getAsString();
   com.google.gson.JsonObject film=admin.request("ADMIN_LIST_MOVIES",vn.cinema.common.Json.obj()).get(5,TimeUnit.SECONDS).getAsJsonArray().get(0).getAsJsonObject();
   film.addProperty("poster_url",media);film.addProperty("banner_url",media);film.addProperty("director","Đạo diễn kiểm thử");film.addProperty("trailer_url","https://example.org/trailer");
   admin.request("ADMIN_SAVE_MOVIE",film).get(5,TimeUnit.SECONDS);
   com.google.gson.JsonObject user=customer.request("LOGIN",vn.cinema.common.Json.obj("username","user1","password","User@1234")).get(5,TimeUnit.SECONDS).getAsJsonObject().getAsJsonObject("user");
   other.request("LOGIN",vn.cinema.common.Json.obj("username","user2","password","User@1234")).get(5,TimeUnit.SECONDS);
   fx(()->{app.start(stage);app.loggedIn(customer,user);return null;});
   awaitButton(stage,"Xem lịch chiếu");fx(()->{screenshot(stage,"customer-home");button(stage,"Xem lịch chiếu").fire();return null;});
   awaitButton(stage,"Chọn ghế");
   fx(()->{
    assertFalse(button(stage,"Xem trailer").isDisabled());
    var root=stage.getScene().getRoot();assertNotNull(root.lookup("#areaFilter"));assertNotNull(root.lookup("#cinemaFilter"));
    javafx.scene.control.DatePicker day=(javafx.scene.control.DatePicker)root.lookup("#showDateFilter");day.setValue(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(2));assertNull(button(stage,"Chọn ghế"));day.setValue(null);assertNotNull(button(stage,"Chọn ghế"));
    screenshot(stage,"movie-detail-filters");button(stage,"Chọn ghế").fire();return null;
   });awaitButton(stage,"B3");
   fx(()->{button(stage,"B3").fire();button(stage,"Giữ ghế · 5 phút").fire();return null;});
   long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(fx(()->button(stage,"Thanh toán mô phỏng").isDisabled())&&System.nanoTime()<limit)Thread.sleep(40);
   assertFalse(fx(()->button(stage,"Thanh toán mô phỏng").isDisabled()));
   long show=customer.request("GET_SHOWTIMES",vn.cinema.common.Json.obj("movieId",1)).get(5,TimeUnit.SECONDS).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsLong();
   assertThrows(ExecutionException.class,()->other.request("HOLD_SEATS",vn.cinema.common.Json.obj("showId",show,"seats",java.util.List.of("B3"))).get(5,TimeUnit.SECONDS));
   fx(()->{screenshot(stage,"customer-seats");return null;});
   customer.request("CONFIRM_BOOKING",vn.cinema.common.Json.obj("showId",show,"seats",java.util.List.of("B3"),"requestId","ui-flow-unique-request")).get(5,TimeUnit.SECONDS);
   fx(()->{button(stage,"Vé của tôi").fire();return null;});awaitButton(stage,"Xem vé / QR");
   fx(()->{app.showLogin();app.loggedIn(admin,manager);return null;});awaitButton(stage,"Phim");
   fx(()->{button(stage,"Phim").fire();return null;});awaitButton(stage,"Thêm mới");
   fx(()->{screenshot(stage,"admin-movies");button(stage,"Thêm mới").fire();return null;});
   long formLimit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(fx(()->catalogDialog()==null)&&System.nanoTime()<formLimit)Thread.sleep(40);
   fx(()->{
    javafx.scene.control.DialogPane pane=catalogDialog();assertNotNull(pane);assertNotNull(pane.lookup("#catalog_poster_url"));assertNotNull(pane.lookup("#catalog_banner_url"));assertNotNull(pane.lookup("#catalog_release_date"));assertNotNull(pane.lookup("#catalog_director"));
    ((javafx.scene.control.TextField)pane.lookup("#catalog_title")).setText("Phim tạo từ giao diện");
    java.util.List<javafx.scene.control.CheckBox> checks=checkBoxes(pane);assertTrue(checks.size()>=2);checks.get(0).setSelected(true);checks.get(1).setSelected(true);
    ((javafx.scene.control.Button)pane.lookupButton(javafx.scene.control.ButtonType.OK)).fire();return null;
   });
   long saveLimit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(fx(()->catalogDialog()!=null)&&System.nanoTime()<saveLimit)Thread.sleep(40);assertNull(fx(FxmlTest::catalogDialog));
   com.google.gson.JsonArray savedMovies=admin.request("ADMIN_LIST_MOVIES",vn.cinema.common.Json.obj()).get(5,TimeUnit.SECONDS).getAsJsonArray();
   assertTrue(java.util.stream.StreamSupport.stream(savedMovies.spliterator(),false).map(com.google.gson.JsonElement::getAsJsonObject).anyMatch(m->"Phim tạo từ giao diện".equals(vn.cinema.common.Json.str(m,"title",""))&&m.getAsJsonArray("genres").size()==2));
   assertThrows(ExecutionException.class,()->other.request("ADMIN_LIST_USERS",vn.cinema.common.Json.obj()).get(5,TimeUnit.SECONDS));
   fx(()->{app.stop();stage.hide();return null;});
  }finally{fx(()->{app.stop();stage.hide();return null;});server.close();}
 }
 private static javafx.scene.control.DialogPane catalogDialog(){
  for(javafx.stage.Window window:javafx.stage.Window.getWindows())if(window.isShowing() && window.getScene()!=null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane && pane.lookup("#catalog_title")!=null)return pane;return null;
 }
 private static java.util.List<javafx.scene.control.CheckBox> checkBoxes(javafx.scene.Node node){
  java.util.List<javafx.scene.control.CheckBox> result=new java.util.ArrayList<>();if(node instanceof javafx.scene.control.CheckBox box)result.add(box);if(node instanceof Parent p)for(var child:p.getChildrenUnmodifiable())result.addAll(checkBoxes(child));return result;
 }
 private static <T>T fx(java.util.concurrent.Callable<T> work)throws Exception{
  CompletableFuture<T> result=new CompletableFuture<>();Platform.runLater(()->{try{result.complete(work.call());}catch(Throwable e){result.completeExceptionally(e);}});return result.get(15,TimeUnit.SECONDS);
 }
 private static javafx.scene.control.Button button(javafx.stage.Stage stage,String text){return find(stage.getScene().getRoot(),text);}
 private static javafx.scene.control.Button find(javafx.scene.Node node,String text){
  if(node instanceof javafx.scene.control.Button b && b.getText().equals(text))return b;
  if(node instanceof Parent p)for(javafx.scene.Node child:p.getChildrenUnmodifiable()){var b=find(child,text);if(b!=null)return b;}return null;
 }
 private static void awaitButton(javafx.stage.Stage stage,String text)throws Exception{
  long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<limit){if(fx(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();return button(stage,text)!=null;}))return;Thread.sleep(40);}fail("Không thấy nút "+text);
 }
 private static void screenshot(javafx.stage.Stage stage,String name)throws Exception{
  Parent root=stage.getScene().getRoot();root.applyCss();root.layout();javafx.scene.image.WritableImage image=root.snapshot(null,null);
  java.awt.image.BufferedImage output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
  java.nio.file.Path path=java.nio.file.Path.of("target/ui-preview/"+name+".png");java.nio.file.Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(output,"png",path.toFile());
 }
 @AfterAll static void stop(){Platform.exit();}
}

