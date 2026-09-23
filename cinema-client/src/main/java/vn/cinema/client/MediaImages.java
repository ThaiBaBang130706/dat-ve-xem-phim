package vn.cinema.client;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javax.imageio.ImageIO;
import vn.cinema.client.net.TcpClient;
import vn.cinema.common.Json;

public final class MediaImages {
 private final TcpClient client;
 private final Map<String,CompletableFuture<byte[]>> assets=new LinkedHashMap<>(32,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<String,CompletableFuture<byte[]>> entry){return size()>32;}
 };
 public MediaImages(TcpClient client){this.client=client;}
 public StackPane movie(JsonObject movie,boolean banner,double width,double height){return view(Json.str(movie,banner?"banner_url":"poster_url",""),Json.num(movie,"id",0),Ui.string(movie,"title"),width,height);}
 public StackPane view(String url,long id,String title,double width,double height){
  StackPane pane=PosterArt.create(id,title,width,height);pane.setAccessibleText(title);
  if(url==null || url.isBlank())return pane;
  if(url.startsWith("asset:")){
   CompletableFuture<byte[]> future=assets.computeIfAbsent(url,key->client.request("GET_IMAGE",Json.obj("id",key.substring(6))).thenApply(v->Base64.getDecoder().decode(v.getAsJsonObject().get("data").getAsString())));
   future.whenComplete((bytes,error)->Ui.run(()->{
    if(error!=null){assets.remove(url);failed(pane);return;}
    try{display(pane,new Image(new ByteArrayInputStream(bytes),width*2,height*2,true,true),width,height);}catch(Exception e){failed(pane);}
   }));
  }else try{
   URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null){failed(pane);return pane;}
   Image image=new Image(url,width*2,height*2,true,true,true);
   if(image.getProgress()>=1)display(pane,image,width,height);
   else image.progressProperty().addListener((o,a,b)->{if(b.doubleValue()>=1)display(pane,image,width,height);});
   image.errorProperty().addListener((o,a,b)->{if(b)failed(pane);});
  }catch(Exception e){failed(pane);}
  return pane;
 }
 private static void display(StackPane pane,Image image,double width,double height){
  if(image.isError() || image.getWidth()<=0 || image.getHeight()<=0){failed(pane);return;}
  double cropWidth=image.getWidth(),cropHeight=cropWidth*height/width;
  if(cropHeight>image.getHeight()){cropHeight=image.getHeight();cropWidth=cropHeight*width/height;}
  ImageView view=new ImageView(image);view.setViewport(new Rectangle2D((image.getWidth()-cropWidth)/2,(image.getHeight()-cropHeight)/2,cropWidth,cropHeight));view.setFitWidth(width);view.setFitHeight(height);view.setSmooth(true);pane.getChildren().setAll(view);
 }
 private static void failed(StackPane pane){Tooltip.install(pane,new Tooltip("Không tải được ảnh. Đang hiển thị ảnh dự phòng."));}
 /** Resize locally before upload to keep each JSON frame below the protocol limit. */
 public static String encodeUpload(Path file)throws Exception {
  if(Files.size(file)>20*1024*1024)throw new IllegalArgumentException("Chọn ảnh PNG/JPEG dưới 20 MB.");
  java.awt.image.BufferedImage source;
  try(var input=ImageIO.createImageInputStream(file.toFile())){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Không đọc được ảnh.");var reader=readers.next();
   try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if((long)w*h>40_000_000)throw new IllegalArgumentException("Ảnh quá lớn; chọn ảnh dưới 40 megapixel.");source=reader.read(0);}finally{reader.dispose();}
  }
  if(source==null)throw new IllegalArgumentException("Không đọc được ảnh.");
  for(int limit:new int[]{1400,1000,700,500}){
   double scale=Math.min(1.0,(double)limit/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,(int)(source.getWidth()*scale)),h=Math.max(1,(int)(source.getHeight()*scale));
   var resized=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=resized.createGraphics();
   try{g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}
   ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(resized,"jpeg",out);if(out.size()<=512*1024)return Base64.getEncoder().encodeToString(out.toByteArray());
  }
  throw new IllegalArgumentException("Ảnh vẫn quá lớn sau khi nén; hãy chọn ảnh nhỏ hơn.");
 }
 public static void trailer(CinemaApp app,String url){
  try{URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null)throw new IllegalArgumentException();}
  catch(Exception e){Ui.error("Chưa có đường dẫn trailer hợp lệ. Vui lòng liên hệ quản trị rạp.");return;}
  Dialog<Void> dialog=Ui.themed(new Dialog<>());dialog.setTitle("Xem trailer");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  Label message=Ui.label("Trailer phát trong trình duyệt. Nếu video không mở được, sao chép liên kết để thử lại.","muted");TextField link=Ui.field(url,"");link.setEditable(false);
  Runnable open=()->{try{app.getHostServices().showDocument(url);}catch(Exception e){message.setText("Không mở được trình duyệt. Anh có thể sao chép liên kết bên dưới.");}};
  Button copy=Ui.button("Sao chép liên kết",()->{ClipboardContent data=new ClipboardContent();data.putString(url);Clipboard.getSystemClipboard().setContent(data);message.setText("Đã sao chép liên kết trailer.");},null);
  VBox content=new VBox(12);String attribution=vn.cinema.common.DemoTrailers.notice(url);
  if(!attribution.isEmpty()){dialog.setTitle("Trailer minh hoạ");Label note=Ui.label(attribution,"muted");note.setWrapText(true);content.getChildren().add(note);}
  content.getChildren().addAll(message,link,new HBox(10,Ui.button("Mở trình duyệt",open,"primary"),copy));
  dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefWidth(600);dialog.setOnShown(e->open.run());dialog.showAndWait();
 }
}
