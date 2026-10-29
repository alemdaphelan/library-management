# TUẦN 10A — NỀN DỮ LIỆU CHO HỆ THỐNG GỢI Ý

Ngày chốt: 19/10/2026.

## 1. Kết quả kiểm kê dữ liệu ban đầu

- Catalog có 269.922 sách và 8 category cũ.
- Category cũ không đủ tin cậy cho gợi ý học thuật: nhóm `Khoa học` chiếm 61,14%; nhiều sách được gán bằng quy tắc dự phòng theo `book_id` thay vì nội dung.
- Toàn bộ sách thiếu `language` và `ddc_call_number`; chưa có `summary` hoặc bộ từ khóa chủ đề chính thức.
- Database vận hành chỉ có 2 chi tiết mượn, 2 người mượn và chưa có lượt gia hạn, chưa đủ để công bố Precision/Recall.
- Người dùng hiện có 12 tài khoản mang giá trị `Khoa Công nghệ thông tin` và 7 tài khoản chưa có khoa.

## 2. Quyết định thiết kế

Không ghi đè 8 category cũ vì chúng đang phục vụ bộ lọc catalog trên Web/Mobile. Module gợi ý dùng lớp chủ đề riêng, hỗ trợ một sách thuộc nhiều chủ đề và luôn lưu:

- nguồn phân loại;
- độ tin cậy từ 0 đến 1;
- bằng chứng phân loại;
- chủ đề chính nếu một sách khớp nhiều quy tắc.

Ba bảng mới:

1. `recommendation_subjects`: taxonomy học thuật ổn định.
2. `department_subject_affinities`: ánh xạ ngành/chuyên ngành sang chủ đề cùng trọng số.
3. `book_subject_assignments`: ánh xạ nhiều-nhiều giữa sách và chủ đề.

Thiết kế này cho phép Admin/import dữ liệu chuẩn hơn về sau mà không cần sửa hoặc xóa category catalog hiện có.

## 3. Taxonomy và ánh xạ HUIT

Migration 17 tạo 13 nhóm chủ đề:

1. Công nghệ thực phẩm.
2. Kinh doanh và Marketing.
3. Tài chính và Kế toán.
4. Luật.
5. Hóa - Sinh - Môi trường.
6. CNTT - Dữ liệu - AI.
7. Dệt may và Thời trang.
8. Cơ khí - Điện - Tự động hóa.
9. Ẩm thực và Dinh dưỡng.
10. Du lịch và Dịch vụ.
11. Ngoại ngữ.
12. Logistics và Quản lý công nghiệp.
13. Kiến thức đại cương và kỹ năng.

Có 46 quan hệ trọng số cho 40 khóa tra cứu, bao phủ 39 ngành đào tạo HUIT năm 2026 và alias `Khoa Công nghệ thông tin` đang tồn tại trong database. Một ngành liên ngành có thể nhận nhiều chủ đề, ví dụ Thương mại điện tử nhận cả Kinh doanh và CNTT.

Nguồn đối chiếu: [Chương trình đào tạo đại học chính quy khóa 26 năm 2026 của HUIT](https://ts.huit.edu.vn/ctdtdh/chuong-trinh-dao-tao-dai-hoc-chinh-quy-khoa-26-nam-2026).

## 4. Phân loại sách an toàn

Migration chỉ tạo assignment khi tiêu đề khớp bộ từ khóa có ranh giới từ rõ ràng. Không dùng chuỗi con ngắn như `ai`, không phân loại ngẫu nhiên và không thay đổi `books.category_id`.

Kết quả trên database hiện tại:

| Chỉ số | Giá trị |
|---|---:|
| Tổng chủ đề | 13 |
| Quan hệ ngành–chủ đề | 46 |
| Sách có ít nhất một chủ đề tin cậy cao | 4.387 |
| Tổng assignment | 4.466 |
| Độ phủ catalog ban đầu | 1,63% |

Độ phủ 1,63% là chủ ý: giai đoạn 10A ưu tiên nhãn đúng hơn số lượng lớn nhưng sai. Những sách chưa được gắn chủ đề vẫn có thể tham gia Content-Based ở 10B qua title, author, publisher và category, nhưng chưa được cộng điểm chuyên ngành cho đến khi có nhãn đáng tin cậy.

## 5. An toàn migration và kiểm thử

- Category và sách cũ được giữ nguyên.
- Ràng buộc database chặn trọng số hoặc confidence ngoài khoảng `(0, 1]`.
- Chuẩn hóa tên ngành bỏ dấu, ký tự phân cách và khoảng trắng thừa trước khi tra cứu.
- Migration 16 được làm sạch dữ liệu orphan trước khi tạo khóa ngoại: tài liệu số vẫn được giữ, `book_id` không còn hợp lệ được chuyển thành `NULL`; log truy cập không còn tài liệu cha mới bị loại.
- Backend đạt 89/89 test; migration 16–17 đã chạy thành công trên PostgreSQL Testcontainers và database Docker hiện tại.

## 6. Phần còn lại chuyển sang 10B–10E

- 10B xây Popularity Baseline, TF-IDF/Cosine và sách tương tự.
- 10C kết hợp điểm nội dung, ngành và popularity; xử lý cold-start và lý do gợi ý.
- 10D tích hợp Web/Mobile, giữ giao diện xanh dương–trắng.
- 10E đã cài audit tách theo thời gian và xác nhận dữ liệu vận hành chỉ có 2 lượt mượn, nên không công bố Precision/Recall như số liệu thật; bảng đối chứng được ghi nhãn riêng là proxy chủ đề.

Không tạo lịch sử mượn giả trong database vận hành. Nếu cần dữ liệu mô phỏng cho thực nghiệm, tập đó phải tách riêng và ghi rõ là synthetic trong báo cáo.
