# HỒ SƠ CHỐT PHẠM VI VÀ TRUY VẾT TUẦN 1 ĐẾN TUẦN 5

> **Dự án:** Hệ thống quản lý thư viện HUIT tích hợp gợi ý sách
> **Mốc rà soát:** 17/10/2026
> **Gói công việc:** 1A chốt phạm vi và truy vết; 1B đồng bộ Use Case, sơ đồ, công nghệ và thiết kế QR
> **Căn cứ:** `CNTT-KLCN119.docx`, `CNTT-KLCN119.txt`, các tài liệu Use Case/sơ đồ trong `Docs` và mã nguồn Backend, Web, Mobile hiện tại

## 1. Mục tiêu và giới hạn của gói 1A

Gói 1A tạo một đường chuẩn duy nhất để tiếp tục đồ án mà không phải triển khai thêm chức năng. Kết quả của gói gồm:

1. Chốt chức năng bắt buộc, tạm hoãn và loại khỏi phạm vi.
2. Truy vết yêu cầu từ Business/System Use Case đến API, màn hình và kiểm thử hiện có.
3. Chỉ ra các điểm tài liệu, ERD và sơ đồ thiết kế không còn khớp mã nguồn.
4. Đánh giá lại tuần 1–5 bằng bằng chứng có trong repository.

Gói 1A không sửa trực tiếp hai file Word Use Case và không vẽ lại ba sơ đồ Mermaid. Gói 1B đã dùng bảng truy vết này làm chuẩn để cập nhật các tài liệu đó, đồng thời bổ sung sequence và wireframe cho QR thủ thư.

## 2. Phạm vi chính thức

### 2.1. Bắt buộc hoàn thành

| Nhóm | Phạm vi tối thiểu phải demo được |
|---|---|
| Tài khoản và phân quyền | Đăng nhập/đăng xuất, JWT/refresh token, khôi phục và đổi mật khẩu, hồ sơ, phân quyền Admin/Librarian/Student |
| Sinh viên | Tra cứu và quản trị sinh viên; Admin được ghi, Librarian chỉ đọc |
| Danh mục và kho | Sách, thể loại, bản sao, barcode, nhà cung cấp, phiếu nhập, tầng/kệ/ngăn và kiểm kê |
| Mượn–trả | Đặt giữ, duyệt/từ chối, checkout, gia hạn, trả, quá hạn, tiền phạt và thông báo |
| Web | Portal sinh viên và giao diện quản trị dùng dữ liệu thật |
| Mobile | Luồng sinh viên dùng API thật; thủ thư có luồng quét QR để nhận diện và chuyển sang checkout/return |
| Tương tác | Đề xuất mua sách được giữ; đánh giá và wishlist là phần mở rộng sau luồng bắt buộc |
| Tài liệu số | Upload/download, metadata và phân quyền truy cập cơ bản |
| Báo cáo | Dashboard dữ liệu thật và báo cáo kiểm kê cơ bản |
| Gợi ý sách | Baseline content-based/hybrid, có top-K và đánh giá tối thiểu |
| Chất lượng | Test tự động, E2E luồng chính, Docker/CI, tài liệu cài đặt và kịch bản demo |

### 2.2. Loại khỏi phạm vi

| Hạng mục | Quyết định | Cách xử lý phần code/tài liệu còn lại |
|---|---|---|
| Đặt phòng học/không gian | Loại hoàn toàn | Không triển khai tiếp, không tính vào tiến độ; đã gỡ khỏi Use Case/ERD/sơ đồ chính |
| Nhận/trả phòng và vi phạm phòng | Loại hoàn toàn | Entity, schema hoặc endpoint `rooms` hiện có được xem là legacy; chưa xóa trong gói 1A để tránh phát sinh migration ngoài phạm vi |

### 2.3. Tạm hoãn hoặc chỉ làm khi còn thời gian

