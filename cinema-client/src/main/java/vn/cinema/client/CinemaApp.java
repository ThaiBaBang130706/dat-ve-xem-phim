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
 @Override public void start(Stage stage)throws Exception{
  this.stage=stage;stage.setTitle("CinemaBooking · Đặt vé xem phim");stage.setMinWidth(1000);stage.setMinHeight(680);
  showLogin();stage.show();
 }
 public void showLogin(){
  if(main!=null){main.dispose();main=null;}
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
   FXMLLoader loader=new FXMLLoader(getClass().getResource("/vn/cinema/client/main.fxml"));
   Parent root=loader.load();main=loader.getController();main.init(this,client,user);scene(root);
  }catch(Exception e){Ui.error("Không mở được giao diện: "+e.getMessage());showLogin();}
 }
 private void scene(Parent root){
  Scene scene=new Scene(root,1180,780);scene.getStylesheets().add(getClass().getResource("/vn/cinema/client/styles.css").toExternalForm());
  stage.setScene(scene);
 }
 public void openDashboard(String host){getHostServices().showDocument("http://"+host+":3000");}
 @Override public void stop(){if(main!=null)main.dispose();if(connection!=null)connection.close();}
}
