# Chạy và trình diễn trên LAN

## Trong IntelliJ IDEA

1. Open thư mục repo, chọn JDK 17, chờ Maven tải các module.
2. Chạy Maven `install -DskipTests` ở root một lần.
3. Chạy class `vn.cinema.server.ServerMain`, working directory là **root repo**.
4. Client chạy bằng Maven `-pl cinema-client javafx:run` để JavaFX có cấu hình đúng hệ điều hành.
5. Muốn hai cửa sổ client thì chạy lệnh ở hai terminal.

Không cần cài SQLite server. JDBC tạo file database tự động.

## Máy server

Máy A chạy Java, sau đó chạy Node trong cinema-dashboard. Giữ hai terminal mở. Lấy IPv4 của card Wi-Fi/Ethernet đang nối cùng mạng với B; không lấy IP VPN hay 127.0.0.1.

Windows PowerShell (chỉ khi chưa có quy tắc firewall, chạy quyền quản trị):

~~~powershell
New-NetFirewallRule -DisplayName "Cinema TCP LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5000 -Profile Private -RemoteAddress LocalSubnet
New-NetFirewallRule -DisplayName "Cinema Dashboard LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 3000 -Profile Private -RemoteAddress LocalSubnet
~~~

Node chạy máy B thì mở thêm cổng 5001 trên A với cùng phạm vi. Chuyển riêng dashboard.key qua kênh nội bộ của nhóm và đặt đường dẫn tuyệt đối:

~~~powershell
$env:JAVA_BRIDGE_URL="http://192.168.1.10:5001"
$env:JAVA_BRIDGE_KEY_FILE="C:\CinemaConfig\dashboard.key"
npm start
~~~

Linux/macOS:

~~~bash
JAVA_BRIDGE_URL=http://192.168.1.10:5001 JAVA_BRIDGE_KEY_FILE=/path/to/dashboard.key npm start
~~~

Mặc định Node và Java cùng máy, không cần các biến trên. `.env.example` chỉ minh hoạ; chương trình đọc biến môi trường, không tự nạp .env.

## Đổi cổng

~~~bash
java -jar cinema-server/target/cinema-server-1.0.0.jar --tcp-port=6000 --http-port=6001
~~~

Đổi cổng JavaFX thành 6000, đặt `JAVA_BRIDGE_URL=http://127.0.0.1:6001` cho Node. Biến PORT đổi cổng Node. Nút mở dashboard trong JavaFX mặc định mở 3000; nếu đổi thì nhập URL mới trực tiếp.

## Lỗi thường gặp

| Hiện tượng | Kiểm tra |
|---|---|
| Connection refused | Server đã chạy? Đúng IP và cổng 5000? |
| Kết nối lâu rồi báo lỗi | Cùng LAN? Firewall cho cổng 5000? Wi-Fi bật client isolation? |
| Không có suất chiếu | Seed đã quá ngày; admin thêm lịch hoặc dùng database demo mới |
| Node thiếu dashboard.key | Chạy Java trước; kiểm tra working directory, database và JAVA_BRIDGE_KEY_FILE |
| Dashboard mất Java server | Kiểm tra JAVA_BRIDGE_URL, khoá đúng database, cổng 5001 |
| JavaFX runtime components missing | Chạy qua Maven javafx:run, chọn JDK 17 |
| Address already in use | Tiến trình khác chiếm cổng; dừng đúng tiến trình hoặc đổi cổng |
| Không thanh toán được | Giữ ghế hết hạn, suất bắt đầu, hoặc dùng cửa sổ khác |
| Mất phản hồi thanh toán | Thử lại cùng yêu cầu hoặc vào Vé của tôi tải lại trước khi đặt lần nữa |

Rút mạng không luôn được phát hiện ngay: heartbeat 20 giây, timeout 60 giây và quét mỗi 5 giây. Ghế được trả sau tối đa khoảng 65 giây không nhận dữ liệu. Đóng cửa sổ bình thường được TCP báo sớm hơn.

## Giữ dữ liệu sau buổi demo

Dừng Java trước khi sao chép cinema.db vì SQLite dùng WAL lúc đang chạy. Không chạy hai Java server chung database: khoá theo suất chỉ có hiệu lực trong một tiến trình, và lúc khởi động server trả các ghế HELD của phiên cũ.

Muốn dữ liệu mới thì dùng `--db` với đường dẫn khác thay vì xoá lịch sử đang có.
