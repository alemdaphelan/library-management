# KHUNG WORD VÀ POWERPOINT THEO MẪU BÁO CÁO KHÓA LUẬN 2025

Tài liệu này chuyển toàn bộ minh chứng kỹ thuật của đề tài HUIT Library sang đúng bố cục của `Mau bao cao_Do an_Khoa luan_2025.docx`. Người làm Word và PowerPoint dùng đây làm khung biên soạn; không dùng thứ tự tuần 1–12 làm cấu trúc chương.

## 1. Cấu trúc bắt buộc của báo cáo

Thứ tự theo mẫu:

1. Bìa chính.
2. Bìa phụ.
3. Lời cảm ơn.
4. Nhận xét của giảng viên hướng dẫn.
5. Mục lục tự động.
6. Danh mục ký hiệu và chữ viết tắt.
7. Danh mục hình tự động.
8. Danh mục bảng tự động.
9. Mở đầu: Giới thiệu; Mục tiêu đề tài; Đối tượng và phạm vi đề tài.
10. Chương 1: Khảo sát hệ thống.
11. Chương 2: Phân tích hệ thống.
12. Chương 3: Thiết kế hệ thống.
13. Chương 4: Cài đặt hệ thống.
14. Chương 5: Thử nghiệm và triển khai.
15. Kết luận: Kết quả đạt được; Đánh giá kết quả; Hạn chế; Hướng phát triển.
16. Tài liệu tham khảo.
17. Phụ lục nếu cần: hướng dẫn cài đặt, danh mục API, kịch bản demo, bảng truy vết và kết quả chi tiết.

## 2. Thông tin người làm Word phải điền

Các trường sau không được tự suy đoán từ repository:

- Tên chính thức của đề tài bằng tiếng Việt và tiếng Anh nếu khoa yêu cầu.
- Ngành/chuyên ngành.
- Họ tên, mã sinh viên và lớp của từng thành viên.
- Họ tên giảng viên hướng dẫn.
- Tháng, năm nộp báo cáo.
- Lời cảm ơn và các thông tin xác nhận của giảng viên.
- Danh mục tài liệu tham khảo đã được nhóm kiểm tra ngày truy cập và quy cách trích dẫn.

Tên gợi ý để nhóm xác nhận: **Xây dựng hệ thống quản lý thư viện và gợi ý tài liệu thông minh cho Trường Đại học Công Thương Thành phố Hồ Chí Minh**.

## 3. Nội dung phần Mở đầu

### 3.1. Giới thiệu

Trình bày bối cảnh thư viện đại học phải quản lý đồng thời tài khoản, đầu sách, bản sao, vị trí, mượn–trả, quá hạn, công nợ, tài liệu số và nhu cầu tra cứu cá nhân hóa. Nêu vấn đề của các quy trình rời rạc: khó theo dõi trạng thái từng bản sách, dễ sai khi tính phạt/đối soát, thiếu gợi ý phù hợp và thiếu kênh hỏi đáp có nguồn.

### 3.2. Mục tiêu đề tài

- Xây dựng Backend tập trung, Web Portal/Admin và Mobile Flutter dùng API thật.
- Chuẩn hóa nghiệp vụ mượn–trả bằng state machine, transaction, idempotency và lịch sử trạng thái.
- Hỗ trợ QR cho thủ thư, không cho sinh viên tự xác nhận trả sách.
- Xây dựng gợi ý Hybrid có cold-start và lý do gợi ý.
- Xây dựng chatbot RAG có nguồn, evidence gate, fallback và kiểm soát an toàn.
- Có test, số liệu thực nghiệm và quy trình vận hành cục bộ có thể tái tạo.

### 3.3. Đối tượng và phạm vi

Đối tượng gồm sinh viên/độc giả, thủ thư, Admin, sách/bản sao/vị trí, phiếu mượn, hàng đợi, tiền phạt, tài liệu số, gợi ý và kho tri thức RAG. Phạm vi triển khai hiện tại là môi trường cục bộ; đặt phòng học, kiosk tự phục vụ, triển khai Internet và các nền tảng AI mở rộng không thuộc bản nghiệm thu mã nguồn hiện tại.

Nguồn dùng để viết phần này: mục 3 và mục 11 của `TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md`.

