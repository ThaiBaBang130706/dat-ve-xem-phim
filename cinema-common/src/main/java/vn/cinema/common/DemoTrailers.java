package vn.cinema.common;

/** Public Blender sample trailers hosted by W3C; never presented as the fictional film's own trailer. */
public final class DemoTrailers {
 public static final String SINTEL="https://media.w3.org/2010/05/sintel/trailer.mp4";
 public static final String BUNNY="https://media.w3.org/2010/05/bunny/trailer.mp4";
 private DemoTrailers(){}
 public static String notice(String url){
  String source=SINTEL.equals(url)?"Sintel (teaser)":BUNNY.equals(url)?"Big Buck Bunny (trailer)":"";
  return source.isEmpty()?"":"Trailer minh hoạ: "+source+" · Blender Foundation. Video mẫu cho đồ án, không phải trailer của phim hư cấu này.";
 }
}
