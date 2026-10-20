# BẢNG ĐÁNH GIÁ VÀ KẾ HOẠCH HOÀN THIỆN TUẦN 1 ĐẾN TUẦN 12

> **Dự án:** Hệ thống quản lý thư viện HUIT tích hợp gợi ý sách
>
> **Căn cứ:** `CNTT-KLCN119.docx`, `CNTT-KLCN119.txt`, mã nguồn Backend, Web App và Mobile App tại ngày 21/10/2026
>
> **Mục đích:** Theo dõi toàn bộ tiến độ tuần 1–12 bằng bằng chứng kiểm chứng được; không tính giao diện tĩnh hoặc endpoint rỗng là chức năng hoàn thành.
>
> **Lưu ý tên file:** Giữ tên file cũ để không làm hỏng các liên kết đã dùng; phạm vi nội dung đã được mở rộng thành tuần 1–12 từ ngày 17/10/2026.

## 1. Quy ước đánh giá

Một hạng mục chỉ được đánh dấu hoàn thành khi đáp ứng đủ bốn điều kiện:

1. Có nghiệp vụ và dữ liệu thật ở Backend, không phải response giả hoặc stub.
2. Web hoặc Mobile gọi đúng API và không dùng dữ liệu hard-code thay thế.
3. Có kiểm thử tự động cho các quy tắc quan trọng và build thành công.
4. Có thể demo theo luồng người dùng từ đầu đến cuối trên cùng cơ sở dữ liệu.

Các trạng thái sử dụng trong tài liệu:

- **Hoàn thành:** Đủ bốn điều kiện trên.
- **Một phần:** Có code thật nhưng thiếu một hoặc nhiều lớp tích hợp, kiểm thử hoặc ràng buộc nghiệp vụ.
- **Giao diện:** Có màn hình nhưng chưa có nghiệp vụ thật.
- **Chưa làm:** Chưa có triển khai sử dụng được.

### 1.1. Phạm vi đã chốt bổ sung ngày 17/10/2026

- **Loại khỏi phạm vi:** Toàn bộ chức năng đặt phòng học/không gian, nhận phòng, trả phòng và xử lý vi phạm đặt phòng. Các entity hoặc endpoint `rooms` còn trong mã nguồn được xem là di sản thiết kế, không được tính vào tiến độ và không cần tiếp tục triển khai.
- **Bắt buộc bổ sung:** Thủ thư phải có chức năng quét mã QR để nhận diện thẻ sinh viên hoặc phiếu mượn tại quầy và chuyển tiếp sang đúng nghiệp vụ checkout/return. Luồng này chỉ được tính hoàn thành khi dùng camera/máy quét thật, kiểm tra quyền `LIBRARIAN`, lấy dữ liệu từ Backend và không giả lập kết quả.
- Kiosk tự phục vụ vẫn là phần mở rộng tạm hoãn; yêu cầu quét QR của thủ thư không đồng nghĩa phải triển khai kiosk.

### 1.2. Phạm vi bổ sung trước tuần 12 ngày 19/10/2026

- **Bổ sung bắt buộc trước khi chốt phần kỹ thuật:** nâng Chatbot Gemini hiện có từ prompt tĩnh thành RAG thật, có truy xuất nguồn dữ liệu, dẫn nguồn, kiểm thử và bảo vệ khóa API ở Backend.
- Chatbot hiện có trên Web được tính là nền giao diện đã làm, chưa được tính là RAG hoàn thành vì chưa có pipeline nạp dữ liệu, chunking, embedding, vector retrieval hoặc nguồn trích dẫn.
- Bổ sung gói bàn giao minh chứng tuần 1–11 để thành viên phụ trách Word/PowerPoint có thể sử dụng trực tiếp mà không phải tự suy luận từ mã nguồn.
- Việc bổ sung RAG không làm thay đổi kết luận hoàn thành của tuần 1–11 theo đề cương; đây là gói kỹ thuật tăng cường được thực hiện trước khi đóng gói và diễn tập tuần 12.

## 2. Kết luận tổng quan

| Tuần | Mục tiêu | Tiến độ thực chất | Kết luận |
|---|---|---:|---|
| Tuần 1 | Khảo sát, phạm vi và kế hoạch | 100% | Đã chốt phạm vi, truy vết và thứ tự triển khai |
| Tuần 2 | Phân tích nghiệp vụ và Business Use Case | 100% | Đã bỏ đặt phòng, phân cấp phần bắt buộc và tạm hoãn |
| Tuần 3 | System Use Case và lớp phân tích | 100% | Đã bỏ UC phòng, tích hợp QR vào UC09/UC12 và cập nhật lớp phân tích |
| Tuần 4 | Lớp thiết kế và ERD | 100% | Đã đồng bộ mô hình mục tiêu; sai lệch code còn lại được ghi thành backlog |
| Tuần 5 | Thiết kế giao diện | 100% | Đã có Web/Mobile và bổ sung sequence cùng wireframe QR thủ thư |
| Tuần 6 | Auth, phân quyền, sinh viên, sách, nhập sách và vị trí | 100% trong phạm vi đã chốt | Đã đạt gate mã nguồn, integration test PostgreSQL/Redis/SMTP thật và có CI tự động |
| Tuần 7 | Web đầy đủ chức năng quản lý thư viện | 100% trong phạm vi triển khai | Đã hoàn tất 7A–7E: toàn bộ màn hình nghiệp vụ dùng API thật, có CRUD bạn đọc và tài liệu số; component/service test và HTTP E2E đã chốt, diễn tập trình duyệt thật thuộc tuần 12 |
| Tuần 8 | Mobile App sinh viên và quét QR cho thủ thư | 100% mã nguồn, chờ nghiệm thu thiết bị | Đã có cập nhật hồ sơ, tìm theo chuyên ngành, FCM, mượn/lịch sử, QR và gợi ý bằng API thật; push cần xác nhận end-to-end trên thiết bị |
| Tuần 9 | Mượn, trả, gia hạn, quá hạn, phạt và nhắc hạn | 100% trong phạm vi đã chốt | Đã hoàn tất 9A–9C: mất/hỏng, đền bù, công nợ, hàng đợi, khóa thẻ, miễn giảm, thu và đối soát |
| Tuần 10 | Thuật toán gợi ý và đánh giá | 100% trong phạm vi đã chốt | Đã hoàn thành 10A–10E: dữ liệu, baseline, Hybrid/cold-start, cache, UI và thực nghiệm có ghi nhãn proxy |
| Tuần 11 | Kiểm thử, tích hợp và triển khai | 100% trong phạm vi cục bộ đã chốt | Có HTTP E2E xuyên suốt, kiểm thử tải/bảo mật, CI, Docker health/smoke và diễn tập backup/restore; triển khai Internet được để lại theo quyết định của nhóm |
| Tuần 12 | Hoàn thiện kỹ thuật, bàn giao kiểm thử/triển khai và cung cấp minh chứng | Gói 12A–12E hoàn thành ở mức mã nguồn | Có RAG, gói Word/PPT, checklist 118 ca; package ID/Firebase và nghiệm thu thiết bị đã giao thành viên kiểm thử/triển khai |

Tiến độ mã nguồn tuần 1–11 và các gói 12A–12E đã hoàn tất trong phạm vi thành viên code. Thành viên kiểm thử/triển khai tiếp nhận package ID `vn.edu.huit.library`, cấu hình Firebase và xác nhận FCM trên thiết bị thật.

### 2.1. Kết quả chốt tuần 1–5

- Gói 1A đã hoàn thành: có phạm vi chính thức, ma trận truy vết yêu cầu và sổ sai lệch thiết kế–code.
- Gói 1B đã hoàn thành: Business/System Use Case, Analysis/Design Class Diagram, ERD, tài liệu công nghệ, README và thiết kế QR đã được đồng bộ.
- Đặt phòng và vi phạm phòng đã loại khỏi phạm vi; code/schema còn lại chỉ là legacy và không được tính tiến độ.
- Quét QR dành cho Librarian là yêu cầu bắt buộc, triển khai sau khi checkout/return 9B ổn định trên nền state machine 9A.
- Tuần 1–5 đã hoàn tất về tài liệu và thiết kế. Các chức năng chưa triển khai vẫn được tính ở tuần 7–11, không được cộng nhầm vào tiến độ code.
- Hồ sơ chi tiết: [`CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md`](CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md).

## 3. Trạng thái kiểm thử và khả năng chạy

