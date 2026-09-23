package vn.cinema.client.controller;

import com.google.gson.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.cinema.client.*;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.Json;

/** Scrollable catalogue forms shared by the administration sections. */
final class CatalogForms {
 private final TcpClient client;private final Runnable saved;private final BooleanSupplier disposed;
 private final List<Dialog<?>> dialogs;private final MediaImages images;
 CatalogForms(TcpClient client,Runnable saved,BooleanSupplier disposed,List<Dialog<?>> dialogs){this.client=client;this.saved=saved;this.disposed=disposed;this.dialogs=dialogs;images=new MediaImages(client);}
 private record Choice(long id,String label){@Override public String toString(){return label;}}
 private void fetch(String command,Consumer<JsonArray> action){client.request(command,Json.obj()).whenComplete((value,error)->Ui.run(()->{if(disposed.getAsBoolean())return;if(error!=null)Ui.error(Ui.message(error));else action.accept(value.getAsJsonArray());}));}
 private static ComboBox<Choice> choices(JsonArray items,String label,long id){
  ComboBox<Choice> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);
  for(JsonElement e:items){JsonObject item=e.getAsJsonObject();if(Json.num(item,"active",0)==1){Choice choice=new Choice(Json.num(item,"id",0),Ui.string(item,label));box.getItems().add(choice);if(choice.id()==id)box.setValue(choice);}}
  if(box.getValue()==null)box.getSelectionModel().selectFirst();return box;
 }
 void movie(JsonObject value){
  JsonObject row=value==null?Json.obj():value;
  fetch("ADMIN_LIST_GENRES",genres->{
   Form form=new Form(value==null?"Thêm phim":"Chỉnh sửa phim");
   TextField title=form.text("Tên phim",row,"title",""),duration=form.text("Thời lượng (phút)",row,"duration_minutes","110");
   ComboBox<String> rating=new ComboBox<>();rating.getItems().setAll("P","K","T13","T16","T18");rating.setValue(Json.str(row,"age_rating","P"));form.add("Giới hạn tuổi",rating);
   FlowPane choices=new FlowPane(12,10);List<CheckBox> checks=new ArrayList<>();Set<Long> selected=new HashSet<>();
   if(row.has("genres"))for(JsonElement e:row.getAsJsonArray("genres"))selected.add(Json.num(e.getAsJsonObject(),"id",0));
   for(JsonElement e:genres){JsonObject genre=e.getAsJsonObject();if(Json.num(genre,"active",0)!=1)continue;long id=Json.num(genre,"id",0);CheckBox check=new CheckBox(Ui.string(genre,"name"));check.setUserData(id);check.setSelected(selected.contains(id));checks.add(check);choices.getChildren().add(check);}
   choices.setPrefWrapLength(420);form.add("Thể loại (chọn nhiều)",choices);
   if(checks.isEmpty())form.add("",Ui.label("Thêm thể loại ở mục Thể loại trước khi lưu phim.","muted"));
   TextArea description=new TextArea(Ui.string(row,"description"));description.setWrapText(true);description.setPrefRowCount(3);form.add("Nội dung",description);
   TextField poster=form.image("Poster",row,"poster_url"),banner=form.image("Banner",row,"banner_url");
   TextField trailer=form.text("Liên kết trailer",row,"trailer_url","");trailer.setPromptText("https://www.youtube.com/watch?v=… hoặc liên kết video");
   DatePicker release=form.date("Ngày khởi chiếu",row,"release_date"),end=form.date("Ngày ngừng chiếu",row,"end_date");
   TextField director=form.text("Đạo diễn",row,"director",""),cast=form.text("Diễn viên",row,"cast_names",""),country=form.text("Quốc gia",row,"country",""),language=form.text("Ngôn ngữ",row,"language",""),presentation=form.text("Phụ đề / lồng tiếng",row,"presentation","");
   presentation.setPromptText("Ví dụ: 2D · Phụ đề tiếng Việt / Lồng tiếng Việt");
   form.show("ADMIN_SAVE_MOVIE",()->{
    if(title.getText().isBlank())throw new IllegalArgumentException("Nhập tên phim.");
    List<Long> ids=checks.stream().filter(CheckBox::isSelected).map(c->(Long)c.getUserData()).toList();if(ids.isEmpty())throw new IllegalArgumentException("Chọn ít nhất một thể loại.");
    if(release.getValue()!=null && end.getValue()!=null && end.getValue().isBefore(release.getValue()))throw new IllegalArgumentException("Ngày ngừng chiếu không được trước ngày khởi chiếu.");
    return Json.obj("id",Json.num(row,"id",0),"title",title.getText(),"duration_minutes",Long.parseLong(duration.getText().strip()),"age_rating",rating.getValue(),"genre_ids",ids,"description",description.getText(),"poster_url",poster.getText(),"banner_url",banner.getText(),"trailer_url",trailer.getText(),"release_date",day(release),"end_date",day(end),"director",director.getText(),"cast_names",cast.getText(),"country",country.getText(),"language",language.getText(),"presentation",presentation.getText());
   });
  });
 }
 void room(JsonObject value){
  JsonObject row=value==null?Json.obj():value;
  fetch("ADMIN_LIST_CINEMAS",cinemas->{
   Form form=new Form(value==null?"Thêm phòng chiếu":"Chỉnh sửa phòng");ComboBox<Choice> cinema=choices(cinemas,"name",Json.num(row,"cinema_id",0));form.add("Thuộc rạp",cinema);
   TextField name=form.text("Tên phòng (duy nhất)",row,"name",""),rows=form.text("Số hàng (1–12)",row,"rows_count","6"),cols=form.text("Ghế mỗi hàng (1–16)",row,"cols_count","8");
   form.show("ADMIN_SAVE_ROOM",()->Json.obj("id",Json.num(row,"id",0),"cinema_id",required(cinema,"rạp"),"name",name.getText(),"rows_count",Long.parseLong(rows.getText().strip()),"cols_count",Long.parseLong(cols.getText().strip())));
  });
 }
 void cinema(JsonObject value){
  JsonObject row=value==null?Json.obj():value;
  fetch("ADMIN_LIST_AREAS",areas->{
   Form form=new Form(value==null?"Thêm rạp":"Chỉnh sửa rạp");ComboBox<Choice> area=choices(areas,"name",Json.num(row,"area_id",0));form.add("Tỉnh / thành phố",area);
   TextField name=form.text("Tên rạp",row,"name",""),address=form.text("Địa chỉ",row,"address",""),phone=form.text("Số điện thoại",row,"phone",""),image=form.image("Ảnh rạp",row,"image_url");
   TextField opens=form.text("Giờ mở cửa (HH:mm)",row,"opens_at","08:00"),closes=form.text("Giờ đóng cửa (HH:mm)",row,"closes_at","23:59");
   form.show("ADMIN_SAVE_CINEMA",()->Json.obj("id",Json.num(row,"id",0),"area_id",required(area,"khu vực"),"name",name.getText(),"address",address.getText(),"phone",phone.getText(),"image_url",image.getText(),"opens_at",opens.getText(),"closes_at",closes.getText()));
  });
 }
 void lookup(JsonObject value,boolean genre){
  JsonObject row=value==null?Json.obj():value;Form form=new Form(genre?"Thể loại phim":"Khu vực / tỉnh thành");TextField name=form.text("Tên",row,"name","");
  form.show(genre?"ADMIN_SAVE_GENRE":"ADMIN_SAVE_AREA",()->Json.obj("id",Json.num(row,"id",0),"name",name.getText()));
 }
 private static long required(ComboBox<Choice> box,String label){if(box.getValue()==null)throw new IllegalArgumentException("Chọn "+label+" đang hoạt động.");return box.getValue().id();}
 private static String day(DatePicker date){return date.getValue()==null?"":date.getValue().toString();}
 private final class Form {
  final Dialog<ButtonType> dialog=Ui.themed(new Dialog<>());final GridPane grid=new GridPane();final Button save;int index,uploads;
  Form(String title){
   dialog.setTitle(title);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);save.setText("Lưu");
   grid.setHgap(18);grid.setVgap(14);grid.setPadding(new Insets(20));ColumnConstraints label=new ColumnConstraints(170);ColumnConstraints control=new ColumnConstraints();control.setHgrow(Priority.ALWAYS);control.setFillWidth(true);grid.getColumnConstraints().addAll(label,control);
   ScrollPane scroll=new ScrollPane(grid);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(520);dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().setPrefWidth(760);dialog.setResizable(true);dialogs.add(dialog);dialog.setOnHidden(e->dialogs.remove(dialog));
  }
  void add(String label,Node node){grid.addRow(index++,Ui.label(label,null),node);GridPane.setHgrow(node,Priority.ALWAYS);}
  TextField text(String label,JsonObject row,String key,String fallback){TextField field=Ui.field(Json.str(row,key,fallback),label);field.setId("catalog_"+key);add(label,field);return field;}
  DatePicker date(String label,JsonObject row,String key){String value=Ui.string(row,key);DatePicker picker=new DatePicker(value.isBlank()?null:LocalDate.parse(value));picker.setEditable(false);picker.setId("catalog_"+key);add(label,new HBox(8,picker,Ui.button("Xoá ngày",()->picker.setValue(null),null)));return picker;}
  TextField image(String label,JsonObject row,String key){
   TextField field=Ui.field(Ui.string(row,key),"https://… hoặc chọn ảnh từ máy");field.setId("catalog_"+key);VBox preview=new VBox();Label status=Ui.label("PNG/JPEG · ảnh được nén và lưu trên server.","muted");
   Button upload=Ui.button("Chọn / tải ảnh",()->{},null);
   upload.setOnAction(event->{
    FileChooser chooser=new FileChooser();chooser.setTitle("Chọn "+label.toLowerCase(Locale.ROOT));chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG / JPEG","*.png","*.jpg","*.jpeg","*.PNG","*.JPG","*.JPEG"));var file=chooser.showOpenDialog(dialog.getOwner());if(file==null)return;
    upload.setDisable(true);uploads++;save.setDisable(true);status.setText("Đang nén và tải ảnh lên server…");
    CompletableFuture.supplyAsync(()->{try{return MediaImages.encodeUpload(file.toPath());}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}}).thenCompose(data->client.request("ADMIN_UPLOAD_IMAGE",Json.obj("data",data))).whenComplete((result,error)->Ui.run(()->{
     uploads--;if(disposed.getAsBoolean() || !dialog.isShowing())return;upload.setDisable(false);save.setDisable(uploads>0);
     if(error!=null){status.setText("Tải ảnh chưa thành công.");Ui.error(Ui.message(error));}
     else{field.setText(Ui.string(result.getAsJsonObject(),"url"));status.setText("Đã tải ảnh lên server. Bấm Lưu để gắn ảnh vào mục này.");preview.getChildren().setAll(images.view(field.getText(),0,label,220,125));}
    }));
   });
   Button see=Ui.button("Xem ảnh",()->preview.getChildren().setAll(images.view(field.getText(),0,label,220,125)),null);
   add(label,new VBox(8,field,new HBox(8,upload,see,Ui.button("Xoá ảnh",()->{field.clear();preview.getChildren().clear();},null)),status,preview));return field;
  }
  void show(String command,Supplier<JsonObject> payload){
   save.addEventFilter(ActionEvent.ACTION,event->{event.consume();if(uploads>0)return;JsonObject data;
    try{data=payload.get();}catch(Exception e){Ui.error("Kiểm tra thông tin: "+Ui.message(e));return;}
    save.setDisable(true);client.request(command,data).whenComplete((value,error)->Ui.run(()->{if(disposed.getAsBoolean())return;save.setDisable(uploads>0);if(error!=null)Ui.error(Ui.message(error));else{dialog.close();saved.run();}}));
   });dialog.showAndWait();
  }
 }
}

