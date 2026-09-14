# Cách hệ thống hoạt động

## Vì sao chọn Client/Server?

Một ghế trong một suất chiếu chỉ được bán một lần. Nhóm đặt quyền quyết định ở Java server; client nhận dữ liệu, cho khách thao tác rồi gửi yêu cầu. Database chỉ nằm ở máy server. Cách này gần với tình huống nhiều quầy cùng bán vé cho một rạp.

Không gọi hệ thống này là P2P hoặc hệ phân tán dự phòng. Dashboard là một client đọc dữ liệu, không phải server đặt vé thứ hai.

~~~mermaid
flowchart TD
  A["JavaFX máy A"] <-->|"TCP JSON Lines · 5000"| S["Java server"]
  B["JavaFX máy B"] <-->|"TCP JSON Lines · 5000"| S
  S <-->|"JDBC + transaction"| D[("SQLite cinema.db")]
  N["Node dashboard · 3000"] -->|"HTTP + khoá · 5001"| S
  W["Trình duyệt admin"] <-->|"Socket.IO"| N
~~~

## Luồng giữ ghế

~~~mermaid
sequenceDiagram
  participant A as Client A
  participant S as Java server
  participant D as SQLite
  participant B as Client B
  A->>S: HOLD_SEATS suất 1, ghế B3
  S->>S: Lấy ReentrantLock của suất 1
  S->>D: Kiểm tra tất cả ghế trong transaction
  alt Ghế trống
    S->>D: Đặt HELD, chủ phiên, hạn giữ; COMMIT
    S->>S: Tăng revision, nhả khoá
    S-->>B: EVENT_SEAT_UPDATE
    S-->>A: Snapshot ghế vừa giữ
  else Có ghế bận
    S->>D: ROLLBACK
    S-->>A: Báo ghế bận, giữ nguyên lựa chọn cũ
  end
~~~

Nhận request trước không bảo đảm mua được vé: request phải qua kiểm tra, giữ ghế và xác nhận trong hạn. Khi hai request tranh một ghế, chỉ một transaction được đổi ghế sang HELD cho phiên của mình.

## Các thuật toán chính

**1. Giữ ghế nguyên tử**

BookingService dùng ConcurrentHashMap ánh xạ showId tới ReentrantLock. Trong khoá, mở transaction, kiểm tra tài khoản/suất và **mọi ghế**. Chỉ khi tất cả hợp lệ mới thay lựa chọn cũ bằng bộ ghế mới và đặt hold_until = giờ server + 300.000 ms. Exception gây rollback.

SQLite chỉ có một writer; Database.write được đồng bộ để tránh hai transaction đọc rồi cùng nâng lên khoá ghi. Do đó các suất có khoá nghiệp vụ riêng nhưng các lần ghi SQLite vẫn tuần tự. Bản này phù hợp demo LAN, không có cam kết tải của hệ thống rạp thương mại.

**2. Xác nhận vé và chống lặp**

Trong transaction: kiểm tra phiên giữ ghế, trạng thái HELD, hạn giữ và suất chưa bắt đầu. Server tự tính tổng giá bằng giá trong showtimes × số ghế. Ghi bookings, tickets và chuyển ghế SOLD trong cùng một lần commit.

UNIQUE(user_id, request_id) nhận diện lần thanh toán đã xử lý. Thử lại cùng dữ liệu trả về booking cũ. Một partial unique index trên tickets(show_id, seat_label) WHERE status='ACTIVE' ngăn hai vé còn hiệu lực cho cùng ghế.

**3. Hết hạn và mất kết nối**

Scheduler quét mỗi 5 giây. Trả ghế HELD có hold_until ≤ giờ server, phát EVENT_SEAT_UPDATE và EVENT_HOLD_EXPIRED. Kiểm tra giữ/mua cũng xét hạn tại thời điểm xử lý, không chỉ trông vào scheduler.

Client gửi PING mỗi 20 giây; server timeout sau 60 giây không nhận dữ liệu, quét 5 giây. Khi đóng socket/đăng xuất, trả ghế của connectionId. Cùng tài khoản mở hai cửa sổ vẫn có hai phiên giữ ghế riêng. Khởi động lại server trả mọi ghế HELD còn sót; vé SOLD vẫn giữ nguyên.