| Thành phần | Kết quả kiểm tra đến ngày 20/10/2026 | Nhận xét |
|---|---|---|
| Backend | Maven build thành công; 121/121 test qua | Có migration/Testcontainers pgvector, evaluator RAG, fallback/privacy/input guard/Admin authorization, FCM/device registration và hồi quy HTTP 401 cho refresh token bị thu hồi bên cạnh HTTP E2E tuần 11 |
| Web App | Angular production build thành công; 69/69 test qua | Chatbot chỉ gọi RAG Backend và hiển thị nguồn; cảnh báo CommonJS từ `qrcode` không làm hỏng build |
| Mobile App | `flutter analyze` không còn vấn đề; 5/5 test qua; build APK debug thành công | Đã tích hợp gợi ý cá nhân hóa, camera QR, QR thẻ/phiếu, mượn/lịch sử, thông báo, đề xuất và catalog thật; đã thêm regression test cho JSON/text/body rỗng |
| Tích hợp hệ thống | Testcontainers PostgreSQL/Redis/Mailpit đã qua; migration và transaction 9A–9C chạy trên PostgreSQL thật | Test riêng xác nhận hồ sơ mất, phạt trễ dừng ngày báo mất, nợ 5 lần + phí kỹ thuật và phiếu thu thanh toán một phần được lưu transactionally |

## 4. Chi tiết tuần 6

### 4.1. Phần đã có

- Đăng nhập, đăng xuất, JWT access token, refresh token và Redis blacklist.
- Quên mật khẩu, OTP, xác thực OTP và đặt lại mật khẩu.
- API hồ sơ người dùng và cập nhật hồ sơ.
- API danh sách sinh viên và CRUD sinh viên.
- API sách CRUD, phân trang, tìm theo tiêu đề, tác giả, ISBN và lọc thể loại.
- API nhà cung cấp và phiếu nhập ở mức cơ bản.
- Cấu trúc bảng tầng, kệ, ngăn và bản sao sách đã có trong cơ sở dữ liệu.
- Docker Compose và 12 Liquibase changeset đã được chuẩn bị.
- Auth chỉ public đúng các endpoint đăng nhập/khôi phục; đổi mật khẩu dùng danh tính từ JWT.
- OTP không còn xuất hiện trong response hoặc log lỗi gửi mail.
- CRUD sinh viên đã phân quyền Admin ghi, Librarian chỉ đọc.
- Web CRUD sách dùng đúng `bookId`, category object và hiển thị tồn/vị trí thật.
- BookCopy có API tra barcode và DTO vị trí đầy đủ tầng/kệ/ngăn.
- Có API CRUD tầng, kệ sách và ngăn sách theo `/locations`.
- Phiếu nhập lưu chi tiết, tính tổng tiền và sinh bản sao `AVAILABLE` theo số lượng.
- Web/Mobile dùng chung dữ liệu category, số bản còn và vị trí từ Backend.
- Backend, Web và Mobile đã đạt các gate build/test cục bộ.
- Docker stack chạy thành công; smoke test xác nhận login/logout-blacklist, phân quyền, catalog, sinh viên, inventory và vị trí.
- OTP có cửa sổ gửi lại 60 giây, khóa 15 phút sau 5 lần nhập sai và xóa trạng thái khi đặt lại mật khẩu thành công.
- Toàn bộ suite hiện có 11 integration test chạy PostgreSQL, Redis và SMTP Mailpit thật: ghi inventory, rollback, khóa ngoại vị trí, OTP/email, migration 9A–9B, checkout/return và tranh chấp đồng thời.
- GitHub Actions tự động chạy backend unit/integration test, Web build/test và Mobile analyze/test.
- Đã chốt PostgreSQL/JPA là search chính cho catalog tuần 6; điều kiện kích hoạt Elasticsearch được ghi trong `ADR-001-CHIEN-LUOC-TIM-KIEM-SACH.md`.

### 4.2. Phần đã chốt phạm vi

- Search sách chính thức dùng PostgreSQL/JPA trong tuần 6. Elasticsearch chỉ được kích hoạt ở tuần 10 nếu có nhu cầu và benchmark theo ADR-001.
- SMTP đã được kiểm tra qua server Mailpit thật trong Docker và Testcontainers. Tài khoản SMTP nhà cung cấp ở production là cấu hình triển khai, không lưu credential vào repository.
- Workflow CI đã có trong mã nguồn; lần chạy trên GitHub sẽ bắt đầu khi repository được push lên remote có bật Actions.

### 4.3. Tiêu chí hoàn thành tuần 6

- [x] Chỉ public login, refresh, forgot-password, verify-otp và reset-password.
- [x] Đổi mật khẩu lấy user từ JWT, kiểm tra mật khẩu cũ và validate mật khẩu mới.
- [x] Refresh token và khôi phục mật khẩu dùng JSON DTO; OTP và mật khẩu mới có validation thống nhất.
- [x] Không trả OTP trong response; không log OTP ở production.
- [x] JWT secret và thời hạn token lấy đúng từ cấu hình môi trường.
- [x] CRUD sinh viên đúng phân quyền: Admin được tạo/sửa/xóa, Librarian chỉ đọc.
- [x] CRUD sách trên Web dùng đúng `bookId` và đúng DTO category.
- [x] Có API tra cứu bản sao theo barcode và trả về vị trí tầng/kệ/ngăn.
- [x] Có API CRUD quản trị tầng/kệ/ngăn và cập nhật vị trí bản sao theo `shelfId`.
- [x] Web/Mobile hiển thị số bản còn và vị trí từ dữ liệu thật.
- [x] API nhà cung cấp và phiếu nhập dùng cùng `/inventory` contract.
- [x] Có test Auth, phân quyền, Book CRUD và các validation chính.
- [x] Backend, Web và Mobile build/test đạt gate mã nguồn đã thống nhất.
- [x] Chạy smoke test trên PostgreSQL/Redis thật và xác nhận migration/API bằng Docker Compose.
- [x] Bổ sung integration test cho các luồng ghi dữ liệu, rollback và ràng buộc khóa ngoại vị trí.

### 4.4. Chia nhỏ phần việc còn lại

#### Nhóm A — ngắn, ít rủi ro, thực hiện ngay

- [x] Chuyển `refresh-token`, `forgot-password`, `verify-otp`, `reset-password` từ query param sang JSON DTO.
- [x] Áp dụng OTP đúng 6 chữ số và mật khẩu mới dài 8–128 ký tự.
- [x] Đồng bộ Web với hợp đồng Auth DTO mới.
- [x] Logout thu hồi cả access token và refresh token trên Web/Mobile.
- [x] Bổ sung unit test cho validation payload khôi phục mật khẩu.
- [x] Chuyển Dockerfile sang multi-stage build để không phải tạo JAR thủ công trước `docker compose up --build`; image đã build thử thành công.
- [x] Thêm `.dockerignore` để giảm build context.

#### Nhóm B — khó hoặc phụ thuộc môi trường, để phiên quota đầy đủ

- [x] Viết integration test bằng Testcontainers cho PostgreSQL và Redis, gồm rollback và khóa ngoại.
- [x] Tự động hóa quality gate/smoke cốt lõi trong CI thay cho kiểm tra thủ công.
- [x] Thêm rate limit gửi lại OTP, giới hạn lần nhập sai và thời gian khóa tạm thời.
- [x] Kiểm thử gửi OTP qua SMTP Mailpit thật trong Docker và Testcontainers; production dùng biến môi trường khi triển khai.
- [x] Quyết định chính thức dùng PostgreSQL/JPA cho tuần 6; Elasticsearch hoãn có điều kiện theo ADR-001.

## 5. Chi tiết tuần 7

### 5.1. Đã nối dữ liệu thật