## 4. Chương 1 Khảo sát hệ thống

### 4.1. Mục tiêu chương

Mô tả hiện trạng, tác nhân, quy trình thư viện và các vấn đề cần giải quyết. Chương này chưa đi sâu vào class, API hoặc chi tiết cài đặt.

### 4.2. Khung đề mục đề xuất

```text
CHƯƠNG 1 KHẢO SÁT HỆ THỐNG
1.1 GIỚI THIỆU ĐƠN VỊ VÀ BỐI CẢNH
1.2 HIỆN TRẠNG NGHIỆP VỤ
1.2.1 Quản lý tài khoản và thẻ thư viện
1.2.2 Quản lý sách bản sao và vị trí
1.2.3 Quy trình đặt giữ mượn gia hạn và trả
1.2.4 Quy trình xử lý quá hạn mất hỏng và thu phạt
1.2.5 Quản lý tài liệu số kiểm kê và thông báo
1.3 CÁC VẤN ĐỀ CẦN GIẢI QUYẾT
1.4 MỤC TIÊU VÀ PHẠM VI HỆ THỐNG
1.5 YÊU CẦU NGHIỆP VỤ ĐẶC THÙ
1.6 TỔNG KẾT CHƯƠNG
```

### 4.3. Nội dung và nguồn

| Đề mục | Nội dung cần viết | Nguồn bàn giao |
|---|---|---|
| 1.1 | Vai trò của thư viện HUIT và nhóm người dùng | Đề cương `CNTT-KLCN119.docx/.txt`; tài liệu quy định thư viện |
| 1.2 | Luồng nghiệp vụ hiện tại và trạng thái cần quản lý | Business Use Case; tài liệu tuần 9 |
| 1.2.4 | Quy định quá hạn, mất/hỏng, đền bù, thanh toán, miễn giảm | `CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md` |
| 1.3 | Sai lệch, rủi ro và nhu cầu số hóa | `CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md` |
| 1.4 | Phạm vi chính thức và nội dung loại bỏ | Gói bàn giao 12C, mục 3 |
| 1.5 | 3 cuốn, 10 ngày, gia hạn 1 lần, hàng đợi 48 giờ, QR thủ thư và khóa thẻ | Chính sách 9C và test nghiệp vụ |

Không khẳng định những chi tiết tổ chức nội bộ của thư viện nếu chưa có nguồn chính thức. Quy định do người dùng cung cấp phải được ghi là quy tắc nghiệp vụ áp dụng cho hệ thống, không tự mở rộng thành quy định pháp lý.

## 5. Chương 2 Phân tích hệ thống

### 5.1. Mục tiêu chương

Chuyển yêu cầu khảo sát thành tác nhân, chức năng, Use Case, quy tắc, trạng thái và mô hình lớp phân tích. Chương này trả lời hệ thống cần làm gì.

### 5.2. Khung đề mục đề xuất

```text
CHƯƠNG 2 PHÂN TÍCH HỆ THỐNG
2.1 XÁC ĐỊNH TÁC NHÂN
2.2 YÊU CẦU CHỨC NĂNG
2.3 YÊU CẦU PHI CHỨC NĂNG
2.4 MÔ HÌNH USE CASE NGHIỆP VỤ
2.5 MÔ HÌNH USE CASE HỆ THỐNG
2.5.1 Use Case của sinh viên
2.5.2 Use Case của thủ thư
2.5.3 Use Case của quản trị viên
2.6 ĐẶC TẢ CÁC USE CASE CHÍNH
2.7 MÔ HÌNH LỚP PHÂN TÍCH
2.8 MÔ HÌNH TRẠNG THÁI MƯỢN TRẢ
2.9 TỔNG KẾT CHƯƠNG
```

### 5.3. Đầu vào đã chuẩn bị

- Bảng tác nhân và chức năng: mục 3 và mục 5 của gói bàn giao trung tâm.
- Business Use Case: `business_usecase_v41.docx` và PNG tương ứng.
- System Use Case: `system_usecase_v41.docx` và bốn PNG theo tác nhân.
- Lớp phân tích: `analysis_class_diagram.mmd`.
- State machine: `circulation_state_machine.mmd`.
- Ma trận yêu cầu–mã–test–bằng chứng: mục 8 của gói bàn giao trung tâm.

