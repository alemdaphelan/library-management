# TUẦN 12C — GÓI BÀN GIAO MINH CHỨNG KỸ THUẬT

Ngày chốt: **20/10/2026**
Đối tượng sử dụng: thành viên làm Word/PowerPoint và thành viên kiểm thử.
Phạm vi: chuẩn hóa đầu vào kỹ thuật; **không** thay người phụ trách biên soạn báo cáo, chụp ảnh, kiểm thử độc lập hoặc triển khai Internet.

Gói bàn giao đã được đối chiếu với `Mau bao cao_Do an_Khoa luan_2025.docx`. Người làm tài liệu phải dùng cấu trúc **Mở đầu → Chương 1 Khảo sát → Chương 2 Phân tích → Chương 3 Thiết kế → Chương 4 Cài đặt → Chương 5 Thử nghiệm và triển khai → Kết luận**, không chuyển nguyên thứ tự tuần 1–12 thành bố cục báo cáo. Khung chi tiết cho Word và PowerPoint nằm trong `TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md`.

## 1. Cách dùng gói bàn giao

Đây là mục lục duy nhất để bắt đầu tra cứu. Khi thông tin khác nhau, ưu tiên theo thứ tự:

1. Mã nguồn, migration và test hiện tại.
2. File JSON kết quả máy đọc được và tài liệu tái tạo số liệu.
3. Khung nội dung theo mẫu báo cáo 2025 và tài liệu tuần 9–12 đã chốt.
4. Các file `api_context.md`, `backend_context.md` và bản Word cũ chỉ dùng tham khảo lịch sử.

Không được đưa API key, JWT, mật khẩu, OTP, email thật hoặc dữ liệu cá nhân vào ảnh, Word hay PowerPoint.

## 2. Mục lục bằng chứng

| Nội dung | Nguồn chính |
|---|---|
| Mẫu hình thức chính thức | `Mau bao cao_Do an_Khoa luan_2025.docx` |
| Khung Word/PPT theo đúng 5 chương | `TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md` |
| Phạm vi, tiến độ tuần 1–12 | `BANG_DANH_GIA_TIEN_DO_CHI_TIET_TUAN_6_DEN_10.md` |
| Chốt yêu cầu tuần 1–5 | `CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md` |
| Quy tắc mất/hỏng, thu phạt | `CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md` |
| Nền dữ liệu và thuật toán gợi ý | `TUAN_10A_NEN_DU_LIEU_GOI_Y.md` đến `TUAN_10D_TICH_HOP_GIAO_DIEN_GOI_Y.md` |
| Thực nghiệm recommender | `TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md`, `recommendation_evaluation_2026-10-19.json` |
| Kiểm thử và vận hành cục bộ | `TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md`, `week11_performance_2026-10-19.json` |
| Kiến trúc và triển khai RAG | `TUAN_12A_CHATBOT_RAG_GEMINI.md` |
| Đánh giá RAG | `TUAN_12B_DANH_GIA_CHATBOT_RAG.md`, `rag_evaluation_2026-10-20.json` |
| Nguồn số liệu và lệnh tái tạo | `TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md` |
| Sơ đồ nguồn | Mục 7 của tài liệu này và các file `.mmd` trong `Docs/` |

## 3. Phạm vi sản phẩm đã chốt

### 3.1. Tác nhân

| Tác nhân | Trách nhiệm chính |
|---|---|
| Sinh viên/độc giả | Đăng nhập, tra cứu, đặt giữ, xem/gia hạn từng tài liệu, nhận thông báo, xem nợ và thanh toán/đền bù theo quy trình; xem gợi ý và hỏi chatbot. |
| Thủ thư | Duyệt/từ chối đặt giữ, quét QR và barcode để checkout/return, quản lý sách/kho, kiểm kê, tài liệu số, lập hồ sơ mất/hỏng, ghi nhận thu tiền và yêu cầu điều chỉnh. |
| Admin | Có quyền quản trị; quản lý tài khoản và cấu hình nghiệp vụ, duyệt miễn giảm/điều chỉnh, chạy đánh giá recommender/RAG và reindex RAG. |
| Hệ thống | Scheduler quá hạn/giữ chỗ/thông báo, tính công nợ, khóa/mở quyền lưu thông, cache, audit và sao lưu/khôi phục theo script. |