- Danh sách sách và sinh viên; trường hiển thị sinh viên đã khớp response thật từ Backend.
- Web nhập sách đã dùng đúng hợp đồng `/inventory`, lập phiếu nhập và hiển thị thống kê/phiếu từ Backend.
- Web quản lý vị trí đã dùng `/locations` để tải và CRUD tầng, dãy kệ, ngăn kệ; toàn bộ sơ đồ và số liệu bản sao hard-code cũ đã được loại bỏ.
- Dashboard đã có bốn API thật cho tổng quan, biểu đồ bảy ngày, hoạt động gần đây và sách được đặt/mượn nhiều; Web không còn tháng, tỷ lệ tăng giảm hoặc biểu đồ chuyên ngành hard-code.
- Kiểm kê kho đã có luồng thật: tạo và xem lịch sử phiên, quét barcode, chống quét trùng trong phiên, đối chiếu vị trí, ghi nhận barcode lạ và khóa kết quả khi hoàn tất.
- Gửi, xem lịch sử và duyệt/từ chối đề xuất sách trên Web.
- Portal sinh viên có tra cứu, xem chi tiết, đặt giữ, QR phiếu, xem lịch sử, hủy và gia hạn.
- Trang quản lý phiếu mượn/quá hạn đã dùng `GET /loans/staff`, có thống kê từ dữ liệu thật, tìm kiếm/lọc trạng thái và mở chi tiết đúng bằng `ticketId`; đã loại bỏ toàn bộ số liệu và nút thao tác giả của màn hình cũ.
- Chi tiết phiếu cho phép thủ thư duyệt `PENDING → RESERVED` hoặc từ chối kèm lý do bằng API thật.
- Quầy Web nhận số phiếu hoặc payload `HUIT-PM-{ticketId}`, chỉ tải phiếu `RESERVED`, đối chiếu đủ barcode thuộc phiếu rồi checkout bằng đúng `ticketId`; trả sách kiểm tra trạng thái bản sao và gửi ghi chú tình trạng. Không còn fallback sách giả khi tra barcode lỗi.
- Màn hình thông báo không còn bộ đếm, lịch sử hoặc thao tác gửi giả; cả Portal và Admin đọc thông báo của tài khoản hiện tại từ `/api/v1/notifications`, lọc đã đọc/chưa đọc và đánh dấu đã đọc qua API thật.
- Route con của Web Admin đã được kiểm tra đăng nhập và role; Admin và Librarian đều truy cập được các trang đọc dữ liệu mà Backend cho phép.
- Trang sinh viên cho Librarian xem danh sách; Admin có tìm kiếm/lọc và form tạo, sửa, xóa tài khoản thật, gồm trạng thái và hạn thẻ.
- Phân hệ tài liệu số có upload kéo thả, kiểm tra định dạng/dung lượng/chữ ký tệp, lưu file bền vững, metadata PostgreSQL, liên kết sách, quyền `AUTHENTICATED`/`STAFF_ONLY`, download và log truy cập.
- Web Admin quản lý metadata/xóa/tải file; Portal chỉ hiển thị tài liệu người dùng được phép và tải qua API có JWT.

### 5.2. Giới hạn đã chuyển sang giai đoạn phù hợp

- Cơ sở dữ liệu chưa có tọa độ mặt bằng nên Web hiện biểu diễn vị trí theo cây tầng → dãy kệ → ngăn, chưa vẽ đường đi vật lý.
- Luồng quầy Web đã nối đúng Backend. Camera và QR resolver có chữ ký dành cho Librarian đã được triển khai trên Mobile; quầy Web vẫn chỉ hỗ trợ nhập số phiếu/chuỗi từ máy quét bàn phím.
- Đồng bộ Excel hàng loạt không bắt buộc để hoàn tất CRUD tuần 7; Backend vẫn giữ hợp đồng đồng bộ danh sách, còn nhập file hàng loạt có thể bổ sung khi có mẫu dữ liệu chính thức của trường.
- Tuần 11 đã bổ sung HTTP E2E xuyên suốt controller → Backend → PostgreSQL cùng môi trường Docker cục bộ; diễn tập giao diện trên trình duyệt thật thuộc checklist demo tuần 12.

### 5.3. Tiêu chí hoàn thành tuần 7

- [x] Web Admin có đủ luồng thật cho tài khoản, sách, nhập sách, vị trí, mượn/trả, phạt, tài liệu số, đề xuất, kiểm kê và dashboard.
- [x] Không còn số liệu hard-code trong màn hình nghiệp vụ.
- [x] Mọi nút thao tác hoặc hoạt động thật hoặc được loại bỏ.
- [x] Có route guard và Backend authorization đồng nhất.
- [x] Có component/service test cho các màn hình quan trọng, HTTP E2E và integration test Backend trên PostgreSQL thật.

### 5.4. Chia nhỏ phần việc tuần 7

#### Gói 7A — ngắn, độc lập với lõi mượn–trả

- [x] Cập nhật đánh giá cũ: Web nhập sách đã chuyển từ `/imports` sang `/inventory`.
- [x] Áp dụng guard cho cả route cha và route con; kiểm tra role ở từng route có khai báo quyền.
- [x] Đồng bộ quyền xem danh sách sinh viên và nhập kho của Librarian với Backend.
- [x] Hiển thị danh sách sinh viên theo đúng `fullName`, `mssv`, `department` và `cardStatus` từ Backend.
- [x] Loại bỏ tìm kiếm/lọc/phân trang giả ở trang tài khoản và nhập kho; bỏ nút import/chỉnh sửa chưa có xử lý cùng ô tìm kiếm và huy hiệu thông báo giả trên thanh Admin.
- [x] Bổ sung test cho route guard, chế độ chỉ xem của Librarian và hợp đồng `/inventory/receipts`.

#### Gói 7B — trung bình, có thể làm độc lập

- [x] Xây dựng Dashboard API và nối số liệu thật lên Web; số liệu lấy từ bản sao, phiếu mượn, chi tiết mượn và giao dịch.
- [x] Kết nối sơ đồ tầng/kệ/ngăn với `/locations`, hỗ trợ thêm/sửa/xóa và trạng thái lỗi/rỗng.
- [x] Triển khai kiểm kê kho cơ bản bằng barcode và lưu kết quả; có trạng thái phiên, đối chiếu vị trí, phát hiện barcode lạ và test Backend/Web.

#### Gói 7C — phải làm sau lõi tuần 9

- [x] Nối trang quản lý mượn/quá hạn với dữ liệu thật; có API danh sách dành cho staff, lọc/tìm kiếm, thống kê và chi tiết phiếu typed theo `ticketId`.
- [x] Nối quầy checkout/return với state machine và transaction Backend; dùng đúng `ticketId`, kiểm tra phiếu `RESERVED`, khớp đủ barcode, ghi chú trả sách và hiển thị lỗi thật.
- [x] Nối phí trễ, nhắc hạn, mất/hỏng, thu tiền và đối soát thật; Web đọc dữ liệu PostgreSQL và xử lý hồ sơ/biên nhận qua API.

#### Gói 7D — tài liệu số — đã hoàn thành

- [x] Hoàn thiện tài liệu số: upload, download, lưu trữ bền vững, liên kết sách, phân quyền, log sử dụng và kiểm thử lỗi.
- [x] Bổ sung migration 16, volume Docker và cấu hình giới hạn tệp 50 MB.
- [x] Thay các nút xem/liên kết giả bằng download và chỉnh sửa metadata thật; Portal sinh viên đọc danh sách được phép.

#### Gói 7E — CRUD tài khoản và kiểm thử chốt tuần — đã hoàn thành

- [x] Hoàn thiện CRUD tài khoản sinh viên cho Admin; Librarian giữ chế độ chỉ xem, Backend kiểm tra dữ liệu trùng và từ chối xóa hồ sơ đã có ràng buộc nghiệp vụ.
- [x] Hoàn thiện component/service test cho tài khoản, Admin tài liệu số và Portal tài liệu số.
- [x] Thêm integration test upload → metadata PostgreSQL → file storage → download → usage log; toàn bộ Backend đạt 87/87 và Web đạt 63/63 test.
- [x] HTTP E2E trên Docker/Testcontainers đã hoàn thành ở tuần 11; diễn tập trình duyệt thật được chuyển sang chuẩn bị demo tuần 12.

## 6. Chi tiết tuần 8

### 6.1. Đã làm thật

- Đăng nhập, đăng xuất và đổi mật khẩu.
- Tra cứu sách và category từ Backend; trạng thái có sẵn, số lượng bản sao và vị trí đều lấy từ dữ liệu catalog thật.
- Hiển thị hồ sơ người dùng.
- Mobile đọc toàn bộ phiếu từ `/loans/my-loans`, đặt giữ qua `/loans/reserve`, gia hạn/hủy qua API thật; không còn tự tạo phiếu hoặc lưu phiếu bằng `SharedPreferences`.
- Mobile đọc thông báo PostgreSQL từ `/api/v1/notifications`, hiển thị số chưa đọc và đánh dấu đã đọc qua API.
- Đề xuất sách dùng `/proposals`, có lịch sử cá nhân và trạng thái duyệt/từ chối từ Backend.
- Trang chủ lấy top-K cá nhân hóa từ Backend tuần 10; hiển thị điểm phù hợp, lý do gợi ý và tự fallback popularity khi cần.
- Backend phát hành QR `HUITQR1` có chữ ký HMAC SHA-256 và hạn 10 phút cho thẻ bạn đọc hoặc phiếu mượn; kiểm tra chủ sở hữu, loại mã, chữ ký và thời hạn.
- Web và Mobile đều hiển thị QR phiếu đã ký; Mobile hiển thị thêm QR thẻ thư viện.
- Màn hình quét dùng camera thật, chỉ hiện cho `LIBRARIAN`/`ADMIN`, có nhập payload thủ công dự phòng và gọi `/api/v1/qr/resolve`.
- Sau khi resolve, Mobile chỉ gửi checkout/return có `idempotencyKey`; mọi cập nhật Ticket/Detail/BookCopy vẫn do transaction Backend xử lý.
- Android/iOS đã khai báo quyền camera; lỗi mã sai/hết hạn được trả từ Backend và hiển thị rõ trên Mobile.

### 6.2. Còn thiếu

