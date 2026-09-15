package vn.cinema.client;

import com.google.gson.JsonObject;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import vn.cinema.client.controller.*;
import vn.cinema.client.net.TcpClient;

public final class CinemaApp extends Application {
 private Stage stage;
 private TcpClient connection;
 private MainController main;
 private AdminShellController admin;
 @Override public void start(Stage stage)throws Exception{
  this.stage=stage;stage.setTitle("CinemaBooking · Đặt vé xem phim");stage.setMinWidth(1000);stage.setMinHeight(680);
  showLogin();stage.show();
 }
 public void showLogin(){
  if(main!=null){main.dispose();main=null;}
  if(admin!=null){admin.dispose();admin=null;}
  if(connection!=null){connection.onDisconnect(message->{});connection.close();connection=null;}
  try{
   FXMLLoader loader=new FXMLLoader(getClass().getResource("/vn/cinema/client/login.fxml"));
   Parent root=loader.load();loader.<ConnectionController>getController().init(this);
   scene(root);
  }catch(Exception e){throw new IllegalStateException("Không mở được màn hình kết nối",e);}
 }
 public void loggedIn(TcpClient client,JsonObject user){
  connection=client;
  try{
   FXMLLoader loader=new FXMLLoader(getClass().getResource(screenFor(user)));
   Parent root=loader.load();
   if("ADMIN".equals(vn.cinema.common.Json.str(user,"role",""))){
    admin=loader.getController();admin.init(this,client,user);
    stage.setTitle("CinemaBooking · Quản trị rạp");
   }else{
    main=loader.getController();main.init(this,client,user);
    stage.setTitle("CinemaBooking · Đặt vé xem phim");
   }
   scene(root);
  }catch(Exception e){Ui.error("Không mở được giao diện: "+e.getMessage());showLogin();}
 }
 public static String screenFor(JsonObject user){
  return "/vn/cinema/client/"+("ADMIN".equals(vn.cinema.common.Json.str(user,"role",""))?"admin-shell.fxml":"main.fxml");
 }
 private void scene(Parent root){
  Scene scene=new Scene(root,1180,780);scene.getStylesheets().add(getClass().getResource("/vn/cinema/client/styles.css").toExternalForm());
  stage.setScene(scene);
 }
 public void openDashboard(String host){getHostServices().showDocument("http://"+host+":3000");}
 @Override public void stop(){if(main!=null)main.dispose();if(admin!=null)admin.dispose();if(connection!=null)connection.close();}
}
