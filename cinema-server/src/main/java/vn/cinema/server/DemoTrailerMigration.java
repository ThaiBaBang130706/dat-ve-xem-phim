package vn.cinema.server;

import java.sql.Connection;
import vn.cinema.common.DemoTrailers;
import static vn.cinema.server.Database.*;

final class DemoTrailerMigration {
 private DemoTrailerMigration(){}
 // Match the original seed description as well as the title, never an arbitrary movie ID.
 static final String DESCRIPTION="Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.";
 static void populate(Connection c)throws Exception {
  for(String title:new String[]{"Hẹn Nhau Ở Huế","Mùa Hè Của Chúng Ta","Bức Thư Chưa Gửi","Đội Bóng Xóm Nhỏ","Bên Kia Cầu Vồng","Một Ngày Thật Khác"})
   fill(c,title,DemoTrailers.BUNNY);
  for(String title:new String[]{"Chuyến Tàu Bình Minh","Mật Mã Đại Dương","Ngôi Nhà Cuối Phố","Hành Trình Sao Hoả"})
   fill(c,title,DemoTrailers.SINTEL);
 }
 private static void fill(Connection c,String title,String url)throws Exception {
  exec(c,"UPDATE movies SET trailer_url=? WHERE title=? AND description=? AND trim(trailer_url)=''",url,title,DESCRIPTION);
 }
}
