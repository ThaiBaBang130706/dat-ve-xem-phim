# Chạy đồ án Java TCP + JavaFX trong IntelliJ

Đồ án chính là **đặt vé rạp**, không phải web streaming. Ba cửa sổ JavaFX dùng chung giao diện NOIR:

- **02_Server_GUI**: khởi động/dừng TCP server, xem kết nối, yêu cầu, lỗi, uptime, bộ nhớ JVM, ghế giữ và nhật ký thật.
- **03_Client_A / 04_Client_B**: khách xem phim, chọn suất/ghế, giữ ghế 5 phút, xác nhận vé demo, xem QR và huỷ vé.
- **05_Admin**: chạy chương trình client rồi đăng nhập admin; server xác định quyền và mở giao diện quản trị riêng.

## Cập nhật an toàn bản ZIP đang dùng

1. Dừng client và server cũ, kể cả JAR server đang chạy.
2. Tải ZIP mới từ main, giải nén vào một thư mục mới.
3. Nếu muốn giữ dữ liệu cũ, chép nguyên thư mục **data** từ dự án cũ vào thư mục mới có pom.xml gốc. Không chép thư mục target cũ.
4. Trong IntelliJ: **File → Open**, chọn **pom.xml gốc** bên cạnh các thư mục cinema-common, cinema-server, cinema-client và cinema-operator. Chọn Open as Project nếu được hỏi.
5. Đặt **Project SDK = JDK 22.0.2**. Trong Settings → Build, Execution, Deployment → Build Tools → Maven, dùng **Bundled Maven**; Maven Runner JRE chọn **Project JDK**. Reload All Maven Projects.
6. Chờ IntelliJ nạp xong dự án. Repo có sẵn cấu hình chia sẻ trong **.run**; chọn ở ô cấu hình cạnh nút Run phía trên.

Không cần gõ `mvn` hoặc `java` ở PowerShell. Không nhập đường dẫn pom.xml vào ô lệnh và không dùng `-f` với dấu nháy lồng nhau.

## Thứ tự chạy

1. Chọn **01_Build** → Run. Chờ **BUILD SUCCESS**. Cấu hình chạy clean install -DskipTests ở thư mục gốc và cài các module vào Maven local.
2. Chọn **02_Server_GUI** → Run. Trong cửa sổ NOIR / SERVER, giữ TCP **5000**, HTTP **5001**, kiểm tra đường dẫn database rồi bấm **Khởi động server**.
3. Chọn **03_Client_A** → Run. Nhập **127.0.0.1**, **5000** → **Kết nối** → đăng nhập **user1 / User@1234**.
4. Chọn **04_Client_B** → Run và đăng nhập **user2 / User@1234** nếu cần thử hai khách đồng thời.
5. Chọn **05_Admin** → Run → Kết nối → đăng nhập **admin / Admin@123**.

Các tài khoản trên chỉ có khi dùng database mẫu chưa đổi mật khẩu. Đăng ký mới luôn tạo tài khoản USER; không có nút tự chọn quyền admin.

Nếu chưa thấy các cấu hình .run: tạo **Run → Edit Configurations → + → Maven**. Đặt Working directory là thư mục gốc cho build, cinema-operator cho server GUI, cinema-client cho các client; Run lần lượt là **clean install -DskipTests** hoặc **javafx:run**. Không chọn Current File/pom.xml để chạy Java.

## Phân biệt ba cửa sổ

- Khách: thanh điều hướng ngang Phim đang chiếu / Vé của tôi / Tài khoản; không có menu quản trị.
- Admin: tiêu đề Quản trị rạp; sidebar Tổng quan / Phim / Phòng chiếu / Suất chiếu / Khách hàng / Đơn đặt vé / Vé theo ghế / Nhật ký; không có luồng mua vé cá nhân.
- Server GUI: công cụ vận hành trên **máy sở hữu server**, không phải màn hình dành cho khách. Dừng server có xác nhận và đóng các kết nối đang mở. Công cụ này không đăng nhập từ xa.

Server dùng lock file bên cạnh database để ngăn hai tiến trình cùng sử dụng một database. Không xoá file lock để cố mở server thứ hai; hãy dừng tiến trình cũ. Nếu cổng bận, dừng JAR/server cũ hoặc chọn cặp cổng mới và nhập đúng cổng TCP ở client.

## Dùng máy khác trong LAN

Giữ server GUI trên máy A. Máy B chạy client, nhập IPv4 hiển thị trong cửa sổ server (không dùng 127.0.0.1). Cả hai máy dùng cùng mạng; cho phép kết nối TCP vào cổng 5000 qua Windows Firewall trên máy A. Client không cần chép database. Có thể chạy toàn bộ trên một laptop để thử.

## Lịch chiếu đã hết

Seed chỉ chạy khi database chưa có người dùng; khởi động lại không xoá hoặc tạo lại lịch cũ. Admin mở **Suất chiếu → Thêm mới**, chọn ngày giờ tương lai. Có thể dùng một đường dẫn database mới để tạo bộ demo mới; không xoá database cũ nếu cần giữ lịch sử.

## Phạm vi bản hoàn thiện

Giao tiếp, giữ/đặt/huỷ vé, phân quyền và số liệu vận hành dùng server/database thật. Poster là vector minh hoạ offline. Thanh toán và QR là mô phỏng; chưa tích hợp cổng thanh toán hoặc kiểm soát vé rạp thương mại. Huỷ vé hiện huỷ toàn bộ đơn, không huỷ riêng một ghế.

Thư mục cinema-web là mẫu thiết kế tham khảo trước đây. Không cần mở HTML hoặc chạy Node dashboard để dùng đồ án JavaFX này. Dashboard web cũ vẫn là công cụ đọc dữ liệu tuỳ chọn và cần chạy riêng.
