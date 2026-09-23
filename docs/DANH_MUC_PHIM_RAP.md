# Phim, hình ảnh và hệ thống rạp

Các chức năng này thuộc ứng dụng **JavaFX qua TCP/IP** (`cinema-client` và `cinema-server`). Chạy cấu hình **01_Build**, **02_Server_GUI**, **05_Admin** và **03_Client_A** trong IntelliJ như hướng dẫn ở `CHAY_INTELLIJ.md`.

## 1. Nhập dữ liệu bằng Admin

Đăng nhập `admin / Admin@123`, rồi thực hiện theo thứ tự:

1. **Khu vực → Thêm mới**: nhập tỉnh/thành phố cần dùng. Danh mục do admin quản lý, không phụ thuộc dịch vụ bản đồ bên ngoài.
2. **Rạp → Thêm mới**: chọn khu vực, nhập tên, địa chỉ, số điện thoại và ảnh rạp.
3. **Phòng chiếu → Thêm mới**: chọn rạp, nhập tên phòng và kích thước ghế. Mỗi rạp có nhiều phòng. Tên phòng hiện phải duy nhất trong hệ thống; có thể đặt `Hue-P1`, `DaNang-P1`.
4. **Thể loại → Thêm mới**: tạo hoặc sửa các thể loại. Khi đổi tên thể loại, nhãn của những phim liên quan được cập nhật cùng lúc.
5. **Phim → Thêm mới / Sửa mục đã chọn**: nhập tên, mô tả, thời lượng, giới hạn tuổi; đánh dấu một hoặc nhiều thể loại; điền đạo diễn, diễn viên, quốc gia, ngôn ngữ, phụ đề/lồng tiếng, ngày khởi chiếu và ngừng chiếu. Form cuộn được để xem toàn bộ trường.
6. **Suất chiếu → Thêm mới**: chọn phim và phòng (hiển thị cả tên rạp), nhập giờ Việt Nam và giá. Server kiểm tra khoảng ngày phát hành, trùng lịch, 15 phút dọn phòng và tình trạng ghế trước khi chấp nhận.

**Ngừng sử dụng** giữ lại lịch sử. Muốn mở lại một mục, chọn dòng đó, sửa và lưu. Không thể ngừng rạp còn phòng hoạt động, khu vực còn rạp hoạt động hoặc thể loại còn phim hoạt động.

## 2. Poster, banner và ảnh rạp

- Nhập URL `http://`/`https://`, hoặc bấm **Chọn / tải ảnh** để chọn PNG/JPEG từ máy admin.
- Ảnh từ máy được nén, gửi qua TCP và lưu trong SQLite của server. Bấm **Lưu** để gắn ảnh vào phim/rạp. Client ở máy khác nhận ảnh từ server, không cần sao chép ảnh thủ công.
- **Xem ảnh** xem trước URL hoặc ảnh vừa tải. **Xoá ảnh** bỏ liên kết khi lưu.
- Mỗi ảnh sau nén tối đa 512 KB; ảnh đầu vào tối đa 20 MB / 40 megapixel. Server chỉ nhận ảnh PNG/JPEG hợp lệ, tối đa 4096×4096 và 12 megapixel.
- Nếu ảnh trống, bị lỗi hoặc URL mất kết nối, giao diện dùng poster minh hoạ dự phòng. Poster thật được hiển thị trong thẻ phim; banner ở trang chủ và chi tiết; ảnh rạp ở dòng suất chiếu.
- Dữ liệu mẫu là phim hư cấu và rạp demo. Admin tự bổ sung hình ảnh phù hợp; chương trình không gắn poster thương mại vào tên phim hư cấu.

## 3. Trailer

10 phim hư cấu có sẵn được gắn luân phiên **2 video minh hoạ** từ Blender Foundation, do W3C lưu trữ:

