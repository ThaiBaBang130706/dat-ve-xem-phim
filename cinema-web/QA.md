# Kiểm tra bàn giao

Ngày: 15/09/2026.

## Đã thực hiện

- JavaScript qua `node --check`.
- Render chuỗi HTML của 26 màn hình bằng Node VM: không có exception hoặc giá trị `undefined` ngoài ý muốn.
- Render 4 trạng thái Loading / Empty / Error / Permission denied.
- Kiểm tra chuyển trạng thái: thêm/bỏ danh sách, vào chi tiết/tập, player lỗi/fallback, ẩn/công khai phim, mở/đóng form, đổi thứ tự tập, duyệt bình luận hàng loạt, xác nhận trước thao tác node, restart/maintenance và retry encode.
- CSS có breakpoint 767px / 1199px; grid KPI/chart dùng 12 cột, bảng cuộn trong container.
- Prototype không tải ảnh/font/script từ mạng. Poster là SVG data URL.

## Chưa xác minh

Môi trường hiện tại không có Chromium và tải trình duyệt kiểm thử bị timeout. Vì vậy chưa chụp screenshot, chưa kiểm tra DOM/layout trực tiếp trong trình duyệt, chưa kiểm tra keyboard/focus bằng trình duyệt thực. Các kiểm tra Node không thay thế kiểm thử end-to-end hoặc kiểm tra accessibility.

Tailwind theme là tài liệu ánh xạ, chưa được build với một dự án Tailwind/React cụ thể.

## Checklist duyệt trên máy

1. Mở index.html bằng Chrome/Edge, F12 → Console, không có lỗi JavaScript.
2. Thử viewport 1440×1000, 1024×768, 390×844; xác nhận không có cuộn ngang toàn trang.
3. Tab qua nút/link/input; mở modal, dùng Tab / Shift+Tab / Escape.
4. Thử search không có kết quả, dữ liệu trống, quyền bị từ chối, retry.
5. Thử thêm phim, poster preview, lọc bảng, kéo/sắp xếp bằng nút, bulk comment.
6. Thử player fallback và xác nhận hành động node.

Chạy lại kiểm tra Node: `node check.cjs`. Không cần Node để mở giao diện.