### 3.2. Ngoài phạm vi

- Đặt phòng học, nhận/trả phòng và vi phạm phòng.
- Kiosk tự phục vụ.
- Triển khai Internet, domain, HTTPS và giám sát production trong đợt hiện tại.
- Fine-tuning, voice bot, agent tự thực hiện nghiệp vụ hoặc tuyên bố AI vượt trội trên dữ liệu hành vi thật khi chưa đủ mẫu.
- MongoDB, Kafka và Elasticsearch không được trình bày là nguồn dữ liệu/tìm kiếm chính đã hoàn thiện.

## 4. Tóm tắt chức năng theo tuần

Phần này chỉ dùng truy vết tiến độ và giải thích quá trình thực hiện. Khi viết Word/PPT, phải phân phối nội dung vào 5 chương theo khung báo cáo 2025, không dùng “Tuần 6”, “Tuần 9” hoặc “Gói 12B” làm tiêu đề chương/mục chính.

| Tuần | Kết quả kỹ thuật có thể trình bày |
|---:|---|
| 1–3 | Khảo sát, chốt phạm vi, Business/System Use Case, đặc tả và lớp phân tích. |
| 4–5 | ERD/lớp thiết kế, kiến trúc, Web Portal/Admin và nền Mobile; đồng bộ lại theo phạm vi bỏ đặt phòng, thêm QR thủ thư. |
| 6 | Auth/JWT/OTP/refresh/logout, phân quyền, DTO, CRUD sách–sinh viên–nhà cung cấp–vị trí và test nền tảng. |
| 7 | Web Admin dùng API thật: dashboard, tài khoản, sách/kho, mượn trả, tài liệu số, kiểm kê, mất/hỏng và thu phạt. |
| 8 | Flutter dùng API thật: đăng nhập, tra cứu, vị trí, phiếu mượn, thông báo, đề xuất, gợi ý và chế độ quét QR cho thủ thư. |
| 9 | State machine mượn–trả; checkout/return transaction; gia hạn từng tài liệu; quá hạn, hàng đợi, mất/hỏng, công nợ, thanh toán, điều chỉnh, miễn giảm và khóa thẻ. |
| 10 | Popularity, TF-IDF/Cosine và Hybrid 0,50/0,35/0,15; cold-start, Redis cache, Web/Mobile và thực nghiệm đối chứng. |
| 11 | Test tích hợp/E2E, concurrency/rollback, kiểm thử tải, bảo mật, Docker smoke và backup/restore cục bộ. |
| 12A–12B | RAG thật qua Backend, pgvector, Gemini, dẫn nguồn/fallback/an toàn và bộ đánh giá 14 ca. |
| 12C | Gói đầu vào kỹ thuật này: mục lục, sơ đồ, ma trận truy vết, số liệu và kịch bản ảnh. |

## 5. Ma trận chức năng theo role

Ký hiệu: `X` được thực hiện; `X*` được phép theo điều kiện nghiệp vụ; `—` không thuộc role.

| Nhóm chức năng | Sinh viên | Thủ thư | Admin | Điểm kiểm soát |
|---|:---:|:---:|:---:|---|
| Đăng nhập, hồ sơ, đổi/quên mật khẩu | X | X | X | JWT, refresh token, BCrypt, OTP |
| Tra cứu/xem chi tiết sách | X | X | X | GET catalog công khai; thao tác ghi có phân quyền |
| Đặt giữ, hủy, xem phiếu | X* | — | — | Chặn thẻ/nợ; không trùng hàng đợi |
| Gia hạn từng tài liệu | X* | — | — | Tối đa 1 lần +10 ngày; chặn quá hạn/nợ/thẻ khóa/hàng đợi |
| Duyệt/từ chối đặt giữ | — | X | X | Lưu lịch sử chuyển trạng thái |
| Quét QR, checkout, return | — | X | X | QR ký số có hạn; barcode; idempotency; transaction |
| Quản lý sách, bản sao, vị trí, nhập kho | — | X | X | API ghi yêu cầu role nhân viên |
| Kiểm kê và tài liệu số | X (đọc) | X | X | Download xác thực; upload/xóa cho nhân viên |
| Xử lý mất/hỏng và ghi nhận thanh toán | — | X | X | Phiếu thu bóc tách, không hard delete |
| Duyệt miễn giảm/điều chỉnh/cấu hình phí | — | — | X | Sinh giao dịch điều chỉnh ngược và audit |
| Xem thông báo/công nợ của mình | X | X | X | Dữ liệu theo tài khoản |
| Gợi ý cá nhân hóa | X | X | X | Yêu cầu đăng nhập; cold-start theo ngành + phổ biến |
| Chatbot RAG | X | X | X | Input guard, rate limit, nguồn, fallback |
| Reindex/evaluate RAG và evaluate recommender | — | — | X | Contract test xác nhận 403 khi thiếu quyền |