- Backend đã có test chữ ký, hết hạn, quyền sở hữu, idempotency và HTTP E2E của toàn luồng QR. Kiểm tra camera/quyền trên thiết bị thật được đưa vào checklist diễn tập demo tuần 12 vì phụ thuộc thiết bị vật lý.

### 6.3. Điều chỉnh phạm vi

Mobile vẫn ưu tiên các chức năng sinh viên theo đề cương, đồng thời **quét QR cho Librarian là yêu cầu bắt buộc của sản phẩm**. Luồng này đã được triển khai trên state machine checkout/return tuần 9: QR chỉ nhận diện người dùng hoặc phiếu; mọi thay đổi trạng thái mượn/trả vẫn được thực hiện transactionally ở Backend.

Đặt phòng học và các chức năng `rooms` đã được loại khỏi phạm vi. Không phát triển thêm UI/API/test cho phân hệ này và không tính nó là hạng mục còn thiếu của tuần 7 hoặc tuần 8.

### 6.4. Tiêu chí hoàn thành tuần 8

- [x] Mobile lấy sách đang mượn và lịch sử từ `/loans/my-loans`.
- [x] Mobile đặt giữ qua `/loans/reserve`, không tự tạo phiếu cục bộ.
- [x] Mobile lấy thông báo thật và hỗ trợ đánh dấu đã đọc.
- [x] Mobile lấy gợi ý cá nhân hóa thật từ Backend; tự fallback popularity, hiển thị lý do/điểm và tải chi tiết sách đầy đủ khi người dùng chọn.
- [x] Vị trí và số bản còn lấy từ catalog/BookCopy đã tổng hợp từ DB.
- [x] Đề xuất sách dùng API chính thức, có lịch sử và trạng thái.
- [x] Librarian đăng nhập đúng role, quét QR thẻ sinh viên hoặc phiếu mượn và tải đúng dữ liệu từ Backend.
- [x] Kết quả quét QR được nối với checkout/return thật; không tự cập nhật phiếu hoặc bản sao ở Mobile.
- [x] Có kiểm thử quyền sở hữu, QR không hợp lệ, QR hết hạn và idempotency khi quét/gửi lặp ở Backend; camera trên thiết bị thật thuộc checklist demo tuần 12.
- [x] Flutter analyze không còn lỗi gate và test chính chạy qua.

## 7. Chi tiết tuần 9

### 7.1. Phần đã có

- Sinh viên đặt giữ bản sao đang `AVAILABLE`.
- Tạo loan ticket/detail và khóa bản sao ở trạng thái `RESERVED`.
- Xem danh sách phiếu cá nhân.
- Hủy đặt giữ và nhả bản sao.
- Gia hạn cơ bản, giới hạn số lần và kiểm tra waitlist.
- Có state machine chuẩn cho `PENDING`, `RESERVED`, `BORROWING`, `OVERDUE`, `RETURNED`, `CANCELLED`, `REJECTED` và chặn transition sai bằng HTTP `409`.
- Có API cho thủ thư/Admin xem chi tiết, duyệt và từ chối phiếu; có lưu người xử lý, lý do và lịch sử chuyển trạng thái.
- Có optimistic locking trên phiếu và migration chuẩn hóa dữ liệu trạng thái cũ sang mô hình mới.
- Checkout/return đã cập nhật LoanTicket, LoanDetail và BookCopy trong cùng transaction, khóa pessimistic theo phiếu/barcode và ghi lịch sử trạng thái.
- Có `idempotencyKey` lưu trong `circulation_operations`; quét lặp cùng payload trả lại kết quả cũ, dùng lại key cho payload khác trả `409`.
- Đã áp dụng tối đa 3 tài liệu, hạn mượn 10 ngày, gia hạn thêm 10 ngày, chặn gia hạn quá hạn và chặn mượn mới khi có quá hạn hoặc nợ.
- Scheduler chạy định kỳ 5 phút, tự hủy giữ chỗ hết hạn, nhả bản sao và ghi lịch sử trạng thái.
- Tài liệu trễ được chuyển đồng bộ sang `OVERDUE`; phí 1.000 đồng cho mỗi ngày trễ bắt đầu được cộng vào `lateFineAccrued`, ví nợ và giao dịch `OUTSTANDING` có khóa chống ghi trùng.
- Có thông báo lưu trong PostgreSQL cho sắp đến hạn, quá hạn và giữ chỗ hết hạn; đã có API xem thông báo cá nhân và đánh dấu đã đọc.
- Gia hạn theo từng `loanDetailId`, tối đa một lần và thêm 10 ngày từ hạn cũ; Web/Mobile đã bỏ quy tắc cũ 2 lần/+7 ngày.
- Hàng đợi FIFO giữ bản sao 48 giờ dương lịch, dời hạn đến lần mở cửa kế tiếp khi trùng ngày nghỉ/giờ đóng cửa và chống một người xếp trùng.
- Phạt theo ngày dương lịch riêng từng tài liệu: mượn về 1.000 đồng/ngày, đọc tại chỗ 5.000 đồng/ngày; báo mất dừng cộng phạt tại ngày báo.
- Có hồ sơ mất/hỏng nhẹ/nặng, phí sửa chữa nhập thủ công, đền 5 lần/3 lần, tỷ giá có nguồn, biểu phí Admin cấu hình và phí kỹ thuật 50.000 đồng không nhân hệ số.
- Sách thay thế phải được thủ thư xác minh và cấp barcode mới; hồ sơ không tự đóng chỉ vì đã trả phí kỹ thuật.
- Cho phép thanh toán nhiều đợt bằng tiền mặt/chuyển khoản; biên nhận lưu phân bổ từng loại phí, người thu và số biên nhận.
- Giao dịch sai không bị xóa: Librarian tạo yêu cầu, Admin duyệt để sinh giao dịch điều chỉnh ngược.
- Bộ đếm quá hạn theo phiếu/năm học; lần thứ ba khóa lưu thông ba tháng và chỉ mở khi hết hạn, trả hết sách và hết nợ. Vi phạm mang sách chưa checkout lưu riêng.
- Miễn giảm dùng `PENDING_REVIEW`, Admin ghi nhận quyết định bên ngoài và hệ thống sinh điều chỉnh công nợ, không tạo thêm role lãnh đạo.
- Web Admin có màn hình xử lý tuần 9; Portal sinh viên xem sổ nợ/hồ sơ ngay cả khi bị khóa.

### 7.2. Sai lệch còn lại

- Không còn sai lệch nghiệp vụ bắt buộc đã biết trong phạm vi tuần 9.
- Thủ thư vẫn phải chịu trách nhiệm xác minh thực tế nhan đề, tác giả, năm xuất bản, loại bìa và giá trị cuốn thay thế; hệ thống lưu barcode và ghi chú xác minh thay vì tự suy đoán từ dữ liệu thiếu.
- Biểu phí tài liệu không có giá bìa phải được Admin cấu hình trước khi xử lý. Đây là dữ liệu vận hành, không phải chức năng còn thiếu.

Chi tiết quy tắc đã chuẩn hóa và backlog triển khai nằm tại `Docs/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md`.

### 7.2.1. Chính sách mất/hỏng đã chốt

- Hư hỏng nhẹ gồm vấy bẩn, rách hoặc mất 1–10 trang: thu chi phí sửa chữa theo quy định.
- Hư hỏng nặng gồm rách bìa; rách/vấy bẩn trên 10 trang; rộp do ướt; mối mọt: xử lý như làm mất tài liệu.
- Báo mất sau hạn trả: vừa đền bù mất tài liệu, vừa chịu phạt mượn quá hạn.
- Tài liệu còn lưu hành: thay đúng nhan đề, tác giả, năm xuất bản bằng hoặc mới hơn; chấp nhận bìa cứng thay bìa mỏng nhưng không theo chiều ngược lại; bản mới hơn không được có giá trị thấp hơn bản mất.
- Tài liệu cũ không còn lưu hành và có giá bìa: Việt ngữ đền 5 lần; ngoại văn đền 3 lần theo tỷ giá tại thời điểm nộp.
- Tài liệu không có giá bìa: dùng biểu phí theo từng loại tài liệu; ngoài đền bù còn có phí xử lý kỹ thuật.
- Hạn mua thay/nộp tiền tối đa 30 ngày từ ngày báo mất; Thủ thư tiếp nhận hồ sơ và Admin ghi nhận văn bản/quyết định phê duyệt bên ngoài, không tạo role lãnh đạo riêng.

### 7.3. Luồng bắt buộc phải hoàn chỉnh

`Đặt giữ → Librarian xem chi tiết → Duyệt hoặc từ chối → Giao sách → Gia hạn → Trả sách → Tính phạt → Cập nhật bản sao → Gửi thông báo`

### 7.4. Tiêu chí hoàn thành tuần 9

