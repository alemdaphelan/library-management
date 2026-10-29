# ADR-001: Chiến lược tìm kiếm sách

- **Trạng thái:** Đã chấp nhận
- **Ngày quyết định:** 17/10/2026
- **Phạm vi:** Tuần 6 đến trước giai đoạn tối ưu tuần 10

## Bối cảnh

Dự án đã có dependency và service Docker cho Elasticsearch, nhưng API tìm kiếm đang hoạt động ổn định bằng PostgreSQL/JPA trên các trường tiêu đề, ISBN và tác giả. Hiện chưa có index mapping, cơ chế đồng bộ khi sách thay đổi, quy trình reindex hoặc kiểm thử tính nhất quán cho Elasticsearch.

Việc đưa Elasticsearch vào luồng chính ngay lúc này sẽ tạo thêm hai nguồn dữ liệu cần đồng bộ trong khi các nghiệp vụ mượn–trả của tuần 9 và gợi ý sách của tuần 10 vẫn chưa hoàn chỉnh.

## Quyết định

PostgreSQL/JPA là công cụ tìm kiếm chính thức cho catalog trong phạm vi tuần 6. Elasticsearch được giữ ở trạng thái hạ tầng thử nghiệm, chưa phải dependency bắt buộc của luồng nghiệp vụ.

Quyết định này được xem xét lại ở tuần 10 chỉ khi có ít nhất một trong các nhu cầu sau:

- Cần tìm kiếm toàn văn trên `summary` hoặc nội dung dài, có xếp hạng độ liên quan.
- Cần xử lý không dấu, fuzzy search hoặc typo tốt hơn PostgreSQL baseline.
- Dữ liệu đạt khoảng 100.000 đầu sách hoặc truy vấn catalog có p95 vượt 300 ms trong phép đo lặp lại.
- Thuật toán gợi ý cần dùng khả năng truy xuất/vector search mà PostgreSQL hiện tại không đáp ứng.

## Cách triển khai hiện tại

- `BookRepository` tiếp tục là nguồn dữ liệu duy nhất cho API catalog.
- Tìm kiếm, lọc thể loại và phân trang phải có test trên PostgreSQL thật.
- Không ghi trực tiếp vào Elasticsearch từ controller hoặc service nghiệp vụ tuần 6.
- Elasticsearch không được dùng làm lý do chặn demo catalog nếu PostgreSQL đang sẵn sàng.

## Điều kiện nếu kích hoạt Elasticsearch sau này

Trước khi chuyển traffic tìm kiếm sang Elasticsearch cần hoàn tất đồng thời:

1. Khai báo index mapping có version cho sách.
2. Đồng bộ create/update/delete bằng outbox hoặc consumer có retry; không dual-write trực tiếp thiếu cơ chế phục hồi.
3. Có lệnh reindex toàn bộ từ PostgreSQL và kiểm tra số lượng/bản ghi lỗi.
4. Có fallback về PostgreSQL khi Elasticsearch không sẵn sàng.
5. Có integration test về tính nhất quán và benchmark chứng minh lợi ích.

## Hệ quả

Tuần 6 giảm rủi ro vận hành và có một nguồn dữ liệu nhất quán. Đổi lại, tìm kiếm nâng cao được hoãn có chủ đích đến khi có dữ liệu và yêu cầu đo được; dependency Elasticsearch hiện có có thể được dọn bỏ nếu đến cuối tuần 10 vẫn không được sử dụng.
