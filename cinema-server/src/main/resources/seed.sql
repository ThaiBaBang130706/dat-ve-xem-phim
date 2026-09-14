-- Danh mục mẫu. tài khoản (hash PBKDF2), giờ chiếu tương đối và vé mẫu được tạo
-- trong Database.seed(Clock) để dữ liệu dùng được ngay vào ngày chạy lần đầu.
INSERT INTO movies(title,genre,duration_minutes,age_rating,description) VALUES
('Hẹn Nhau Ở Huế','Tình cảm',105,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Chuyến Tàu Bình Minh','Phiêu lưu',107,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Mật Mã Đại Dương','Hành động',109,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Mùa Hè Của Chúng Ta','Học đường',111,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Ngôi Nhà Cuối Phố','Bí ẩn',113,'T16','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Hành Trình Sao Hoả','Khoa học viễn tưởng',115,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Bức Thư Chưa Gửi','Gia đình',117,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Đội Bóng Xóm Nhỏ','Hài',119,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Bên Kia Cầu Vồng','Hoạt hình',121,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.'),
('Một Ngày Thật Khác','Tâm lý',123,'P','Phim minh hoạ cho đồ án CinemaBooking. Đây là dữ liệu demo, không phải lịch chiếu thương mại.');
INSERT INTO rooms(name,rows_count,cols_count) VALUES('P1',6,8),('P2',7,8),('IMAX',8,10);