- [x] Áp dụng tối đa 3 tài liệu, hạn 10 ngày và gia hạn 10 ngày.
- [x] Chặn gia hạn quá hạn và chặn mượn mới khi có quá hạn/nợ.
- [x] Có API lấy chi tiết, duyệt và từ chối phiếu.
- [x] Checkout/return cập nhật transactionally ticket, detail và book copy.
- [x] Tính và ghi nhận phạt trễ hạn tối thiểu 1.000 đồng/ngày.
- [x] Scheduler tự hủy đặt giữ quá hạn và nhả bản sao.
- [x] Scheduler tạo thông báo sắp hạn và quá hạn.
- [x] Có integration test cho state transition trọng yếu, gồm scheduler quá hạn/giữ chỗ, phạt, thông báo, checkout/return, rollback và tranh chấp trên PostgreSQL thật.
- [x] Có hồ sơ mất/hỏng, phân loại nhẹ/nặng và xử lý đúng ranh giới 10/11 trang.
- [x] Tính đền bù đúng quy tắc thay tài liệu, hệ số 5 lần/3 lần, phí kỹ thuật và phạt quá hạn khi báo trễ.
- [x] Có luồng hạn xử lý 30 ngày, miễn/giảm đúng thẩm quyền và audit trail.
- [x] Hoàn thiện thu tiền/thay sách, cập nhật nợ và đối soát transactionally; có idempotency, phân quyền và integration test PostgreSQL.

### 7.5. Chia nhỏ phần việc tuần 9

#### Gói 9A — nền trạng thái và hợp đồng — đã hoàn thành

- [x] Chuẩn hóa enum trạng thái phiếu, chi tiết và loại phiếu; có ánh xạ dữ liệu legacy.
- [x] Tập trung luật chuyển trạng thái trong state machine và trả `409` khi transition không hợp lệ.
- [x] Thêm API chi tiết, duyệt, từ chối; cancel dùng chung state service.
- [x] Lưu lịch sử trạng thái, người xử lý, lý do quyết định và optimistic locking.
- [x] Thêm Liquibase migration, constraint, rollback và kiểm thử migration trên PostgreSQL thật.
- [x] Bổ sung unit/contract test; mốc 9A đạt Backend 44/44, Web 37/37.

#### Gói 9B — giao và trả sách transactionally — đã hoàn thành

- [x] Thực hiện checkout/return thật và đồng bộ LoanTicket, LoanDetail, BookCopy trong một transaction.
- [x] Áp dụng tối đa 3 tài liệu, hạn 10 ngày, gia hạn 10 ngày; chặn quá hạn/nợ.
- [x] Thêm idempotency, pessimistic lock và test rollback/tranh chấp barcode; toàn bộ Backend đạt 54/54 test.

#### Gói 9C — phạt, scheduler, mất/hỏng và đối soát — đã hoàn thành

- [x] Tính phí trễ hạn theo từng ngày bắt đầu, cập nhật chi tiết mượn, tổng nợ ví và giao dịch có dedup key.
- [x] Tự giải phóng đặt giữ hết hạn, đánh dấu quá hạn, đồng bộ bản sao và tạo thông báo nhắc hạn không trùng.
- [x] Thêm API lấy thông báo cá nhân/đánh dấu đã đọc, cấu hình scheduler và migration 14 có rollback/index/FK.
- [x] Có unit test và integration test PostgreSQL thật; mốc lõi ban đầu đạt 63/63, bản hoàn thiện tuần 9 đạt 78/78 test.
- [x] Ghi nhận và chuẩn hóa quy tắc mất/hỏng HUIT từ nội dung được cung cấp ngày 18/10/2026.
- [x] Triển khai hồ sơ mất/hỏng, đền bù/thay tài liệu, phí kỹ thuật, hạn 30 ngày và miễn/giảm.
- [x] Hoàn thiện API/giao diện thu tiền, cập nhật nợ, biên nhận phân bổ và đối soát; có integration test bắt buộc.
- [x] Hoàn thiện hàng đợi 48 giờ, lịch nhắc, khóa ba tháng theo số lần quá hạn và hồ sơ vi phạm riêng.

## 8. Chi tiết tuần 10

### 8.1. Hiện trạng

- Endpoint `/books/recommendations` và `/books/recommendations/popular` đã trả Popularity Baseline top-K thật; `/books/{id}/similar` trả TF-IDF/Cosine.
- Endpoint cần JWT `/books/recommendations/personalized` đã trả Hybrid hoặc cold-start tùy lịch sử sinh viên.
- Chưa có trường `summary` và dữ liệu nội dung sách.
- Elasticsearch chưa được dùng trong search thực tế.
- Đã kiểm kê 269.922 sách: category cũ bị lệch mạnh và lịch sử vận hành mới có 2 chi tiết mượn, chưa đủ đánh giá khoa học.
- Đã tạo taxonomy gợi ý riêng gồm 13 chủ đề, không ghi đè 8 category đang phục vụ catalog.
- Đã tạo 46 ánh xạ trọng số cho 40 khóa ngành/alias, bao phủ 39 ngành HUIT năm 2026.
- Đã gắn chủ đề tin cậy cao cho 4.387 sách, tạo 4.466 assignment, độ phủ ban đầu 1,63%; sách không khớp được để trống thay vì gán ngẫu nhiên.
- Đã có pipeline TF-IDF/Cosine, popularity time-decay/log normalization và Hybrid có điểm thành phần, lý do, loại sách đã mượn cùng cache Redis; Web/Mobile đã tích hợp và 10E đã có số liệu proxy được ghi nhãn rõ.

### 8.2. Lựa chọn thuật toán

Không coi K-Means là lựa chọn bắt buộc. K-Means tạo cụm nhưng không trực tiếp tạo thứ hạng cá nhân hóa. Hướng triển khai chính:

1. Content-based similarity bằng TF-IDF/cosine trên title, author, publisher, category và chủ đề đã kiểm chứng; bổ sung summary khi có dữ liệu thật.
2. Tăng trọng số theo `department_subject_affinities` và lịch sử người dùng.
3. Dùng popularity đã chuẩn hóa làm fallback cho người dùng mới.
4. Hybrid score khởi tạo bằng trọng số cấu hình, không coi 0,50/0,35/0,15 là hằng số khoa học chưa kiểm chứng.

K-Means có thể được dùng làm thuật toán so sánh trong báo cáo nếu còn thời gian.

### 8.3. Tiêu chí hoàn thành tuần 10

- [x] Có taxonomy chủ đề riêng, ánh xạ ngành–chủ đề có trọng số và assignment sách có confidence/source/evidence.
- [x] Có taxonomy/metadata đủ chạy proxy evaluation; lịch sử thật được audit riêng và hiện được ghi nhận là chưa đủ mẫu.
- Trường `summary` và tìm kiếm theo summary là hướng mở rộng, không thuộc phạm vi recommender 10A–10E đã chốt.
- [x] API trả danh sách top-K có điểm số, lý do gợi ý, phương pháp và cờ độ đủ dữ liệu.
- [x] Web và Mobile không còn danh sách gợi ý hard-code; hiển thị top-K, điểm phù hợp, lý do và trạng thái rỗng/lỗi từ API thật.
- [x] Có bộ proxy 38 kịch bản và các chỉ số Precision@10, Recall@10, HitRate@10, Coverage, p50/p95 latency.
- [x] Báo cáo mô tả nguồn dữ liệu, thuật toán, cold-start, thí nghiệm, kết quả và hạn chế dữ liệu thật.

### 8.4. Các gói triển khai đã chốt

#### Gói 10A — chuẩn hóa nền dữ liệu — đã hoàn thành

- [x] Kiểm kê category, metadata, chuyên ngành người dùng và lịch sử mượn thật.
- [x] Tạo lớp taxonomy riêng để không phá bộ lọc category Web/Mobile.
- [x] Ánh xạ 39 ngành HUIT năm 2026 và alias khoa hiện có sang 13 nhóm chủ đề với trọng số.
- [x] Phân loại sách bằng bộ từ khóa có ranh giới từ; không dùng chuỗi con `ai` hoặc fallback theo `book_id`.
- [x] Thêm entity/repository, chuẩn hóa khóa tên ngành và migration integration test.
- [x] Chạy migration trên PostgreSQL Docker; Backend đạt 89/89 test.
- Hồ sơ chi tiết: [`TUAN_10A_NEN_DU_LIEU_GOI_Y.md`](TUAN_10A_NEN_DU_LIEU_GOI_Y.md).

#### Gói 10B — thuật toán cơ sở — đã hoàn thành

