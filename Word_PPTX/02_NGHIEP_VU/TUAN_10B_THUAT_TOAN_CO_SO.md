# TUẦN 10B — THUẬT TOÁN GỢI Ý CƠ SỞ

Ngày hoàn thành: 19/10/2026.

## 1. Phạm vi

Gói 10B xây hai baseline độc lập để làm nền đối chứng cho mô hình Hybrid ở 10C:

1. Popularity Baseline dựa trên lịch sử mượn hợp lệ.
2. Content-Based cho mục “sách tương tự” bằng TF-IDF và Cosine Similarity.

Gói này chỉ thay đổi Backend/API, chưa thay giao diện Web hoặc Mobile. Cá nhân hóa theo sinh viên, loại sách sinh viên đã mượn và cache thuộc 10C.

## 2. Popularity Baseline

Chỉ tính các chi tiết ở trạng thái `BORROWING`, `OVERDUE`, `RETURNED`, `LOST`, `DAMAGED` hoặc đã có ngày trả; bỏ phiếu `CANCELLED` và `REJECTED`.

Điểm thô của sách là tổng trọng số suy giảm theo thời gian của từng lượt mượn:

```text
rawPopularity(book) = Σ 0.5 ^ (ageDays / halfLifeDays)
halfLifeDays = max(30, windowDays / 4)
```

Điểm trả về được chuẩn hóa bằng log để sách quá phổ biến không lấn át hoàn toàn:

```text
popularityScore = log(1 + rawPopularity) / log(1 + maxRawPopularity)
```

- `limit` được chặn trong khoảng 1–50.
- `windowDays` được chặn trong khoảng 30–3.650 ngày.
- Dữ liệu chỉ được coi là đủ khi có ít nhất 10 đầu sách và 20 lượt mượn hợp lệ trong cửa sổ.
- Nếu chưa đủ kết quả, API bù sách từ danh mục và ghi rõ lý do fallback; các sách này có điểm 0, không bị trình bày sai thành kết quả popularity.

## 3. TF-IDF và Cosine Similarity

Candidate được lấy theo thứ tự:

1. Sách chung taxonomy chủ đề từ 10A.
2. Sách chung category catalog.
3. Danh mục chung để bù candidate.

Candidate pool tối đa 1.500 sách để giới hạn bộ nhớ và thời gian tính. Cuốn đang xem luôn bị loại khỏi kết quả.

Trọng số metadata:

| Trường | Trọng số |
|---|---:|
| Taxonomy chủ đề | 4 |
| Tên sách | 3 |
| Category catalog | 2 |
| Tác giả | 1 |
| Nhà xuất bản | 1 |

Pipeline chuẩn hóa chữ thường, bỏ dấu tiếng Việt, loại stop-word cơ bản, sinh unigram và bigram. TF dùng `1 + log(frequency)`; IDF được làm trơn bằng `log((N + 1) / (df + 1)) + 1`. Các vector sau đó được xếp hạng bằng Cosine Similarity.

## 4. API

```http
GET /books/recommendations?limit=10&windowDays=365
GET /books/recommendations/popular?limit=10&windowDays=365
GET /books/{bookId}/similar?limit=10
```

Response thống nhất gồm:

- `method`: `POPULARITY_BASELINE` hoặc `TF_IDF_COSINE`.
- `dataSufficient`: không che giấu tình trạng dữ liệu thưa.
- `candidateCount`: số candidate thực tế được xét.
- `generatedAt` và danh sách `items`.
- Mỗi item có metadata sách, `score`, `borrowCount` và `reason`.

## 5. Kết quả kiểm chứng

- Câu truy vấn time-decay đã chạy trực tiếp trên PostgreSQL Docker và nhận đúng 2 đầu sách có lịch sử mượn; API trả `dataSufficient=false` rồi bù danh mục.
- Với sách nguồn `Elements of Programming With Perl`, 5 kết quả đầu đều là sách Perl/Programming liên quan; điểm Cosine lớn nhất là `0.443164`.
- Đã xử lý lỗi PostgreSQL `DISTINCT/ORDER BY` được phát hiện khi smoke test dữ liệu thật bằng cách sắp xếp qua alias join của `BookEntity`.
- Unit test mới kiểm tra thứ hạng nội dung, metadata nguồn trống, chuẩn hóa popularity, fallback dữ liệu thưa và loại sách nguồn.
- Toàn bộ Backend: **93/93 test qua**, gồm Testcontainers PostgreSQL/Redis/Mailpit.

## 6. Giới hạn còn lại

- Lịch sử thật hiện chỉ có 2 lượt mượn nên chưa đủ công bố Precision@K hoặc kết luận baseline tốt/xấu.
- Chưa tạo hồ sơ sở thích cá nhân, chưa dùng chuyên ngành để tăng điểm và chưa loại sách đã mượn của người đang đăng nhập; đây là 10C.
- Chưa cache kết quả; endpoint sách tương tự hiện tính tối đa 1.500 candidate mỗi lần. Cache và invalidation thuộc 10C.
- Web/Mobile chưa gọi các API mới; việc tích hợp giao diện thuộc 10D.
