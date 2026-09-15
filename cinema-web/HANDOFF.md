# NOIR — Bàn giao thiết kế

## 1. Hướng thiết kế

User ưu tiên khám phá nội dung: hero lớn, ít chữ trên hình, poster 2:3, tiếp tục xem nằm gần đầu trang. Admin ưu tiên thao tác: sidebar ổn định, search/filter nằm ngay trên bảng, action gắn với dòng dữ liệu. Server ưu tiên phát hiện sự cố: health kèm chữ, KPI lớn, failover nhìn được ngay và xác nhận trước hành động ảnh hưởng phiên xem.

Ba lớp dùng cùng `:root` trong styles.css. Accent chỉ dùng cho hành động chính, lựa chọn đang active và đường dữ liệu chính. Success/warning/error có nhãn chữ đi kèm, không chỉ dùng màu.

## 2. Token

| Token | Giá trị | Sử dụng |
|---|---|---|
| background | #0B0B0D | Nền trang |
| surface | #141418 | Card, bảng, modal |
| raised | #1C1C22 | Nút phụ, hover |
| border | #2A2A30 | Đường chia, viền |
| text | #F4F1EA | Nội dung chính |
| muted | #A6A6B1 | Nhãn và nội dung phụ |
| accent | #E8B84A | CTA, active, progress |
| success | #3DDC97 | Healthy, công khai, thành công |
| warning | #F5A524 | Cảnh báo, pending |
| error | #E5484D | Lỗi, tác vụ thất bại |

Nút vàng dùng chữ tối #171309. Badge lỗi/cảnh báo dùng nền tint tối và chữ sáng hơn token gốc để dễ đọc ở kích thước nhỏ. Đường viền là phân cách nhẹ, không dùng làm dấu hiệu focus; focus có viền vàng 2px, offset 4px.

Spacing: 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64 px. Radius: control 8px, card 10px, modal 12px. Grid desktop 12 cột, gutter 24px; KPI span 3, chart span 8, bảng phụ span 4.

Typography: Inter → Segoe UI → Arial → sans-serif. Display 64/65, H1 32/38, H2 22/29, H3 16/24, body 14/22, caption 12/18. Dashboard có metadata 10–11px; không dùng cỡ này cho hướng dẫn quan trọng hoặc nút chính.

## 3. Responsive

| Viewport | User | Admin / Server |
|---|---|---|
| ≥ 1200px | Header đầy đủ, hero ngang, poster rows | Sidebar 228px, KPI 4 cột, chart + panel phụ |
| 768–1199px | Header rút gọn, hàng phim cuộn | Sidebar rail 76px, tooltip tên mục, khoảng nội dung 24px |
| < 768px | Hamburger, search, hero ảnh trên/nội dung dưới | Drawer, KPI 2 cột, panel xếp dọc, bảng cuộn bên trong |

Khoảng trên mobile giảm còn 12–20px. Modal có max-height 90vh và cuộn; không đẩy toàn trang vượt viewport. Poster trên touch mở chi tiết, không phụ thuộc hover. Bảng dùng vùng overflow-x riêng; không giấu các cột thao tác.

## 4. Mapping sang React / Next

Prototype hiện là HTML/CSS/JavaScript thuần để mở trực tiếp, chưa phải dự án Next đã build. Khi chuyển sang React, giữ token/CSS và thay hàm render bằng component có props; không đưa nguyên chuỗi HTML vào dangerouslySetInnerHTML cho dữ liệu thực.

| Component | Props quan trọng | Trách nhiệm |
|---|---|---|
| Button / IconButton | variant, size, disabled, pending, onClick, ariaLabel | Primary / secondary / destructive |
| Input / Select | label, value, error, hint, required, onChange | Nhãn rõ, lỗi nối aria-describedby |
| Badge / HealthBadge | status, label | Ánh xạ status sang màu và chữ |
| Modal | open, title, onClose, children, footer | Focus trap, Escape, trả focus về trigger |
| DataTable | columns, rows, rowKey, selection, sort, pagination | Dữ liệu dense, cuộn ngang nội bộ |
| FilterBar | query, status, timeRange, onChange, onReset | Search/lọc dùng chung |
| KpiCard | label, value, unit, trend, timestamp | Có thời điểm dữ liệu |
| MoviePoster | movie, onOpen, progress? | 2:3, overlay hover/focus |
| PlayerControls | source, quality, subtitles, speed, time, status | Tách adapter HLS/DASH khỏi UI |
| Toast | tone, message, action? | aria-live; không che nút đang thao tác |
| StateBoundary | loading, error, empty, forbidden, retry | Các trạng thái nhất quán |