- [x] Popularity Baseline lọc trạng thái mượn hợp lệ, suy giảm theo thời gian, chuẩn hóa log và có fallback khi lịch sử thưa.
- [x] TF-IDF/Cosine Content-Based trên title, author, publisher, category và taxonomy chủ đề; candidate pool giới hạn 1.500 sách.
- [x] Có API top-K, điểm số, lý do, `dataSufficient` và `candidateCount`.
- [x] Test thứ hạng, chuẩn hóa, metadata trống, fallback dữ liệu thưa và loại chính cuốn sách nguồn; toàn bộ Backend đạt 93/93 test.
- Việc loại các sách **đã mượn của từng sinh viên** được giữ ở 10C vì cần hồ sơ người dùng đăng nhập, không thuộc hai baseline vô danh của 10B.
- Hồ sơ chi tiết: [`TUAN_10B_THUAT_TOAN_CO_SO.md`](TUAN_10B_THUAT_TOAN_CO_SO.md).

#### Gói 10C — Hybrid và Cold-Start — đã hoàn thành

- [x] Kết hợp Content-Based, độ phù hợp chuyên ngành và popularity bằng trọng số cấu hình mặc định `0,50 / 0,35 / 0,15`.
- [x] Hồ sơ nội dung có trọng số theo độ mới, số lượt mượn và số lần gia hạn; loại toàn bộ đầu sách sinh viên đã mượn khỏi kết quả.
- [x] Cold-start sinh viên mới dùng ánh xạ ngành–chủ đề trước rồi mới popularity/fallback; sách mới vẫn có thể được chọn qua taxonomy và metadata.
- [x] API trả tổng điểm cùng `contentScore`, `departmentScore`, `popularityScore` và lý do dễ giải thích.
- [x] Redis cache mặc định 30 phút; khóa chứa fingerprint ngành và lịch sử nên tự đổi khi mượn/trả/gia hạn cập nhật dữ liệu; lỗi Redis không làm hỏng API.
- [x] Endpoint cá nhân hóa bắt buộc JWT; smoke test xác nhận anonymous nhận HTTP 403.
- [x] Full regression Backend đạt 97/97 test; smoke test PostgreSQL/Redis thật qua cả nhánh có lịch sử và cold-start.
- Hồ sơ chi tiết: [`TUAN_10C_HYBRID_COLD_START.md`](TUAN_10C_HYBRID_COLD_START.md).

#### Gói 10D — tích hợp giao diện — đã hoàn thành

- [x] Web Portal dùng API Hybrid khi có JWT và fallback Popularity cho khách hoặc token hết hạn.
- [x] Flutter dùng cùng API, hiển thị phương pháp, điểm phù hợp và lý do; bấm sách tải lại chi tiết catalog trước khi mở màn hình mượn.
- [x] Có trạng thái tải/rỗng/lỗi, ảnh bìa dự phòng và không đưa dữ liệu gợi ý hard-code trở lại.
- [x] Giữ nguyên tông xanh dương–trắng; Angular production build thành công, Web đạt 66/66 test, Mobile analyze sạch và 3/3 test qua.
- Hồ sơ chi tiết: [`TUAN_10D_TICH_HOP_GIAO_DIEN_GOI_Y.md`](TUAN_10D_TICH_HOP_GIAO_DIEN_GOI_Y.md).

#### Gói 10E — đánh giá và số liệu báo cáo — đã hoàn thành

- [x] Cài đặt audit chia 80/20 theo thời gian; xác nhận lịch sử thật chỉ có 2 tương tác/2 người và 0 người đủ điều kiện, không giả mạo metric thật.
- [x] Chạy 38 kịch bản proxy trên 4.387 sách đã phân loại, tách riêng và ghi nhãn `SYNTHETIC_TOPIC_PROXY_FROM_PRODUCTION_TAXONOMY`.
- [x] So sánh Popularity, Content-Based và Hybrid bằng Precision/Recall/HitRate@10, Coverage và p50/p95 latency.
- [x] Hybrid đạt Precision@10 `1,000000`, Coverage `4,878%`, p50 `53,946 ms`, p95 `85,162 ms` trên proxy; không tuyên bố là hành vi thật.
- [x] Endpoint evaluator chỉ dành cho Admin và có unit/authorization test; toàn bộ Backend đạt 99/99 test.
- Hồ sơ chi tiết: [`TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md`](TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md).

## 9. Chi tiết tuần 11

### 9.1. Phần đã hoàn thành

- Docker Compose cho các dịch vụ nền tảng và Dockerfile Backend multi-stage.
- Testcontainers kiểm tra PostgreSQL, Redis và SMTP Mailpit thật.
- Workflow GitHub Actions chạy Backend test, Web build/test và Mobile analyze/test.
- Quality gate cuối tuần 11: Backend 101/101 test, Web 66/66 test và production build, Mobile analyze sạch cùng 5/5 test.
- HTTP E2E chạy xuyên suốt đăng nhập → tra cứu → đặt giữ → duyệt → QR → checkout → quá hạn/phạt → return trên PostgreSQL thật.
- Kiểm thử tải catalog 200 request/mức đồng thời 20 đạt 200/200 HTTP 200, p95 807,54 ms và không có lỗi.
- CORS allow-list, dependency audit, authorization contract và health endpoint đã được kiểm tra.
- Backup 18.933.805 byte đã restore thành công vào database tạm gồm 48 bảng rồi được dọn sạch.

### 9.2. Phạm vi được để lại

- Không triển khai Web lên hosting/production ở giai đoạn này theo quyết định ngày 19/10/2026.
- Không coi benchmark máy phát triển là cam kết hiệu năng production hoặc thay thế pentest độc lập.
- Diễn tập demo trên thiết bị thật được đưa vào phần kỹ thuật tuần 12.

### 9.3. Tiêu chí hoàn thành tuần 11

- [x] CI bao phủ unit, integration, component/widget, HTTP E2E, build và dependency audit.
- [x] Có E2E tối thiểu cho đăng nhập, tra cứu, đặt giữ, QR thủ thư, checkout, return và phạt.
- [x] Có test tranh chấp checkout cùng phiếu/barcode và chứng minh chỉ một transaction thành công.
- [x] Có báo cáo kiểm thử hiệu năng và bảo mật ở mức phù hợp đồ án.
- [x] Có hướng dẫn cấu hình, vận hành cục bộ, backup/restore và smoke test.

Hồ sơ chi tiết: [`TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md`](TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md).

## 10. Chi tiết tuần 12

### 10.0. Phân công trách nhiệm chính thức

| Vai trò | Phần việc | Không tính vào phần việc |
|---|---|---|
| Thành viên phụ trách code | Hoàn thiện mã nguồn, migration, test tự động, build, đánh giá kỹ thuật RAG; chuẩn hóa cấu hình, hướng dẫn chạy và repository trước khi push; sửa lỗi do vòng kiểm thử phản hồi | Không triển khai Web lên Internet; không soạn hoặc định dạng Word/PowerPoint; không tự thực hiện kiểm thử nghiệm thu thay người nhận bàn giao |
| Thành viên kiểm thử/triển khai | Clone phiên bản đã push, cấu hình môi trường riêng, chạy kiểm thử nghiệm thu, phản hồi lỗi; sau khi đạt thì triển khai hệ thống lên Internet và kiểm tra môi trường production | Không sửa trực tiếp lịch sử kết quả kiểm thử cục bộ của thành viên code; mọi lỗi cần có bước tái hiện rõ ràng |
| Thành viên Word/PowerPoint | Dùng bộ minh chứng kỹ thuật để viết báo cáo, làm slide, chọn/chú thích ảnh và chuẩn bị phần trình bày | Không phải tự suy luận nghiệp vụ, metric hoặc kiến trúc từ mã nguồn khi chưa có bằng chứng |

Ranh giới này không làm giảm tiêu chí chung của đồ án; nó chỉ xác định đúng người chịu trách nhiệm. Thành viên code phải cung cấp đầu vào có thể kiểm chứng, nhưng không bị tính là chưa hoàn thành chỉ vì Word/PPT hoặc triển khai Internet của thành viên khác chưa hoàn tất.

### 10.1. Hiện trạng

