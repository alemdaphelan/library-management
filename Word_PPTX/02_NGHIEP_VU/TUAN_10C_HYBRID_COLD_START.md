# TUẦN 10C — HYBRID, COLD-START VÀ CACHE

Ngày hoàn thành: 19/10/2026.

## 1. Phạm vi

Gói 10C bổ sung API cá nhân hóa cho người dùng đã đăng nhập, giữ nguyên các baseline 10B để làm đối chứng. Việc tích hợp Web và Mobile đã được thực hiện tiếp ở gói 10D.

```http
GET /books/recommendations/personalized?limit=10
Authorization: Bearer <JWT>
```

Gọi không có JWT bị từ chối. `limit` được chặn trong khoảng 1–50.

## 2. Công thức Hybrid

Trọng số mặc định:

```text
HybridScore = 0,50 × ContentScore
            + 0,35 × DepartmentScore
            + 0,15 × PopularityScore
```

Các trọng số nằm trong `application.yml`, có thể thay bằng biến môi trường và được chuẩn hóa lại nếu tổng khác 1. Response trả cả tổng điểm lẫn ba điểm thành phần để phục vụ giải thích và thực nghiệm 10E.

### ContentScore

Hồ sơ sở thích là trung bình có trọng số của vector TF-IDF các sách đã mượn. Trọng số tương tác gồm:

- Độ mới với chu kỳ bán rã 180 ngày.
- Số lượt mượn cùng đầu sách.
- Mỗi lần gia hạn tăng hệ số 25%.

Các sách đã từng mượn được loại hoàn toàn khỏi candidate trước khi xếp hạng.

### DepartmentScore

Điểm ngành dùng `department_subject_affinities.weight × book_subject_assignments.confidence`, lấy mức phù hợp cao nhất của sách. Tên ngành được chuẩn hóa bằng cùng quy tắc của 10A.

### PopularityScore

Dùng lại baseline time-decay và log normalization của 10B trong cửa sổ mặc định 365 ngày.

## 3. Candidate và cold-start

Candidate được gom theo thứ tự:

1. Chủ đề từ ngành và lịch sử mượn.
2. Category của các sách đã mượn.
3. Sách popularity.
4. Danh mục đã phân loại và fallback catalog.

Candidate pool mặc định tối đa 3.000 sách.

- Có lịch sử: trả `HYBRID_PERSONALIZED`.
- Chưa có lịch sử nhưng có ngành: trả `HYBRID_COLD_START`, ưu tiên sách đúng ngành.
- Thiếu cả ngành và lịch sử: dùng popularity rồi catalog fallback và ghi rõ lý do.
- Sách mới chưa có lượt mượn vẫn có thể được chọn nhờ taxonomy hoặc metadata nội dung.

## 4. Cache và invalidation

- Kết quả lưu Redis với TTL mặc định 30 phút.
- Cache key chứa user, limit và fingerprint SHA-256 rút gọn của ngành cùng toàn bộ lịch sử tương tác.
- Khi phát sinh mượn, trả hoặc gia hạn làm thay đổi lịch sử/timestamp, fingerprint đổi và kết quả cũ không còn được đọc.
- Thay đổi catalog/taxonomy được đồng bộ chậm nhất theo TTL.
- Redis lỗi thì API chạy fail-open và vẫn tính kết quả trực tiếp.

## 5. Kết quả kiểm chứng trên dữ liệu thật

### Sinh viên có lịch sử

- Method: `HYBRID_PERSONALIZED`.
- Xét 3.000 candidate.
- Sách lịch sử ID `22` không xuất hiện trong top 10; overlap bằng 0.
- Kết quả đầu có `departmentScore = 0,95` và lý do phù hợp khoa Công nghệ thông tin.

### Sinh viên mới

- Method: `HYBRID_COLD_START`.
- Xét 1.640 candidate.
- Kết quả đầu là `Elements of Programming With Perl`, `departmentScore = 0,95`.
- Lý do: gợi ý khởi đầu theo chuyên ngành Công nghệ thông tin.

### Cache và bảo mật

- Lần gọi thứ hai đọc được cùng kết quả qua Redis.
- TTL quan sát sau smoke test còn 1.784 giây, đúng cấu hình gần 30 phút.
- Gọi endpoint không có JWT nhận HTTP 403.
- Full Backend regression: **97/97 test qua**.

## 6. Phần còn lại

- 10D: đã kết nối API cá nhân hóa vào Web Portal và Flutter, giữ màu xanh dương–trắng; xem `TUAN_10D_TICH_HOP_GIAO_DIEN_GOI_Y.md`.
- 10E: đã audit chia tập theo thời gian và so sánh Popularity/Content/Hybrid trên bộ proxy bằng Precision/Recall/HitRate@K, Coverage và latency; xem `TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md`.
- Dữ liệu mượn thật vẫn rất thưa; chưa dùng kết quả smoke test để tuyên bố chất lượng khoa học của mô hình.
