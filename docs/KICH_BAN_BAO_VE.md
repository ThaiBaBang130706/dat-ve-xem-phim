# Kịch bản bảo vệ — 10 đến 15 phút

Chuẩn bị server GUI + 2 client khách + 1 client admin trên cùng máy hoặc LAN. Chọn một suất tương lai và ghế chưa bán, chẳng hạn B3.

| Bước | Thao tác | Kết quả cần quan sát |
|---|---|---|
| 1 | Khởi động server, đăng nhập ba tài khoản | Server tăng số kết nối; hiện IP, tài khoản và quyền |
| 2 | Khách A và B cùng mở một suất | Hai sơ đồ nhận trạng thái từ server |
| 3 | A chọn B3 và bấm Giữ ghế | B3 thuộc phiên A; B nhận sự kiện TCP cập nhật ghế |
| 4 | B thử giữ B3 | Không đặt trùng; server kiểm tra và từ chối nếu có request tranh chấp |
| 5 | A xác nhận thanh toán demo | Server tạo đơn/vé trong transaction, B3 chuyển SOLD, A xem được mã QR |
| 6 | Admin xem Vé theo ghế / Đơn đặt vé | Có đơn vừa tạo, đúng khách, phim, phòng, ghế, giá |
| 7 | A huỷ trước giờ chiếu | Đơn/vé chuyển CANCELLED; ghế trở lại AVAILABLE; lịch sử vẫn giữ |
| 8 | A giữ C3 rồi đóng client | Server dọn ghế của kết nối; B nhận sơ đồ mới |
| 9 | A giữ một ghế và chờ hết 5 phút (nếu đủ giờ) | Scheduler thu hồi giữ ghế, báo EVENT_HOLD_EXPIRED |
| 10 | Admin tạo lịch chồng phòng với lịch cũ | Bị từ chối; đổi giờ hợp lệ thì lưu thành công |
| 11 | Dừng server trong GUI | Xác nhận, ngắt client, trả ghế; khởi động lại dữ liệu đã mua vẫn còn |

## Giải thích phần Lập trình mạng

- TCP ServerSocket; mỗi kết nối có luồng nhận và hàng đợi gửi riêng.
- JSON Lines UTF-8 có request ID để ghép response; event không có request ID.
- Heartbeat và timeout phát hiện kết nối chết.
- Server giữ session/token và kiểm quyền ADMIN ở backend; client không quyết định quyền.
- Khóa theo suất chiếu + transaction SQLite tránh hai người mua một ghế; unique constraint bảo vệ vé ACTIVE.
- requestId thanh toán giúp gửi lại yêu cầu mà không tạo đơn trùng.
- Revision của sơ đồ tránh áp dụng sự kiện cũ sau bản mới.
- Database chỉ ở server; client không sửa SQLite trực tiếp.

Có thể dùng Wireshark filter `tcp.port == 5000` để quan sát LOGIN, HOLD_SEATS, CONFIRM_BOOKING và EVENT_SEAT_UPDATE trong môi trường demo. Giao thức đồ án hiện là TCP thuần trong LAN, chưa có TLS; chỉ dùng tài khoản mẫu khi trình diễn bắt gói.

## Kiểm thử trong repo

CI chạy JDK 17 và 22: codec, tranh chấp ghế, hết hạn, rollback, phân quyền, API đọc, runtime start/stop, UI JavaFX với TCP thật, rồi smoke test JAR. Ảnh chụp giao diện JavaFX được xuất làm artifact để đối chiếu bản chạy thực tế.
