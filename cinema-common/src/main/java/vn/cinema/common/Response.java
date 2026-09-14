package vn.cinema.common;
import com.google.gson.JsonElement;
public record Response(String id,String type,boolean success,String message,JsonElement data) {
 public static Response ok(Request r,Object data) {return new Response(r.id(),r.type().equals("PING")?"PONG":r.type(),true,"OK",Json.GSON.toJsonTree(data));}
 public static Response error(String id,String type,String message) {return new Response(id,type,false,message,Json.obj());}
 public static Response event(String type,Object data) {return new Response(null,type,true,"",Json.GSON.toJsonTree(data));}
}
