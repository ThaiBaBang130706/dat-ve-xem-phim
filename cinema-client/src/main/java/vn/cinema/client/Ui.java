package vn.cinema.client;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.cinema.common.Json;

public final class Ui {
 private Ui(){}
 public static void run(Runnable r){if(Platform.isFxApplicationThread())r.run();else Platform.runLater(r);}
 public static void async(CompletableFuture<JsonElement> request,Consumer<JsonElement> success){
  request.whenComplete((value,error)->run(()->{if(error!=null)error(message(error));else success.accept(value);}));
 }
 public static String message(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage()==null?"Chưa xử lý được yêu cầu.":error.getMessage();}
 public static void error(String text){new Alert(Alert.AlertType.ERROR,text,ButtonType.OK).show();}
 public static void info(String text){new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK).show();}
 public static boolean confirm(String text){return new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.OK,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
 public static Label label(String text,String style){Label l=new Label(text);if(style!=null)l.getStyleClass().add(style);l.setWrapText(true);return l;}
 public static Button button(String text,Runnable action,String style){Button b=new Button(text);if(style!=null)b.getStyleClass().add(style);b.setOnAction(e->action.run());return b;}
 public static String money(long n){return String.format(Locale.forLanguageTag("vi-VN"),"%,d đ",n);}
 public static String date(long epoch){return Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));}
 public static String string(JsonObject row,String key){return Json.str(row,key,"");}
 public static TableView<JsonObject> table(JsonArray data,String... columns){
  TableView<JsonObject> table=new TableView<>();
  for(int i=0;i<columns.length;i+=2){
   String key=columns[i];TableColumn<JsonObject,String> column=new TableColumn<>(columns[i+1]);
   column.setCellValueFactory(cell->{
    JsonObject row=cell.getValue();String value=string(row,key);
    if(key.endsWith("_at") && !value.isBlank())value=date(row.get(key).getAsLong());
    if(Set.of("total_vnd","price_vnd","revenue").contains(key) && !value.isBlank())value=money(row.get(key).getAsLong());
    if(key.equals("status") || key.equals("role"))value=switch(value){case "ACTIVE"->"Hoạt động";case "LOCKED"->"Đã khoá";case "CONFIRMED"->"Đã xác nhận";case "CANCELLED"->"Đã huỷ";case "OPEN"->"Đang bán";case "ADMIN"->"Quản trị";case "USER"->"Khách hàng";default->value;};
    if(key.equals("active"))value=value.equals("1")?"Có":"Ngừng sử dụng";
    return new ReadOnlyStringWrapper(value);
   });
   column.setPrefWidth(key.equals("title")||key.equals("detail")?230:130);table.getColumns().add(column);
  }
  table.setItems(FXCollections.observableArrayList());data.forEach(e->table.getItems().add(e.getAsJsonObject()));
  table.setPlaceholder(new Label("Chưa có dữ liệu."));table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
  VBox.setVgrow(table,Priority.ALWAYS);return table;
 }
 public static TextField field(String value,String prompt){TextField f=new TextField(value);f.setPromptText(prompt);f.setMaxWidth(Double.MAX_VALUE);return f;}
}
