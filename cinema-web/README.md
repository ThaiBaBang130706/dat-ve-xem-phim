# VKU Cinema — Cinema UI Kit

Prototype tương tác cho **User · Content Admin · Server Operations**, cùng một design system dark cinema. Đây là bộ giao diện web xem phim độc lập; chưa tích hợp vào ứng dụng Java TCP đặt vé rạp.

## Mở trên máy anh

1. Cập nhật repo hoặc giải nén ZIP mới tải từ GitHub, rồi mở thư mục `cinema-web`.
2. Nhấp đúp **index.html**, chọn Chrome hoặc Edge nếu Windows hỏi.
3. Dùng thanh trên cùng để chuyển **User / Admin / Server / UI kit**.
4. Chọn **Mặc định / Loading / Empty / Error / Không có quyền** để xem từng trạng thái.

Không cần chạy Maven, Java, npm hoặc server. Không cần internet để xem prototype: CSS, JavaScript và poster SVG đều nằm trong file.

## Nhận diện VKU

Đỏ `#E31C23` cho CTA/progress; vàng `#F5C518` cho rating/focus; xanh `#0057B8` cho điều hướng. Nền tối `#0B0D12`, surface `#161A22`. Logo ba dải màu là biểu tượng riêng cho đồ án, lấy cảm hứng VKU, không phải logo chính thức của trường.

Header có tìm kiếm ở giữa; menu nằm tại nút ba gạch. Poster dạng lưới 6/4/2 cột theo desktop/tablet/mobile, tỉ lệ 2:3, hover glow vàng. Ba giao diện User/Admin/Server có bố cục và chức năng riêng.

## Các màn hình

| Lớp | Màn hình |
|---|---|
| User | Khám phá, danh mục, danh sách cá nhân, chi tiết phim, player |
| Admin | Dashboard, phim, tập/season, thể loại, người dùng, bình luận, banner, nguồn phát, báo cáo, cài đặt |
| Server | Tổng quan cụm, node, nguồn/transcode, routing/failover, log/cảnh báo, lưu trữ/CDN, cài đặt |
| UI kit | Token, component, trạng thái UX, hướng dẫn bàn giao |

## Thử các luồng chính

- **User:** tìm “bình minh” → mở chi tiết → thêm danh sách → Xem ngay → Thử lỗi nguồn → Chuyển sang nguồn dự phòng.
- **Admin / Phim:** thêm phim → nhập tiêu đề, năm, slug → chọn ảnh poster để xem trước → lưu → tìm lại phim → sửa hoặc ẩn.
- **Admin / Tập:** kéo một dòng; hoặc dùng nút ↑ ↓ nếu dùng bàn phím/mobile. Mở Sửa nguồn để nhập URL mẫu.
- **Admin / Bình luận:** chọn nhiều dòng → Duyệt / Ẩn / Xoá. Xoá cần xác nhận.
- **Admin / Banner:** đổi lịch; hệ thống chặn ngày kết thúc trước ngày bắt đầu. Kéo hoặc dùng ↑ ↓ để đổi thứ tự.
- **Admin / Người dùng:** xem lịch sử mẫu, đổi vai trò, khoá/mở. Tài khoản admin hiện tại được giữ quyền trong bản mẫu.
- **Server / Node:** Restart node đỏ → đọc ảnh hưởng → xác nhận. Chưa xác nhận thì trạng thái không thay đổi.
- **Server / Routing:** Thử node down để xem đường dự phòng. Sửa rule; backup phải khác node chính.
- **Server / Transcode:** Retry tác vụ thất bại để đưa lại vào hàng đợi mẫu.
- **Server / Log:** tìm node, chọn mức độ, lọc từ một giờ cụ thể.
- **UI kit:** xem màu/spacing/component; tải JSON token; thử modal và toast.

## Tệp bàn giao

- `index.html`: bản gộp, mở trực tiếp và chạy offline.
- `source.html`, `styles.css`, `app.js`: cùng prototype nhưng tách tệp để sửa trong IDE.
- `tokens.json`: màu, spacing, bo góc, grid, breakpoint.
- `tailwind-theme.css`: ánh xạ token sang CSS theme để chuyển sang Tailwind.
- `HANDOFF.md`: cấu trúc component, route, responsive, API boundary và giới hạn.
- `check.cjs`: kiểm tra render chuỗi HTML và một số chuyển trạng thái bằng Node.
- `QA.md`: phạm vi kiểm tra đã thực hiện và phần chưa xác minh.

Khi sửa mã nguồn tách tệp, mở `source.html`. `index.html` là snapshot gộp; muốn đồng bộ lại, chạy `python3 bundle.py`.

## Phạm vi

Phim, poster, tài khoản, URL, lượt xem, log và node là dữ liệu mẫu. Các thay đổi chỉ tồn tại trong bộ nhớ của tab; tải lại trang sẽ đặt lại. Player mô phỏng giao diện và tiến trình, **không phát phim thật**. Không có upload lên server, transcode, lệnh restart thật hoặc phân quyền backend.

Poster là minh hoạ vector do bộ prototype tạo, không sử dụng poster phim thương mại. Font dùng font hệ thống nếu máy không có Inter.

