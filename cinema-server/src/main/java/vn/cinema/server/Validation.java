package vn.cinema.server;

import com.google.gson.*;
import vn.cinema.common.Json;

public final class Validation {
 private Validation() {}
 public static void require(boolean condition,String message) {
  if(!condition) throw new IllegalArgumentException(message);
 }
 public static String text(JsonObject data,String key,int min,int max) {
  String value=Json.str(data,key,"").strip();
  require(value.length()>=min && value.length()<=max,"Trường "+key+" cần "+min+"–"+max+" ký tự.");
  return value;
 }
 public static long number(JsonObject data,String key,long min,long max) {
  long value=Json.num(data,key,Long.MIN_VALUE);
  require(value>=min && value<=max,"Giá trị "+key+" không hợp lệ.");
  return value;
 }
}