| Hạng mục | Quyết định |
|---|---|
| Kiosk tự phục vụ | Tạm hoãn; QR của thủ thư không đồng nghĩa với kiosk |
| Chatbot Gemini/RAG | Tạm hoãn đến sau khi lõi mượn–trả và recommender đạt gate |
| Đồng bộ thời gian thực với hệ thống trường | Tạm hoãn; chỉ giữ import/sync có kiểm soát nếu thật sự cần |
| Giao ca thủ thư | Tạm hoãn |
| Đặt cọc, hoàn cọc và đối soát tài chính nâng cao | Chỉ đưa trở lại nếu giảng viên xác nhận bắt buộc; tiền phạt mượn–trả vẫn thuộc lõi |
| BI/export nâng cao và thuật toán AI phức tạp | Làm sau baseline và E2E |

## 3. Ma trận truy vết yêu cầu

Quy ước: **Đạt** = có luồng thật và bằng chứng kiểm thử phù hợp; **Gần đạt** = phần chính đã dùng dữ liệu thật nhưng còn thiếu một phần nhỏ; **Một phần** = có nền móng nhưng chưa hoàn chỉnh end-to-end; **Chưa làm** = stub, mock hoặc chưa có; **Loại** = không còn trong phạm vi.

| ID | Yêu cầu | Business/System Use Case liên quan | Backend/API hiện có | Web/Mobile hiện có | Bằng chứng test | Trạng thái và đích xử lý |
|---|---|---|---|---|---|---|
| REQ-01 | Xác thực, hồ sơ và phân quyền | BUC01; UC01, UC04, UC34 | `/api/v1/auth/*` | Web Auth/Profile; Mobile Login/Profile/Change Password | `AuthControllerTest`, `AuthDtoValidationTest`, `AuthServiceTest`, `UserAuthorizationContractTest` | **Gần đạt**; tài liệu đã chốt phạm vi, vòng đời thẻ trong code còn cần hoàn thiện khi tích hợp mượn–trả |
| REQ-02 | Quản lý sinh viên | BUC01; UC01 | `/users/students*`, `/users/sync` | Web Accounts hiện hỗ trợ đọc đúng dữ liệu; chưa có đủ UI CRUD/sync | Test hợp đồng phân quyền Admin/Librarian | **Một phần**; hoàn thiện UI sau lõi tuần 9 |
| REQ-03 | Danh mục, tìm kiếm, bản sao và barcode | BUC02, BUC06; UC05, UC20, UC21 | `/books`, `/categories`, `/book-copies` | Web Books/Portal Search/Detail; Mobile Search/Detail | `BookServiceTest`, `BookCopyServiceTest`, Web component/service tests | **Gần đạt**; Mobile còn một số dữ liệu lọc/vị trí giả |
| REQ-04 | Nhà cung cấp và nhập sách | BUC06; UC21, UC33 | `/inventory/suppliers`, `/inventory/receipts`, `/inventory/stats` | Web Imports | `InventoryServiceTest`, `Week6IntegrationTest`, `imports.service.spec.ts` | **Đạt** ở mức cơ bản |
| REQ-05 | Tầng, kệ, ngăn và vị trí sách | BUC06, BUC12; UC20, UC21, UC28 | `/locations/*`, `/book-copies/{barcode}/location` | Web Shelf Map/Detail; Mobile Detail chưa dùng hoàn toàn dữ liệu thật | `LocationServiceTest`, `BookCopyServiceTest`, test FK integration, Web location/shelf tests | **Gần đạt**; Mobile phải bỏ vị trí sinh từ `hashCode` |
| REQ-06 | Đặt giữ, mượn, gia hạn và trả | BUC02, BUC03; UC06–UC09, UC11, UC12, UC36 | `/loans/reserve`, `/my-loans`, `/cancel`, `/renew`; `/checkout` và `/return` còn stub | Web Circulation/History/Detail; Mobile Borrowed/Detail còn ghép mock | Chưa có test state machine mượn–trả | **Một phần**; ưu tiên cao nhất ở tuần 9 |
| REQ-07 | Quá hạn, mất/hỏng và tiền phạt | BUC03, BUC07; UC13–UC16, UC23, UC31, UC32 | `/transactions/pay`, `/refund` và payment hiện chưa có nghiệp vụ đầy đủ | Web Borrows còn số liệu tĩnh; Mobile chưa có luồng thật | Chưa có test | **Chưa làm**; tuần 9 |
| REQ-08 | Nhắc hạn và thông báo | BUC08; UC22, UC23 | Mới có entity/repository rời rạc, chưa có service/controller/scheduler hoàn chỉnh | Web và Mobile đang hiển thị dữ liệu giả | Chưa có test | **Chưa làm**; tuần 9 rồi nối 7C/8 |
| REQ-09 | Tài liệu số | BUC09; UC24, UC25 | Có entity/repository nhưng thiếu upload/download service và phân quyền | Web Digital Assets/Portal Digital mới là khung | Chưa có test | **Chưa làm**; gói 7D |
| REQ-10 | Đề xuất mua sách | BUC10; UC29 | `/proposals` và endpoint tương thích `/feedback/proposals` | Web Suggest/Admin Suggestions; Mobile Suggest | Có Web test cho Portal Suggest; thiếu test Backend chuyên biệt | **Gần đạt**; bổ sung test và lịch sử/trạng thái Mobile |
| REQ-11 | Khiếu nại, đánh giá và wishlist | BUC11, BUC13; UC27, UC30 | Có endpoint khung; reviews/wishlist chưa thành luồng hoàn chỉnh | Chưa có màn hình end-to-end đầy đủ | Chưa có test | **Một phần**; khiếu nại giữ trong phạm vi, đánh giá và wishlist được hạ thành phần mở rộng |
| REQ-12 | Dashboard và báo cáo | BUC12; UC28 | `/dashboard/stats`, `/weekly-stats`, `/recent-activities`, `/trending-books` | Web Dashboard dùng dữ liệu thật | 3 Backend service tests và Web dashboard/service tests | **Đạt** cho dashboard cơ bản; export nâng cao tạm hoãn |
| REQ-13 | Kiểm kê kho | BUC12; UC37 | `/inventory/audits*` | Web Inventory Audit | 7 unit tests, integration persistence/complete và Web tests | **Đạt** ở mức cơ bản |
| REQ-14 | Thủ thư quét QR | Yêu cầu chủ dự án bổ sung; nối với UC09, UC12 | Chưa có hợp đồng resolve QR ổn định; sẽ gọi state machine checkout/return | Chưa có chế độ hoặc màn hình scanner cho Librarian | Chưa có test quyền, QR lỗi/hết hạn/trùng | **Bắt buộc nhưng chưa làm**; thực hiện sau Backend tuần 9 |
| REQ-15 | Gợi ý sách cá nhân hóa | BUC13; UC26 | `/books/recommendations` đang trả rỗng | Web/Mobile dùng danh sách tĩnh | Chưa có test thuật toán hoặc metric | **Chưa làm**; tuần 10 |
| REQ-16 | Mobile sinh viên | Mục tiêu tuần 8; dùng các UC sinh viên tương ứng | Dùng nhiều API hiện có nhưng chưa đồng bộ toàn bộ | Có bộ màn hình chính; Loans/Notifications/Recommendations còn mock | `flutter analyze` sạch; mới có 1 widget test | **Một phần**; tuần 8 |
| REQ-17 | QA, CI và triển khai | Mục tiêu tuần 11 | Docker Compose, Testcontainers và workflow CI | Không áp dụng màn hình | Backend 29/29, Web 34/34, Mobile 1/1 tại mốc rà soát | **Một phần**; còn E2E, hiệu năng, bảo mật và triển khai thật |
| REQ-18 | Đặt phòng và vi phạm phòng | BUC04; UC17, UC18, UC35 | Còn entity/schema và `/rooms/book` stub | Không cần màn hình | Không cần test mới | **Loại khỏi phạm vi**; đã gỡ khỏi Use Case và các sơ đồ chính |
| REQ-19 | Kiosk, giao ca, đồng bộ trường thời gian thực | UC10, UC38, UC39 | Một số endpoint/schema khung | Không có luồng hoàn chỉnh | Không có | **Tạm hoãn**; không chặn nghiệm thu lõi |

