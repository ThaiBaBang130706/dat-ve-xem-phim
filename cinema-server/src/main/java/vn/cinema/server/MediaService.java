package vn.cinema.server;

import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.imageio.ImageIO;
import vn.cinema.common.Json;
import static vn.cinema.server.Database.*;
import static vn.cinema.server.Validation.*;

/** Images travel over the authenticated TCP connection, including across LAN machines. */
public final class MediaService {
 public static final int MAX_IMAGE_BYTES=512*1024;
 private final Database db;private final Clock clock;
 public MediaService(Database db,Clock clock){this.db=db;this.clock=clock;}
 public JsonObject upload(Session session,JsonObject data)throws Exception {
  db.read(c->AuthService.check(c,session,true));
  String encoded=Json.str(data,"data","");
  require(encoded.length()>0 && encoded.length()<=4*((MAX_IMAGE_BYTES+2)/3),"Ảnh tải lên tối đa 512 KB.");
  byte[] bytes;
  try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Dữ liệu ảnh không hợp lệ.");}
  require(bytes.length<=MAX_IMAGE_BYTES,"Ảnh tải lên tối đa 512 KB.");
  String mime;
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);require(readers.hasNext(),"Chọn ảnh PNG hoặc JPEG hợp lệ.");
   var reader=readers.next();
   try{
    reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    require(Set.of("png","jpeg","jpg").contains(format),"Chỉ hỗ trợ PNG và JPEG.");
    int width=reader.getWidth(0),height=reader.getHeight(0);
    require(width>0 && height>0 && width<=4096 && height<=4096 && (long)width*height<=12_000_000,"Kích thước ảnh quá lớn.");
    require(reader.read(0)!=null,"Không giải mã được ảnh.");mime=format.equals("png")?"image/png":"image/jpeg";
   }finally{reader.dispose();}
  }catch(IOException e){throw new IllegalArgumentException("Ảnh bị hỏng hoặc không đọc được.");}
  String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  return db.write(c->{AuthService.check(c,session,true);exec(c,"INSERT OR IGNORE INTO media_assets(id,mime_type,data,created_at) VALUES(?,?,?,?)",id,mime,encoded,clock.millis());return Json.obj("url","asset:"+id);});
 }
 public JsonObject image(String id)throws Exception {
  require(id.matches("[0-9a-f]{64}"),"Mã ảnh không hợp lệ.");
  return db.read(c->{JsonObject asset=one(c,"SELECT id,mime_type,data FROM media_assets WHERE id=?",id);require(asset!=null,"Không tìm thấy ảnh trên server.");return asset;});
 }
 public static String url(String value,boolean image){
  String text=value.strip();if(text.isEmpty())return text;
  if(image && text.matches("asset:[0-9a-f]{64}"))return text;
  require(text.length()<=2000,"Đường dẫn quá dài.");
  try{URI uri=URI.create(text);require(("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost()!=null && uri.getUserInfo()==null,"Dùng đường dẫn http:// hoặc https:// hợp lệ.");}
  catch(IllegalArgumentException e){throw new IllegalArgumentException("Dùng đường dẫn http:// hoặc https:// hợp lệ; ảnh trên máy phải tải lên server.");}
  return text;
 }
}
