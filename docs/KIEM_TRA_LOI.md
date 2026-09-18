# Rà soát lỗi và hướng dẫn chạy — 18/09/2026

## Phát hiện và sửa

| Vấn đề | Ảnh hưởng | Cách sửa |
|---|---|---|
| README gộp yêu cầu Java và Node | Người dùng tưởng mọi chế độ đều cần npm/Node | Tách ba lộ trình: web VKU offline, JavaFX thật, Node dashboard tuỳ chọn |
| Thiếu chỉ dẫn khi Current File/Run bị mờ | Mở pom.xml nhưng không chạy được ứng dụng | Thêm cách chọn cấu hình có sẵn và tạo Maven configuration thủ công |
| Đường dẫn gốc và dấu nháy PowerShell | POM/JAR không tìm thấy, java không được nhận diện | Test-Path, JDK theo IDE, lệnh PowerShell có &, không nháy lồng |
| TcpClient phát callback ngắt kết nối trong catch và trong close | Cùng lỗi đọc socket có thể thông báo hai lần | Tập trung thông báo tại close(message), dùng AtomicBoolean để chỉ phát một lần |
| Reader chạy trước khi đăng ký heartbeat | Server đóng ngay có thể shutdown timer trước lệnh schedule, gây RejectedExecutionException lúc khởi tạo | Đăng ký heartbeat trước khi khởi động reader |

Thêm TcpClientTest: peer TCP thật gửi phản hồi sai định dạng; client phải báo ngắt đúng một lần, close lặp không báo thêm, yêu cầu sau đóng phải thất bại. Kiểm thử này không cần JavaFX GUI.

## Xác minh

- GitHub Actions kiểm tra Java 17 và 22: unit/integration tests, nạp JavaFX, server JAR qua TCP/HTTP.
- Dashboard có syntax checks và test HTTP/Socket.IO.
- Web VKU có 26 màn hình, bốn trạng thái, kiểm thử chuyển trạng thái và một số cặp màu tương phản.
- Xem lần chạy ứng với commit mới nhất ở tab Actions; không dùng trạng thái của commit cũ để kết luận bản mới đạt.

## Giới hạn còn lại

- Chưa thao tác trực tiếp IntelliJ/Windows trên máy người dùng; hướng dẫn được đối chiếu cấu hình .run và pom.xml trong repo.
- Chưa xác minh layout web bằng trình duyệt thực hoặc kiểm tra toàn bộ accessibility. Test Node chỉ render chuỗi và kiểm tra state.
- Web VKU chưa nối backend, thay đổi mất khi reload, player và vận hành node là mô phỏng.
- TCP/HTTP LAN chưa có TLS; payment/QR là demo, không phải hệ thống sản xuất.
- Seed không làm mới lịch cũ. Node không tự đọc .env; key thay đổi cần khởi động lại Node.

## Checklist thử trên máy

Theo README mục 5: hai khách tranh cùng ghế; thanh toán demo và xem đơn ở admin; huỷ; đóng client trả ghế; dừng/khởi động server; xác nhận dữ liệu đã lưu. Với lỗi còn gặp, lưu lệnh/cấu hình chạy, thông báo đầy đủ, đường dẫn DB và kết quả Test-NetConnection; không gửi mật khẩu hoặc dashboard.key.
