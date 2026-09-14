package vn.cinema.common;
import com.google.gson.JsonObject;
public record Request(String id,String type,String token,JsonObject data) {}
