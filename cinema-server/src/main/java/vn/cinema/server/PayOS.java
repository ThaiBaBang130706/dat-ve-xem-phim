package vn.cinema.server;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import vn.cinema.common.Json;
import static vn.cinema.server.Validation.require;

/** payOS payment-requests API. Credentials never travel to clients or logs. */
public class PayOS {
 private final String clientId,apiKey,checksum,returnUrl,cancelUrl;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public PayOS(){this(System.getenv());}
 PayOS(Map<String,String> env){clientId=env.getOrDefault("PAYOS_CLIENT_ID","");apiKey=env.getOrDefault("PAYOS_API_KEY","");checksum=env.getOrDefault("PAYOS_CHECKSUM_KEY","");returnUrl=env.getOrDefault("PAYOS_RETURN_URL","");cancelUrl=env.getOrDefault("PAYOS_CANCEL_URL","");}
 public boolean enabled(){return !clientId.isBlank()&&!apiKey.isBlank()&&!checksum.isBlank()&&validUrl(returnUrl)&&validUrl(cancelUrl);}
 private static boolean validUrl(String value){try{URI u=URI.create(value);return Set.of("https","http").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
 static String signature(String data,String key)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));}
 public JsonObject create(long id,long amount,long expiry)throws Exception {
  require(enabled(),"QR thật chưa được cấu hình trên server.");
  String description="CINEMA";
  JsonObject d=Json.obj("orderCode",id,"amount",amount,"description",description,"cancelUrl",cancelUrl,"returnUrl",returnUrl,"expiredAt",expiry/1000);
  d.addProperty("signature",signature("amount="+amount+"&cancelUrl="+cancelUrl+"&description="+description+"&orderCode="+id+"&returnUrl="+returnUrl,checksum));
  return call("",d);
 }
 public JsonObject get(long id)throws Exception{return call("/"+id,null);}
 private JsonObject call(String path,JsonObject body)throws Exception {
  require(enabled(),"QR thật chưa được cấu hình trên server.");
  HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("https://api-merchant.payos.vn/v2/payment-requests"+path)).timeout(Duration.ofSeconds(8)).header("x-client-id",clientId).header("x-api-key",apiKey).header("Content-Type","application/json");
  if(body!=null)b.POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(body)));else b.GET();
  HttpResponse<String> response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
  require(response.statusCode()==200,"Chưa liên lạc được payOS. Kiểm tra trạng thái trước khi tạo giao dịch khác.");
  JsonObject result=Json.GSON.fromJson(response.body(),JsonObject.class);
  require("00".equals(Json.str(result,"code","")),"payOS chưa chấp nhận yêu cầu. Kiểm tra cấu hình hoặc trạng thái đơn.");
  return result.getAsJsonObject("data");
 }
}