## 6. Danh mục API chính

Danh sách này phục vụ báo cáo và demo, không thay Swagger. Các controller cũ chưa đồng nhất tiền tố `/api/v1`; phải giữ đúng đường dẫn dưới đây.

| Nhóm | Method và endpoint | Role chính | Mục đích |
|---|---|---|---|
| Auth | `POST /api/v1/auth/login`, `/refresh-token`, `/logout` | Công khai/xác thực | Phiên JWT và thu hồi token |
| Auth | `POST /api/v1/auth/forgot-password`, `/verify-otp`, `/reset-password` | Công khai | Khôi phục mật khẩu |
| Catalog | `GET /books`, `GET /books/{id}`, `GET /categories` | Công khai | Tra cứu và chi tiết sách |
| Catalog | `POST/PUT/DELETE /books` | Librarian/Admin | Quản trị đầu sách |
| Location | `/locations/floors`, `/bookshelves`, `/shelves` | Librarian/Admin | CRUD vị trí vật lý |
| Circulation | `POST /loans/reserve`, `GET /loans/my-loans`, `POST /loans/cancel/{id}` | Sinh viên | Đặt giữ và quản lý phiếu cá nhân |
| Circulation | `POST /loans/renew/{ticketId}/items/{detailId}` | Sinh viên | Gia hạn đúng một tài liệu |
| Circulation | `POST /loans/{id}/approve`, `/reject` | Librarian/Admin | Xử lý yêu cầu |
| Circulation | `POST /loans/checkout`, `POST /loans/return` | Librarian/Admin | Giao/nhận sách tại quầy |
| QR | `GET /api/v1/qr/tickets/{id}`, `GET /api/v1/qr/cards/me` | Xác thực | Sinh QR ký số, có TTL |
| QR | `POST /api/v1/qr/resolve` | Librarian/Admin | Xác thực QR và xác định hành động server-side |
| Waitlist | `POST /waitlist/join`, `GET /waitlist/my` | Sinh viên | Hàng đợi theo đầu sách |
| Penalty | `/api/v1/penalty-cases` và các action review/replacement | Theo action | Hồ sơ mất/hỏng, miễn giảm, chấp nhận thay thế |
| Payment | `POST /api/v1/transactions/payments` | Librarian/Admin | Thu tiền từng phần/toàn phần và sinh biên nhận |
| Payment | `/api/v1/transactions/adjustments` | Librarian tạo/Admin duyệt | Điều chỉnh không xóa giao dịch gốc |
| Notification | `GET /api/v1/notifications`, `PATCH /{id}/read` | Xác thực | Thông báo và đánh dấu đã đọc |
| Digital | `GET /digital-assets`, `GET /{id}/download` | Xác thực | Danh sách và tải tài liệu số |
| Digital | `POST /digital-assets/upload`, `PUT/DELETE /{id}` | Librarian/Admin | Quản trị tài liệu số |
| Recommendation | `GET /books/recommendations/personalized` | Xác thực | Top-K Hybrid kèm lý do |
| Recommendation | `GET /books/{id}/similar` | Công khai | Sách tương tự theo nội dung |
| Recommendation | `GET /admin/recommendations/evaluation` | Admin | Thực nghiệm đối chứng |
| RAG | `POST /api/v1/chatbot/query` | Công khai có kiểm soát | Hỏi đáp có nguồn |
| RAG Admin | `GET /api/v1/admin/rag/status`, `POST /reindex`, `POST /evaluate` | Admin | Trạng thái, lập chỉ mục và đánh giá |
| Operations | `GET /actuator/health` | Công khai | Health check tổng quát |

## 7. Danh mục sơ đồ và nguồn chỉnh sửa