Yêu cầu phi chức năng nên gồm: phân quyền, tính toàn vẹn transaction, không lộ secret, khả năng truy vết/audit, thời gian phản hồi đo được, backup/restore và khả năng suy giảm an toàn của RAG. Chỉ dùng con số đã có trong tài liệu nguồn.

## 6. Chương 3 Thiết kế hệ thống

### 6.1. Mục tiêu chương

Trình bày hệ thống được tổ chức như thế nào: kiến trúc, dữ liệu, lớp thiết kế, giao diện, bảo mật, QR, thuật toán gợi ý và RAG.

### 6.2. Khung đề mục đề xuất

```text
CHƯƠNG 3 THIẾT KẾ HỆ THỐNG
3.1 KIẾN TRÚC TỔNG THỂ
3.2 THIẾT KẾ CƠ SỞ DỮ LIỆU
3.3 SƠ ĐỒ LỚP THIẾT KẾ
3.4 THIẾT KẾ PHÂN QUYỀN VÀ AN TOÀN
3.5 THIẾT KẾ QUY TRÌNH QR CHO THỦ THƯ
3.6 THIẾT KẾ THUẬT TOÁN GỢI Ý SÁCH
3.6.1 Popularity Baseline
3.6.2 Content Based sử dụng TF IDF và Cosine Similarity
3.6.3 Mô hình Hybrid và xử lý Cold Start
3.7 THIẾT KẾ CHATBOT RAG
3.7.1 Nguồn tri thức và lập chỉ mục
3.7.2 Truy xuất sinh câu trả lời và dẫn nguồn
3.7.3 An toàn fallback và audit
3.8 THIẾT KẾ GIAO DIỆN WEB VÀ MOBILE
3.9 TỔNG KẾT CHƯƠNG
```

### 6.3. Sơ đồ đưa vào chương

| Số hình gợi ý | Nội dung | File nguồn |
|---|---|---|
| Hình 3.1 | Kiến trúc tổng thể | `system_architecture.mmd` |
| Hình 3.2 | ERD | `huit_library_erd.mmd` |
| Hình 3.3 | Lớp thiết kế | `design_class_diagram.mmd` |
| Hình 3.4 | Luồng QR thủ thư | `librarian_qr_flow.mmd` |
| Hình 3.5 | Wireframe QR | `librarian_qr_wireframe.mmd` |
| Hình 3.6 | Pipeline gợi ý Hybrid | `recommendation_pipeline.mmd` |
| Hình 3.7 | Pipeline RAG | `rag_pipeline.mmd` |
| Hình 3.8 | Sequence truy vấn RAG | `rag_query_sequence.mmd` |

Phần giao diện chỉ chọn các màn hình đại diện theo luồng; không đưa toàn bộ ảnh chụp vào Chương 3. Ảnh kết quả chạy thật nên để ở Chương 4 hoặc Chương 5.

## 7. Chương 4 Cài đặt hệ thống

### 7.1. Mục tiêu chương

Giải thích cách thiết kế được hiện thực hóa bằng công nghệ, module, API, migration và giao diện. Chương này trả lời hệ thống được cài đặt ra sao.

### 7.2. Khung đề mục đề xuất

```text
CHƯƠNG 4 CÀI ĐẶT HỆ THỐNG
4.1 MÔI TRƯỜNG VÀ CÔNG NGHỆ
4.2 CẤU TRÚC MÃ NGUỒN
4.3 CÀI ĐẶT BACKEND VÀ CƠ SỞ DỮ LIỆU
4.3.1 Xác thực và phân quyền
4.3.2 Danh mục kho và tài liệu số
4.3.3 Mượn trả hàng đợi và QR
4.3.4 Tiền phạt công nợ và thanh toán
4.3.5 Gợi ý sách
4.3.6 Chatbot RAG
4.4 CÀI ĐẶT WEB PORTAL VÀ WEB ADMIN
4.5 CÀI ĐẶT ỨNG DỤNG MOBILE
4.6 CẤU HÌNH VẬN HÀNH CỤC BỘ
4.7 TỔNG KẾT CHƯƠNG
```