**4. Xếp lịch phòng**

Với khoảng chiếu mới [start,end], từ chối nếu cùng phòng có suất OPEN với start_cũ < end + 15 phút và end_cũ > start − 15 phút. Thời lượng lấy ở movies. Không cho sửa suất có ghế đang giữ hoặc đã bán.

**5. Cập nhật giao diện**

Server phát snapshot sau commit. Snapshot có revision; JavaFX bỏ bản cũ hơn nếu các luồng gửi tới không đúng thứ tự. Mỗi kết nối có một hàng đợi gửi 128 phản hồi và một writer riêng. Client chậm đầy hàng đợi bị ngắt, không giữ toàn bộ luồng đặt vé đứng lại.

JavaFX dùng CompletableFuture; luồng đọc socket không cập nhật control trực tiếp mà chuyển qua Platform.runLater. Sau 15 giây chưa phản hồi, hiện lỗi để người dùng kiểm tra hoặc thử lại.

## Tổ chức dữ liệu

| Bảng | Nội dung | Ràng buộc đáng chú ý |
|---|---|---|
| users | Tài khoản, hash mật khẩu, role, status | username UNIQUE không phân biệt hoa thường |
| movies | Tên, thể loại, thời lượng, độ tuổi, mô tả | Ngừng dùng bằng active=0 |
| rooms | Tên phòng, số hàng/cột | Tên UNIQUE, tối đa 12×16 |
| showtimes | Phim, phòng, giờ, giá, trạng thái | FK movie/room; kiểm tra trùng ở service |
| seats_state | Trạng thái từng ghế theo suất | PK(show_id,seat_label), CHECK trạng thái/chủ giữ |
| bookings | Đơn, người đặt, tổng tiền, requestId | code UNIQUE; UNIQUE(user_id,request_id) |
| tickets | Từng ghế đã mua, thông tin chụp tại lúc mua | Partial UNIQUE cho vé ACTIVE |
| logs | Ai thực hiện, hành động, chi tiết, thời điểm | Không ghi mật khẩu/token |

Schema ở `cinema-server/src/main/resources/schema.sql`. Danh mục mẫu ở seed.sql; Database.seed(Clock) tạo hash mật khẩu, lịch hai ngày và vé mẫu trong transaction. Lần sau có user thì không seed lại.

Foreign key bật trên từng kết nối. Câu SQL có dữ liệu người dùng dùng PreparedStatement. Tiền lưu số nguyên VND, thời điểm lưu epoch milliseconds; dùng múi giờ Asia/Ho_Chi_Minh khi hiển thị. Vé chứa tên phim/phòng/giờ/giá tại lúc mua để việc sửa danh mục không làm mất thông tin vé cũ.

Huỷ vé đổi trạng thái đơn và ticket, trả ghế AVAILABLE trong một transaction. Không xoá lịch sử. Muốn sao lưu file SQLite thì dừng server trước, tránh bỏ sót WAL.

## Phân quyền và phạm vi

Mật khẩu PBKDF2-HMAC-SHA256, salt ngẫu nhiên 16 byte, 600.000 vòng; so sánh hash bằng MessageDigest.isEqual. TCP token gắn với kết nối, quyền/status được kiểm tra lại tại server. Lỗi đăng nhập được giới hạn theo kết nối; đây chưa phải hệ thống chống tấn công trên Internet.

HTTP bridge dùng khoá ngẫu nhiên 32 byte lưu cạnh database. Node giữ khoá ở phía server và xác thực admin trước khi cho đọc. Cookie dashboard HttpOnly/SameSite=Strict, thời hạn 4 giờ. Không có endpoint Node để đặt vé hoặc sửa database.

Bản LAN chưa triển khai TLS, thanh toán thật, cụm dự phòng, hàng đợi phân tán hay giao dịch giữa nhiều database. Nếu một Java server hỏng, dịch vụ tạm dừng đến khi khởi động lại.

## Tài liệu thư viện

- [OpenJFX: Maven và JavaFX](https://openjfx.io/openjfx-docs/)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
- [Gson](https://github.com/google/gson)
- [Socket.IO: middleware](https://socket.io/docs/v4/middlewares/)
- [Express API](https://expressjs.com/en/4x/api/)
- [Chart.js](https://www.chartjs.org/docs/latest/)