- Đã có đề cương, Use Case, sơ đồ, ADR, README và bảng tiến độ làm nguồn đầu vào.
- Chatbot Web đã chuyển sang gọi `POST /api/v1/chatbot/query`; không còn API key hoặc lệnh gọi Google AI trực tiếp trong Angular.
- Backend đã có kho tri thức kiểm soát, chunking, embedding 768 chiều, pgvector/HNSW cosine retrieval, catalog retrieval, Gemini gateway, nguồn trích dẫn và fallback offline có căn cứ.
- Gemini đã được cấu hình cục bộ và xác minh trực tuyến: 7 chunk dùng `gemini-embedding-2`; câu trả lời thử nghiệm 12A dùng `gemini-3.6-flash`, có dẫn nguồn và không dùng fallback.
- Gói 12B đã có bộ 14 câu phiên bản hóa, endpoint Admin, script tái tạo và JSON kết quả. Lượt chốt đạt 100% Hit/Recall@5, nguồn đúng, nội dung bắt buộc, citation và từ chối đúng; p50 1.067 ms, p95 2.100 ms, không có lỗi bất ngờ.
- Gói 12C đã có mục lục bàn giao duy nhất, bảng role/API/truy vết, danh mục sơ đồ nguồn, kịch bản chụp màn hình, đoạn tóm tắt kỹ thuật và tệp nguồn số liệu có checksum/cách tái tạo. Nội dung Word/PPT đã được ánh xạ theo `Mau bao cao_Do an_Khoa luan_2025.docx`: Mở đầu, 5 chương, Kết luận, danh mục hình/bảng và khung 16 slide.
- Gói 12D đã chốt cấu hình mẫu, tài khoản demo, gate Backend/Web/Mobile, Docker health/smoke, tải, backup/restore, preflight secret và hồ sơ bàn giao. Chưa commit/push theo chủ ý của người dùng.
- Bản Word, PowerPoint và việc triển khai Internet là đầu ra của các thành viên nhận bàn giao, không nằm trong phần code còn lại.

### 10.2. Gói 12A — nâng Chatbot Gemini thành RAG thật

- [x] Chuyển việc gọi Gemini từ Angular sang Backend; khóa API chỉ đọc từ biến môi trường/secret và tuyệt đối không gửi xuống trình duyệt hoặc commit vào repository.
- [x] Tạo kho tri thức có kiểm soát từ chính sách thư viện đã chốt, hướng dẫn sử dụng và metadata catalog phù hợp; mỗi tài liệu có `source`, phiên bản, ngày hiệu lực và hash nội dung.
- [x] Xây dựng pipeline nạp dữ liệu gồm làm sạch, chia đoạn, embedding và lưu chỉ mục `vector(768)` với HNSW cosine trên pgvector.
- [x] Khi nhận câu hỏi, Backend truy xuất top-K đoạn và catalog liên quan trước khi gọi Gemini; phản hồi trả kèm nguồn tham chiếu.
- [x] Có ngưỡng tin cậy và fallback: không đủ bằng chứng thì từ chối trả lời; thiếu key/lỗi Gemini thì trích xuất từ nguồn đã truy xuất.
- [x] Đồng bộ quy định mượn–trả, gia hạn, hàng đợi, mất/hỏng, phí và miễn giảm với nghiệp vụ tuần 9; không còn policy prompt hard-code ở Web.
- [x] Web giữ tông xanh dương–trắng, gọi API Chatbot của Backend, hiển thị nguồn và xử lý trạng thái tải/lỗi/fallback.
- [x] Có rate limit, timeout, giới hạn độ dài đầu vào, che email/SĐT/MSSV trước khi gọi Gemini, audit dạng hash và biện pháp chống prompt injection.

Hồ sơ chi tiết: [`TUAN_12A_CHATBOT_RAG_GEMINI.md`](TUAN_12A_CHATBOT_RAG_GEMINI.md). Gói 12A hoàn thành cả mã nguồn, vận hành cục bộ và kết nối Gemini trực tuyến; bộ câu hỏi chuẩn cùng metric định lượng được thực hiện ở 12B.

### 10.3. Gói 12B — kiểm thử và đánh giá RAG

**Người thực hiện chính:** thành viên phụ trách code. Đây là phần kỹ thuật bắt buộc trước khi push để người khác kiểm thử.

- [x] Tạo bộ 14 câu chuẩn bao phủ quy định, tra cứu catalog, câu hỏi không có dữ liệu, câu hỏi mâu thuẫn và prompt injection.
- [x] Kiểm thử riêng retrieval (đúng nguồn/top-K), generation/fallback (bám nguồn, không bịa), authorization, lỗi Gemini, lỗi vector store và timeout.
- [x] Đo Hit/Recall@5, nguồn đúng, nội dung bám nguồn, citation, từ chối đúng và p50/p95 latency; ghi rõ đây là bộ kiểm thử thiết kế, không phải log người dùng thật.
- [x] Có integration/unit test Backend và component/service test Web; production build không chứa API key.
- [x] Xuất kết quả kỹ thuật, kiến trúc, sequence, bộ câu hỏi và hạn chế dưới dạng Markdown/JSON có thể tái tạo; không yêu cầu thành viên code soạn Word/PPT.

Hồ sơ chi tiết: [`TUAN_12B_DANH_GIA_CHATBOT_RAG.md`](TUAN_12B_DANH_GIA_CHATBOT_RAG.md) và [`rag_evaluation_2026-10-20.json`](rag_evaluation_2026-10-20.json).

### 10.4. Gói 12C — đầu vào kỹ thuật cho người làm Word và PowerPoint

**Người thực hiện chính:** thành viên phụ trách code chỉ chuẩn hóa dữ liệu và bằng chứng; thành viên tài liệu chịu trách nhiệm viết, định dạng và hoàn thiện Word/PPT.

- [x] Tạo một mục lục bàn giao duy nhất, liên kết đến đúng tài liệu và bằng chứng thay vì yêu cầu người viết tự tìm trong repository.
- [x] Cung cấp bản tóm tắt kỹ thuật về phạm vi, actor, chức năng theo tuần và các nội dung đã loại khỏi phạm vi để người viết sử dụng làm nguồn.
- [x] Kiểm tra và bàn giao file nguồn của các sơ đồ: kiến trúc tổng thể, deployment cục bộ, Business/System Use Case, ERD, Analysis/Design Class, state machine mượn–trả, QR thủ thư, recommender và pipeline RAG; việc dàn trang trong báo cáo/slide do thành viên tài liệu thực hiện.
- [x] Lập bảng chức năng theo role, bảng API chính và ma trận yêu cầu → mã nguồn → test → bằng chứng.
- [x] Lập danh sách màn hình, đường dẫn và kịch bản cần chụp; thành viên Word/PPT hoặc kiểm thử phụ trách chụp/chú thích ảnh trên bản nghiệm thu, đồng thời phải che khóa, token, mật khẩu và dữ liệu nhạy cảm.
- [x] Tổng hợp số liệu đã kiểm chứng: quality gate tuần 11, HTTP E2E, benchmark, backup/restore, recommender và RAG; không trộn metric proxy với dữ liệu hành vi thật.
- [x] Viết các đoạn tóm tắt kỹ thuật ngắn về công nghệ, quyết định thiết kế, kết quả, giới hạn và điểm mới; thành viên tài liệu tự biên tập thành văn phong báo cáo/slide.
- [x] Bổ sung riêng một trang/tệp “nguồn số liệu và cách tái tạo” để mọi con số trong Word/PPT truy ngược được về lệnh chạy hoặc file kết quả.

Hồ sơ chi tiết: [`TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md`](TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md), [`TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md`](TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md) và [`TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md`](TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md).

### 10.5. Gói 12D — chuẩn bị repository để push và bàn giao kiểm thử — đã hoàn thành phần kỹ thuật

**Người thực hiện chính:** thành viên code chuẩn bị bản bàn giao; thành viên kiểm thử/triển khai thực hiện nghiệm thu và triển khai Internet sau khi nhận repository.

Đã tạo hai khu vực bàn giao theo vai trò: `TestChucNang` có checklist 118 ca và hướng dẫn package ID/Firebase; `Word_PPTX` có snapshot khung báo cáo, nghiệp vụ, sơ đồ và số liệu. Hai gói đã được bổ sung Mobile/FCM ngày 21/10/2026.

- [x] Chốt dữ liệu seed và tài khoản demo Admin/Librarian/Student; xác minh đăng nhập bằng email và MSSV; không đưa credential production hoặc API key thật vào Git.
- [x] Chạy quality gate cập nhật ngày 21/10/2026: Backend 121 test và JAR; Web 69 test/build; Mobile analyze/5 test/APK; tất cả đạt.
- [x] Hoàn thiện `.env.example`, migration, hướng dẫn khởi động/dừng, health check, RAG reindex và backup/restore để người nhận có thể tự dựng môi trường.
- [x] Soạn checklist bàn giao gồm nhánh/commit nền, phiên bản công cụ, lệnh chạy test, dữ liệu seed, tài khoản demo, luồng nghiệm thu và giới hạn đã biết.
- [x] Kiểm tra repository trước khi push bằng `week12d-preflight.ps1`: 0 lỗi, không phát hiện mẫu secret; bỏ `node_modules` gốc khỏi Git index; chỉ commit/push khi người dùng yêu cầu.
- [ ] Sau khi push, tiếp nhận lỗi có bước tái hiện từ thành viên kiểm thử, sửa trong mã nguồn và chạy lại gate liên quan.
- [ ] Thành viên kiểm thử/triển khai clone bản đã push, chạy nghiệm thu trên môi trường của họ, cấu hình secret/HTTPS/domain và triển khai Internet; các bước này không tính vào phần code của thành viên hiện tại.