### 7.3. Nguồn và hình ảnh

- Công nghệ và cấu trúc repository: `README.md` và `backend/pom.xml`, `web-app/package.json`, `mobile-app/pubspec.yaml`.
- API chính: mục 6 của gói bàn giao trung tâm; danh sách đầy đủ dùng Swagger.
- Migration: `backend/src/main/resources/db/changelog/`.
- Triển khai cục bộ: `local_deployment.mmd`, `docker-compose.yml`, `.env.example`.
- Danh sách màn hình và kịch bản chụp: mục 9 của gói bàn giao trung tâm.

Ảnh giao diện cần ưu tiên: tra cứu/chi tiết, lịch sử và gia hạn từng tài liệu, dashboard, quầy mượn–trả, QR thủ thư, mất/hỏng–thu phạt, gợi ý sách và chatbot có nguồn. Che toàn bộ key, token, OTP, mật khẩu và dữ liệu cá nhân.

## 8. Chương 5 Thử nghiệm và triển khai

### 8.1. Mục tiêu chương

Trình bày môi trường thử nghiệm, kịch bản, kết quả, cách tái tạo và giới hạn. “Triển khai” trong bản hiện tại chỉ là Docker/vận hành cục bộ; không viết rằng hệ thống đã triển khai Internet.

### 8.2. Khung đề mục đề xuất

```text
CHƯƠNG 5 THỬ NGHIỆM VÀ TRIỂN KHAI
5.1 MÔI TRƯỜNG VÀ CHIẾN LƯỢC KIỂM THỬ
5.2 KIỂM THỬ CHỨC NĂNG VÀ PHÂN QUYỀN
5.3 KIỂM THỬ TÍCH HỢP VÀ LUỒNG E2E
5.4 KIỂM THỬ HIỆU NĂNG VÀ VẬN HÀNH
5.5 THỰC NGHIỆM THUẬT TOÁN GỢI Ý
5.5.1 Dữ liệu và phương pháp đối chứng
5.5.2 Precision Recall Hit Rate Coverage và Latency
5.5.3 Kết quả và giới hạn dữ liệu
5.6 ĐÁNH GIÁ CHATBOT RAG
5.6.1 Bộ câu hỏi và metric
5.6.2 Kết quả retrieval grounding citation và refusal
5.6.3 Fallback quota và giới hạn
5.7 TRIỂN KHAI VẬN HÀNH CỤC BỘ
5.8 TỔNG KẾT CHƯƠNG
```

### 8.3. Bảng kết quả nên có

| Số bảng gợi ý | Nội dung | Nguồn bắt buộc |
|---|---|---|
| Bảng 5.1 | Quality gate Backend Web Mobile | Tài liệu tuần 11 và trạng thái gate cuối 12D |
| Bảng 5.2 | Kịch bản HTTP E2E chính | `TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md` |
| Bảng 5.3 | Benchmark catalog | `week11_performance_2026-10-19.json` |
| Bảng 5.4 | Backup và restore | Tài liệu tuần 11 |
| Bảng 5.5 | So sánh ba thuật toán gợi ý | `recommendation_evaluation_2026-10-19.json` |
| Bảng 5.6 | Đánh giá RAG 14 ca | `rag_evaluation_2026-10-20.json` |
| Bảng 5.7 | Các giới hạn và ảnh hưởng | Tài liệu 10E, 12B và gói 12C |

Mọi con số phải đối chiếu `TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md`. Ghi rõ recommender dùng proxy chủ đề, RAG dùng controlled evaluation set và benchmark chạy trên máy phát triển.

## 9. Phần Kết luận

### 9.1. Kết quả đạt được

Tóm tắt sản phẩm theo bốn lớp: Backend và dữ liệu; Web; Mobile; thuật toán gợi ý và RAG. Nêu khả năng kiểm thử/vận hành nhưng không lặp lại toàn bộ bảng Chương 5.

### 9.2. Đánh giá kết quả

Đối chiếu mục tiêu ở Mở đầu với chức năng đã hoàn thành. Dùng ma trận yêu cầu–mã–test–bằng chứng để chứng minh, không tự đặt phần trăm hoàn thành mới.

### 9.3. Hạn chế