| Sơ đồ | Nguồn | Trạng thái/sử dụng |
|---|---|---|
| Business Use Case | `business_usecase_v41.docx`, `Mô hình/business usecase/business usecase.png` | Đã đồng bộ phạm vi; bản Word là nguồn chỉnh sửa |
| System Use Case | `system_usecase_v41.docx`, các PNG trong `Mô hình/system usecase/` | Tách theo tác nhân |
| Analysis Class | `analysis_class_diagram.mmd` | Mermaid chỉnh sửa được |
| Design Class | `design_class_diagram.mmd` | Mermaid chỉnh sửa được |
| ERD/Relational schema | `huit_library_erd.mmd` | Mermaid; phản ánh circulation/finance/recommendation/RAG |
| Kiến trúc tổng thể | `system_architecture.mmd` | Nguồn mới cho báo cáo/slide |
| Triển khai cục bộ | `local_deployment.mmd` | Phân biệt Docker Backend và Angular dev server |
| State machine mượn–trả | `circulation_state_machine.mmd` | Trạng thái phiếu chuẩn; LOST/DAMAGED ở mức chi tiết/hồ sơ |
| QR thủ thư | `librarian_qr_flow.mmd`, `librarian_qr_wireframe.mmd` | Luồng và wireframe |
| Pipeline recommender | `recommendation_pipeline.mmd` | Ba thành phần, cold-start và cache |
| Pipeline RAG | `rag_pipeline.mmd` | Index, retrieval, evidence gate, generation/fallback |
| Sequence RAG | `rag_query_sequence.mmd` | Chuỗi gọi Web–Backend–pgvector–Gemini–audit |

Khi xuất hình, người làm tài liệu cần ghi tên hình và chú thích “Nguồn: nhóm tác giả”, giữ font/độ phân giải thống nhất, và không sửa logic sơ đồ nếu chưa đối chiếu mã nguồn.

## 8. Ma trận truy vết yêu cầu → mã → test → bằng chứng

| Yêu cầu | Mã nguồn đại diện | Test đại diện | Bằng chứng |
|---|---|---|---|
| Auth/JWT/OTP/phân quyền | `modules/auth`, `core/security`, `SecurityConfig.java` | `AuthServiceTest`, `AuthControllerTest`, `UserAuthorizationContractTest`, `Week6IntegrationTest` | Progress tuần 6; quality gate |
| CRUD sách và vị trí | `modules/catalog` | `BookServiceTest`, `BookCopyServiceTest`, `LocationServiceTest` | Web `/admin/books`, `/admin/shelf-map` |
| State machine đặt giữ–mượn–trả | `CirculationStateMachine.java`, `CirculationStateService.java` | `CirculationStateMachineTest`, `CirculationStateServiceTest`, HTTP E2E tuần 11 | `circulation_state_machine.mmd` |
| Checkout/return an toàn | `CirculationStateService.java`, operation/idempotency repositories | `CirculationTransactionServiceTest`, HTTP E2E, concurrency/rollback integration | Tài liệu tuần 11 |
| QR thủ thư | `LibrarianQrController/Service.java`, màn hình Flutter QR | `LibrarianQrServiceTest`, `LibrarianQrAuthorizationContractTest` | `librarian_qr_flow.mmd` |
| Hàng đợi 48 giờ | `WaitlistService.java`, `CirculationMaintenanceService.java` | `WaitlistServiceTest`, `CirculationMaintenanceServiceTest` | Chính sách 9C |
| Quá hạn/mất/hỏng/vi phạm | `modules/penalty` | `LateFineServiceTest`, `PenaltyCaseServiceTest`, `OverdueViolationServiceTest` | Chính sách 9C; Web penalties |
| Công nợ, thanh toán, điều chỉnh | `modules/finance` | `DebtLedgerServiceTest` và HTTP E2E | API payment/ledger/adjustment |
| Tài liệu số | `modules/digital` | `DigitalAssetServiceTest`, `DigitalAssetAuthorizationContractTest` | Web `/digital`, `/admin/digital-assets` |
| Kiểm kê kho | `InventoryAuditService.java` và controller/DTO | `InventoryAuditServiceTest`, `InventoryServiceTest` | Web `/admin/inventory-audit` |
| Hybrid recommender | `modules/recommendation`, endpoint trong `BookController` | 6 nhóm test recommendation | Tài liệu 10A–10E và JSON thực nghiệm |
| RAG có nguồn/fallback/an toàn | `modules/rag`, migration 18, Web chatbot | 8 nhóm test RAG + 2 test Web chatbot | Tài liệu 12A–12B và JSON 14 ca |
| E2E, tải và vận hành | test integration, scripts tuần 11 | Backend/Web/Mobile gate, smoke, load, restore | Tài liệu tuần 11 và JSON hiệu năng |