| Video gốc | Phim mẫu sử dụng |
|---|---|
| [Big Buck Bunny — trailer](https://media.w3.org/2010/05/bunny/trailer.mp4) | Hẹn Nhau Ở Huế, Mùa Hè Của Chúng Ta, Bức Thư Chưa Gửi, Đội Bóng Xóm Nhỏ, Bên Kia Cầu Vồng, Một Ngày Thật Khác |
| [Sintel — teaser](https://media.w3.org/2010/05/sintel/trailer.mp4) | Chuyến Tàu Bình Minh, Mật Mã Đại Dương, Ngôi Nhà Cuối Phố, Hành Trình Sao Hoả |

Nguồn: [trang minh hoạ video của W3C](https://www.w3.org/2010/05/video/mediaevents.html), ghi công Blender Foundation. Ứng dụng chỉ liên kết đến video, không chép video vào repository. Nút **Xem trailer minh hoạ**, tên video gốc và thông báo cạnh nút/trong hộp thoại giúp phân biệt với trailer thật của phim.

**Cập nhật database cũ:** kéo mã mới, build và khởi động lại server. Migration phiên bản 2 chỉ điền trailer đang trống của phim có tên và mô tả khớp dữ liệu mẫu ban đầu. Giữ nguyên trailer đã nhập, phim đã đổi tên/mô tả, suất chiếu và vé. Không cần xoá database. Migration chạy một lần; sau đó admin có thể thay hoặc xoá trailer mà server không tự gắn lại. Database mới cũng có sẵn các video này.

Nhập liên kết YouTube hoặc URL video vào **Liên kết trailer**, sau đó lưu. Người dùng mở phim và bấm **Xem trailer** để phát bằng trình duyệt mặc định. Cửa sổ có **Mở trình duyệt** và **Sao chép liên kết** khi trình duyệt/video không mở được. Nếu chưa khai báo trailer, nút bị vô hiệu hoá và có chú thích. Trailer và ảnh URL ngoài cần mạng Internet; ảnh tải lên server vẫn xem được trong LAN.

## 4. Người dùng tìm và đặt vé

- Trang chủ tìm theo tên phim, thể loại hoặc diễn viên; lọc thể loại và trạng thái phát hành.
- Trang chi tiết hiển thị poster/banner, mô tả, thông tin phim, ngày phát hành và trailer.
- Phần lịch chiếu lọc đồng thời **Khu vực**, **Rạp** và **Ngày chiếu**. Chọn **Bỏ lọc** để xem mọi suất sắp tới của phim.
- Mỗi suất ghi rõ rạp, địa chỉ, điện thoại, khu vực, phòng, giờ chiếu và giá. Luồng chọn ghế, giữ ghế, xác nhận, QR và huỷ vé được giữ nguyên. Vé mới lưu kèm tên rạp/phòng tại thời điểm mua.

## 5. Quy tắc ngày và trạng thái

Toàn bộ lịch dùng múi giờ `Asia/Ho_Chi_Minh`.

| Trạng thái | Quy tắc |
|---|---|
| Sắp chiếu | Ngày khởi chiếu còn ở tương lai; hoặc chưa nhập ngày nhưng lịch chiếu đầu tiên còn lại ở một ngày tương lai |
| Đang chiếu | Đã tới ngày khởi chiếu và có suất còn diễn ra hoặc sắp diễn ra tại rạp/phòng đang hoạt động |
| Chưa có lịch chiếu | Chưa hết khoảng phát hành nhưng chưa có lịch phù hợp |
| Đã ngừng chiếu | Đã qua ngày ngừng chiếu |
| Ngừng sử dụng | Admin đã ẩn phim |

Ngày khởi chiếu/ngừng chiếu có thể bỏ trống. Ngày ngừng chiếu được tính hết ngày đó. Không thể sửa ngày để đẩy một suất đang/sắp chiếu ra ngoài khoảng phát hành. Server kiểm tra lại khi giữ và xác nhận ghế. Phim sắp chiếu vẫn đặt trước được nếu có suất hợp lệ.

## 6. Database đang sử dụng

Khi khởi động, server chạy nâng cấp có transaction và ghi phiên bản vào `schema_migrations`. Những phim, tài khoản, vé, suất và mã phòng cũ được giữ nguyên. Phòng cũ được gắn với **Rạp hiện tại / Chưa phân khu**; thể loại dạng chữ được đưa vào `genres` và `movie_genres`. Admin có thể đổi tên rạp/khu vực; nếu rạp còn lịch đang/sắp chiếu, đổi địa chỉ hoặc chuyển khu vực sẽ bị từ chối.

Dùng đúng đường dẫn database cũ trong cấu hình server; không cần xoá database hay chạy lại seed. Với database mới, dữ liệu mẫu gồm hai rạp tại Huế/Đà Nẵng, các phim đang/sắp chiếu và lịch tương đối theo ngày khởi tạo. Lịch mẫu không tự dời ngày ở mỗi lần mở lại.

## 7. Kiểm tra trên hai máy

1. Chạy server, mở Admin và tải poster từ máy A, rồi lưu phim.
2. Máy B mở Client, kết nối IP LAN của A và đăng nhập `user1 / User@1234`. Mở lại danh sách phim: ảnh phải xuất hiện dù máy B không có file ảnh gốc.
3. Lọc khu vực/rạp/ngày và kiểm tra chỉ còn suất phù hợp; thử ngày không có lịch để xem thông báo trống.
4. Chạy hai client cùng chọn một ghế: chỉ một client giữ được ghế đó.
5. Khởi động lại server và kiểm tra poster, thông tin phim, lịch và vé vẫn tồn tại.

Chạy kiểm thử bằng `mvn verify` (Linux không có màn hình dùng `xvfb-run -a mvn verify`). CI của repository kiểm tra Java 17 và Java 22.