- Lịch sử mượn thật chưa đủ để đánh giá recommender trên hành vi thực tế.
- Bộ RAG mới gồm 14 ca kiểm soát; lượt chốt dùng fallback nhiều vì quota Gemini.
- Benchmark là cục bộ, chưa phải SLA production.
- Chưa nghiệm thu trên môi trường độc lập và chưa triển khai Internet.
- MongoDB, Kafka, Elasticsearch mới ở hướng cấu hình/mở rộng, không phải luồng dữ liệu chính hoàn tất.

### 9.4. Hướng phát triển

- Thu thập lịch sử mượn đủ lớn và đánh giá recommender theo thời gian.
- Mở rộng bộ RAG, theo dõi chất lượng và chi phí/quota.
- Triển khai domain/HTTPS, quản lý secret, monitoring và backup production.
- Nâng cao tìm kiếm, accessibility và kiểm thử thiết bị Mobile thật.
- Chỉ xem xét kiosk, voice hoặc agent sau khi yêu cầu cốt lõi ổn định.

## 10. Danh mục chữ viết tắt đề xuất

Người làm Word chỉ giữ các mục thực sự xuất hiện trong báo cáo.

| Viết tắt | Tiếng Anh | Tiếng Việt/ý nghĩa |
|---|---|---|
| API | Application Programming Interface | Giao diện lập trình ứng dụng |
| JWT | JSON Web Token | Chuẩn token xác thực |
| RBAC | Role Based Access Control | Kiểm soát truy cập theo vai trò |
| CRUD | Create Read Update Delete | Tạo đọc cập nhật xóa |
| ERD | Entity Relationship Diagram | Sơ đồ thực thể liên kết |
| ORM | Object Relational Mapping | Ánh xạ đối tượng quan hệ |
| OTP | One Time Password | Mật khẩu dùng một lần |
| QR | Quick Response | Mã phản hồi nhanh |
| RAG | Retrieval Augmented Generation | Sinh câu trả lời tăng cường truy xuất |
| TF IDF | Term Frequency Inverse Document Frequency | Trọng số từ theo tần suất |
| E2E | End to End | Kiểm thử xuyên suốt |
| CI | Continuous Integration | Tích hợp liên tục |
| SLA | Service Level Agreement | Cam kết mức dịch vụ |

## 11. Danh mục hình dự kiến

Danh sách dưới đây giúp tạo caption và danh mục hình tự động. Số cuối cùng có thể đổi khi dàn trang nhưng phải theo chương.

| Chương | Hình bắt buộc/ưu tiên |
|---|---|
| Chương 2 | Business Use Case; System Use Case sinh viên; System Use Case thủ thư; System Use Case Admin/nhân viên; lớp phân tích; state machine mượn–trả |
| Chương 3 | Kiến trúc tổng thể; ERD; lớp thiết kế; QR flow; QR wireframe; recommender pipeline; RAG pipeline; RAG sequence |
| Chương 4 | Sơ đồ triển khai cục bộ; các màn hình Web/Mobile đại diện |
| Chương 5 | Ảnh kết quả E2E/smoke nếu cần; biểu đồ so sánh recommender; biểu đồ metric RAG nếu người làm tài liệu tạo từ JSON |

Không dùng screenshot mã nguồn làm hình chính nếu sơ đồ hoặc bảng diễn đạt rõ hơn.

## 12. Danh mục bảng dự kiến

- Bảng phạm vi chức năng và nội dung loại bỏ.
- Bảng quy tắc nghiệp vụ chính.
- Bảng tác nhân và quyền.
- Bảng yêu cầu chức năng/phi chức năng.
- Bảng công nghệ sử dụng.
- Bảng nhóm API chính.
- Bảng môi trường kiểm thử.
- Bảng quality gate.
- Bảng benchmark.
- Bảng so sánh recommender.
- Bảng đánh giá RAG.
- Bảng hạn chế và hướng phát triển.

## 13. Quy cách trình bày phải tuân theo mẫu

