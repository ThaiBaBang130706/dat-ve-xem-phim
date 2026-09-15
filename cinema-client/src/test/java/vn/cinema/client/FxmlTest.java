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
       assertEquals(java.util.List.of("Tổng quan","Phim","Phòng chiếu","Suất chiếu","Khách hàng","Đơn đặt vé","Vé theo ghế","Nhật ký"),tabs.getTabs().stream().map(javafx.scene.control.Tab::getText).toList());
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
 @AfterAll static void stop(){Platform.exit();}
}
