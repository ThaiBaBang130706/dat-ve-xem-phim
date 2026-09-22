# CinemaBooking — Hướng dẫn chạy và kiểm tra

Đồ án **đặt vé xem phim đa người dùng qua TCP/IP trong LAN, mô hình Client–Server**. Tình huống chính: hai khách cùng giữ một ghế, server chỉ chấp nhận một người.

**Cập nhật danh mục phim/rạp:** poster và banner thật, tải ảnh qua LAN, trailer, nhiều thể loại, ngày phát hành, thông tin phim và bộ lọc khu vực/rạp/ngày. [Hướng dẫn sử dụng](docs/DANH_MUC_PHIM_RAP.md).

[![Build and test](https://github.com/ThaiBaBang130706/dat-ve-xem-phim/actions/workflows/ci.yml/badge.svg)](https://github.com/ThaiBaBang130706/dat-ve-xem-phim/actions/workflows/ci.yml)

## 1. Chọn đúng giao diện muốn mở

| Mục đích | Cách mở | Dữ liệu |
|---|---|---|
| Xem thiết kế web VKU đỏ/vàng/xanh | Mở `cinema-web/index.html` bằng Chrome/Edge | Mẫu trong bộ nhớ, tải lại sẽ đặt lại; không phát phim thật |
| Chạy đồ án đặt vé có server thật | IntelliJ: Server GUI + Client + Admin, theo mục 3 | TCP + SQLite; giao diện JavaFX NOIR |
| Xem thống kê thật bằng trình duyệt | Java server + `cinema-dashboard`, mục 7 | Dashboard chỉ đọc, có đăng nhập admin |

**Web VKU chưa nối backend Java.** Chuyển User/Admin/Server trên thanh demo không phải đăng nhập hoặc phân quyền thật. Chạy JavaFX không tự mở thiết kế web VKU. Node.js chỉ cần cho dashboard thống kê, không cần để chạy JavaFX hoặc mở bản web VKU.

## 2. Cập nhật mã và mở đúng thư mục

Nếu tải ZIP: dừng các chương trình cũ → GitHub **Code → Download ZIP** → giải nén vào thư mục mới. Nếu giữ dữ liệu, sao chép thư mục `data` khi server đã dừng; không chép `target` hoặc cấu hình IDE cũ sang bản mới.

Thư mục gốc đúng phải chứa cùng lúc `pom.xml`, `cinema-common`, `cinema-server`, `cinema-client`, `cinema-operator`. Sau giải nén có thể lồng hai thư mục cùng tên. Với đường dẫn đã dùng trên máy anh, kiểm tra trong PowerShell:

```powershell
Set-Location 'I:\dat-ve-xem-phim-main\dat-ve-xem-phim-main'
Test-Path .\pom.xml
Test-Path .\cinema-operator\pom.xml
```

Cả hai phải trả về `True`. Nếu thư mục mới ở nơi khác, thay đường dẫn tương ứng. Không gõ đường dẫn `pom.xml` như một lệnh chạy chương trình.

## 3. Chạy bằng IntelliJ trên Windows — cách nên dùng

### Chuẩn bị một lần

1. **File → Open** → chọn `pom.xml` gốc → mở dưới dạng project nếu được hỏi.
2. **File → Project Structure → Project → SDK**: chọn JDK **22.0.2** đang có. Nếu chưa có, Add JDK và chọn thư mục cài JDK, ví dụ `C:\Program Files\Java\jdk-22` (không chọn thư mục `bin`).
3. **Settings → Build, Execution, Deployment → Build Tools → Maven**: chọn Maven đi kèm IntelliJ (**Bundled**). Trong **Runner → JRE**, chọn Project JDK; Maven Importer dùng cùng JDK nếu có tuỳ chọn.
4. Mở bảng **Maven** bên phải → **Reload All Maven Projects**, chờ tải thư viện xong. Lần đầu cần Internet. Không cần cài JavaFX SDK riêng.
5. Ở thanh trên, bấm danh sách đang ghi **Current File** và chọn cấu hình bên dưới. Nút Run của `pom.xml` không phải nút chạy ứng dụng.

### Thứ tự chạy trên một laptop

| Bước | Cấu hình chọn cạnh nút ▶ | Việc cần làm / kết quả |
|---|---|---|
| 1 | `01_Build` | Chạy và đợi **BUILD SUCCESS** |
| 2 | `02_Server_GUI` | Cửa sổ server mở → giữ TCP 5000, HTTP 5001 → bấm **Khởi động server** |
| 3 | `03_Client_A` | Host **127.0.0.1**, port **5000** → Kết nối → đăng nhập user1 |
| 4 | `04_Client_B` | Chạy thêm client, đăng nhập user2 để thử tranh ghế |
| 5 | `05_Admin` | Kết nối như trên, đăng nhập admin để vào quản trị rạp |

Giữ server chạy khi mở các client. Có thể mở cả năm cấu hình theo thứ tự; build kết thúc, bốn cửa sổ ứng dụng có thể chạy đồng thời. **Không chạy thêm JAR server khi Server GUI đã khởi động server.** `05_Admin` không tự đăng nhập: quyền phụ thuộc tài khoản server xác thực.

| Tài khoản mẫu | Mật khẩu | Giao diện |
|---|---|---|
| user1 | User@1234 | Chọn phim/suất/ghế, vé của tôi, tài khoản |
| user2 | User@1234 | Khách thứ hai |
| admin | Admin@123 | Phim, thể loại, khu vực, rạp, phòng, suất chiếu, khách hàng, đơn vé, vé theo ghế, nhật ký |

Tài khoản này chỉ đúng với dữ liệu mẫu chưa đổi mật khẩu. Bật “Tạo dữ liệu mẫu nếu database còn trống” khi khởi động database mới. Admin không có luồng mua vé cá nhân.

### Không thấy cấu hình hoặc Run bị mờ

Vào **Run → Edit Configurations → + → Maven**, tạo các cấu hình sau. Ô lệnh có thể tên **Run** hoặc **Command line** tuỳ phiên bản IntelliJ.

| Tên | Working directory | Lệnh |
|---|---|---|
| 01_Build | Thư mục gốc có pom.xml | `clean install -DskipTests` |
| 02_Server_GUI | Thư mục gốc / cinema-operator | `javafx:run` |
| 03_Client_A | Thư mục gốc / cinema-client | `javafx:run` |
| 04_Client_B | Thư mục gốc / cinema-client | `javafx:run` |
| 05_Admin | Thư mục gốc / cinema-client | `javafx:run` |

Chọn thư mục bằng nút duyệt của IDE; **không thêm `-f` hay dấu nháy vào Working directory**. Apply → OK → chọn tên cấu hình → ▶. Không dùng `-am` cùng `javafx:run` vì Maven có thể chạy mục tiêu trên module không có ứng dụng JavaFX.

## 4. Mở web VKU

1. Mở thư mục `cinema-web` của bản mới.
2. Nhấp đúp `index.html`, chọn Chrome/Edge.
3. Thanh trên cùng có User / Admin / Server / UI kit; mỗi lớp có bố cục riêng.
4. Thử tìm phim, mở chi tiết, thêm danh sách; Admin thử thêm/ẩn phim; Server thử node và xác nhận thao tác mô phỏng.

Không chạy Java, Maven hoặc npm cho bản này. Nếu vẫn thấy bản cũ, kiểm tra đường dẫn trên thanh địa chỉ và mở file ở thư mục vừa giải nén. Xem [hướng dẫn web](cinema-web/README.md).

## 5. Kiểm tra đồ án hoạt động đúng

1. Server báo đang nghe TCP 5000; hai client đăng nhập thành công.
2. Hai khách mở **cùng một suất chiếu tương lai**, cùng chọn ghế B3 còn trống. Chọn ghế chưa giữ ghế: cần bấm **Giữ ghế**.
3. Client A giữ trước; client B giữ cùng ghế phải bị từ chối. Các client xem cùng suất nhận cập nhật ghế.
4. A xác nhận thanh toán demo; mở **Vé của tôi**. Admin phải thấy đơn và vé tương ứng.
5. A huỷ đơn trước giờ chiếu; ghế trở lại trống. Huỷ áp dụng toàn bộ đơn.
6. Giữ một ghế khác rồi đóng client; kiểm tra ghế được trả. Hết hạn giữ 5 phút cũng phải trả ghế.
7. Dừng server từ Server GUI; client phải báo ngắt kết nối. Khởi động lại rồi kết nối/đăng nhập lại.
8. Đơn đã xác nhận vẫn còn sau khi khởi động lại với **cùng database**.

Nếu lịch mẫu đã hết: admin thêm suất mới trong tương lai. Seed tạo lịch ngày mai/ngày kia **tính từ lần khởi tạo database**, không tự tạo lại mỗi lần chạy.

## 6. Lỗi thường gặp và cách xử lý

| Biểu hiện | Kiểm tra / cách xử lý |
|---|---|
| `java` hoặc `mvn is not recognized` | PowerShell chưa có PATH. Dùng Maven Bundled trong IntelliJ theo mục 3; IDE nhận JDK không đồng nghĩa terminal nhận Java |
| `POM file ... does not exist` | Sai thư mục hoặc nháy lồng nhau; kiểm tra mục 2, bỏ `-f`, chạy tại thư mục gốc |
| `BUILD SUCCESS` nhưng chưa có giao diện | Mới build xong. Chạy `02_Server_GUI`, sau đó client |
| `Connection refused` | Server chưa bấm Khởi động, đã dừng, hoặc sai IP/cổng. Kiểm tra trạng thái trên server |
| Cổng đang được sử dụng | Dừng server/JAR cũ. Không mở hai server cùng cổng. Nếu đổi TCP, client phải nhập cổng mới |
| Database đang được server khác sử dụng | Dừng tiến trình server cũ; không xoá lock để chạy chồng hai server |
| Không tìm thấy module `vn.cinema` | Chạy `01_Build` tại gốc để install đủ module, rồi chạy lại GUI |
| Không tìm thấy JAR hoặc `Unable to access jarfile` | Build chưa thành công hoặc chạy sai thư mục; kiểm tra `cinema-server/target` |
| JavaFX runtime components are missing | Chạy `javafx:run` bằng cấu hình Maven, không nhấp đúp JAR client |
| Đăng nhập mẫu thất bại | Database cũ đã đổi mật khẩu/khoá tài khoản, hoặc không seed. Không xoá dữ liệu để thử; có thể chọn database demo mới |
| Không thấy suất chiếu | Lịch seed cũ đã qua; admin thêm lịch tương lai |
| Dữ liệu “biến mất” | Xem đường dẫn DB trong Server GUI/log. Chạy từ thư mục khác có thể trỏ sang DB khác |
| Web VKU không có đơn vừa đặt | Web VKU là prototype riêng, chưa nối Java; kiểm tra đơn trong JavaFX Admin hoặc dashboard thật |
| Không có `dashboard.key` | Khởi động Java trước; kiểm tra key cùng thư mục DB và cấu hình ở mục 7 |
| Dashboard báo không kết nối được Java | Kiểm tra HTTP 5001, bridge URL/key; đổi DB hoặc key thì khởi động lại Node |
| Dashboard `EADDRINUSE` | Cổng 3000 đang bận: dừng Node cũ hoặc đặt PORT khác |

Chẩn đoán cổng trên cùng máy bằng PowerShell:

```powershell
Test-NetConnection 127.0.0.1 -Port 5000
Get-NetTCPConnection -LocalPort 5000,5001 -State Listen -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

`TcpTestSucceeded: True` chỉ chứng minh có dịch vụ nghe cổng, chưa chứng minh đặt vé đúng. Xác minh thêm bằng kịch bản mục 5. Không tắt toàn bộ firewall.

## 7. Dashboard thống kê thật — tuỳ chọn

Cần **Node.js 22+** và Java server đã chạy. Tại thư mục gốc:

```powershell
Set-Location .\cinema-dashboard
node --version
npm ci
npm start
```

Mở **http://localhost:3000**, đăng nhập admin. Dashboard chỉ đọc dữ liệu; thay đổi phim/phòng/đơn dùng JavaFX Admin. Java cung cấp HTTP bridge 5001, Node cập nhật dashboard mỗi 2 giây.

Nếu đổi đường dẫn database hoặc cổng HTTP, trong cùng terminal trước `npm start`:

```powershell
$env:JAVA_BRIDGE_KEY_FILE = 'I:\duong-dan-du-an\data\dashboard.key'
$env:JAVA_BRIDGE_URL = 'http://127.0.0.1:5001'
$env:PORT = '3000'
npm start
```

Thay đường dẫn mẫu bằng file key thực tế bên cạnh database. Các biến chỉ áp dụng cho terminal hiện tại. `src/server.js` đọc biến môi trường; **chỉ tạo `.env` không tự nạp cấu hình**. Không đưa key/database lên GitHub. Với HTTP nội bộ giữ COOKIE_SECURE mặc định false; true dành cho triển khai HTTPS.

## 8. Chạy bằng PowerShell — chỉ khi đã có Maven trong PATH

Không bắt buộc nếu dùng IntelliJ. Ví dụ JDK của anh; nếu cài nơi khác, sửa đường dẫn:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

Chỉ tiếp tục nếu cả hai lệnh thành công và Maven nhận đúng Java. Chạy tại thư mục gốc:

```powershell
mvn -B install -DskipTests
```

Đợi BUILD SUCCESS rồi chạy **một** server:

```powershell
mvn -pl cinema-operator javafx:run
```

Mỗi client dùng terminal mới, vẫn ở thư mục gốc:

```powershell
mvn -pl cinema-client javafx:run
```

Nếu chỉ cần server console thay cho Server GUI, dùng:

```powershell
& 'C:\Program Files\Java\jdk-22\bin\java.exe' -jar '.\cinema-server\target\cinema-server-1.0.0.jar'
```

PowerShell cần ký tự `&` trước đường dẫn chương trình có dấu cách. Không thêm nháy lồng quanh đường dẫn. JAR server chạy console sẽ **không có cửa sổ Server GUI**.

## 9. Chạy hai máy LAN

- Máy A chạy server. Dùng IPv4 LAN hiển thị trên Server GUI hoặc `ipconfig`.
- Máy B chạy client: Host là IPv4 máy A, Port là 5000; không nhập `IP:port` chung vào ô Host, không dùng 127.0.0.1 cho máy khác.
- Cho phép TCP 5000 qua firewall trên máy A trong mạng riêng. Cùng Wi-Fi nhưng mạng có client isolation vẫn có thể chặn kết nối.
- Dashboard: thêm cổng 3000 nếu truy cập từ máy B. Cổng 5001 chỉ cần cho phép từ máy Node nếu Node chạy riêng.
- Không cần máy thứ hai để bảo vệ demo: hai client trên một máy vẫn tạo hai kết nối TCP thật.

Xem [hướng dẫn LAN](docs/HUONG_DAN_LAN.md). Ngoài LAN cần thiết lập đường mạng riêng phù hợp; bản thực hành TCP/HTTP chưa có TLS, không công khai trực tiếp các cổng lên Internet.

## 10. Kiểm thử tự động và giới hạn

`01_Build` có `-DskipTests`: build thành công **không có nghĩa test đã chạy**. Để chạy test, tạo Maven configuration tại gốc với lệnh `verify` (không có `-DskipTests`), hoặc dùng:

```powershell
mvn -B verify
```

Dashboard: chạy `npm run check` rồi `npm test` trong `cinema-dashboard` sau `npm ci`. Web VKU: chạy `node cinema-web/check.cjs` từ gốc; chỉ kiểm tra render/state, không thay thế kiểm thử trình duyệt. Khi sửa source web, chạy `python cinema-web/bundle.py` trước để đồng bộ index.html.

GitHub Actions chạy Java 17/22, JavaFX trên màn hình ảo, kiểm thử tranh ghế/phân quyền/rollback, TCP/HTTP qua JAR thật và dashboard. Xem [kết quả rà soát](docs/KIEM_TRA_LOI.md). Không cam kết phần mềm không còn lỗi; giao diện và LAN Windows cần thử trên máy sử dụng.

## 11. Cấu trúc và tài liệu

| Thư mục | Vai trò |
|---|---|
| cinema-common | Giao thức JSON Lines |
| cinema-server | TCP, nghiệp vụ, SQLite, HTTP bridge |
| cinema-client | JavaFX khách hàng và quản trị theo quyền |
| cinema-operator | JavaFX vận hành server tại máy chủ |
| cinema-dashboard | Dashboard Node chỉ đọc dữ liệu thật |
| cinema-web | Prototype thiết kế VKU độc lập |
| scripts | Smoke test server qua TCP/HTTP |

[Giao thức](protocol.md) · [Kiến trúc](docs/KIEN_TRUC.md) · [Quản trị](docs/QUAN_TRI.md) · [Hướng dẫn IntelliJ](docs/CHAY_INTELLIJ.md) · [Kịch bản bảo vệ](docs/KICH_BAN_BAO_VE.md) · [Slide HTML](docs/slides.html)

Một server / một SQLite, tối đa 64 kết nối TCP. Thanh toán và QR là demo; ảnh có thể dùng URL hoặc tải từ máy admin, poster minh hoạ được dùng làm dự phòng; chưa có cổng thanh toán thật, kiểm soát vé thương mại hoặc cụm dự phòng. Dữ liệu mẫu gồm phim hư cấu, phòng chiếu và lịch tương lai khi khởi tạo.