| Thành phần | Quy định |
|---|---|
| Khổ giấy/lề | A4; trái 3,5 cm; phải 2,5 cm; trên 2,5 cm; dưới 2,5 cm |
| Font | Times New Roman cho toàn bộ nội dung |
| Tên chương | Cỡ 18, in hoa, đậm, căn giữa |
| Đề mục cấp 1 như 1.1 | Cỡ 14, in hoa, đậm |
| Đề mục cấp 2/3 | Cỡ 13, chữ thường, đậm |
| Nội dung | Cỡ 13; line spacing Multiple 1.3; Before/After 6 pt; căn đều hai bên; thụt đầu dòng |
| Bullet cấp 1 | Dấu `-`; vị trí 0,3 cm; text indent 0,8 cm |
| Bullet cấp 2 | Dấu `+`; vị trí 0,8 cm; text indent 1,3 cm |
| Caption hình | Đặt dưới hình, căn giữa, đánh số theo chương như Hình 3.1 |
| Caption bảng | Đặt trên bảng, căn giữa, đánh số theo chương như Bảng 5.1 |
| Nội dung bảng | Cỡ 12; Multiple 1.3; Before/After 0 pt; lặp tiêu đề khi qua trang |
| Số trang | Bìa không đánh số; phần đầu dùng i, ii, iii; từ Mở đầu dùng 1, 2, 3; căn giữa chân trang |
| Mục lục | Tạo tự động cho đề mục, hình và bảng; không gõ số trang bằng tay |

## 14. Khung PowerPoint đề xuất

PowerPoint nên cô đọng theo cùng mạch 5 chương để không mâu thuẫn Word.

| Slide | Nội dung | Nguồn/visual |
|---:|---|---|
| 1 | Tên đề tài, nhóm, GVHD | Bìa báo cáo |
| 2 | Bối cảnh và vấn đề | Mở đầu + Chương 1 |
| 3 | Mục tiêu, đối tượng và phạm vi | Mở đầu |
| 4 | Tác nhân và nghiệp vụ chính | Business Use Case + bảng role |
| 5 | Kiến trúc tổng thể | `system_architecture.mmd` |
| 6 | Dữ liệu và state machine | ERD + state machine |
| 7 | Các chức năng Web/Mobile | 3–4 ảnh giao diện đại diện |
| 8 | QR thủ thư và an toàn giao dịch | QR flow |
| 9 | Thuật toán gợi ý Hybrid | Recommendation pipeline + công thức |
| 10 | Chatbot RAG | RAG pipeline/sequence |
| 11 | Công nghệ và triển khai cục bộ | Local deployment |
| 12 | Chiến lược kiểm thử và E2E | Tài liệu tuần 11 |
| 13 | Kết quả recommender | Bảng/biểu đồ 3 phương pháp, kèm nhãn proxy |
| 14 | Kết quả RAG | Metric 14 ca, kèm fallback/quota |
| 15 | Kết quả đạt được và hạn chế | Kết luận |
| 16 | Hướng phát triển và luồng demo | Kết luận + checklist demo 12D |

Mỗi slide chỉ giữ một thông điệp chính. Không sao chép nguyên bảng dài từ Word; biểu đồ phải lấy từ JSON và giữ cảnh báo nguồn dữ liệu ngay trên hoặc dưới biểu đồ.

## 15. Checklist bàn giao cho người làm Word và PowerPoint

- [ ] Nhận đủ thông tin bìa và tên đề tài đã được nhóm/GVHD xác nhận.
- [ ] Dùng khung 5 chương, không dùng tuần 1–12 làm tên chương.
- [ ] Tạo style Heading và mục lục tự động ngay từ đầu.
- [ ] Tạo caption tự động cho hình/bảng và cập nhật danh mục.
- [ ] Chỉ chèn sơ đồ đã đối chiếu file nguồn.
- [ ] Chụp màn hình trên bản nghiệm thu, che dữ liệu nhạy cảm.
- [ ] Lấy metric từ file JSON/tài liệu nguồn, không lấy lại từ ảnh.
- [ ] Ghi nhãn proxy/controlled set/local benchmark đúng chỗ.
- [ ] Không tuyên bố đã triển khai Internet khi mới chạy cục bộ.
- [ ] Đối chiếu Word và PowerPoint để tên chức năng, số liệu và giới hạn thống nhất.
- [ ] Cập nhật mục lục, danh mục hình/bảng và số trang trước khi xuất PDF.