Kết quả chi tiết: [`TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md`](TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md).

### 10.6. Tiêu chí hoàn thành phần kỹ thuật của thành viên code

- [x] Chatbot sử dụng RAG thật qua Backend, có nguồn, fallback và bộ đánh giá; API key không xuất hiện ở Web hoặc Git.
- [x] Toàn bộ test/build đã chốt chạy lại thành công trên repository chuẩn bị push.
- [x] Có dữ liệu seed, cấu hình mẫu, hướng dẫn dựng môi trường và checklist bàn giao đủ để thành viên khác tự kiểm thử.
- [x] Metric, API, sơ đồ và kết quả kỹ thuật đều truy ngược được về mã nguồn, lệnh chạy hoặc file kết quả.
- [x] Preflight không phát hiện secret trong các file sẽ bàn giao; `.env`, backup, build và file tạm được loại khỏi Git; repository sẵn sàng tạo commit/push khi người dùng yêu cầu.
- [ ] Các lỗi thuộc mã nguồn do vòng nghiệm thu phát hiện được xử lý và kiểm tra hồi quy.

### 10.7. Tiêu chí hoàn thành chung của nhóm

- [ ] Thành viên kiểm thử xác nhận bản đã push chạy đúng trên môi trường độc lập và lưu kết quả nghiệm thu.
- [ ] Thành viên triển khai cấu hình môi trường Internet, secret, domain/HTTPS, backup và health monitoring.
- [ ] Thành viên tài liệu hoàn tất Word/PPT dựa trên bộ bằng chứng kỹ thuật và phản ánh đúng sản phẩm đã nghiệm thu.
- [ ] Nhóm diễn tập demo cuối trên bản triển khai hoặc phương án cục bộ dự phòng.

## 11. Thứ tự triển khai chính thức

### Giai đoạn 0: Chốt tuần 1–5

1. Gói 1A: chốt phạm vi, truy vết và kiểm kê sai lệch — **đã hoàn thành**.
2. Gói 1B: cập nhật Business/System Use Case, sơ đồ phân tích, sơ đồ thiết kế, ERD, tài liệu công nghệ và thiết kế QR — **đã hoàn thành**.
3. Tuần 1–5 đã được đóng về tài liệu và thiết kế ngày 17/10/2026.

### Giai đoạn A: Tuần 6 — đã hoàn tất

1. Hardening Auth và chuẩn hóa Security.
2. Chuẩn hóa DTO và hợp đồng API.
3. Sửa CRUD sách, category và API vị trí.
4. Chuẩn hóa inventory supplier/receipt.
5. Thêm test và đạt build gate.

### Giai đoạn B: Hoàn tất lõi tuần 9

1. Gói 9A: xây dựng state machine, chuẩn hóa migration và hoàn thiện API chi tiết/duyệt/từ chối — **đã hoàn thành**.
2. Gói 9B: thêm checkout/return transactionally cùng các ràng buộc 3 cuốn, 10 ngày, gia hạn, quá hạn, nợ và idempotency — **đã hoàn thành**.
3. Gói 9C: tiền phạt trễ, scheduler, hàng đợi, mất/hỏng, khóa lưu thông, miễn giảm, thu tiền và đối soát — **đã hoàn thành cùng integration test PostgreSQL**.
4. Quy tắc HUIT được chốt ngày 18/10/2026 đã có migration, API, Web staff và Portal sinh viên. Các biểu phí biến đổi được Admin cấu hình thay vì hard-code.

Tuần 9 được làm trước phần giao diện còn thiếu của tuần 7 và tuần 8 vì đây là lõi nghiệp vụ mà cả Web và Mobile phụ thuộc.

### Giai đoạn C: Hoàn tất tuần 7 và phần triển khai chính tuần 8 — đã hoàn thành

1. Kết nối Web Admin vào API lõi thật.
2. Kết nối Mobile sinh viên vào API lõi thật và bổ sung chế độ quét QR bắt buộc cho Librarian — **đã hoàn thành; cá nhân hóa và HTTP E2E cũng đã được chốt ở tuần 10–11**.
3. Hoàn thiện nhập sách, vị trí, tài liệu số, kiểm kê và dashboard — **đã hoàn thành**.
4. Xóa mock và nút không có chức năng — **đã hoàn thành trong màn hình nghiệp vụ tuần 7**.
5. Hoàn thiện test Web/Mobile — **component/service/widget test và HTTP E2E đã hoàn thành**.

### Giai đoạn D: Hoàn tất tuần 10

1. Gói 10A: chuẩn hóa taxonomy, ánh xạ ngành và dữ liệu chủ đề — **đã hoàn thành**.
2. Gói 10B: Popularity Baseline, TF-IDF/Cosine và sách tương tự — **đã hoàn thành**.
3. Gói 10C: Hybrid, cold-start, loại sách đã mượn, lý do gợi ý và cache — **đã hoàn thành**.
4. Gói 10D: tích hợp Web/Mobile — **đã hoàn thành**.
5. Gói 10E: thực nghiệm đối chứng và số liệu báo cáo — **đã hoàn thành**.

### Giai đoạn E: Hoàn tất tuần 11 — đã hoàn thành

1. Chạy HTTP E2E toàn bộ luồng chính với PostgreSQL/Redis/Mailpit thật — **đã hoàn thành**.
2. Kiểm tra phân quyền, CORS, dependency, rollback và concurrency — **đã hoàn thành**.
3. Chạy benchmark, Docker health/smoke và diễn tập backup/restore — **đã hoàn thành**.
4. Sửa lỗi giải mã response JSON/text/rỗng trên Mobile và chốt quality gate — **đã hoàn thành**.

### Giai đoạn F: Hoàn tất phần kỹ thuật bổ sung và tuần 12

1. **Gói 12A:** thay Chatbot prompt tĩnh bằng RAG Backend an toàn, có kho tri thức, retrieval và dẫn nguồn — **đã hoàn thành ngày 20/10/2026**.
2. **Gói 12B:** thành viên code kiểm thử, đánh giá RAG và xuất số liệu kỹ thuật có thể tái tạo — **đã hoàn thành ngày 20/10/2026**.
3. **Gói 12C:** thành viên code chuẩn hóa đầu vào kỹ thuật; thành viên tài liệu dùng đầu vào này để làm Word/PowerPoint — **đã hoàn thành ngày 20/10/2026**.
4. **Gói 12D:** thành viên code chốt seed/cấu hình/gate và chuẩn bị repository để push — **đã hoàn thành ngày 20/10/2026; chuyển sang bàn giao GitHub ngày 21/10/2026**.
5. **Gói 12E:** hoàn thiện hồ sơ/tìm kiếm Mobile, loại dữ liệu cố định và tích hợp FCM thật — **đã hoàn thành mã nguồn ngày 21/10/2026; package ID/Firebase và nghiệm thu thiết bị đã bàn giao cho thành viên kiểm thử/triển khai**.
6. Thành viên kiểm thử/triển khai clone repository, đổi package ID thành `vn.edu.huit.library`, tạo và cấu hình Firebase, nghiệm thu rồi phản hồi lỗi; thành viên code chỉ sửa các lỗi mã nguồn có bước tái hiện rõ ràng.
7. Sau khi nghiệm thu đạt, thành viên triển khai đưa Web lên Internet; thành viên tài liệu chốt Word/PowerPoint.

Thứ tự tiếp theo là: **người dùng yêu cầu tạo commit/push → thành viên kiểm thử/triển khai làm tài liệu `TestChucNang/06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md` và kiểm thử đúng commit → thành viên code sửa lỗi nếu có**. Việc triển khai Internet chỉ bắt đầu sau khi bản push được nghiệm thu; Word/PowerPoint được làm song song bởi thành viên tài liệu. Phần phát triển chức năng theo đề cương của thành viên code đã hoàn tất; FCM còn là bước cấu hình/nghiệm thu của thành viên kiểm thử/triển khai.

## 12. Những phần tạm hoãn để bảo vệ tiến độ

- Đặt phòng học và toàn bộ luồng `rooms` đã loại khỏi phạm vi, không triển khai về sau.
- Kiosk tự phục vụ và các chức năng ngoài phạm vi bắt buộc của đề cương.
- Các hiệu ứng hoặc dashboard trang trí không dùng dữ liệu thật.
- Thuật toán phức tạp hơn baseline nếu chưa hoàn thành kiểm thử nghiệp vụ chính.
- Các mở rộng Chatbot ngoài tiêu chí RAG 12A–12B như giọng nói, đa ngôn ngữ, agent tự thao tác nghiệp vụ hoặc fine-tuning mô hình.

Các phần trên chỉ được tiếp tục sau khi các tiêu chí bắt buộc của tuần 1–12 đã đạt hoặc được giảng viên hướng dẫn xác nhận điều chỉnh phạm vi.
