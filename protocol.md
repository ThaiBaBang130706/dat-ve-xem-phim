# Giao thức CinemaBooking v1

## Khung TCP

UTF-8, một JSON trên một dòng kết thúc LF (`\n`), tối đa 1 MiB. Một JSON có thể bị chia thành nhiều gói TCP; nhiều JSON có thể nằm cùng một gói. Đọc đến LF rồi mới parse.

~~~json
{"id":"req-001","type":"LOGIN","token":null,"data":{"username":"user1","password":"User@1234"}}
~~~

~~~json
{"id":"req-001","type":"LOGIN","success":true,"message":"OK","data":{"token":"token-do-server-tao","user":{"id":2,"username":"user1","display_name":"Sinh viên A","role":"USER","status":"ACTIVE","created_at":1789344000000}}}
~~~

id dài 1–64 ký tự ghép phản hồi với yêu cầu; type là tên lệnh; data là object. Mọi lệnh sau đăng nhập phải kèm token của **chính kết nối TCP đó**, trừ PING/LOGIN/REGISTER. Reconnect phải đăng nhập lại.

Lỗi nghiệp vụ giữ nguyên id/type, success=false, message tiếng Việt, data={}. Không gửi stack trace, password hash hay mã phiên giữ ghế cho client.

## Lệnh khách hàng

| type | data | Phản hồi thành công |
|---|---|---|
| PING | {} | {serverTime}; type phản hồi là PONG |
| REGISTER | {username,password,displayName} | User; không tự đăng nhập |
| LOGIN | {username,password} | {token,user} |
| LOGOUT | {} | {}; trả ghế của phiên |
| GET_PROFILE | {} | User hiện tại |
| UPDATE_PROFILE | {displayName} | User sau cập nhật |
| CHANGE_PASSWORD | {oldPassword,newPassword} | {} |
| GET_MOVIES | {} | Mảng phim đang hoạt động |
| GET_MOVIE_DETAIL | {movieId} | Một phim |
| GET_SHOWTIMES | {movieId} hoặc {} | Suất đang bán, chưa bắt đầu |
| GET_SEATMAP | {showId} | Snapshot, không tự subscribe |
| SUBSCRIBE_SHOW | {showId} | Snapshot và nhận sự kiện |
| UNSUBSCRIBE_SHOW | {} | {}; không tự trả ghế |
| HOLD_SEATS | {showId,seats:["B3","B4"]} | Snapshot sau khi giữ |
| RELEASE_SEATS | {showId} | Snapshot sau khi trả ghế do phiên giữ |
| CONFIRM_BOOKING | {showId,seats,requestId} | Booking, tickets, qrText |
| GET_MY_TICKETS | {} | Tối đa 500 booking của user, mới nhất trước |
| GET_TICKET | {bookingId} | Booking/tickets, chỉ chủ vé hoặc admin |
| CANCEL_BOOKING | {bookingId} | Booking đã huỷ; chủ vé/admin, trước giờ chiếu |

Mỗi kết nối subscribe một suất. Giữ 1–8 ghế không trùng. HOLD_SEATS thay thế lựa chọn cũ **trong cùng suất** nếu tất cả ghế hợp lệ; một ghế bận thì giữ nguyên lựa chọn trước.

CONFIRM_BOOKING dùng data.requestId dài 8–64 ký tự chống thanh toán lặp. Thử lại phải giữ nguyên requestId/showId/danh sách ghế. requestId này độc lập với id của khung TCP. Cùng user/requestId đã thành công trả lại booking cũ; dùng cho suất hoặc bộ ghế khác bị từ chối.

## Lệnh quản trị

Tất cả kiểm tra role ADMIN và status ACTIVE tại Java server.

