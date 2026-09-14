# Nội dung 13 slide CinemaBooking

Mở slides.html trong trình duyệt để trình chiếu. File có sơ đồ, màu ghế, các tình huống, thuật toán, dữ liệu và hướng dẫn demo; hoạt động không cần Internet. Có thể in thành PDF ngang 16:9. Bấm sửa tên nhóm ở trang đầu; trình duyệt lưu nội dung đó trên máy đang dùng.

| Slide | Ý chính | Gợi ý lời trình bày |
|---|---|---|
| 1 | Ghế B3 chỉ bán cho một người | Nhóm chọn đặt vé xem phim vì dễ quan sát xung đột giữa hai máy |
| 2 | Hai máy cùng bấm giữ | Màn hình có thể đang hiển thị dữ liệu cũ; server mới quyết định giữ ghế |
| 3 | Sơ đồ hoạt động | JavaFX dùng TCP 5000, Java ghi SQLite; Node chỉ đọc qua HTTP 5001 |
| 4 | Luồng đặt vé | Chọn khác với giữ; giữ khác với mua. Mỗi bước có kiểm tra lại |
| 5 | Chức năng server | Tài khoản/quyền, ghế/vé, lịch/vận hành |
| 6 | Chức năng client | Kết nối, chọn phim/suất/ghế, bộ đếm, cập nhật, vé/QR |
| 7 | Các tình huống | Tranh ghế, giữ một bộ ghế, hết hạn, mất mạng, thử thanh toán lại, sai quyền |
| 8 | Thuật toán giữ | Khoá theo suất, kiểm tra cả bộ, transaction và rollback |
| 9 | Thuật toán xác nhận | requestId chống lặp và UNIQUE bảo vệ vé ACTIVE |
| 10 | Dữ liệu | Tám bảng; B3 thuộc về suất chiếu; huỷ giữ lịch sử |
| 11 | Demo | Hai cửa sổ và dashboard; theo dõi B3 từ xanh đến cam/vàng, đỏ rồi xanh |
| 12 | Kiểm tra và giới hạn | Nêu kết quả Actions; nói rõ một server, chưa TLS, tiền/QR demo |
| 13 | Phân công | Gợi ý 2–3 SV; thay bằng tên và phần việc thực tế |

Không ghi “đã chạy ổn định trên nhiều máy” nếu nhóm chưa tự chạy LAN. Khi bảo vệ nên mở ứng dụng thật và tab Actions của repo thay vì chỉ đọc slide.
