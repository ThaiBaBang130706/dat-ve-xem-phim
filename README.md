# CinemaBooking — Đặt vé xem phim qua Java TCP

Ứng dụng đặt vé cho nhiều máy trong cùng mạng LAN. Tình huống nhóm tập trung giải quyết: **hai người cùng chọn ghế B3 thì server chỉ cho một người giữ và mua ghế đó**.

[![Build and test](https://github.com/ThaiBaBang130706/dat-ve-xem-phim/actions/workflows/ci.yml/badge.svg)](https://github.com/ThaiBaBang130706/dat-ve-xem-phim/actions/workflows/ci.yml)

## Chạy nhanh

Cần **JDK 17**, **Maven 3.9+** và **Node.js 22+**. Lần đầu cần Internet để tải thư viện; sau đó các máy dùng ứng dụng qua LAN. Maven tải JavaFX theo hệ điều hành, không cần cài SDK JavaFX riêng.

Mở terminal tại thư mục gốc repo:

~~~bash
mvn -B install -DskipTests
java -jar cinema-server/target/cinema-server-1.0.0.jar
~~~

Mở terminal thứ hai, vẫn ở thư mục gốc:

~~~bash
mvn -pl cinema-client javafx:run
~~~

Trong cửa sổ client: nhập `127.0.0.1`, cổng `5000` → **Kết nối** → đăng nhập.

| Tài khoản mẫu | Mật khẩu | Quyền |
|---|---|---|
| admin | Admin@123 | Quản trị |
| user1 | User@1234 | Khách hàng |
| user2 | User@1234 | Khách hàng |

Server tự tạo `data/cinema.db` và `data/dashboard.key` khi chạy lần đầu. Mẫu gồm 10 phim hư cấu, phòng P1/P2/IMAX, 24 suất chiếu trong **ngày mai và ngày kia tính từ lần khởi tạo**, cùng một số ghế A1/A2 đã bán. Thanh toán và vé QR đều là mô phỏng.

Lịch mẫu được giữ nguyên ở các lần chạy sau để bảo toàn lịch sử. Nếu đã hết lịch, admin thêm suất chiếu. Muốn dữ liệu demo mới, dừng server rồi dùng đường dẫn database mới:

~~~bash
java -jar cinema-server/target/cinema-server-1.0.0.jar --db=data-demo-moi/cinema.db
~~~

Khi dùng database khác, đặt `JAVA_BRIDGE_KEY_FILE` cho Node trỏ đến `dashboard.key` nằm cùng thư mục database đó.

## Dashboard

Mở terminal thứ ba:

~~~bash
cd cinema-dashboard
npm ci
npm start
~~~

Mở <http://localhost:3000>, đăng nhập admin. Dashboard hiển thị doanh thu, vé, ghế đang giữ, số client, biểu đồ, lịch chiếu, đơn vé và nhật ký. Node lấy dữ liệu từ Java mỗi 2 giây rồi đẩy tới trình duyệt bằng Socket.IO.

Node mặc định đọc `../data/dashboard.key`. Không nhập khoá này vào trình duyệt hoặc commit khoá/database lên Git. Nên chạy Node và Java cùng máy để demo thuận tiện.

## Giao diện theo tài khoản

Đăng nhập **admin** sẽ mở riêng cửa sổ **Quản trị rạp**, gồm Tổng quan, Phim, Phòng chiếu, Suất chiếu, Khách hàng, Đơn đặt vé, Vé theo ghế và Nhật ký. Không hiển thị Phim đang chiếu hoặc Vé của tôi trong giao diện này.

Đăng nhập **user1/user2** mở giao diện khách để chọn phim và đặt vé. Cả hai loại cửa sổ dùng chung Java server, có thể chạy đồng thời trên một máy.

Quản trị có tìm kiếm, lọc trạng thái/ngày, xem chi tiết đơn và vé theo từng ghế, xem sơ đồ ghế để mở/khoá ghế trống. Xem [hướng dẫn quản trị và cập nhật bản đang chạy](docs/QUAN_TRI.md).

## Có thể làm gì?

- **Khách hàng:** đăng ký, đăng nhập, tìm phim, xem nội dung/lịch chiếu, chọn tối đa 8 ghế, giữ ghế 5 phút, thanh toán demo, xem vé/QR, huỷ vé trước giờ chiếu, sửa tên và đổi mật khẩu.
- **Quản trị JavaFX:** thống kê, thêm/sửa/ngừng dùng phim và phòng, thêm/sửa/huỷ suất chiếu, mở/khoá ghế trống, khoá/mở tài khoản, xem/huỷ đơn vé, xem nhật ký.
- **Cập nhật trực tiếp:** ghế đổi màu ở các client đang xem cùng suất; tự trả ghế khi hết 5 phút, đăng xuất hoặc mất kết nối.
- **Ràng buộc:** không bán trùng ghế; không sửa suất có ghế giữ/đã bán; không xếp lịch trùng phòng; có 15 phút dọn phòng; kiểm tra quyền admin tại server.

Màu ghế: xanh = trống, vàng = người khác giữ, cam = bạn giữ, đỏ = đã bán, xám = tạm khoá. Viền đậm là ghế đang chọn tại máy của bạn. **Chọn ghế chưa có nghĩa là đã giữ ghế**; cần bấm “Giữ ghế”.

## Chạy hai máy trong LAN

1. Máy A chạy Java server và Node. Xem IPv4 bằng `ipconfig` trên Windows, ví dụ `192.168.1.10`.
2. Máy B chạy JavaFX, nhập `192.168.1.10:5000`. Có thể mở thêm client trên A bằng `127.0.0.1:5000`.
3. Đăng nhập user1/user2, mở cùng suất, cùng chọn B3, rồi bấm giữ ghế.
4. Mở dashboard từ máy B tại `http://192.168.1.10:3000`.

Firewall cần cho cổng TCP **5000** và **3000**. Cổng **5001** cần mở nếu Node chạy máy khác. Xem [hướng dẫn LAN và lỗi thường gặp](docs/HUONG_DAN_LAN.md).

## Cấu trúc dự án

| Thư mục | Vai trò |
|---|---|
| cinema-common | Request/Response, Gson, đọc và ghi JSON Lines |
| cinema-server | TCP, xử lý đặt vé, JDBC/SQLite, phân quyền, HTTP bridge |
| cinema-client | JavaFX, FXML, CSS, controller, TCP bất đồng bộ, mã QR |
| cinema-dashboard | Express, Socket.IO, Chart.js; chỉ đọc |
| docs | Kiến trúc, demo, hướng dẫn, slide thuyết trình |
| scripts | Kiểm tra server đã đóng gói bằng hai client TCP thật |

Không dùng Spring Boot/Hibernate. Luồng đặt vé chính dùng ServerSocket/Socket; Socket.IO chỉ phục vụ dashboard.

## Tài liệu và thuyết trình

- [Giao thức và toàn bộ lệnh](protocol.md)
- [Kiến trúc, thuật toán, tổ chức dữ liệu](docs/KIEN_TRUC.md)
- [Kịch bản demo và phân công nhóm 2–3 SV](docs/DEMO.md)
- [Slide HTML](docs/slides.html): tải repo, mở file bằng trình duyệt; dùng ←/→, bấm Toàn màn hình hoặc in thành PDF. Không cần Internet.
- [Nội dung slide dạng văn bản](docs/NOI_DUNG_SLIDE.md)

## Kiểm tra

~~~bash
mvn -B verify
cd cinema-dashboard
npm test
~~~

Test JavaFX cần môi trường đồ hoạ. Linux không có màn hình dùng `xvfb-run -a mvn -B verify`. GitHub Actions dùng cách này và kiểm tra thêm file JAR thật qua TCP/HTTP.

Kiểm thử gồm tranh ghế, rollback khi ghế bận, hết hạn giữ, ngắt kết nối, xác nhận lặp, quyền sở hữu vé, khoá tài khoản, bảo vệ HTTP/Socket.IO và nạp FXML/CSS.

## Phạm vi bản thực hành

Một Java server quản lý một database SQLite; đây là **Client/Server**, chưa có cụm server dự phòng. Giới hạn 64 kết nối TCP. Mật khẩu được băm PBKDF2, nhưng TCP/HTTP của bản LAN chưa có TLS; dùng tài khoản mẫu trong mạng thực hành.

QR chứa mã vé demo, chưa có ứng dụng quét soát vé hoặc cổng thanh toán thật. Một suất có một mức giá, chưa tính ghế VIP, bắp nước, khuyến mãi. Poster dùng thẻ chữ để mở được khi không có Internet.
