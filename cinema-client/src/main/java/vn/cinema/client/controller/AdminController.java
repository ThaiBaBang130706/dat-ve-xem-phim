package vn.cinema.client.controller;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.cinema.client.*;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.Json;

public final class AdminController {
 @FXML private TabPane tabs;
 @FXML private Button dashboardButton;
 private TcpClient client;
 private Runnable dashboard;
 private boolean disposed;
 private long refreshVersion;
 private final List<Dialog<?>> openDialogs=new ArrayList<>();
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
 public void init(TcpClient client,Runnable dashboard){
  this.client=client;this.dashboard=dashboard;
  String[] names={"Tổng quan","Phim","Phòng chiếu","Suất chiếu","Khách hàng","Đơn đặt vé","Vé theo ghế","Nhật ký"};
  for(String name:names){Tab tab=new Tab(name);tab.setClosable(false);tabs.getTabs().add(tab);}
  tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->refresh());refresh();
 }
 public void dispose(){disposed=true;refreshVersion++;for(Dialog<?> dialog:List.copyOf(openDialogs))dialog.close();}
 private void request(java.util.concurrent.CompletableFuture<JsonElement> future,Consumer<JsonElement> success){
  future.whenComplete((value,error)->Ui.run(()->{if(disposed)return;if(error!=null)Ui.error(Ui.message(error));else success.accept(value);}));
 }
 @FXML private void openDashboard(){dashboard.run();}
 @FXML private void refresh(){
  if(disposed)return;
  long version=++refreshVersion;
  int index=tabs.getSelectionModel().getSelectedIndex();if(index<0)return;
  Tab tab=tabs.getTabs().get(index);VBox body=new VBox(12);body.getStyleClass().add("admin-body");tab.setContent(body);
  String[] commands={"ADMIN_GET_STATS","ADMIN_LIST_MOVIES","ADMIN_LIST_ROOMS","ADMIN_LIST_SHOWS","ADMIN_LIST_USERS","ADMIN_LIST_BOOKINGS","ADMIN_LIST_TICKETS","ADMIN_LIST_LOGS"};
  request(client.request(commands[index],Json.obj()),value->{
   if(version!=refreshVersion)return;
   if(index==0){stats(body,value.getAsJsonObject());return;}
   String[][] columns={
    {},{"id","Mã","title","Tên phim","genre","Thể loại","duration_minutes","Phút","age_rating","Tuổi","active","Hoạt động"},
    {"id","Mã","name","Tên phòng","rows_count","Số hàng","cols_count","Ghế / hàng","active","Hoạt động"},
    {"id","Mã","title","Phim","room_name","Phòng","starts_at","Bắt đầu","price_vnd","Giá vé","sold","Đã bán","status","Trạng thái"},
    {"id","Mã","username","Tên đăng nhập","display_name","Tên hiển thị","role","Quyền","status","Trạng thái"},
    {"id","Mã","code","Mã vé","username","Khách","title","Phim","seats","Ghế","total_vnd","Tổng tiền","status","Trạng thái"},
    {"id","Mã vé ghế","code","Mã đơn","username","Khách","movie_title","Phim","room_name","Phòng","starts_at","Giờ chiếu","seat_label","Ghế","price_vnd","Giá vé","ticket_state","Trạng thái"},
    {"created_at","Thời gian","username","Tài khoản","action","Thao tác","detail","Chi tiết"}};
   JsonArray records=value.getAsJsonArray();
   if(index==6)for(JsonElement e:records)e.getAsJsonObject().addProperty("ticket_state","ACTIVE".equals(Ui.string(e.getAsJsonObject(),"status"))?"Còn hiệu lực":"Đã huỷ");
   TableView<JsonObject> table=Ui.table(records,columns[index]);
   HBox actions=new HBox(10);
   if(index>=1 && index<=3){
    actions.getChildren().add(Ui.button("Thêm mới",()->edit(index,null),"primary"));
    actions.getChildren().add(Ui.button("Sửa mục đã chọn",()->selected(table,row->edit(index,row)),null));
    String[] remove={"","ADMIN_DELETE_MOVIE","ADMIN_DELETE_ROOM","ADMIN_DELETE_SHOW"};
    actions.getChildren().add(Ui.button(index==3?"Huỷ suất chiếu":"Ngừng sử dụng",()->selected(table,row->{
     if(Ui.confirm(index==3?"Huỷ suất chiếu này?":"Ngừng sử dụng mục này? Dữ liệu lịch sử vẫn được giữ."))mutate(remove[index],Json.obj("id",Json.num(row,"id",0)));
    }),null));
   }
   if(index==3)actions.getChildren().add(Ui.button("Sơ đồ ghế / mở, khoá ghế",()->selected(table,this::seatForm),null));
   if(index==4)actions.getChildren().add(Ui.button("Khoá / mở tài khoản",()->selected(table,row->{
    String status="ACTIVE".equals(Ui.string(row,"status"))?"LOCKED":"ACTIVE";
    if(Ui.confirm("Đổi trạng thái @"+Ui.string(row,"username")+" thành "+status+"?"))mutate("ADMIN_SET_USER_STATUS",Json.obj("id",Json.num(row,"id",0),"status",status));
   }),null));
   if(index==5)actions.getChildren().add(Ui.button("Huỷ đơn vé",()->selected(table,row->{
    if(Ui.confirm("Huỷ đơn "+Ui.string(row,"code")+"?"))mutate("CANCEL_BOOKING",Json.obj("bookingId",Json.num(row,"id",0)));
   }),null));
   if(index==5 || index==6)actions.getChildren().add(Ui.button("Xem chi tiết đơn / vé",()->selected(table,row->bookingDetails(Json.num(row,index==5?"id":"booking_id",0))),"primary"));
   if(index==6)actions.getChildren().add(Ui.button("Huỷ đơn chứa vé",()->selected(table,row->{
    if(Ui.confirm("Huỷ toàn bộ đơn "+Ui.string(row,"code")+"? Tất cả vé trong đơn sẽ bị huỷ."))
     mutate("CANCEL_BOOKING",Json.obj("bookingId",Json.num(row,"booking_id",0)));
   }),null));
   body.getChildren().addAll(filters(table,records,index),actions,table,Ui.label(index==6?"Hiển thị tối đa 1.000 vé gần nhất. Huỷ vé tại đây áp dụng cho toàn bộ đơn.":index==3 || index==5?"Hiển thị tối đa 500 bản ghi gần nhất.":"Lọc trên dữ liệu đã tải; bấm Tải lại dữ liệu để cập nhật.","muted"));
  });
 }
 private void stats(VBox body,JsonObject data){
  FlowPane cards=new FlowPane(12,12);
  cards.getChildren().addAll(stat("Doanh thu demo",Ui.money(Json.num(data,"revenue",0))),stat("Đơn đã xác nhận",Ui.string(data,"bookings")),stat("Vé còn hiệu lực",Ui.string(data,"tickets")),stat("Ghế đang giữ",Ui.string(data,"heldSeats")),stat("Khách hàng",Ui.string(data,"users")));
  body.getChildren().addAll(cards,Ui.label("Doanh thu theo ngày · 7 ngày gần nhất","section-title"),Ui.table(data.getAsJsonArray("daily"),"day","Ngày","bookings","Số đơn","revenue","Doanh thu"));
 }
 private VBox stat(String label,String value){VBox box=new VBox(8,Ui.label(label,"muted"),Ui.label(value,"section-title"));box.getStyleClass().add("card");box.setPrefWidth(185);return box;}
 private void selected(TableView<JsonObject> table,Consumer<JsonObject> action){JsonObject row=table.getSelectionModel().getSelectedItem();if(row==null)Ui.info("Chọn một dòng trong bảng.");else action.accept(row);}
 private void mutate(String command,JsonObject data){request(client.request(command,data),value->refresh());}
 private void edit(int index,JsonObject row){
  if(index==3){showForm(row);return;}
  LinkedHashMap<String,String> fields=new LinkedHashMap<>();
  if(index==1){fields.put("title","Tên phim");fields.put("genre","Thể loại");fields.put("duration_minutes","Thời lượng (phút)");fields.put("age_rating","Phân loại: P / K / T13 / T16 / T18");fields.put("description","Nội dung phim");}
  else{fields.put("name","Tên phòng");fields.put("rows_count","Số hàng (1–12)");fields.put("cols_count","Số ghế mỗi hàng (1–16)");}
  Dialog<ButtonType> dialog=dialog(row==null?"Thêm mới":"Chỉnh sửa");
  GridPane form=grid();Map<String,TextInputControl> controls=new LinkedHashMap<>();int i=0;
  for(var entry:fields.entrySet()){
   String value=row==null?"":Ui.string(row,entry.getKey());
   if(row==null && entry.getKey().equals("age_rating"))value="P";
   if(row==null && entry.getKey().equals("duration_minutes"))value="110";
   TextInputControl input=entry.getKey().equals("description")?new TextArea(value):Ui.field(value,entry.getValue());
   if(input instanceof TextArea area){area.setPrefRowCount(3);area.setWrapText(true);}
   controls.put(entry.getKey(),input);form.addRow(i++,Ui.label(entry.getValue(),null),input);
  }
  dialog.getDialogPane().setContent(form);
  wireSave(dialog,index==1?"ADMIN_SAVE_MOVIE":"ADMIN_SAVE_ROOM",()->{
   JsonObject data=Json.obj("id",row==null?0:Json.num(row,"id",0));
   controls.forEach((key,input)->{
    if(Set.of("duration_minutes","rows_count","cols_count").contains(key))data.addProperty(key,Long.parseLong(input.getText().strip()));
    else data.addProperty(key,input.getText());
   });
   return data;
  });
  dialog.showAndWait();
 }
 private void showForm(JsonObject row){
  var movies=client.request("ADMIN_LIST_MOVIES",Json.obj());
  var rooms=client.request("ADMIN_LIST_ROOMS",Json.obj());
  movies.thenCombine(rooms,(m,r)->List.of(m.getAsJsonArray(),r.getAsJsonArray())).whenComplete((lists,error)->Ui.run(()->{
   if(disposed)return;
   if(error!=null){Ui.error(Ui.message(error));return;}
   ComboBox<Choice> movie=choices(lists.get(0),"title",row==null?0:Json.num(row,"movie_id",0));
   ComboBox<Choice> room=choices(lists.get(1),"name",row==null?0:Json.num(row,"room_id",0));
   TextField date=Ui.field(row==null?LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).withHour(10).withMinute(0).format(DATE):Instant.ofEpochMilli(Json.num(row,"starts_at",0)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(DATE),"yyyy-MM-dd HH:mm");
   TextField price=Ui.field(row==null?"75000":Ui.string(row,"price_vnd"),"Giá vé VND");
   Dialog<ButtonType> dialog=dialog(row==null?"Thêm suất chiếu":"Sửa suất chiếu");GridPane form=grid();
   form.addRow(0,Ui.label("Phim",null),movie);form.addRow(1,Ui.label("Phòng",null),room);
   form.addRow(2,Ui.label("Giờ Việt Nam (yyyy-MM-dd HH:mm)",null),date);form.addRow(3,Ui.label("Giá vé (VND)",null),price);
   dialog.getDialogPane().setContent(form);
   wireSave(dialog,"ADMIN_SAVE_SHOW",()->{
    if(movie.getValue()==null || room.getValue()==null)throw new IllegalArgumentException("Chọn phim và phòng chiếu.");
    long starts=LocalDateTime.parse(date.getText().strip(),DATE).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
    return Json.obj("id",row==null?0:Json.num(row,"id",0),"movie_id",movie.getValue().id(),"room_id",room.getValue().id(),"starts_at",starts,"price_vnd",Long.parseLong(price.getText().strip()));
   });dialog.showAndWait();
  }));
 }
 private void seatForm(JsonObject show){
  long showId=Json.num(show,"id",0);
  Dialog<ButtonType> dialog=dialog("Sơ đồ ghế · "+Ui.string(show,"room_name")+" · "+Ui.date(Json.num(show,"starts_at",0)));
  dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
  dialog.getDialogPane().setPrefWidth(850);
  GridPane seats=grid();seats.setHgap(8);seats.setVgap(8);
  ScrollPane scroll=new ScrollPane(seats);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(400);
  Label summary=Ui.label("Đang tải sơ đồ ghế…","muted");
  Runnable[] reload=new Runnable[1];
  reload[0]=()->request(client.request("GET_SEATMAP",Json.obj("showId",showId)),value->{
   if(!dialog.isShowing())return;
   seats.getChildren().clear();int available=0,held=0,sold=0,blocked=0;
   for(JsonElement e:value.getAsJsonObject().getAsJsonArray("seats")){
    JsonObject seat=e.getAsJsonObject();String label=Ui.string(seat,"seat_label"),status=Ui.string(seat,"status");
    switch(status){case "AVAILABLE"->available++;case "HELD"->held++;case "SOLD"->sold++;default->blocked++;}
    Button b=new Button(label);b.getStyleClass().addAll("seat",switch(status){case "AVAILABLE"->"seat-available";case "HELD"->"seat-held";case "SOLD"->"seat-sold";default->"seat-unavailable";});
    b.setDisable(status.equals("SOLD") || status.equals("HELD"));
    b.setTooltip(new Tooltip(status.equals("SOLD")?"Đã bán":status.equals("HELD")?"Khách đang giữ":status.equals("AVAILABLE")?"Bấm để tạm khoá":"Bấm để mở bán"));
    b.setOnAction(event->{
     String next=status.equals("AVAILABLE")?"UNAVAILABLE":"AVAILABLE";
     if(!Ui.confirm((next.equals("AVAILABLE")?"Mở bán":"Tạm khoá")+" ghế "+label+" của suất này?"))return;
     b.setDisable(true);
     client.request("ADMIN_SET_SEAT_STATUS",Json.obj("showId",showId,"seat",label,"status",next)).whenComplete((r,error)->Ui.run(()->{
      if(disposed || !dialog.isShowing())return;
      if(error!=null)Ui.error(Ui.message(error));reload[0].run();
     }));
    });
    seats.add(b,Integer.parseInt(label.substring(1))-1,label.charAt(0)-'A');
   }
   summary.setText("Trống: "+available+" · Đang giữ: "+held+" · Đã bán: "+sold+" · Tạm khoá: "+blocked);
  });
  VBox box=new VBox(12,Ui.label(Ui.string(show,"title"),"section-title"),summary,Ui.button("Tải lại sơ đồ",()->reload[0].run(),null),scroll,Ui.label("Bấm ghế trống để khoá hoặc ghế xám để mở bán. Ghế đang giữ/đã bán không thể đổi trạng thái trực tiếp.","muted"));
  dialog.getDialogPane().setContent(box);dialog.setOnShown(e->reload[0].run());dialog.showAndWait();
 }
 private void bookingDetails(long bookingId){
  request(client.request("GET_TICKET",Json.obj("bookingId",bookingId)),value->{
   JsonObject booking=value.getAsJsonObject();JsonArray tickets=booking.getAsJsonArray("tickets");
   for(JsonElement e:tickets)e.getAsJsonObject().addProperty("ticket_state","ACTIVE".equals(Ui.string(e.getAsJsonObject(),"status"))?"Còn hiệu lực":"Đã huỷ");
   Dialog<ButtonType> dialog=dialog("Chi tiết đơn "+Ui.string(booking,"code"));
   dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);dialog.getDialogPane().setPrefWidth(950);
   TableView<JsonObject> table=Ui.table(tickets,"id","Mã vé ghế","movie_title","Phim lúc mua","room_name","Phòng","starts_at","Giờ chiếu","seat_label","Ghế","price_vnd","Giá vé","ticket_state","Trạng thái");
   table.setPrefHeight(300);
   VBox box=new VBox(12,Ui.label(Ui.string(booking,"code")+" · Khách: "+Ui.string(booking,"username"),"section-title"),
    Ui.label("Tổng đơn: "+Ui.money(Json.num(booking,"total_vnd",0))+" · "+("CONFIRMED".equals(Ui.string(booking,"status"))?"Đã xác nhận":"Đã huỷ"),null),
    Ui.label("Đặt lúc "+Ui.date(Json.num(booking,"created_at",0))+" · Thanh toán mô phỏng","muted"),table);
   dialog.getDialogPane().setContent(box);dialog.showAndWait();
  });
 }
 private VBox filters(TableView<JsonObject> table,JsonArray records,int section){
  TextField search=Ui.field("","Tìm mã vé, phim, khách hàng, phòng hoặc ghế…");
  ComboBox<Choice> state=new ComboBox<>();state.getItems().add(new Choice(-1,"Tất cả trạng thái"));
  List<String> statuses=new ArrayList<>();
  for(JsonElement e:records){
   JsonObject row=e.getAsJsonObject();String value=row.has("status")?Ui.string(row,"status"):Ui.string(row,"active");
   if(!value.isBlank() && !statuses.contains(value))statuses.add(value);
  }
  for(int i=0;i<statuses.size();i++){
   String value=statuses.get(i),label=switch(value){case "ACTIVE"->section==6?"Vé còn hiệu lực":"Hoạt động";case "LOCKED"->"Đã khoá";case "CONFIRMED"->"Đã xác nhận";case "CANCELLED"->"Đã huỷ";case "OPEN"->"Đang bán";case "1"->"Đang sử dụng";case "0"->"Ngừng sử dụng";default->value;};
   state.getItems().add(new Choice(i,label));
  }
  state.getSelectionModel().selectFirst();
  DatePicker day=new DatePicker();day.setPromptText(section==3 || section==6?"Ngày chiếu":"Ngày tạo");
  day.setVisible(section>=3);day.setManaged(section>=3);
  Label count=Ui.label("","muted");
  Runnable apply=()->{
   String query=search.getText().strip().toLowerCase(Locale.ROOT).replace("cinema-demo:","");
   String status=state.getValue()==null || state.getValue().id()<0?"":statuses.get((int)state.getValue().id());
   table.getItems().clear();
   for(JsonElement e:records){
    JsonObject row=e.getAsJsonObject();String current=row.has("status")?Ui.string(row,"status"):Ui.string(row,"active");
    String dateKey=section==3 || section==6?"starts_at":"created_at";
    boolean sameDay=day.getValue()==null || row.has(dateKey) && Instant.ofEpochMilli(Json.num(row,dateKey,0)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().equals(day.getValue());
    if((query.isBlank() || row.toString().toLowerCase(Locale.ROOT).contains(query)) && (status.isBlank() || status.equals(current)) && sameDay)table.getItems().add(row);
   }
   count.setText(table.getItems().size()+" / "+records.size()+" bản ghi đã tải");
  };
  search.textProperty().addListener((o,b,c)->apply.run());state.valueProperty().addListener((o,b,c)->apply.run());day.valueProperty().addListener((o,b,c)->apply.run());
  HBox row=new HBox(10,search,state,day,Ui.button("Bỏ lọc",()->{search.clear();state.getSelectionModel().selectFirst();day.setValue(null);},null));
  HBox.setHgrow(search,Priority.ALWAYS);apply.run();return new VBox(8,row,count);
 }
 private record Choice(long id,String label){@Override public String toString(){return label;}}
 private ComboBox<Choice> choices(JsonArray items,String label,long selected){
  ComboBox<Choice> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);
  for(JsonElement e:items){JsonObject item=e.getAsJsonObject();if(Json.num(item,"active",0)==1){Choice choice=new Choice(Json.num(item,"id",0),Ui.string(item,label));box.getItems().add(choice);if(choice.id()==selected)box.setValue(choice);}}
  if(box.getValue()==null)box.getSelectionModel().selectFirst();return box;
 }
 private Dialog<ButtonType> dialog(String title){Dialog<ButtonType> d=new Dialog<>();d.setTitle(title);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);d.getDialogPane().setPrefWidth(650);openDialogs.add(d);d.setOnHidden(e->openDialogs.remove(d));return d;}
 private GridPane grid(){GridPane p=new GridPane();p.setHgap(16);p.setVgap(14);p.setPadding(new javafx.geometry.Insets(20));return p;}
 private void wireSave(Dialog<ButtonType> dialog,String command,Supplier<JsonObject> payload){
  Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);save.setText("Lưu");
  save.addEventFilter(ActionEvent.ACTION,event->{
   event.consume();JsonObject data;
   try{data=payload.get();}catch(Exception e){Ui.error("Kiểm tra lại số và định dạng ngày giờ: "+Ui.message(e));return;}
   save.setDisable(true);
   client.request(command,data).whenComplete((value,error)->Ui.run(()->{
    if(disposed)return;
    save.setDisable(false);if(error!=null)Ui.error(Ui.message(error));else{dialog.close();refresh();}
   }));
  });
 }
}