| type | data |
|---|---|
| ADMIN_GET_STATS | {} |
| ADMIN_LIST_MOVIES / ADMIN_LIST_ROOMS / ADMIN_LIST_SHOWS | {} |
| ADMIN_LIST_USERS / ADMIN_LIST_BOOKINGS / ADMIN_LIST_LOGS | {} |
| ADMIN_SAVE_MOVIE | {id:0,title,genre,duration_minutes,age_rating,description} |
| ADMIN_DELETE_MOVIE | {id}; ngừng dùng, giữ lịch sử |
| ADMIN_SAVE_ROOM | {id:0,name,rows_count,cols_count} |
| ADMIN_DELETE_ROOM | {id}; ngừng dùng, giữ lịch sử |
| ADMIN_SAVE_SHOW | {id:0,movie_id,room_id,starts_at,price_vnd} |
| ADMIN_DELETE_SHOW | {id}; chuyển CANCELLED |
| ADMIN_SET_USER_STATUS | {id,status:"ACTIVE"} hoặc LOCKED |
| ADMIN_SET_SEAT_STATUS | {showId,seat:"B3",status:"UNAVAILABLE"} hoặc AVAILABLE |

id=0 thêm mới, id có sẵn để sửa. Server tính ends_at từ thời lượng phim. Không sửa kích thước phòng khi đã có suất, không sửa suất có ghế HELD/SOLD, không huỷ suất có vé CONFIRMED. Không tự khoá admin đang dùng.

LIST_SHOWS/BOOKINGS trả tối đa 500 dòng, LIST_LOGS trả 200 dòng. Admin huỷ đơn bằng CANCEL_BOOKING. Lệnh chưa hỗ trợ bị từ chối.

## Sự kiện chủ động

id=null, success=true.

| type | Khi gửi | data |
|---|---|---|
| EVENT_SYSTEM | Vừa kết nối | message, heartbeatSeconds=20, holdSeconds=300 |
| EVENT_SEAT_UPDATE | Giữ/trả/mua/huỷ/hết hạn/thay đổi ghế | Snapshot đầy đủ |
| EVENT_HOLD_EXPIRED | Giữ ghế của phiên hết 5 phút | showId, message |
| EVENT_SHOW_UPDATED | Admin lưu/ngừng phim/phòng/suất | message |

Snapshot có show, seats, serverTime, revision. Ghế có seat_label, status, hold_until, is_mine (0/1). HELD_MINE là cách hiển thị khi HELD và is_mine=1, không phải trạng thái riêng trong DB.

revision tăng trong tiến trình server. Client bỏ snapshot có revision nhỏ hơn lần đã nhận; xoá revision cũ khi đổi suất/reconnect. Đếm ngược dựa trên hold_until và serverTime. Thời điểm dùng **Unix epoch milliseconds**, hiển thị giờ Việt Nam.

## HTTP bridge và Node

Java HTTP 5001 yêu cầu `Authorization: Bearer <dashboard.key>` ở tất cả endpoint:

| Endpoint | Method | Nội dung |
|---|---|---|
| /api/stats | GET | users, bookings, tickets, revenue, heldSeats, daily, connectedClients |
| /api/shows | GET | Tối đa 500 suất và số ghế |
| /api/bookings | GET | 500 đơn gần nhất |
| /api/logs | GET | 200 nhật ký gần nhất |
| /api/auth | POST | Xác thực {username,password} của admin |
| /api/auth/check | POST | Kiểm tra {userId} vẫn là admin hoạt động |

Khoá giữ ở Java/Node, không gửi vào trình duyệt. Node 3000 có POST /api/login, POST /api/logout, GET /api/session, GET /api/dashboard. Cookie HttpOnly, SameSite=Strict, thời hạn 4 giờ. Kiểm tra lại quyền khi đọc API và trước khi phát dữ liệu; Socket.IO cũng cần phiên admin.

Socket.IO phát dashboard:update khi lấy số liệu thành công; dashboard:status khi mất Java. Khi mất server, giữ số liệu cuối và báo trạng thái.
