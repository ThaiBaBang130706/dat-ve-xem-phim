package vn.cinema.client.controller;

import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import vn.cinema.client.*;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.Json;

/** Administrative workspace, separate from the customer's booking controller. */
public final class AdminShellController {
 @FXML private Label identity,connection,notice;
 @FXML private AdminController managementController;
 private CinemaApp app;
 private TcpClient client;
 private JsonObject user;
 private boolean disposed,loggingOut;
 public void init(CinemaApp app,TcpClient client,JsonObject user){
  if(!"ADMIN".equals(Json.str(user,"role","")))throw new IllegalArgumentException("Cần quyền quản trị.");
  this.app=app;this.client=client;this.user=user;
  identity.setText(Ui.string(user,"display_name")+" · @"+Ui.string(user,"username"));
  connection.setText("Đã kết nối "+client.host()+":"+client.port());
  managementController.init(client,()->app.openDashboard(client.host()));
  client.onEvent(event->Ui.run(()->{
   if(disposed)return;
   if("EVENT_SHOW_UPDATED".equals(event.type()))notice.setText("Danh mục hoặc lịch chiếu vừa thay đổi. Bấm Tải lại dữ liệu để xem bản mới.");
  }));
  client.onDisconnect(message->Ui.run(()->{
   if(disposed)return;
   dispose();Ui.error(message);app.showLogin();
  }));
 }
 public void dispose(){disposed=true;managementController.dispose();}
 @FXML private void logout(){
  if(loggingOut || !Ui.confirm("Đăng xuất khỏi giao diện quản trị?"))return;
  loggingOut=true;
  client.request("LOGOUT",Json.obj()).whenComplete((value,error)->Ui.run(()->{if(!disposed){dispose();app.showLogin();}}));
 }
 @FXML private void profile(){
  Dialog<Void> dialog=Ui.themed(new Dialog<>());dialog.setTitle("Tài khoản quản trị");
  dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  TextField name=Ui.field(Ui.string(user,"display_name"),"Tên hiển thị");
  PasswordField oldPassword=new PasswordField(),newPassword=new PasswordField();
  oldPassword.setPromptText("Mật khẩu hiện tại");newPassword.setPromptText("Mật khẩu mới (8–128 ký tự)");
  Button save=Ui.button("Lưu tên",()->Ui.async(client.request("UPDATE_PROFILE",Json.obj("displayName",name.getText())),value->{
   if(disposed)return;
   user=value.getAsJsonObject();identity.setText(Ui.string(user,"display_name")+" · @"+Ui.string(user,"username"));Ui.info("Đã lưu tên.");
  }),"primary");
  Button password=Ui.button("Đổi mật khẩu",()->Ui.async(client.request("CHANGE_PASSWORD",Json.obj("oldPassword",oldPassword.getText(),"newPassword",newPassword.getText())),value->{
   oldPassword.clear();newPassword.clear();Ui.info("Đã đổi mật khẩu.");
  }),null);
  VBox form=new VBox(12,Ui.label("@"+Ui.string(user,"username"),"section-title"),name,save,new Separator(),oldPassword,newPassword,password);
  form.setPadding(new javafx.geometry.Insets(20));form.setPrefWidth(420);dialog.getDialogPane().setContent(form);dialog.showAndWait();
 }
}

