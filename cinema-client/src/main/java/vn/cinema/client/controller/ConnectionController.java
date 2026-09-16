package vn.cinema.client.controller;

import com.google.gson.JsonObject;
import java.util.concurrent.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import vn.cinema.client.*;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.Json;

public final class ConnectionController {
 @FXML private TextField hostField,portField,usernameField,nameField;
 @FXML private PasswordField passwordField;
 @FXML private Button connectButton,loginButton,registerButton;
 @FXML private Label statusLabel;
 @FXML private VBox loginBox;
 private CinemaApp app;private TcpClient client;private boolean disposed;
 public void detach(){disposed=true;client=null;}
 public void dispose(){disposed=true;if(client!=null){client.onDisconnect(m->{});client.close();client=null;}}
 public void init(CinemaApp app){this.app=app;}
 @FXML private void connect(){
  String host=hostField.getText().strip();int port;
  try{port=Integer.parseInt(portField.getText().strip());if(port<1 || port>65535 || host.isBlank())throw new NumberFormatException();}
  catch(NumberFormatException e){statusLabel.setText("Nhập IP/hostname và cổng từ 1 đến 65535.");return;}
  connectButton.setDisable(true);statusLabel.setText("Đang kết nối tới "+host+"…");
  CompletableFuture.supplyAsync(()->{try{return new TcpClient(host,port);}catch(Exception e){throw new CompletionException(e);}})
   .whenComplete((connected,error)->Ui.run(()->{
    if(disposed){if(connected!=null)connected.close();return;}
    connectButton.setDisable(false);
    if(error!=null){statusLabel.setText("Chưa kết nối được: "+Ui.message(error));return;}
    if(client!=null){client.onDisconnect(message->{});client.close();}
    client=connected;loginBox.setDisable(false);statusLabel.setText("Đã kết nối · "+host+":"+port);
    client.onDisconnect(message->Ui.run(()->{if(!disposed){loginBox.setDisable(true);statusLabel.setText(message);}}));
    usernameField.requestFocus();
   }));
 }
 @FXML private void login(){
  if(client==null)return;busy(true);
  client.request("LOGIN",Json.obj("username",usernameField.getText(),"password",passwordField.getText()))
   .whenComplete((value,error)->Ui.run(()->{if(disposed)return;busy(false);if(error!=null)statusLabel.setText(Ui.message(error));else{passwordField.clear();app.loggedIn(client,value.getAsJsonObject().getAsJsonObject("user"));}}));
 }
 @FXML private void register(){
  if(client==null)return;busy(true);
  JsonObject data=Json.obj("username",usernameField.getText(),"password",passwordField.getText(),"displayName",nameField.getText());
  client.request("REGISTER",data).whenComplete((value,error)->Ui.run(()->{
   if(disposed)return;busy(false);statusLabel.setText(error==null?"Đã tạo tài khoản. Bấm Đăng nhập để tiếp tục.":Ui.message(error));
  }));
 }
 private void busy(boolean value){loginButton.setDisable(value);registerButton.setDisable(value);connectButton.setDisable(value);}
}

