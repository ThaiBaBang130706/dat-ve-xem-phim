# Kịch bản demo trước lớp

## Chuẩn bị

Chạy server, Node và hai cửa sổ JavaFX. Đăng nhập user1/user2; trình duyệt dùng admin. Mở cùng một suất ở cả hai client, chọn ghế B3/B4 còn trống. Lịch seed tạo ngày mai/ngày kia ở lần chạy database đầu tiên, nên kiểm tra trước buổi trình bày.

Các tình huống dưới đây là **kịch bản để nhóm tự chạy và ghi kết quả quan sát**, không phải số liệu tự đánh giá đã chạy trên máy của nhóm.

| Tình huống | Thao tác | Kết quả cần nhìn thấy |
|---|---|---|
| Cùng chọn B3 | A và B chọn B3 trước khi bấm giữ; bấm giữ gần nhau | Chỉ một máy giữ thành công; máy kia nhận ghế bận hoặc nút bị cập nhật trạng thái |
| Hai ghế, một ghế bận | A giữ B3; B cố giữ B3 và B4 | B không giữ được cả bộ; B4 vẫn trống |
| Thanh toán | Máy đang giữ B3 bấm thanh toán demo | B3 đỏ ở hai máy; có mã vé và QR; dashboard tăng doanh thu |
| Mất kết nối | Giữ B4 rồi đóng client | B4 trả trống; rút mạng có thể cần tối đa khoảng 65 giây |
| Hết hạn | Giữ ghế và chờ đủ 5 phút | Tự trả ghế, có thông báo; không mua được bằng phiên giữ cũ |
| Huỷ vé | Vào Vé của tôi, chọn đơn và huỷ trước giờ chiếu | Đơn huỷ, ghế xanh, doanh thu demo giảm |
| Quyền quản trị | Đăng nhập user1 rồi gọi lệnh admin trong test/API | Server từ chối dù client tự gửi tên lệnh |
| Trùng lịch | Admin thêm suất cùng phòng trùng giờ | Bị từ chối, không làm mất lịch cũ |
| Mất Java server | Tắt Java nhưng để dashboard mở | Dashboard báo mất kết nối và giữ số liệu lần trước |

Kịch bản trên giao diện cho thấy cập nhật giữa hai máy. Kiểm thử tự động bổ sung trường hợp 12 luồng tranh một ghế và gửi xác nhận lặp cùng requestId, vì hai lần bấm tay khó tạo thời điểm thật sát nhau.

## Thứ tự trình bày khoảng 10–12 phút

1. Bài toán ghế B3 và lý do dùng Client/Server — 1 phút.
2. Sơ đồ JavaFX → Java TCP → SQLite, vai trò dashboard — 2 phút.
3. Demo tranh ghế, mua vé, huỷ vé trên hai cửa sổ — 4 phút.
4. Giải thích khoá, transaction, hạn giữ và requestId — 2 phút.
5. Mở bảng dữ liệu/kiểm thử, nêu giới hạn — 2 phút.

Có thể giữ một ghế ngay đầu buổi rồi quay lại sau 5 phút để demo hết hạn mà không cần đứng chờ. Không sửa thời gian giữ trong code chỉ để có kết quả nhanh.

## Phân công gợi ý

| Quy mô | Thành viên A | Thành viên B | Thành viên C |
|---|---|---|---|
| 2 SV | TCP, xử lý ghế, SQLite, test | JavaFX, dashboard, demo/slide | — |
| 3 SV | TCP và thuật toán giữ ghế | JavaFX và luồng đặt/hủy vé | SQLite, dashboard, kiểm thử/demo |

Thay ký hiệu A/B/C bằng tên và MSSV thật. Mỗi người nên giải thích được luồng B3 từ khi click tới lúc commit, không chỉ phần màn hình mình phụ trách.

## Câu hỏi dễ được hỏi

**Đã có khoá Java, sao còn transaction?** Khoá điều phối các luồng trong tiến trình; transaction bảo đảm booking, tickets và trạng thái ghế cùng thành công hoặc cùng rollback. Index UNIQUE là lớp bảo vệ dữ liệu thêm.

**Server hỏng thì sao?** Chưa có dự phòng. Khởi động lại giữ vé đã bán và trả ghế HELD của phiên cũ; người dùng phải đăng nhập lại.

**Bấm thanh toán mà mất phản hồi có mua hai lần không?** Cùng user/requestId trả lại đơn cũ. Trước khi đặt lại bằng yêu cầu mới, xem Vé của tôi.

**Sao không gọi đây là hệ phân tán?** Trạng thái đặt vé vẫn do một Java server quyết định. Nhiều máy giao diện hoặc thêm dashboard không đồng nghĩa có cụm server phân tán.

**Tiền và QR có thật không?** Không thu tiền. QR chứa mã vé demo, chưa có dịch vụ soát vé.