## 4. Đánh giá tuần 1 đến tuần 5

| Tuần | Yêu cầu theo `CNTT-KLCN119` | Bằng chứng hiện có | Ước tính | Kết luận |
|---|---|---|---:|---|
| Tuần 1 | Khảo sát, phạm vi và kế hoạch | Đề cương, phạm vi, ma trận truy vết và thứ tự triển khai đã thống nhất | 100% | Đã chốt |
| Tuần 2 | Phân tích nghiệp vụ và Business Use Case | Business Use Case đã bỏ BUC04, phân cấp phần bắt buộc và tạm hoãn | 100% | Đã chốt |
| Tuần 3 | System Use Case và lớp phân tích | Đã bỏ UC17, UC18, UC35; tích hợp QR vào UC09/UC12; lớp phân tích theo phạm vi mới | 100% | Đã chốt |
| Tuần 4 | Lớp thiết kế và ERD | Design Class Diagram phân biệt hiện có/dự kiến; ERD dùng mô hình và kiểu ID mục tiêu | 100% | Đã chốt thiết kế; sai lệch code còn lại chuyển thành backlog triển khai |
| Tuần 5 | Thiết kế giao diện | Web/Mobile hiện có cùng sequence và wireframe QR thủ thư | 100% | Đã chốt thiết kế; mock được ghi rõ, không tính là chức năng hoàn thành |

