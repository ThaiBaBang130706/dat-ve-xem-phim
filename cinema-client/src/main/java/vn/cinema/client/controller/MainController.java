package vn.cinema.client.controller;

import com.google.gson.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.*;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.fxml.*;
import javafx.geometry.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import vn.cinema.client.*;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.*;

public final class MainController {
 @FXML private Label userLabel,connectionLabel,pageTitle,notice;
 @FXML private Button adminButton;
 @FXML private VBox content;
 private CinemaApp app;private TcpClient client;private JsonObject user;
 private boolean disposed,busy;
 private long view,currentShow,lastRevision=-1,serverOffset,holdUntil;
 private JsonObject currentMap;
 private final Set<String> selected=new LinkedHashSet<>(),mine=new LinkedHashSet<>();
 private String paymentId;
 private GridPane seatGrid;private Label selectionLabel,countdownLabel;private Button holdButton,payButton,releaseButton;
 private Timeline ticker;
 public void init(CinemaApp app,TcpClient client,JsonObject user){
  this.app=app;this.client=client;this.user=user;
  userLabel.setText(Ui.string(user,"display_name"));connectionLabel.setText("● "+client.host()+":"+client.port());
  boolean admin="ADMIN".equals(Ui.string(user,"role"));adminButton.setVisible(admin);adminButton.setManaged(admin);
  client.onEvent(event->Ui.run(()->event(event)));
  client.onDisconnect(message->Ui.run(()->{if(!disposed){disposed=true;Ui.error(message);app.showLogin();}}));
  ticker=new Timeline(new KeyFrame(Duration.seconds(1),e->countdown()));ticker.setCycleCount(Animation.INDEFINITE);ticker.play();
  home();
 }
 public void dispose(){disposed=true;if(ticker!=null)ticker.stop();}
 private void event(Response event){
  if(disposed)return;
  switch(event.type()){
   case "EVENT_SEAT_UPDATE" -> {if(currentShow>0)applyMap(event.data().getAsJsonObject());}
   case "EVENT_HOLD_EXPIRED" -> {
    if(Json.num(event.data().getAsJsonObject(),"showId",0)==currentShow){paymentId=null;notice.setText("Đã hết thời gian giữ ghế. Chọn lại ghế để tiếp tục.");}
   }
   case "EVENT_SHOW_UPDATED" -> notice.setText("Lịch chiếu vừa thay đổi. Mở lại danh sách phim để xem lịch mới.");
   default -> {}
  }
 }
 private void load(String type,JsonObject data,Consumer<JsonElement> success){
  long expected=view;
  client.request(type,data).whenComplete((value,error)->Ui.run(()->{
   if(disposed || view!=expected)return;
   if(error!=null)Ui.error(Ui.message(error));else success.accept(value);
  }));
 }
 private void leaveShow(){
  view++;long old=currentShow;currentShow=0;currentMap=null;lastRevision=-1;mine.clear();selected.clear();holdUntil=0;paymentId=null;busy=false;
  if(old>0){client.request("RELEASE_SEATS",Json.obj("showId",old));client.request("UNSUBSCRIBE_SHOW",Json.obj());}
  content.getChildren().clear();
 }
 @FXML public void home(){
  if(busy){Ui.info("Đợi yêu cầu đặt vé hiện tại hoàn tất.");return;}
  leaveShow();pageTitle.setText("Tối nay xem gì?");notice.setText("Phim và lịch chiếu bên dưới là dữ liệu thực hành.");
  TextField search=Ui.field("","Tìm theo tên phim hoặc thể loại…");content.getChildren().add(search);
  FlowPane cards=new FlowPane(18,18);cards.setPrefWrapLength(860);
  ScrollPane scroll=new ScrollPane(cards);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);content.getChildren().add(scroll);
  load("GET_MOVIES",Json.obj(),value->{
   JsonArray movies=value.getAsJsonArray();
   Runnable filter=()->{
    cards.getChildren().clear();String term=search.getText().strip().toLowerCase(Locale.ROOT);
    for(JsonElement e:movies){
     JsonObject movie=e.getAsJsonObject();String title=Ui.string(movie,"title"),genre=Ui.string(movie,"genre");
     if(!(title+" "+genre).toLowerCase(Locale.ROOT).contains(term))continue;
     long id=Json.num(movie,"id",0);
     VBox poster=new VBox(14,Ui.label("CINEMA / "+String.format("%02d",id),"poster-kicker"),Ui.label(title,"poster-title"));
     poster.setPrefSize(235,210);poster.getStyleClass().addAll("poster","poster-"+(id%4));poster.setAlignment(Pos.BOTTOM_LEFT);
     VBox card=new VBox(10,poster,Ui.label(genre+" · "+Ui.string(movie,"duration_minutes")+" phút","muted"),Ui.label("Phân loại: "+Ui.string(movie,"age_rating"),"muted"),Ui.button("Xem lịch chiếu",()->detail(movie),"primary"));
     card.getStyleClass().add("movie-card");card.setPrefWidth(235);cards.getChildren().add(card);
    }
    if(cards.getChildren().isEmpty())cards.getChildren().add(Ui.label("Chưa có phim phù hợp với từ khoá này.","muted"));
   };
   search.textProperty().addListener((o,a,b)->filter.run());filter.run();
  });
 }
 private void detail(JsonObject movie){
  leaveShow();pageTitle.setText(Ui.string(movie,"title"));notice.setText(Ui.string(movie,"genre")+" · "+Ui.string(movie,"duration_minutes")+" phút · "+Ui.string(movie,"age_rating"));
  content.getChildren().addAll(Ui.button("← Danh sách phim",this::home,null),Ui.label(Ui.string(movie,"description"),null),Ui.label("Chọn suất chiếu","section-title"));
  VBox shows=new VBox(10);ScrollPane scroll=new ScrollPane(shows);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);content.getChildren().add(scroll);
  load("GET_SHOWTIMES",Json.obj("movieId",Json.num(movie,"id",0)),value->{
   if(value.getAsJsonArray().isEmpty())shows.getChildren().add(Ui.label("Phim chưa có suất chiếu sắp tới.","muted"));
   for(JsonElement e:value.getAsJsonArray()){
    JsonObject show=e.getAsJsonObject();
    Label label=Ui.label(Ui.date(Json.num(show,"starts_at",0))+"   /   "+Ui.string(show,"room_name"),"section-title");
    HBox row=new HBox(20,label,new Region(),Ui.label(Ui.money(Json.num(show,"price_vnd",0)),null),Ui.button("Chọn ghế",()->seats(show),"primary"));
    HBox.setHgrow(row.getChildren().get(1),Priority.ALWAYS);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("card");shows.getChildren().add(row);
   }
  });
 }
 private void seats(JsonObject show){
  leaveShow();currentShow=Json.num(show,"id",0);pageTitle.setText(Ui.string(show,"title"));notice.setText(Ui.string(show,"room_name")+" · "+Ui.date(Json.num(show,"starts_at",0)));
  Label screen=Ui.label("M À N   H Ì N H","screen");screen.setMaxWidth(Double.MAX_VALUE);screen.setAlignment(Pos.CENTER);
  seatGrid=new GridPane();seatGrid.setHgap(7);seatGrid.setVgap(7);seatGrid.setAlignment(Pos.CENTER);seatGrid.setPadding(new Insets(20));
  ScrollPane scroll=new ScrollPane(seatGrid);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);
  FlowPane legend=new FlowPane(15,8);
  legend.getChildren().addAll(Ui.label("● Trống","legend-available"),Ui.label("● Người khác giữ","legend-held"),Ui.label("● Bạn đang giữ","legend-mine"),Ui.label("● Đã bán","legend-sold"),Ui.label("● Tạm khoá","muted"));
  selectionLabel=Ui.label("Chọn tối đa 8 ghế.","section-title");countdownLabel=Ui.label("Ghế chỉ được giữ sau khi bấm Giữ ghế.","muted");
  holdButton=Ui.button("Giữ ghế · 5 phút",this::hold,"primary");
  payButton=Ui.button("Thanh toán mô phỏng",this::pay,"primary");
  releaseButton=Ui.button("Bỏ giữ ghế",this::release,null);
  content.getChildren().addAll(screen,scroll,legend,selectionLabel,countdownLabel,new HBox(10,holdButton,payButton,releaseButton),Ui.label("Thanh toán demo không thu tiền. Giá vé được tính tại server.","muted"));
  load("SUBSCRIBE_SHOW",Json.obj("showId",currentShow),value->applyMap(value.getAsJsonObject()));
  countdown();
 }
 private void applyMap(JsonObject map){
  if(currentShow==0 || Json.num(map.getAsJsonObject("show"),"id",0)!=currentShow)return;
  long revision=Json.num(map,"revision",0);if(revision<lastRevision)return;lastRevision=revision;currentMap=map;
  serverOffset=Json.num(map,"serverTime",System.currentTimeMillis())-System.currentTimeMillis();
  mine.clear();holdUntil=0;Set<String> valid=new HashSet<>();
  for(JsonElement e:map.getAsJsonArray("seats")){
   JsonObject seat=e.getAsJsonObject();String label=Ui.string(seat,"seat_label"),status=Ui.string(seat,"status");
   if(Json.num(seat,"is_mine",0)==1){mine.add(label);holdUntil=Json.num(seat,"hold_until",0);valid.add(label);}
   if(status.equals("AVAILABLE"))valid.add(label);
  }
  selected.retainAll(valid);renderSeats();
 }
 private void renderSeats(){
  if(currentMap==null)return;seatGrid.getChildren().clear();
  for(JsonElement e:currentMap.getAsJsonArray("seats")){
   JsonObject seat=e.getAsJsonObject();String label=Ui.string(seat,"seat_label"),status=Ui.string(seat,"status");
   String style=mine.contains(label)?"seat-mine":switch(status){case "AVAILABLE"->"seat-available";case "HELD"->"seat-held";case "SOLD"->"seat-sold";default->"seat-unavailable";};
   Button button=Ui.button(label,()->{
    if(selected.contains(label))selected.remove(label);
    else if(selected.size()<8)selected.add(label);
    else {Ui.info("Một lần đặt tối đa 8 ghế.");return;}
    renderSeats();
   },style);
   button.getStyleClass().add("seat");if(selected.contains(label))button.getStyleClass().add("seat-selected");
   button.setDisable(busy || !status.equals("AVAILABLE") && !mine.contains(label));
   seatGrid.add(button,Integer.parseInt(label.substring(1))-1,label.charAt(0)-'A');
  }
  long price=Json.num(currentMap.getAsJsonObject("show"),"price_vnd",0);
  selectionLabel.setText(selected.isEmpty()?"Chọn tối đa 8 ghế.":"Ghế "+String.join(", ",selected)+"  ·  "+Ui.money(price*selected.size()));
  countdown();
 }
 private void countdown(){
  if(currentShow==0 || countdownLabel==null)return;
  long seconds=Math.max(0,(holdUntil-System.currentTimeMillis()-serverOffset+999)/1000);
  boolean open=currentMap!=null && "OPEN".equals(Ui.string(currentMap.getAsJsonObject("show"),"status")) && Json.num(currentMap.getAsJsonObject("show"),"starts_at",0)>System.currentTimeMillis()+serverOffset;
  countdownLabel.setText(!open?"Suất chiếu không còn mở bán.":seconds>0?"Còn "+String.format("%02d:%02d",seconds/60,seconds%60)+" để xác nhận vé.":"Chọn ghế và bấm Giữ ghế để bắt đầu.");
  holdButton.setDisable(busy || !open || selected.isEmpty());
  payButton.setDisable(busy || !open || seconds==0 || mine.isEmpty() || !mine.equals(selected));
  releaseButton.setDisable(busy || mine.isEmpty());
 }
 private void transaction(String type,JsonObject data,Consumer<JsonElement> success){
  busy=true;renderSeats();long expected=view;
  client.request(type,data).whenComplete((value,error)->Ui.run(()->{
   if(disposed || expected!=view)return;busy=false;
   if(error!=null){Ui.error(Ui.message(error));renderSeats();}
   else success.accept(value);
  }));
 }
 private void hold(){
  paymentId=null;
  transaction("HOLD_SEATS",Json.obj("showId",currentShow,"seats",selected),value->{applyMap(value.getAsJsonObject());notice.setText("Đã giữ ghế. Xác nhận thanh toán trước khi bộ đếm về 00:00.");});
 }
 private void release(){transaction("RELEASE_SEATS",Json.obj("showId",currentShow),value->{selected.clear();paymentId=null;applyMap(value.getAsJsonObject());});}
 private void pay(){
  if(!Ui.confirm("Xác nhận thanh toán mô phỏng cho ghế "+String.join(", ",mine)+"?\nKhông có giao dịch tiền thật."))return;
  if(paymentId==null)paymentId=UUID.randomUUID().toString();
  transaction("CONFIRM_BOOKING",Json.obj("showId",currentShow,"seats",mine,"requestId",paymentId),value->{
   JsonObject booking=value.getAsJsonObject();paymentId=null;ticketDialog(booking);tickets();
  });
 }
 @FXML public void tickets(){
  if(busy){Ui.info("Đợi yêu cầu đặt vé hiện tại hoàn tất.");return;}
  leaveShow();pageTitle.setText("Vé của tôi");notice.setText("Có thể huỷ vé trước giờ chiếu. Mã QR dùng trong bản demo.");
  load("GET_MY_TICKETS",Json.obj(),value->{
   TableView<JsonObject> table=Ui.table(value.getAsJsonArray(),"code","Mã vé","title","Phim","room_name","Phòng","starts_at","Giờ chiếu","seats","Ghế","total_vnd","Tổng tiền","status","Trạng thái");
   Button show=Ui.button("Xem vé / QR",()->{
    JsonObject row=table.getSelectionModel().getSelectedItem();
    if(row==null){Ui.info("Chọn một vé trong bảng.");return;}
    load("GET_TICKET",Json.obj("bookingId",Json.num(row,"id",0)),ticket->ticketDialog(ticket.getAsJsonObject()));
   },"primary");
   Button cancel=Ui.button("Huỷ vé đã chọn",()->{
    JsonObject row=table.getSelectionModel().getSelectedItem();
    if(row==null){Ui.info("Chọn một vé trong bảng.");return;}
    if(Ui.confirm("Huỷ vé "+Ui.string(row,"code")+"?"))load("CANCEL_BOOKING",Json.obj("bookingId",Json.num(row,"id",0)),r->tickets());
   },null);
   content.getChildren().addAll(new HBox(12,show,cancel,Ui.button("Tải lại",this::tickets,null)),table);
  });
 }
 private void ticketDialog(JsonObject ticket){
  Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Vé xem phim · "+Ui.string(ticket,"code"));dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  JsonArray tickets=ticket.getAsJsonArray("tickets");JsonObject first=tickets.isEmpty()?ticket:tickets.get(0).getAsJsonObject();
  List<String> labels=new ArrayList<>();tickets.forEach(e->labels.add(Ui.string(e.getAsJsonObject(),"seat_label")));
  VBox box=new VBox(12,Ui.label(Ui.string(ticket,"code"),"section-title"),Ui.label(Ui.string(first,"movie_title"),null),Ui.label(Ui.string(first,"room_name")+" · "+Ui.date(Json.num(first,"starts_at",0)),null),Ui.label("Ghế: "+String.join(", ",labels),null),Ui.label(Ui.money(Json.num(ticket,"total_vnd",0))+" · "+Ui.string(ticket,"status"),null));
  if("CONFIRMED".equals(Ui.string(ticket,"status")))try{
   BitMatrix matrix=new MultiFormatWriter().encode(Ui.string(ticket,"qrText"),BarcodeFormat.QR_CODE,220,220);
   WritableImage image=new WritableImage(220,220);
   for(int y=0;y<220;y++)for(int x=0;x<220;x++)image.getPixelWriter().setColor(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
   box.getChildren().add(new ImageView(image));
  }catch(WriterException e){box.getChildren().add(Ui.label(Ui.string(ticket,"qrText"),null));}
  box.getChildren().add(Ui.label("Vé demo · không dùng để vào rạp thương mại.","muted"));box.setPadding(new Insets(20));dialog.getDialogPane().setContent(box);dialog.showAndWait();
 }
 @FXML public void account(){
  if(busy)return;leaveShow();pageTitle.setText("Tài khoản");notice.setText("Cập nhật tên hiển thị và mật khẩu.");
  load("GET_PROFILE",Json.obj(),value->{
   user=value.getAsJsonObject();TextField name=Ui.field(Ui.string(user,"display_name"),"Tên hiển thị");
   PasswordField old=new PasswordField(),next=new PasswordField();old.setPromptText("Mật khẩu hiện tại");next.setPromptText("Mật khẩu mới (ít nhất 8 ký tự)");
   VBox form=new VBox(14,Ui.label("@"+Ui.string(user,"username"),"section-title"),name,Ui.button("Lưu tên hiển thị",()->load("UPDATE_PROFILE",Json.obj("displayName",name.getText()),r->{user=r.getAsJsonObject();userLabel.setText(Ui.string(user,"display_name"));Ui.info("Đã cập nhật tên.");}),"primary"),new Separator(),old,next,Ui.button("Đổi mật khẩu",()->load("CHANGE_PASSWORD",Json.obj("oldPassword",old.getText(),"newPassword",next.getText()),r->{old.clear();next.clear();Ui.info("Đã đổi mật khẩu.");}),null));
   form.setMaxWidth(480);form.getStyleClass().add("card");content.getChildren().add(form);
  });
 }
 @FXML public void administration(){
  if(busy)return;leaveShow();pageTitle.setText("Quản lý rạp");notice.setText("Các thay đổi được kiểm tra tại server trước khi lưu.");
  try{
   FXMLLoader loader=new FXMLLoader(getClass().getResource("/vn/cinema/client/admin.fxml"));Parent root=loader.load();
   loader.<AdminController>getController().init(client,()->app.openDashboard(client.host()));VBox.setVgrow(root,Priority.ALWAYS);content.getChildren().add(root);
  }catch(Exception e){Ui.error("Không mở được màn hình quản trị: "+e.getMessage());}
 }
 @FXML public void logout(){
  if(busy){Ui.info("Đợi yêu cầu đặt vé hiện tại hoàn tất.");return;}
  if(!Ui.confirm("Đăng xuất? Ghế đang giữ sẽ được trả lại."))return;
  client.request("LOGOUT",Json.obj()).whenComplete((v,e)->Ui.run(()->{dispose();app.showLogin();}));
 }
}
