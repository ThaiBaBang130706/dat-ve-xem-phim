package vn.cinema.client.controller;

import com.google.gson.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.*;
import java.time.*;
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
 @FXML private VBox content;
 private CinemaApp app;private TcpClient client;private JsonObject user;private MediaImages images;
 private boolean disposed,busy;
 private long view,currentShow,lastRevision=-1,serverOffset,holdUntil;
 private JsonObject currentMap;
 private final Set<String> selected=new LinkedHashSet<>(),mine=new LinkedHashSet<>();
 private String paymentId;
 private GridPane seatGrid;private Label selectionLabel,countdownLabel;private Button holdButton,payButton,releaseButton;
 private Timeline ticker;
 public void init(CinemaApp app,TcpClient client,JsonObject user){
  this.app=app;this.client=client;this.user=user;images=new MediaImages(client);
  userLabel.setText(Ui.string(user,"display_name"));connectionLabel.setText("● "+client.host()+":"+client.port());
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
   content.getChildren().removeIf(node->"loading".equals(node.getId()));
   if(error!=null){notice.setText("Chưa tải được dữ liệu. Thử lại hoặc kiểm tra kết nối server.");Ui.error(Ui.message(error));}else success.accept(value);
  }));
 }
 private void leaveShow(){
  view++;long old=currentShow;currentShow=0;currentMap=null;lastRevision=-1;mine.clear();selected.clear();holdUntil=0;paymentId=null;busy=false;
  if(old>0){client.request("RELEASE_SEATS",Json.obj("showId",old));client.request("UNSUBSCRIBE_SHOW",Json.obj());}
  content.getChildren().clear();
 }
 @FXML public void home(){
  if(busy){Ui.info("Đợi yêu cầu đặt vé hiện tại hoàn tất.");return;}
  leaveShow();pageTitle.setText("Khám phá điện ảnh");notice.setText("Chọn phim, rạp và ngày chiếu để đặt vé.");
  TextField search=Ui.field("","Tìm phim, thể loại hoặc diễn viên…");search.setId("movieSearch");
  ComboBox<String> genres=new ComboBox<>();genres.getItems().add("Tất cả thể loại");genres.getSelectionModel().selectFirst();genres.setPrefWidth(170);genres.setId("genreFilter");
  ComboBox<String> status=new ComboBox<>();status.getItems().setAll("Tất cả phim","Đang chiếu","Sắp chiếu","Chưa có lịch chiếu","Đã ngừng chiếu");status.getSelectionModel().selectFirst();status.setId("screeningFilter");
  HBox filters=new HBox(10,search,genres,status,Ui.button("Tải lại",this::home,null));HBox.setHgrow(search,Priority.ALWAYS);
  Label loading=Ui.label("Đang tải danh mục phim…","muted");loading.setId("loading");
  VBox body=new VBox(24);FlowPane cards=new FlowPane(24,24);cards.setId("movieCards");
  ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);
  cards.prefWrapLengthProperty().bind(scroll.widthProperty().subtract(30));content.getChildren().addAll(filters,loading,scroll);
  load("GET_MOVIES",Json.obj(),value->{
   JsonArray movies=value.getAsJsonArray();
   if(!movies.isEmpty()){
    JsonObject featured=movies.get(0).getAsJsonObject();
    Label title=Ui.label(Ui.string(featured,"title"),"hero-title");title.setMaxWidth(500);
    Label desc=Ui.label(Ui.string(featured,"description"),"light-text");desc.setMaxWidth(520);desc.setMaxHeight(72);
    VBox copy=new VBox(14,Ui.label("NOIR SELECTION · HẸN NHAU Ở RẠP","eyebrow"),title,Ui.label(Ui.string(featured,"genre")+"  ·  "+Ui.string(featured,"duration_minutes")+" phút  ·  "+Ui.string(featured,"screening_label"),"muted"),desc,Ui.button("Xem lịch & đặt vé",()->detail(featured),"primary"));
    copy.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(copy,Priority.ALWAYS);
    boolean hasBanner=!Ui.string(featured,"banner_url").isBlank();
    HBox hero=new HBox(24,copy,images.movie(featured,hasBanner,hasBanner?280:170,hasBanner?190:255));hero.getStyleClass().add("hero");hero.setAlignment(Pos.CENTER_LEFT);body.getChildren().add(hero);
   }
   body.getChildren().addAll(Ui.label("Danh mục phim","section-title"),cards);
   TreeSet<String> genreSet=new TreeSet<>();for(JsonElement e:movies)genreSet.addAll(genreNames(e.getAsJsonObject()));genres.getItems().addAll(genreSet);
   Runnable filter=()->{
    cards.getChildren().clear();String term=search.getText().strip().toLowerCase(Locale.ROOT);
    for(JsonElement e:movies){JsonObject movie=e.getAsJsonObject();String title=Ui.string(movie,"title"),genre=Ui.string(movie,"genre");
     if(!(title+" "+genre+" "+Ui.string(movie,"cast_names")).toLowerCase(Locale.ROOT).contains(term) || genres.getSelectionModel().getSelectedIndex()>0&&!genreNames(movie).contains(genres.getValue()) || status.getSelectionModel().getSelectedIndex()>0&&!status.getValue().equals(Ui.string(movie,"screening_label")))continue;
     Label name=Ui.label(title,"section-title");name.setMinHeight(48);name.setMaxHeight(72);
     VBox card=new VBox(10,images.movie(movie,false,200,300),name,Ui.label(Ui.string(movie,"screening_label"),"eyebrow"),Ui.label(genre+" · "+Ui.string(movie,"duration_minutes")+" phút","muted"),Ui.label("Phân loại: "+Ui.string(movie,"age_rating"),"muted"),Ui.button("Xem lịch chiếu",()->detail(movie),"primary"));
     card.getStyleClass().add("movie-card");card.setPrefWidth(200);card.setMaxWidth(200);cards.getChildren().add(card);
    }
    if(cards.getChildren().isEmpty())cards.getChildren().add(Ui.label("Chưa có phim phù hợp. Thử từ khoá, thể loại hoặc trạng thái khác.","muted"));
   };
   search.textProperty().addListener((o,a,b)->filter.run());genres.valueProperty().addListener((o,a,b)->filter.run());status.valueProperty().addListener((o,a,b)->filter.run());filter.run();
  });
 }
 private static Set<String> genreNames(JsonObject movie){
  Set<String> names=new LinkedHashSet<>();if(movie.has("genres"))for(JsonElement e:movie.getAsJsonArray("genres"))names.add(Ui.string(e.getAsJsonObject(),"name"));
  if(names.isEmpty())names.addAll(Arrays.asList(Ui.string(movie,"genre").split(",\\s*")));return names;
 }
 private record Place(long id,String name){@Override public String toString(){return name;}}
 private void detail(JsonObject movie){
  leaveShow();pageTitle.setText(Ui.string(movie,"title"));notice.setText(Ui.string(movie,"genre")+" · "+Ui.string(movie,"duration_minutes")+" phút · "+Ui.string(movie,"age_rating"));
  VBox body=new VBox(16);ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);content.getChildren().add(scroll);
  body.getChildren().add(Ui.button("← Danh sách phim",this::home,null));
  if(!Ui.string(movie,"banner_url").isBlank())body.getChildren().add(images.movie(movie,true,820,205));
  VBox copy=new VBox(9,Ui.label(Ui.string(movie,"screening_label"),"eyebrow"),Ui.label(Ui.string(movie,"description"),null));copy.setMinWidth(250);HBox.setHgrow(copy,Priority.ALWAYS);
  String[][] details={{"Đạo diễn","director"},{"Diễn viên","cast_names"},{"Quốc gia","country"},{"Ngôn ngữ","language"},{"Phiên bản","presentation"},{"Khởi chiếu","release_date"},{"Ngừng chiếu","end_date"}};
  for(String[] field:details)if(!Ui.string(movie,field[1]).isBlank())copy.getChildren().add(Ui.label(field[0]+": "+Ui.string(movie,field[1]),"muted"));
  String trailerUrl=Ui.string(movie,"trailer_url"),trailerNotice=vn.cinema.common.DemoTrailers.notice(trailerUrl);
  Button trailer=Ui.button(trailerNotice.isEmpty()?"Xem trailer":"Xem trailer minh hoạ",()->MediaImages.trailer(app,trailerUrl),"primary");trailer.setId("trailerButton");trailer.setDisable(trailerUrl.isBlank());trailer.setTooltip(new Tooltip(trailer.isDisabled()?"Phim chưa có trailer":"Mở trailer trong trình duyệt"));copy.getChildren().add(trailer);
  if(!trailerNotice.isEmpty()){Label attribution=Ui.label(trailerNotice,"muted");attribution.setId("trailerNotice");attribution.setWrapText(true);copy.getChildren().add(attribution);}
  body.getChildren().addAll(new HBox(20,images.movie(movie,false,150,225),copy),Ui.label("Chọn rạp & suất chiếu","section-title"));
  ComboBox<Place> area=new ComboBox<>(),cinema=new ComboBox<>();area.setId("areaFilter");cinema.setId("cinemaFilter");area.setPrefWidth(170);cinema.setPrefWidth(230);
  DatePicker day=new DatePicker();day.setEditable(false);day.setPromptText("Tất cả ngày chiếu");day.setId("showDateFilter");day.setPrefWidth(170);
  FlowPane filters=new FlowPane(10,10,area,cinema,day,Ui.button("Bỏ lọc",()->{area.getSelectionModel().selectFirst();cinema.getSelectionModel().selectFirst();day.setValue(null);},null));body.getChildren().add(filters);
  VBox shows=new VBox(12);shows.setId("showCards");shows.getChildren().add(Ui.label("Đang tải lịch chiếu…","muted"));body.getChildren().add(shows);
  load("GET_CINEMAS",Json.obj(),places->load("GET_SHOWTIMES",Json.obj("movieId",Json.num(movie,"id",0)),value->{
   JsonArray cinemas=places.getAsJsonArray(),schedules=value.getAsJsonArray();area.getItems().add(new Place(0,"Tất cả khu vực"));
   Set<Long> seen=new HashSet<>();for(JsonElement e:cinemas){JsonObject c=e.getAsJsonObject();long id=Json.num(c,"area_id",0);if(seen.add(id))area.getItems().add(new Place(id,Ui.string(c,"area_name")));}area.getSelectionModel().selectFirst();
   Runnable render=()->{
    shows.getChildren().clear();long areaId=area.getValue()==null?0:area.getValue().id(),cinemaId=cinema.getValue()==null?0:cinema.getValue().id();
    for(JsonElement e:schedules){JsonObject show=e.getAsJsonObject();
     if(areaId>0 && areaId!=Json.num(show,"area_id",0) || cinemaId>0 && cinemaId!=Json.num(show,"cinema_id",0) || day.getValue()!=null && !day.getValue().equals(Instant.ofEpochMilli(Json.num(show,"starts_at",0)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()))continue;
     VBox info=new VBox(6,Ui.label(Ui.string(show,"cinema_name")+" · "+Ui.string(show,"area_name"),"section-title"),Ui.label(Ui.string(show,"address")+" · "+Ui.string(show,"phone"),"muted"),Ui.label(Ui.date(Json.num(show,"starts_at",0))+" / "+Ui.string(show,"room_name"),null));HBox.setHgrow(info,Priority.ALWAYS);
     HBox row=new HBox(14,info,new VBox(10,Ui.label(Ui.money(Json.num(show,"price_vnd",0)),null),Ui.button("Chọn ghế",()->seats(show),"primary")));row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("card");
     if(!Ui.string(show,"cinema_image_url").isBlank())row.getChildren().add(0,images.view(Ui.string(show,"cinema_image_url"),Json.num(show,"cinema_id",0),Ui.string(show,"cinema_name"),110,75));shows.getChildren().add(row);
    }
    if(shows.getChildren().isEmpty())shows.getChildren().add(Ui.label("Chưa có suất chiếu phù hợp với ngày và rạp đã chọn.","muted"));
   };
   Runnable populate=()->{cinema.getItems().setAll(new Place(0,"Tất cả rạp"));long id=area.getValue()==null?0:area.getValue().id();for(JsonElement e:cinemas){JsonObject c=e.getAsJsonObject();if(id==0 || id==Json.num(c,"area_id",0))cinema.getItems().add(new Place(Json.num(c,"id",0),Ui.string(c,"name")));}cinema.getSelectionModel().selectFirst();};
   area.valueProperty().addListener((o,a,b)->{populate.run();render.run();});cinema.valueProperty().addListener((o,a,b)->render.run());day.valueProperty().addListener((o,a,b)->render.run());populate.run();render.run();
  }));
 }
 private void seats(JsonObject show){
  leaveShow();currentShow=Json.num(show,"id",0);pageTitle.setText(Ui.string(show,"title"));notice.setText(Ui.string(show,"cinema_name")+" / "+Ui.string(show,"room_name")+" · "+Ui.date(Json.num(show,"starts_at",0)));
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
  Dialog<Void> dialog=Ui.themed(new Dialog<>());dialog.setTitle("Vé xem phim · "+Ui.string(ticket,"code"));dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
 @FXML public void logout(){
  if(busy){Ui.info("Đợi yêu cầu đặt vé hiện tại hoàn tất.");return;}
  if(!Ui.confirm("Đăng xuất? Ghế đang giữ sẽ được trả lại."))return;
  client.request("LOGOUT",Json.obj()).whenComplete((v,e)->Ui.run(()->{dispose();app.showLogin();}));
 }
}