### Điều kiện để gọi tuần 1–5 là hoàn tất

- [x] Có phạm vi chính thức và thứ tự ưu tiên duy nhất.
- [x] Có ma trận truy vết yêu cầu đến code và test.
- [x] Đặt phòng được đánh dấu loại; QR thủ thư được đánh dấu bắt buộc.
- [x] Có danh sách sai lệch thiết kế–code và hành động sửa.
- [x] Business Use Case Word được cập nhật theo phạm vi mới.
- [x] System Use Case Word được cập nhật theo phạm vi mới và có luồng QR thủ thư.
- [x] Analysis Class Diagram, Design Class Diagram và ERD khớp code/schema mục tiêu.
- [x] Tài liệu công nghệ và README không còn mô tả stack hoặc tính năng chưa được triển khai như hiện trạng.
- [x] Sequence và wireframe có luồng Librarian QR, lỗi QR và ranh giới state change ở Backend.

Vì vậy, **gói 1A và 1B đã hoàn thành; tuần 1–5 được chốt về tài liệu và thiết kế**. Kết luận này không có nghĩa các chức năng mô tả trong Use Case đã được triển khai đầy đủ.

## 5. Sổ đăng ký sai lệch thiết kế–mã nguồn

Mức ưu tiên: **P0** phải sửa trước khi triển khai tiếp để tránh làm sai phạm vi; **P1** phải sửa trước báo cáo/ nghiệm thu; **P2** có thể ghi chú hoặc dọn sau khi lõi ổn định.

