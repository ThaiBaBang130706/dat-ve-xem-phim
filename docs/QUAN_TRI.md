# Giao diện quản trị rạp

## Đăng nhập đúng vai trò

Cùng chương trình JavaFX, nhưng sau đăng nhập có hai workspace riêng:

- admin / Admin@123: vào thẳng Quản trị rạp, không có chức năng mua vé cá nhân.
- user1 hoặc user2 / User@1234: giao diện khách hàng đặt vé.

Tài khoản admin còn nút sửa tên và đổi mật khẩu của chính mình ở thanh đầu trang. Không chọn vai trò bằng nút trên client: server trả quyền từ tài khoản đã xác thực.

## Các mục quản trị

| Mục | Thao tác |
|---|---|
| Tổng quan | Doanh thu demo, đơn xác nhận, vé còn hiệu lực, ghế giữ, số khách, doanh thu 7 ngày |
| Phim | Tìm, thêm, sửa, ngừng dùng hoặc mở lại bằng lưu bản sửa |
| Phòng chiếu | Tìm, thêm, sửa tên, cấu hình hàng/cột cho phòng chưa có lịch, ngừng dùng |
| Suất chiếu | Tìm/lọc ngày/trạng thái, tạo/sửa/huỷ lịch, chỉnh giá, kiểm tra trùng phòng |
| Sơ đồ ghế trong Suất chiếu | Xem ghế theo màu, mở/khoá ghế trống bằng cách bấm trực tiếp |
| Khách hàng | Tìm tài khoản, lọc trạng thái/ngày tạo, khoá/mở tài khoản; giữ các bảo vệ tài khoản admin |
| Đơn đặt vé | Tìm mã đơn/khách/phim/ghế, lọc trạng thái/ngày đặt, xem chi tiết, huỷ trước giờ chiếu |
| Vé theo ghế | Mỗi dòng là một ghế đã mua; tìm mã đơn hoặc nội dung QR demo, lọc ngày chiếu/trạng thái, xem đơn chứa vé |
| Nhật ký | Tìm/lọc thao tác theo dữ liệu đã tải, xem tài khoản và thời điểm |

Nút Tải lại dữ liệu lấy bản mới từ server. Bộ lọc áp dụng trên danh sách đã tải: tối đa 500 suất/đơn, 1.000 vé ghế và 200 nhật ký gần nhất. Các giới hạn này được hiển thị hoặc mô tả tại giao diện.

Huỷ từ tab Vé theo ghế sẽ **huỷ toàn bộ đơn chứa vé**, và có xác nhận nêu rõ trước khi thực hiện. Chưa có huỷ riêng một ghế trong đơn.

Ghế đang giữ hoặc đã bán không thể khoá/mở trực tiếp. Muốn huỷ vé, thực hiện ở Đơn đặt vé. Sơ đồ có nút Tải lại để xem trạng thái mới; server vẫn kiểm tra lại ghế khi lưu để tránh ghi đè ghế vừa được khách mua.

## Cập nhật bản ZIP đang dùng trên Windows

GitHub không tự cập nhật thư mục đã giải nén trên máy.

1. Dừng tất cả cửa sổ client và server trong IntelliJ.
2. Tải ZIP mới từ nhánh main và giải nén ra thư mục tạm.
3. Chép đè **cinema-client** và **cinema-server** từ ZIP mới vào đúng thư mục dự án đang chứa pom.xml gốc.
4. Giữ nguyên thư mục **data** của anh để giữ tài khoản, lịch chiếu và vé đã tạo. Giữ nguyên cấu hình IntelliJ đang dùng.
5. Reload Maven trong IntelliJ.
6. Chạy cấu hình build ở thư mục gốc, lệnh **clean install -DskipTests**, chờ BUILD SUCCESS.
7. Chạy lại cấu hình JAR server, sau đó Maven client với **javafx:run**.
8. Đăng nhập admin: tiêu đề cửa sổ phải là **CinemaBooking · Quản trị rạp** và có 8 mục quản trị trong sidebar.

Nếu thấy menu Phim đang chiếu và Vé của tôi khi đăng nhập admin, anh vẫn đang chạy bản client cũ. Kiểm tra working directory của cấu hình Maven có trỏ đúng thư mục vừa cập nhật không.

Server đã có cửa sổ JavaFX vận hành trong module cinema-operator, cấu hình 02_Server_GUI. Xem [hướng dẫn IntelliJ](CHAY_INTELLIJ.md).
