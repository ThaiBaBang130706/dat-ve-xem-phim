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
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
 public void init(TcpClient client,Runnable dashboard){
  this.client=client;this.dashboard=dashboard;
  String[] names={"Tổng quan","Phim","Phòng chiếu","Suất chiếu","Tài khoản","Đơn vé","Nhật ký"};
  for(String name:names){Tab tab=new Tab(name);tab.setClosable(false);tabs.getTabs().add(tab);}
  tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->refresh());refresh();
 }
 @FXML private void openDashboard(){dashboard.run();}
 @FXML private void refresh(){
  int index=tabs.getSelectionModel().getSelectedIndex();if(index<0)return;
  Tab tab=tabs.getTabs().get(index);VBox body=new VBox(12);body.getStyleClass().add("admin-body");tab.setContent(body);
  String[] commands={"ADMIN_GET_STATS","ADMIN_LIST_MOVIES","ADMIN_LIST_ROOMS","ADMIN_LIST_SHOWS","ADMIN_LIST_USERS","ADMIN_LIST_BOOKINGS","ADMIN_LIST_LOGS"};
  Ui.async(client.request(commands[index],Json.obj()),value->{
   if(index==0){stats(body,value.getAsJsonObject());return;}
   String[][] columns={
    {},{"id","Mã","title","Tên phim","genre","Thể loại","duration_minutes","Phút","age_rating","Tuổi","active","Hoạt động"},
    {"id","Mã","name","Tên phòng","rows_count","Số hàng","cols_count","Ghế / hàng","active","Hoạt động"},
    {"id","Mã","title","Phim","room_name","Phòng","starts_at","Bắt đầu","price_vnd","Giá vé","sold","Đã bán","status","Trạng thái"},
    {"id","Mã","username","Tên đăng nhập","display_name","Tên hiển thị","role","Quyền","status","Trạng thái"},
    {"id","Mã","code","Mã vé","username","Khách","title","Phim","seats","Ghế","total_vnd","Tổng tiền","status","Trạng thái"},
    {"created_at","Thời gian","username","Tài khoản","action","Thao tác","detail","Chi tiết"}};
   TableView<JsonObject> table=Ui.table(value.getAsJsonArray(),columns[index]);
   HBox actions=new HBox(10);
   if(index>=1 && index<=3){
    actions.getChildren().add(Ui.button("Thêm mới",()->edit(index,null),"primary"));
    actions.getChildren().add(Ui.button("Sửa mục đã chọn",()->selected(table,row->edit(index,row)),null));
    String[] remove={"","ADMIN_DELETE_MOVIE","ADMIN_DELETE_ROOM","ADMIN_DELETE_SHOW"};
    actions.getChildren().add(Ui.button(index==3?"Huỷ suất chiếu":"Ngừng sử dụng",()->selected(table,row->{
     if(Ui.confirm(index==3?"Huỷ suất chiếu này?":"Ngừng sử dụng mục này? Dữ liệu lịch sử vẫn được giữ."))mutate(remove[index],Json.obj("id",Json.num(row,"id",0)));
    }),null));
   }
   if(index==3)actions.getChildren().add(Ui.button("Mở / khoá ghế",()->selected(table,this::seatForm),null));
   if(index==4)actions.getChildren().add(Ui.button("Khoá / mở tài khoản",()->selected(table,row->{
    String status="ACTIVE".equals(Ui.string(row,"status"))?"LOCKED":"ACTIVE";
    if(Ui.confirm("Đổi trạng thái @"+Ui.string(row,"username")+" thành "+status+"?"))mutate("ADMIN_SET_USER_STATUS",Json.obj("id",Json.num(row,"id",0),"status",status));
   }),null));
   if(index==5)actions.getChildren().add(Ui.button("Huỷ đơn vé",()->selected(table,row->{
    if(Ui.confirm("Huỷ đơn "+Ui.string(row,"code")+"?"))mutate("CANCEL_BOOKING",Json.obj("bookingId",Json.num(row,"id",0)));
   }),null));
   body.getChildren().addAll(actions,table);
  });
 }
 private void stats(VBox body,JsonObject data){
  FlowPane cards=new FlowPane(12,12);
  cards.getChildren().addAll(stat("Doanh thu demo",Ui.money(Json.num(data,"revenue",0))),stat("Đơn đã xác nhận",Ui.string(data,"bookings")),stat("Vé còn hiệu lực",Ui.string(data,"tickets")),stat("Ghế đang giữ",Ui.string(data,"heldSeats")),stat("Khách hàng",Ui.string(data,"users")));
  body.getChildren().addAll(cards,Ui.label("Doanh thu theo ngày · 7 ngày gần nhất","section-title"),Ui.table(data.getAsJsonArray("daily"),"day","Ngày","bookings","Số đơn","revenue","Doanh thu"));
 }
 private VBox stat(String label,String value){VBox box=new VBox(8,Ui.label(label,"muted"),Ui.label(value,"section-title"));box.getStyleClass().add("card");box.setPrefWidth(185);return box;}
 private void selected(TableView<JsonObject> table,Consumer<JsonObject> action){JsonObject row=table.getSelectionModel().getSelectedItem();if(row==null)Ui.info("Chọn một dòng trong bảng.");else action.accept(row);}
 private void mutate(String command,JsonObject data){Ui.async(client.request(command,data),value->refresh());}
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
  Dialog<ButtonType> dialog=dialog("Mở / khoá ghế · suất "+Ui.string(show,"id"));GridPane form=grid();
  TextField seat=Ui.field("","Ví dụ B3");ComboBox<String> status=new ComboBox<>();status.getItems().addAll("Trống — mở bán","Tạm khoá");status.getSelectionModel().selectFirst();
  form.addRow(0,Ui.label("Ghế",null),seat);form.addRow(1,Ui.label("Trạng thái ghế",null),status);
  dialog.getDialogPane().setContent(form);
  wireSave(dialog,"ADMIN_SET_SEAT_STATUS",()->Json.obj("showId",Json.num(show,"id",0),"seat",seat.getText().strip().toUpperCase(Locale.ROOT),"status",status.getSelectionModel().getSelectedIndex()==0?"AVAILABLE":"UNAVAILABLE"));
  dialog.showAndWait();
 }
 private record Choice(long id,String label){@Override public String toString(){return label;}}
 private ComboBox<Choice> choices(JsonArray items,String label,long selected){
  ComboBox<Choice> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);
  for(JsonElement e:items){JsonObject item=e.getAsJsonObject();if(Json.num(item,"active",0)==1){Choice choice=new Choice(Json.num(item,"id",0),Ui.string(item,label));box.getItems().add(choice);if(choice.id()==selected)box.setValue(choice);}}
  if(box.getValue()==null)box.getSelectionModel().selectFirst();return box;
 }
 private Dialog<ButtonType> dialog(String title){Dialog<ButtonType> d=new Dialog<>();d.setTitle(title);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);d.getDialogPane().setPrefWidth(650);return d;}
 private GridPane grid(){GridPane p=new GridPane();p.setHgap(16);p.setVgap(14);p.setPadding(new javafx.geometry.Insets(20));return p;}
 private void wireSave(Dialog<ButtonType> dialog,String command,Supplier<JsonObject> payload){
  Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);save.setText("Lưu");
  save.addEventFilter(ActionEvent.ACTION,event->{
   event.consume();JsonObject data;
   try{data=payload.get();}catch(Exception e){Ui.error("Kiểm tra lại số và định dạng ngày giờ: "+Ui.message(e));return;}
   save.setDisable(true);
   client.request(command,data).whenComplete((value,error)->Ui.run(()->{
    save.setDisable(false);if(error!=null)Ui.error(Ui.message(error));else{dialog.close();refresh();}
   }));
  });
 }
}