| Mức | Sai lệch đã xác nhận | Ảnh hưởng | Kết quả xử lý hoặc backlog |
|---|---|---|---|
| P0 | Business/System Use Case, ERD và sơ đồ lớp còn đặt phòng/vi phạm phòng | Làm tăng sai phạm vi và tạo cảm giác thiếu chức năng | **Đã xử lý trong tài liệu:** bỏ BUC04, UC17/18/35 và quan hệ phòng khỏi sơ đồ chính |
| P0 | Chưa có Use Case/sequence rõ cho Librarian quét QR | Yêu cầu bắt buộc mới không có thiết kế để triển khai/test | **Đã xử lý:** UC09/UC12, sequence và wireframe bao phủ quyền, lỗi/hết hạn/trùng và checkout/return |
| P0 | Hợp đồng đường dẫn API không thống nhất: Auth dùng `/api/v1`, phần lớn module dùng root; Feedback còn hai alias | Web/Mobile/Nginx dễ gọi sai và SecurityConfig phải khai báo kép | **Đã ghi nhận trong API contract; code chưa chuẩn hóa.** Thực hiện cùng tuần 9 hoặc trước E2E |
| P0 | Sơ đồ recommendation mô tả AI client/Python/FastAPI/Elasticsearch đang hoạt động nhưng endpoint thực tế trả rỗng, search dùng JPA/PostgreSQL | Báo cáo phóng đại hiện trạng và dẫn hướng triển khai sai | **Đã xử lý trong thiết kế:** baseline PostgreSQL và TF IDF/cosine; hạ tầng mở rộng chỉ kích hoạt theo ADR 001 |
| P1 | Design diagram dùng nhiều `*ServiceImpl`/controller không tồn tại; code dùng các service trực tiếp | Không truy vết được lớp thiết kế sang code | **Đã xử lý:** sơ đồ dùng đúng controller/service hiện có và đánh dấu riêng lớp planned |
| P1 | Kiểu ID trong sơ đồ chủ yếu là `string`; JPA thực tế dùng hỗn hợp `Long`, `UUID` và barcode `String` | ERD, DTO và migration không khớp | **Đã xử lý trong ERD mục tiêu:** UUID cho user, Long cho định danh số và String cho barcode |
| P1 | ERD chuẩn hóa Author/Publisher/BookAuthor nhưng `BookEntity` đang lưu author/publisher dạng chuỗi và liên kết Category | Query/migration trong báo cáo không phản ánh code | **Đã xử lý:** ERD phản ánh author/publisher dạng text và quan hệ Category hiện tại |
| P1 | Thực thể kiểm kê và trường trong ERD khác code; một số kiểu `auditId` chưa nhất quán | Có nguy cơ lỗi liên kết và mô tả sai nghiệp vụ kiểm kê | **ERD đã chốt `Long`; code `InventoryDetail.auditId` còn phải sửa bằng migration/JPA** |
| P1 | Sơ đồ Inventory chỉ mô tả tạo báo cáo; code có tạo phiên, quét, xem và chốt phiên | Bỏ sót phần đã làm tốt trong báo cáo | **Đã xử lý:** Design diagram có create, list, get, scan và complete audit |
| P1 | Tài liệu tách ImportController/ImportService; code nhập sách nằm trong InventoryController/InventoryService | Tên module và API không trùng bằng chứng thực thi | **Đã xử lý:** sơ đồ và backend context dùng module Inventory hiện tại |
| P1 | `README.md` và `Technologies.docx` còn mô tả .NET/SQL Server, Kafka/Elasticsearch/Python AI hoặc test nâng cao như hiện trạng hoàn tất | Người đọc hiểu sai stack và tiến độ | **Đã xử lý:** tách rõ đang dùng, tùy chọn, chưa làm và tạm hoãn |
| P1 | Quy tắc Loan hiện là 14 ngày/gia hạn 7 ngày, thiếu tối đa 3 cuốn và chặn quá hạn/nợ | Mã nguồn không khớp quy định nghiệp vụ | Sửa ở tuần 9 rồi cập nhật sequence/state diagram và test |
| P2 | Code/schema/endpoint phòng vẫn còn dưới dạng legacy | Tăng nhiễu khi bảo trì nhưng chưa chặn chức năng khác | Không mở rộng; lập migration loại bỏ sau khi sao lưu và xác nhận không còn tham chiếu |
| P2 | Giao ca, kiosk và một số finance endpoint là stub | Endpoint tồn tại dễ bị tính nhầm là hoàn thành | Đánh dấu deferred hoặc ẩn khỏi API công khai đến khi triển khai thật |

## 6. Kết quả gói 1B

Các đầu ra đã hoàn thành:

1. Business Use Case đã bỏ đặt phòng, làm rõ QR và hạ ưu tiên nghiệp vụ mở rộng.
2. System Use Case đã bỏ UC phòng, đánh dấu phần tạm hoãn và bổ sung QR thủ thư trong UC09/UC12.
3. Analysis Class Diagram, Design Class Diagram và ERD đã được viết lại theo mô hình mục tiêu.
4. API context, backend context, README và tài liệu công nghệ đã phân biệt phần đang chạy, một phần, stub và tạm hoãn.
5. `librarian_qr_flow.mmd` và `librarian_qr_wireframe.mmd` mô tả luồng xử lý và trạng thái giao diện QR.
6. Ba file Word đã được render và kiểm tra toàn bộ 34 trang sau khi sửa.

Thứ tự phát triển tiếp theo là: **Tuần 9 → hoàn tất 7C và tuần 8 (gồm QR thủ thư) → tuần 10 → tuần 11 → tuần 12**.