## 9. Danh sách màn hình và kịch bản cần chụp

Người kiểm thử hoặc người làm tài liệu chụp trên bản nghiệm thu sau khi seed dữ liệu. Mỗi ảnh cần thấy tiêu đề trang, dữ liệu kết quả và trạng thái thành công/lỗi có ý nghĩa; không chụp màn hình trống.

| Mã ảnh | Nền tảng/đường dẫn | Kịch bản cần thể hiện |
|---|---|---|
| W01 | Web `/login` | Đăng nhập và thông báo validation; không để lộ mật khẩu |
| W02 | Web `/search` + `/book/:id` | Tìm sách, vị trí/bản sao và sách tương tự |
| W03 | Web `/` | Gợi ý cá nhân hóa kèm lý do và khu vực chatbot |
| W04 | Web `/history` | Nhiều tài liệu, trạng thái và nút gia hạn theo từng tài liệu |
| W05 | Web `/notifications` | Nhắc hạn/quá hạn/khóa hoặc mở thẻ |
| W06 | Web `/digital` | Danh sách và tải tài liệu số |
| A01 | Web `/admin` | Dashboard dùng dữ liệu thật |
| A02 | Web `/admin/books` + `/admin/shelf-map` | CRUD sách/bản sao/vị trí |
| A03 | Web `/admin/circulation` | Duyệt phiếu, checkout/return bằng barcode |
| A04 | Web `/admin/imports` + `/admin/inventory-audit` | Phiếu nhập và kiểm kê quét barcode |
| A05 | Web `/admin/penalties` | Hồ sơ mất/hỏng, breakdown phí và trạng thái review |
| A06 | Web `/admin/borrows` | Công nợ, thanh toán từng phần/biên nhận/điều chỉnh |
| A07 | Web `/admin/digital-assets` | Upload và phân quyền tài liệu số |
| M01 | Flutter đăng nhập + trang chủ | Đăng nhập, Top-K gợi ý và điều hướng |
| M02 | Flutter tra cứu + chi tiết | Tìm kiếm, vị trí và đặt giữ |
| M03 | Flutter sách đang mượn | Hạn trả, gia hạn từng cuốn và trạng thái nợ/khóa |
| M04 | Flutter thông báo | Nhắc hạn và thông báo nghiệp vụ |
| M05 | Flutter `LibrarianQrScreen` | Quét QR hợp lệ và hành động `CHECKOUT`/`RETURN` do server trả về |
| R01 | Chatbot trên Web | Câu hỏi quy định, câu trả lời có citation và danh sách nguồn |
| R02 | Chatbot trên Web | Câu ngoài phạm vi bị từ chối có kiểm soát |
| T01 | Swagger/Postman | Admin gọi status/reindex/evaluate RAG; che JWT/key |
| T02 | Terminal/CI | Kết quả gate Backend/Web/Mobile, không chụp secret/env |

Quy ước tên ảnh gợi ý: `W01-login.png`, `A03-circulation-checkout.png`, `R01-rag-citation.png`. Ảnh chỉ là minh họa giao diện; số liệu phải lấy từ JSON/tài liệu nguồn, không đọc lại bằng mắt từ ảnh.

## 10. Số liệu đã kiểm chứng để trích dẫn

