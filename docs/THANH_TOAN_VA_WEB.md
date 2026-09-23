# Chạy các chức năng mới

## Web kết nối server thật

1. Build dự án Java bằng `mvn clean install` (hoặc 01_Build trong IntelliJ).
2. Khởi động Server GUI như trước, TCP 5000.
3. Tại thư mục gốc, dùng Node.js 22 trở lên: `node cinema-web/server.mjs`.
4. Mở http://localhost:8080. Máy LAN khác mở http://IP-MAY-CHU:8080.

Không cần npm install cho web mới. `index.html` vẫn là prototype cũ; mở bằng nhấp đúp không kết nối server. Gateway giữ một kết nối TCP riêng cho mỗi phiên trình duyệt. Java kiểm tra tài khoản, quyền, giá và tranh chấp ghế. Web đọc lại ghế mỗi 4 giây. Khi mất kết nối, đăng nhập lại và kiểm tra Vé & thanh toán trước khi đặt tiếp. Không tự phát lại lệnh thanh toán.

Biến môi trường cho Node: `CINEMA_TCP_HOST` (mặc định 127.0.0.1), `CINEMA_TCP_PORT` (5000), `WEB_PORT` (8080). Gateway không cho trình duyệt chọn tuỳ ý máy chủ TCP. Chỉ dùng mạng LAN tin cậy; nếu đưa web ra Internet, cần HTTPS reverse proxy, đặt `COOKIE_SECURE=true`, chuyển nguyên Host/Origin và không công khai TCP 5000.

## Phim, trailer, lịch và giờ hoạt động

Admin có thể sửa phim: URL poster/banner, URL trailer YouTube hoặc MP4/WebM, ngày khởi chiếu/ngừng chiếu. Phim mẫu là hư cấu nên không gán trailer của phim khác; nút trailer bị vô hiệu khi chưa có URL. Web phát YouTube bằng iframe privacy-enhanced, MP4 bằng video; JavaFX mở trailer trong trình duyệt. Poster tải lên JavaFX có thể hiển thị trên web qua GET_IMAGE.

Rạp có giờ mở/đóng dạng HH:mm. Giờ đóng nhỏ hơn giờ mở nghĩa là hoạt động qua nửa đêm; hai giờ bằng nhau nghĩa là 24 giờ. Server từ chối tạo/sửa suất chiếu ngoài khung hoạt động và không cho thay giờ rạp làm sai lịch đã mở. Toàn bộ ngày/giờ kinh doanh dùng Asia/Ho_Chi_Minh. Chọn rạp/ngày trong chi tiết phim để lọc lịch.

## Mã giảm giá và điểm

Admin → Khuyến mãi: mã, phần trăm giảm, mức giảm tối đa, giá trị đơn tối thiểu, ngày bắt đầu/kết thúc, tổng lượt, lượt mỗi khách, bật/tắt. Không có mã được tự kích hoạt trên dữ liệu thật.

Server tính giá; không tin số tiền từ trình duyệt. Giao dịch QR đang chờ giữ một lượt mã. Thành công cộng 1 điểm cho mỗi 10.000đ thực trả sau giảm (làm tròn xuống). Lịch sử điểm lưu trong SQLite; yêu cầu lặp không cộng hai lần. Huỷ vé demo hoàn lại lượt mã và trừ đúng điểm đã cộng. Điểm chưa có chức năng đổi tiền/quà. Chế độ demo cũng ghi điểm demo trong cùng dữ liệu, vì vậy dùng database riêng để thử nghiệm trước khi vận hành thật.

## QR payOS

Mã đã tích hợp API chính thức: https://payos.vn/docs/api/. Dùng tạo link và truy vấn trạng thái qua HTTPS; server chủ động đối soát nên không cần đưa cổng LAN ra Internet để nhận webhook. Cần Internet trên máy Java và thiết bị mở payOS.

Đặt trên tiến trình **Java server**, hoặc Environment variables của cấu hình `02_Server_GUI`:

- `PAYOS_CLIENT_ID`
- `PAYOS_API_KEY`
- `PAYOS_CHECKSUM_KEY`
- `PAYOS_RETURN_URL`: URL web của bạn dùng để quay lại sau thanh toán.
- `PAYOS_CANCEL_URL`: URL web của bạn dùng khi rời trang thanh toán.

Không gửi khoá trong chat, không commit khoá hoặc `.env` vào GitHub. Chưa đủ biến: QR thật bị tắt, giao diện ghi rõ thanh toán demo. Đủ biến: server chặn CONFIRM_BOOKING demo; khách chỉ tạo QR qua payOS. JavaFX vẽ QR từ chuỗi payOS trả về, web mở trang QR chính thức của payOS.

Server tạo chữ ký HMAC-SHA256 cho yêu cầu tạo link. Kết quả returnUrl hoặc thao tác quét QR không xác nhận đã nhận tiền. Chỉ tra cứu qua API payOS bằng khoá server, đối chiếu orderCode, amount, amountPaid và trạng thái PAID mới tạo vé/cộng điểm trong một transaction.

Giữ ghế QR không phụ thuộc kết nối khách, tồn tại sau khi khởi động lại. QR hết hạn trong 5 phút (hoặc trước giờ chiếu); ghế có thêm tối đa 60 giây chờ đối soát. Giao dịch trả tiền nhưng không còn ghế hoặc số tiền không đúng chuyển REVIEW, không xuất vé. Admin → Thanh toán QR kiểm tra các trường hợp này và xử lý với khách/nhà cung cấp. Huỷ vé đã thu tiền thật bị chặn để tránh báo đã hoàn tiền trong khi chưa hoàn. Chưa có API hoàn tiền tự động.

Server đối soát tự động giao dịch chờ/hết hạn trong 7 ngày, mỗi đợt tối đa 20 giao dịch; khách có thể bấm kiểm tra giao dịch của mình. Khi mất Internet, dữ liệu không tự chuyển PAID. Không đổi khoá kênh payOS khi còn đơn cần đối soát.

**Chưa xác thực giao dịch tiền thật:** cần khoá kênh của chủ rạp và chạy thử một giao dịch thực tế. Các kiểm thử sử dụng provider giả lập, không chuyển tiền.

## API TCP bổ sung

GET_PAYMENT_CONFIG, GET_PROMOTIONS, GET_LOYALTY, QUOTE_BOOKING, CREATE_PAYMENT, GET_PAYMENT, GET_MY_PAYMENTS; admin: ADMIN_LIST_PROMOTIONS, ADMIN_SAVE_PROMOTION, ADMIN_LIST_PAYMENTS. QUOTE_BOOKING/CREATE_PAYMENT nhận showId, seats, coupon; CREATE_PAYMENT thêm requestId ổn định khi thử lại. GET_PAYMENT nhận paymentId. CONFIRM_BOOKING nhận thêm coupon trong chế độ demo.

Migration version 2 bổ sung bảng và cột, giữ nguyên phim/vé hiện có. Sao lưu database khi server đã dừng trước khi triển khai.

Kiểm thử: `mvn verify`; `node --test cinema-web/server.test.mjs`; `node --check cinema-web/live.js`.
