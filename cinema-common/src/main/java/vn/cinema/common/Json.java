package vn.cinema.common;
import com.google.gson.*;
public final class Json {
 public static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
 private Json() {}
 public static JsonObject obj(Object... pairs) {
  JsonObject o=new JsonObject();
  for(int i=0;i<pairs.length;i+=2) o.add(pairs[i].toString(),GSON.toJsonTree(pairs[i+1]));
  return o;
 }
 public static String str(JsonObject o,String key,String fallback) {
  JsonElement e=o.get(key); return e==null||e.isJsonNull()?fallback:e.getAsString();
 }
 public static long num(JsonObject o,String key,long fallback) {
  JsonElement e=o.get(key); return e==null||e.isJsonNull()?fallback:e.getAsLong();
 }
}