| Nhóm | Kết quả được phép dùng | Loại dữ liệu/cảnh báo |
|---|---|---|
| Gate sau 12B | Backend **115/115**, Web **69/69**, Angular production build đạt | Kết quả chạy cục bộ ngày 20/10/2026; Mobile không chạy lại trong 12B |
| Gate tuần 11 | Backend 101/101, Web 66/66, Mobile 5/5, `flutter analyze` sạch, `npm audit` 0 | Snapshot tại thời điểm 19/10/2026 |
| HTTP E2E | Login → search → reserve → approve → QR → checkout → overdue/fine → return đạt | PostgreSQL/Redis/Mailpit Testcontainers thật |
| Load catalog | 200/200 HTTP 200, concurrency 20, p50 618,01 ms, p95 807,54 ms, 30,90 req/s | Máy phát triển, không phải SLA production |
| Backup/restore | Backup 18.933.805 byte; restore kiểm tra 48 bảng | Diễn tập cục bộ, không ghi đè DB chính |
| Recommender | Hybrid Precision@10 1,0; Recall@10 0,167082; Coverage 4,878%; p95 85,162 ms | **Proxy chủ đề synthetic**, 38 kịch bản/4.387 sách; không phải hành vi thật |
| Lịch sử mượn thật | 2 tương tác/2 người; 0 người đủ điều kiện | Không đủ công bố Precision/Recall thật |
| RAG | 14 ca; Hit/Recall@5, source, grounded, citation, refusal đều 100%; p50 1.067 ms, p95 2.100 ms | Dataset kiểm soát; fallback 92,86% vì quota generation; không suy rộng production |

Chi tiết cách tái tạo và SHA-256 nằm trong `TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md`.

## 11. Đoạn tóm tắt kỹ thuật cho người làm báo cáo

### Công nghệ

Hệ thống gồm Web Angular 21, ứng dụng Flutter và Backend Java 17/Spring Boot 3.2. PostgreSQL là dữ liệu nghiệp vụ chính; pgvector lưu embedding cho RAG, Redis phục vụ OTP, token bị thu hồi và cache, Liquibase quản lý schema. Hạ tầng kiểm thử cục bộ dùng Docker Compose, Nginx, Mailpit và Testcontainers.

### Quyết định thiết kế

Nghiệp vụ mượn–trả được khóa bằng state machine và transaction thay vì cập nhật trạng thái rời rạc. QR chỉ mang payload ký số có thời hạn; server xác thực rồi quyết định hành động, còn thủ thư vẫn phải quét barcode và kiểm tra sách. Gợi ý dùng mô hình Hybrid để xử lý dữ liệu thưa và cold-start. Chatbot dùng RAG qua Backend để API key không xuất hiện trên trình duyệt và câu trả lời phải bám nguồn.

### Kết quả

Các luồng chính đã được nối vào Web/Mobile và kiểm thử từ controller qua security/service đến PostgreSQL thật. Hệ thống có test hồi quy, benchmark cục bộ, health/smoke, backup/restore, thực nghiệm recommender và bộ đánh giá RAG có kết quả máy đọc được.

### Điểm mới có thể nhấn mạnh

Điểm nổi bật là sự kết hợp giữa quy tắc thư viện cụ thể, QR thủ thư có xác thực, state machine có lịch sử chuyển trạng thái, gợi ý lai có giải thích/cold-start và RAG có evidence gate, citation, refusal cùng fallback an toàn.

### Giới hạn bắt buộc nêu

Thực nghiệm gợi ý hiện dùng proxy chủ đề vì lịch sử mượn thật chưa đủ; kết quả RAG dùng bộ câu hỏi kiểm soát nhỏ và lượt chốt có tỷ lệ fallback cao do quota Gemini. Benchmark chạy trên máy phát triển. Bản hiện tại chưa được nghiệm thu trên môi trường độc lập và chưa triển khai Internet.

## 12. Trách nhiệm bàn giao và bước tiếp theo

- Thành viên code: hoàn tất 12D, chuẩn bị seed/cấu hình/gate/repository; chỉ commit/push khi người dùng yêu cầu; sửa lỗi có bước tái hiện từ người kiểm thử.
- Thành viên kiểm thử/triển khai: clone bản đã push, dựng môi trường độc lập, chạy checklist, chụp ảnh nghiệm thu, cấu hình secret/domain/HTTPS và triển khai Internet sau.
- Thành viên Word/PPT: dùng gói 12C làm nguồn, biên tập văn phong và dàn trang; không tự sửa số liệu hoặc bỏ cảnh báo giới hạn.

Trước khi bắt đầu dàn trang, thành viên Word/PPT cần đọc `TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md`. Tài liệu này đã chỉ rõ đề mục từng chương, hình/bảng cần dùng, quy cách Times New Roman, lề trang, caption, số trang và khung 16 slide.

Gói 12C hoàn thành khi các liên kết, sơ đồ nguồn, ma trận truy vết, checklist ảnh và nguồn số liệu trong tài liệu này đều còn tồn tại và đọc được.