Route gợi ý:

- `/`, `/movies`, `/movies/[slug]`, `/watch/[id]`, `/my-list`.
- `/admin`, `/admin/movies`, `/admin/episodes`, `/admin/genres`, `/admin/users`, `/admin/comments`, `/admin/banners`, `/admin/sources`, `/admin/reports`, `/admin/settings`.
- `/ops`, `/ops/nodes`, `/ops/transcode`, `/ops/routing`, `/ops/logs`, `/ops/storage`, `/ops/settings`.

Thư mục gợi ý: `components/ui`, `components/cinema`, `components/admin`, `components/ops`, `features`, `lib/api`, `styles/tokens.css`. Tailwind theme được tách ở `tailwind-theme.css` theo cú pháp CSS-first; cần kiểm tra tương thích phiên bản khi tích hợp dự án thực.

## 5. Dữ liệu và API cần bổ sung khi triển khai

| Tính năng | Prototype | Bản triển khai |
|---|---|---|
| Tìm kiếm/lọc | Mảng trong bộ nhớ | Query API có phân trang, debounce, huỷ request cũ |
| CRUD phim/tập/banner | State trong tab | Validation backend, ID bền vững, version/concurrency guard |
| Upload poster | FileReader xem trước | Upload service kiểm MIME/size, lưu object storage, URL ảnh |
| Login/role | Thanh chuyển vai trò demo | Session thật; kiểm RBAC ở backend trên mọi endpoint |
| Player | Ảnh SVG và timeline mô phỏng | HLS/DASH player, subtitles, resume, buffering, retry có giới hạn |
| Realtime | KPI mẫu đổi mỗi 5 giây | SSE/WebSocket + timestamp + stale/disconnected state |
| Transcode | Danh sách queue mẫu | Job service, worker state, cancellation, retry idempotent |
| Restart/Drain | Xác nhận rồi đổi nhãn | Control plane auth, audit log, trạng thái pending/result |
| Routing | Sơ đồ và rule mẫu | Health checks, weighted routing, circuit breaker, failover thực |
| Analytics | Mẫu cố định | Event pipeline, định nghĩa metric, khoảng thời gian rõ ràng |

URL mẫu có đuôi `.test`. Bản thật chỉ nhận nguồn phát đã được phép, tránh gọi URL tuỳ ý trực tiếp từ dịch vụ backend. Quyền phát nội dung phải được quản lý bằng ngày bắt đầu/kết thúc, vùng và quyền truy cập.

## 6. Trạng thái và hành vi UX

- Loading: skeleton giữ kích thước khối, aria-busy.
- Empty: lý do + hành động tiếp theo; khác với lỗi mạng.
- Error: thông báo cụ thể, giữ dữ liệu/form nếu có, nút retry.
- Permission denied: không hiển thị dữ liệu nhạy cảm; đường quay lại.
- Destructive: nêu đối tượng, ảnh hưởng, nút huỷ và xác nhận.
- Bảng không có kết quả: giữ bộ lọc, có nút bỏ lọc.
- Drag/drop có nút ↑ ↓ tương đương, dùng được bằng touch/bàn phím.
- Modal hỗ trợ Escape và vòng Tab trong hộp thoại; đóng trả focus nếu trigger còn tồn tại.
- Chuyển nguồn lỗi trong player: giữ bối cảnh, thông báo nguồn dự phòng; mô phỏng không reset tiến trình.

## 7. Giới hạn thiết kế hiện tại

Series minh hoạ một season; chưa có editor metadata cho nhiều season. Các thông số biểu đồ, quyền nội dung, lịch sử xem và số người online là mẫu. Chưa có hệ thống đăng nhập, billing, DRM, phụ đề thực, giám sát thật hoặc thao tác hạ tầng thật. Hero hiện minh hoạ một vị trí chính; màn quản trị có sắp xếp và lịch banner mẫu.

Bộ này phục vụ duyệt thiết kế và thống nhất cách implement; không mô tả là nền tảng streaming đã vận hành.
