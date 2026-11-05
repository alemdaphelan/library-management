# Hồ sơ đã đối chiếu với project hiện tại

Lưu ý sau tích hợp main ngày 05/11/2026: bộ sáu file này giữ nguyên snapshot đối chiếu ngày 04/11, trước commit `b88ac60`. Incoming bổ sung giá trị sách/hạn mức cọc, tham số thu hồi/công nợ, bảo mật và nhiều luồng Mobile; vì vậy không coi các Word, số cột ERD hoặc source hash của bộ này là baseline sau merge. Xem [checkpoint tích hợp](../MERGE_MAIN_20261105.md) và PROJECT_CONTEXT.md cho hiện trạng mới. Các bản gốc và đầu ra sáu file không bị ghi đè trong task merge.

Đã hiệu chỉnh đủ sáu file người dùng cung cấp. Đối chiếu source và DB cục bộ ngày **04/11/2026**; hoàn tất kiểm tra tài liệu ngày **05/11/2026**. Mốc Git: `main`, HEAD `756870b271f9098f12c301515deb741005b49482`, **bao gồm source chưa commit** của các batch trước. Không coi riêng HEAD là toàn bộ baseline.

Bản gốc tại `C:/Users/Lenovo/OneDrive/Desktop/library-managment` được giữ nguyên và kiểm tra lại SHA-256. Đợt này sửa hồ sơ, không xóa code, bảng hoặc dữ liệu để làm khớp tài liệu. Không sửa source runtime hoặc áp migration trong đợt rà soát này.

## Các file sử dụng

| File đã sửa | Nội dung hiện tại |
|---|---|
| [business_usecase_v41.docx](business_usecase_v41.docx) | 15 BUC đang mô tả, 4 BUC cũ chuyển chờ hoặc ngoài phạm vi; 7 trang. |
| [system_usecase_v41.docx](system_usecase_v41.docx) | 27 UC cũ hiệu chỉnh, 15 UC mới UC40–UC54, 12 UC cũ ghi rõ chưa hoàn chỉnh; 16 trang. |
| [Technologies.docx](Technologies.docx) | Stack, cách sử dụng thực tế, triển khai, kiểm tra và giới hạn; 4 trang. |
| [analysis_class_diagram.mmd](analysis_class_diagram.mmd) | 48 lớp miền nghiệp vụ, 55 liên kết logic, thuộc tính chọn lọc. |
| [design_class_diagram.mmd](design_class_diagram.mmd) | 58 lớp Java thật, 58 phụ thuộc qua field; một số phương thức public, lược tham số. |
| [huit_library_erd.mmd](huit_library_erd.mmd) | Snapshot vật lý 66 bảng, 622 cột và 48 FK được DB khai báo. |

Các tên v41 được giữ để đối chiếu đầu vào; nội dung đã cập nhật theo baseline nêu trên. Các file Word có comment tại một số điểm hiệu chỉnh lớn. Định dạng bảng kể luồng dài được đổi thành đoạn văn theo từng UC để dễ đọc; vẫn giữ tác nhân, điều kiện, bước xử lý, ngoại lệ, kết quả và nguồn code.

## Các sai lệch chính đã sửa

| Phần trong hồ sơ cũ | Kết luận từ source và cách sửa | Nguồn đại diện |
|---|---|---|
| Nhiều nhóm độc giả và vai trò kế toán riêng | Chỉ có ADMIN, LIBRARIAN, STUDENT trong phạm vi xác thực hiện tại; quyền thủ thư xem hồ sơ khác quyền Admin sửa tài khoản. | [auth/controller/UserController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/UserController.java) |
| In thẻ, thu phí thẻ và điểm danh tự kích hoạt | Không có luồng thu phí cấp thẻ hoàn chỉnh; điểm danh chỉ cập nhật enrollment và lớp. Chuyển UC02 và UC04 sang chờ. | [orientation/service/OrientationService.java](../../backend/src/main/java/com/huit/library/modules/orientation/service/OrientationService.java) |
| Duyệt tay trước khi giữ sách, khóa giỏ 15 phút, hai lượt gia hạn | Giữ chỗ đủ điều kiện tự chuyển RESERVED trong giao dịch; giỏ chưa gửi không giữ kho; gia hạn theo từng chi tiết và chính sách. | [circulation/service/LoanService.java](../../backend/src/main/java/com/huit/library/modules/circulation/service/LoanService.java) |
| Báo mất tự tạo phạt hoặc nộp tiền là đã trả sách | Tách báo mất của sinh viên, kiểm tra tại quầy, hồ sơ mất hỏng, mốc nhận vật lý và sổ tiền. Bổ sung UC43–UC45. | [circulation/service/ReturnInspectionService.java](../../backend/src/main/java/com/huit/library/modules/circulation/service/ReturnInspectionService.java) |
| Author, Publisher, BookAuthor riêng và tầng Area | Tác giả, nhà xuất bản là chuỗi của books; vị trí là Floor → Bookshelf → Shelf. Xóa các lớp và bảng tưởng định khỏi sơ đồ hiện trạng. | [catalog/entity/BookEntity.java](../../backend/src/main/java/com/huit/library/modules/catalog/entity/BookEntity.java) |
| Một đầu sách chỉ có một tài liệu số; DRM chống sao chép | Một đầu sách có nhiều file, file có thể độc lập; hai mức truy cập AUTHENTICATED và STAFF_ONLY; preview/download có kiểm tra quyền, không có DRM. | [digital/service/DigitalAssetService.java](../../backend/src/main/java/com/huit/library/modules/digital/service/DigitalAssetService.java) |
| Python FastAPI, neural CF, PyTorch hoặc TensorFlow | Gợi ý popularity, TF-IDF và hybrid chạy trong Java; RAG cũng ở backend, Gemini tùy cấu hình. | [recommendation/service/HybridRecommendationService.java](../../backend/src/main/java/com/huit/library/modules/recommendation/service/HybridRecommendationService.java) |
| Kafka Mongo Elastic là pipeline AI chính, STOMP realtime | Có dependency, container hoặc repository khung chưa đủ chứng minh luồng hoạt động. Thông báo DB cùng email/FCM sau commit; không có durable outbox. | [backend/src/main/java/com/huit/library/config/NotificationDeliveryConfig.java](../../backend/src/main/java/com/huit/library/config/NotificationDeliveryConfig.java) |
| NgRx Material PrimeNG Tailwind, Jasmine Karma | Manifest hiện dùng Angular, CSS, service/RxJS và Vitest; không có các bộ UI/state nêu trên. | [web-app/package.json](../../web-app/package.json) |
| Một lệnh Compose chạy cả Web và AI, Nginx có TLS | Compose không build/serve Angular hoặc Python AI; root Nginx là placeholder, cấu hình HTTP 80 và danh sách proxy chưa bao phủ mọi controller. | [nginx/conf.d/default.conf](../../nginx/conf.d/default.conf) |
| Mọi ID trong entity đều là FK DB | ERD chỉ vẽ FK thật; quan hệ scalar ở lớp nghiệp vụ được giữ trong sơ đồ phân tích, không bịa constraint. UUID, bigint, chuỗi barcode và numeric được phân biệt. | [backend/src/main/resources/db/changelog/db.changelog-master.yaml](../../backend/src/main/resources/db/changelog/db.changelog-master.yaml) |
| Phân ngành chính thức từ trọng số gợi ý | Phân loại nhiều ngành có nguồn, phiên bản và lịch sử riêng; không thay bằng trọng số recommendation. Bổ sung UC46. | [dashboard/service/MaterialClassificationService.java](../../backend/src/main/java/com/huit/library/modules/dashboard/service/MaterialClassificationService.java) |
| Thanh lý đầy đủ và ghi giảm tồn sau gửi đề xuất | Chỉ có lập, sửa, gửi, hủy và theo dõi đề xuất; duyệt và thực hiện của Admin còn chờ. Bổ sung UC47 có giới hạn. | [inventory/service/DisposalService.java](../../backend/src/main/java/com/huit/library/modules/inventory/service/DisposalService.java) |
| Thu tiền là tất toán mọi nghĩa vụ; cọc là doanh thu | Có trả từng phần và payment_allocations; tách cọc, miễn giảm, tiền thu. Admin duyệt điều chỉnh hoặc miễn giảm theo nhánh riêng. | [finance/service/DebtLedgerService.java](../../backend/src/main/java/com/huit/library/modules/finance/service/DebtLedgerService.java) |

## Đối chiếu đủ 39 mã hệ thống gốc

Mỗi mã gốc chỉ xuất hiện một lần trong bảng này. “Hiệu chỉnh” là mô tả đã được sửa theo source, không phải chứng nhận toàn bộ luồng đã qua kiểm thử triển khai.

| Mã | Tên ở bản gốc | Xử lý trong bản mới | Nguồn hoặc giới hạn |
|---|---|---|---|
| UC01 | Thêm mới thông tin hồ sơ độc giả | Hiệu chỉnh thành Quản lý hồ sơ sinh viên | [auth/controller/UserController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/UserController.java) |
| UC02 | Ghi nhận lệ phí làm thẻ và thu tiền thế chân | Chờ triển khai | Thu phí cấp thẻ chưa có quy trình xác nhận riêng; ví hoặc cọc không phải biên nhận phí thẻ. Xem UC01 và UC31 cho phần hiện có. [auth/service/UserService.java](../../backend/src/main/java/com/huit/library/modules/auth/service/UserService.java) |
| UC03 | Nạp danh sách điểm danh lớp hướng dẫn | Hiệu chỉnh thành Quản lý lớp hướng dẫn và điểm danh | [orientation/controller/OrientationController.java](../../backend/src/main/java/com/huit/library/modules/orientation/controller/OrientationController.java) |
| UC04 | Kích hoạt thẻ thư viện tự động | Chờ triển khai | Điểm danh chỉ lưu enrollment và trạng thái lớp, không tự kích hoạt thẻ. Phần điểm danh giữ ở UC03. [orientation/service/OrientationService.java](../../backend/src/main/java/com/huit/library/modules/orientation/service/OrientationService.java) |
| UC05 | Tra cứu và lọc tài liệu đa chiều | Hiệu chỉnh thành Tra cứu danh mục và vị trí sách | [catalog/controller/BookController.java](../../backend/src/main/java/com/huit/library/modules/catalog/controller/BookController.java) |
| UC06 | Lập Phiếu Mượn Trực Tuyến | Hiệu chỉnh thành Đăng ký mượn trực tuyến | [circulation/controller/LoanController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java) |
| UC07 | Hủy phiếu mượn tự động do quá thời hạn | Hiệu chỉnh thành Hết hạn giữ chỗ và hủy đăng ký | [circulation/service/CirculationMaintenanceService.java](../../backend/src/main/java/com/huit/library/modules/circulation/service/CirculationMaintenanceService.java) |
| UC08 | Quản lý trạng thái bản vật lý, Thu hồi và Hủy phiếu mượn lỗi | Hiệu chỉnh thành Cập nhật vị trí và tình trạng bản sao | [catalog/controller/BookCopyController.java](../../backend/src/main/java/com/huit/library/modules/catalog/controller/BookCopyController.java) |
| UC09 | Lập Phiếu Mượn Tại Quầy | Hiệu chỉnh thành Giao sách tại quầy | [circulation/controller/LoanController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java) |
| UC10 | Lập Phiếu Mượn Tự Động Tại Kiosk | Ngoài phạm vi | Không có luồng kiosk, RFID hoặc cân điện tử; dùng quầy mượn trả UC09 và UC45. [circulation/controller/LoanController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java) |
| UC11 | Gia hạn tài liệu trực tuyến | Hiệu chỉnh thành Gia hạn từng tài liệu | [circulation/service/LoanService.java](../../backend/src/main/java/com/huit/library/modules/circulation/service/LoanService.java) |
| UC12 | Lập Phiếu Trả Tài Liệu | Hiệu chỉnh thành Nhận trả sách bình thường | [circulation/controller/LoanController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java) |
| UC13 | Ghi nhận hư hỏng và tính phí bồi thường | Hiệu chỉnh thành Lập hồ sơ mất hỏng và bồi thường | [penalty/controller/PenaltyCaseController.java](../../backend/src/main/java/com/huit/library/modules/penalty/controller/PenaltyCaseController.java) |
| UC14 | Xử lý thanh toán khoản phạt trực tuyến hoặc tiền mặt | Hiệu chỉnh thành Thu và phân bổ tiền công nợ | [finance/controller/PaymentController.java](../../backend/src/main/java/com/huit/library/modules/finance/controller/PaymentController.java) |
| UC15 | Đối soát giao dịch thanh toán | Chờ triển khai | Có callback VNPAY kiểm tra chữ ký và chống lặp; chưa có tác vụ đối soát cổng hằng đêm như mô tả cũ. [finance/service/OnlinePaymentService.java](../../backend/src/main/java/com/huit/library/modules/finance/service/OnlinePaymentService.java) |
| UC16 | Khôi phục trạng thái hoạt động thẻ | Hiệu chỉnh thành Cập nhật trạng thái thẻ sau xử lý | [auth/controller/UserController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/UserController.java) |
| UC17 | Tra cứu, đặt, nhận, gia hạn và trả phòng học nhóm | Ngoài phạm vi | Đặt phòng bị loại khỏi phạm vi hiện tại; RoomController chỉ là khung. Bảng cũ vẫn còn. [facilities/controller/RoomController.java](../../backend/src/main/java/com/huit/library/modules/facilities/controller/RoomController.java) |
| UC18 | Xử lý vi phạm không sử dụng phòng nhóm | Ngoài phạm vi | Chưa có quy trình kiểm tra và xử phạt phòng hoạt động. [facilities/controller/RoomController.java](../../backend/src/main/java/com/huit/library/modules/facilities/controller/RoomController.java) |
| UC19 | Tạo và duyệt yêu cầu hoàn tiền thế chân hoặc Hủy thẻ | Chờ triển khai | Chưa có luồng yêu cầu hoàn cọc, duyệt hoàn và hủy thẻ như tài liệu cũ. Không suy ra từ nạp cọc. [finance/controller/PaymentController.java](../../backend/src/main/java/com/huit/library/modules/finance/controller/PaymentController.java) |
| UC20 | Lấy thông tự động hoặc Nhập thủ công | Hiệu chỉnh thành Biên mục và nhận diện ISBN trong danh mục nội bộ | [catalog/controller/BookController.java](../../backend/src/main/java/com/huit/library/modules/catalog/controller/BookController.java) |
| UC21 | Sinh mã vạch và cập nhật số lượng tồn kho | Hiệu chỉnh thành Quản lý barcode và cây vị trí | [catalog/controller/LocationController.java](../../backend/src/main/java/com/huit/library/modules/catalog/controller/LocationController.java) |
| UC22 | Gửi thư điện tử nhắc hạn hoặc Thu hồi tài liệu đột xuất | Hiệu chỉnh thành Nhắc hạn và nhận thông báo | [notification/service/NotificationService.java](../../backend/src/main/java/com/huit/library/modules/notification/service/NotificationService.java) |
| UC23 | Xử lý vi phạm quá hạn để Khấu trừ tiền hoặc Kỷ luật | Hiệu chỉnh thành Theo dõi quá hạn và vi phạm lưu thông | [circulation/service/CirculationMaintenanceService.java](../../backend/src/main/java/com/huit/library/modules/circulation/service/CirculationMaintenanceService.java) |
| UC24 | Quản lý tài liệu số và Sách điện tử | Hiệu chỉnh thành Quản lý tài liệu số | [digital/controller/DigitalAssetController.java](../../backend/src/main/java/com/huit/library/modules/digital/controller/DigitalAssetController.java) |
| UC25 | Đọc tài liệu số trực tuyến | Hiệu chỉnh thành Xem trước và tải tài liệu số | [digital/controller/DigitalAssetController.java](../../backend/src/main/java/com/huit/library/modules/digital/controller/DigitalAssetController.java) |
| UC26 | Đề xuất sách cá nhân hóa bằng Trí tuệ nhân tạo | Hiệu chỉnh thành Nhận gợi ý sách | [recommendation/service/BookRecommendationService.java](../../backend/src/main/java/com/huit/library/modules/recommendation/service/BookRecommendationService.java) |
| UC27 | Đánh giá xếp hạng và Lưu danh sách quan tâm | Hiệu chỉnh thành Đánh giá và lưu sách yêu thích | [interactive/service/BookInteractionService.java](../../backend/src/main/java/com/huit/library/modules/interactive/service/BookInteractionService.java) |
| UC28 | Truy xuất Bảng điều khiển báo cáo thống kê vận hành | Hiệu chỉnh thành Xem dashboard và báo cáo vận hành | [dashboard/controller/CirculationAnalyticsController.java](../../backend/src/main/java/com/huit/library/modules/dashboard/controller/CirculationAnalyticsController.java) |
| UC29 | Quản lý đề xuất sách bổ sung | Hiệu chỉnh thành Đề xuất mua sách và xử lý đề xuất | [interactive/controller/BookProposalController.java](../../backend/src/main/java/com/huit/library/modules/interactive/controller/BookProposalController.java) |
| UC30 | Tiếp nhận và quản lý khiếu nại | API khung | FeedbackController trả HTTP 200 rỗng, chưa nhận payload hoặc lưu complaint. Không đủ điều kiện coi góp ý là chức năng hoàn thiện. [interactive/controller/FeedbackController.java](../../backend/src/main/java/com/huit/library/modules/interactive/controller/FeedbackController.java) |
| UC31 | Nộp bổ sung tiền thế chân | Hiệu chỉnh thành Nạp cọc qua phiên thanh toán | [finance/controller/OnlinePaymentController.java](../../backend/src/main/java/com/huit/library/modules/finance/controller/OnlinePaymentController.java) |
| UC32 | Đối chiếu và lập báo cáo thu chi định kỳ | Chờ triển khai | Chưa có nghiệp vụ kế toán đối soát hai cấp và chốt sổ. Phần hiện có là sổ công nợ và báo cáo ở UC14, UC28, UC48. [finance/controller/PaymentController.java](../../backend/src/main/java/com/huit/library/modules/finance/controller/PaymentController.java) |
| UC33 | Quản lý Nhà cung cấp và Phiếu nhập kho | Hiệu chỉnh thành Nhập kho từ nhà cung cấp | [inventory/controller/InventoryController.java](../../backend/src/main/java/com/huit/library/modules/inventory/controller/InventoryController.java) |
| UC34 | Quản lý gia hạn và khóa thẻ hết hạn định kỳ | Hiệu chỉnh thành Xem và cập nhật hạn thẻ | [auth/controller/LibraryCardController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/LibraryCardController.java) |
| UC35 | Khởi tạo lại bộ đếm vi phạm phòng nhóm theo học kỳ | Ngoài phạm vi | Không có quy trình reset vi phạm đặt phòng theo học kỳ đang sử dụng. [facilities/controller/RoomController.java](../../backend/src/main/java/com/huit/library/modules/facilities/controller/RoomController.java) |
| UC36 | Quản lý Hàng đợi (Waitlist) | Hiệu chỉnh thành Tham gia danh sách chờ | [circulation/controller/WaitlistController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/WaitlistController.java) |
| UC37 | Kiểm kê Kho Định kỳ | Hiệu chỉnh thành Kiểm kê bằng quét barcode | [inventory/controller/InventoryController.java](../../backend/src/main/java/com/huit/library/modules/inventory/controller/InventoryController.java) |
| UC38 | Bàn giao Ca trực và Chốt két | API khung | Endpoint bàn giao ca trả rỗng; chưa ghi nhận tiền mặt thực đếm và chênh lệch. [inventory/controller/InventoryController.java](../../backend/src/main/java/com/huit/library/modules/inventory/controller/InventoryController.java) |
| UC39 | Đồng bộ Real-time với Phòng Đào Tạo | Chờ tích hợp | API /users/sync nhận payload do Admin gửi; chưa có webhook realtime từ hệ thống trường. Phần cập nhật hồ sơ hiện có nằm ở UC01. [auth/controller/UserController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/UserController.java) |

## Các UC mới

| Mã | Phạm vi bổ sung | Nguồn |
|---|---|---|
| UC40 | Đăng nhập và quản lý thông tin cá nhân | [auth/controller/AuthController.java](../../backend/src/main/java/com/huit/library/modules/auth/controller/AuthController.java) |
| UC41 | Phát và tra mã QR tại quầy | [circulation/controller/LibrarianQrController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LibrarianQrController.java) |
| UC42 | Đăng ký mượn nhiều sách từ giỏ | [circulation/controller/LoanController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java) |
| UC43 | Sinh viên báo mất tài liệu đang mượn | [circulation/controller/LoanLossReportController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanLossReportController.java) |
| UC44 | Yêu cầu thu hồi sách đang mượn | [circulation/controller/LoanRecallController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/LoanRecallController.java) |
| UC45 | Kiểm tra trả sách bình thường hỏng hoặc mất | [circulation/controller/ReturnInspectionController.java](../../backend/src/main/java/com/huit/library/modules/circulation/controller/ReturnInspectionController.java) |
| UC46 | Gán ngành chính thức cho tài nguyên | [dashboard/controller/HoldingsAnalyticsController.java](../../backend/src/main/java/com/huit/library/modules/dashboard/controller/HoldingsAnalyticsController.java) |
| UC47 | Lập và gửi đề xuất xử lý sách cũ | [inventory/controller/DisposalController.java](../../backend/src/main/java/com/huit/library/modules/inventory/controller/DisposalController.java) |
| UC48 | Đề nghị và phê duyệt điều chỉnh công nợ | [finance/controller/PaymentController.java](../../backend/src/main/java/com/huit/library/modules/finance/controller/PaymentController.java) |
| UC49 | Xuất danh sách sinh viên còn sách quá hạn hoặc còn nợ | [reporting/controller/StudentObligationReportController.java](../../backend/src/main/java/com/huit/library/modules/reporting/controller/StudentObligationReportController.java) |
| UC50 | Tổng hợp đề xuất mua và xuất Excel | [reporting/controller/ProposalSummaryController.java](../../backend/src/main/java/com/huit/library/modules/reporting/controller/ProposalSummaryController.java) |
| UC51 | Hỏi đáp quy định bằng RAG | [rag/controller/RagChatController.java](../../backend/src/main/java/com/huit/library/modules/rag/controller/RagChatController.java) |
| UC52 | Cập nhật chính sách thư viện có lịch sử | [policy/controller/LibraryPolicyController.java](../../backend/src/main/java/com/huit/library/modules/policy/controller/LibraryPolicyController.java) |
| UC53 | Ghi nhận mang tài liệu ra trái phép | [penalty/controller/LibraryViolationController.java](../../backend/src/main/java/com/huit/library/modules/penalty/controller/LibraryViolationController.java) |
| UC54 | Quản lý tùy chọn và thiết bị nhận thông báo | [notification/controller/NotificationController.java](../../backend/src/main/java/com/huit/library/modules/notification/controller/NotificationController.java) |

## Đối chiếu mã nghiệp vụ

| Nhóm mã | Cách xử lý |
|---|---|
| BUC01 BUC02 BUC03 BUC06 BUC08 BUC09 BUC10 BUC12 BUC13 | Giữ mã và hiệu chỉnh mục tiêu, luồng, tác nhân cùng giới hạn. |
| BUC04 | Đặt phòng nằm ngoài phạm vi hiện tại. |
| BUC05 | Hoàn cọc và hủy thẻ chưa có luồng đầy đủ; phần nạp cọc và sổ tiền hiện có được đưa vào BUC14. |
| BUC07 | Chưa có kế toán đối soát/chốt sổ; phần báo cáo và công nợ hiện có thuộc BUC12/BUC14. |
| BUC11 | Góp ý còn API khung, không trình bày như đã hoàn thiện. |
| BUC14–BUC19 | Thêm quản lý tiền, lớp hướng dẫn, phân loại ngành, đề xuất xử lý sách cũ, RAG và chính sách có lịch sử. |

## Cách đọc ba sơ đồ

- Sơ đồ phân tích là miền nghiệp vụ, dùng liên kết logic kể cả khi code giữ ID scalar. Thuộc tính được chọn lọc, không phải bản sao toàn bộ entity hoặc danh sách constraint.
- Sơ đồ thiết kế là góc nhìn các controller và collaborator Java có thật, không phải toàn bộ class của repository. Mũi tên là phụ thuộc qua field; phương thức chọn lọc bỏ tham số và kiểu trả về để sơ đồ không quá nặng. DTO, repository chi tiết và các lớp cấu hình không được liệt kê hết.
- ERD là snapshot PostgreSQL cục bộ, không tính databasechangelog và databasechangeloglock. Tên cột, loại dữ liệu cơ bản, PK, FK và nullable lấy từ metadata. Bội số đầu con xét unique index một cột không có điều kiện; không vẽ quan hệ giả cho scalar ID. Không xuất toàn bộ index, CHECK, default, độ dài hoặc precision thành DDL.
- Nét liền ERD dùng khi FK tham gia PK; nét đứt là quan hệ không định danh. Nét đứt không có nghĩa “chưa triển khai”. Ngày chụp và số lượng không chứng minh mọi DB khác đã áp migration giống nhau.
- Sáu bảng legacy vẫn còn trong ERD: rooms, room_bookings, room_booking_members, kiosks, shift_handovers, university_sync_logs. Không giữ chúng như chức năng đang hoạt động trong BUC/UC.
- Các bảng audit_logs, complaints, system_configs hoặc orientation_attendance tồn tại không bảo đảm mọi endpoint đều ghi vào chúng. rag_knowledge_* được truy cập qua store/JDBC nên không suy toàn bộ DB từ danh sách @Entity.
- Các sơ đồ tổng thể lớn; mở bằng Mermaid có khả năng zoom, không ép cả ERD 66 bảng vào một trang A4. Cú pháp được kiểm tra bằng Mermaid 11.4.1.

## Phần cần triển khai hoặc sửa code riêng

| Việc còn thiếu | Tình trạng quan sát |
|---|---|
| Sửa trạng thái hoặc đổi ngăn bản sao | BookCopyService.updateStatus chỉ kiểm tra tập giá trị rồi ghi; chưa đối chiếu nghĩa vụ đang mượn. updateLocation kiểm tra quy tắc môn học nhưng chưa khóa xung đột lưu thông. Đã ghi rõ tại UC08 và tài liệu công nghệ. |
| Gateway Nginx | Một số controller mới như orientation không nằm trong allowlist đường cũ; cần rà route trước triển khai. Không sửa cấu hình trong task hồ sơ. |
| Hoàn cọc, phí cấp thẻ, tự kích hoạt thẻ, kế toán | Chưa có hợp đồng xử lý đầy đủ như bản cũ. Không chứng nhận hoàn tất dựa vào ví hoặc endpoint cập nhật hồ sơ. |
| Góp ý, bàn giao ca, phòng học | Endpoint khung hoặc ngoài phạm vi; bảng legacy chưa được xóa. |
| Quyết định và thực hiện thanh lý | Chờ phần Admin; chưa ghi giảm tồn hay thu thanh lý. |
| Tích hợp ngoài | Email/FCM, VNPAY, Gemini tùy cấu hình; cần kiểm thử dịch vụ hoặc thiết bị thật. Không có university webhook realtime, DRM hoặc đối soát ngân hàng hằng đêm hoàn chỉnh. |

## Kiểm tra đã thực hiện

- Đọc sáu file gốc, đối chiếu controller, service, entity, migration, client route, manifest, Compose và CI theo từng phạm vi. Không dùng tài liệu lịch sử thay source.
- Đọc metadata PostgreSQL trong transaction READ ONLY; container được dừng lại sau khi đọc. Không đọc/xuất dữ liệu bạn đọc, không áp migration và không ghi business data.
- Kiểm tra cú pháp và kết xuất cả ba sơ đồ bằng Mermaid 11.4.1, xem ảnh tổng thể; kiểm tra số lớp, cột, cạnh và quan hệ đối chiếu dữ liệu nguồn.
- Kết xuất cả ba Word và kiểm tra đủ 27 trang PNG: nghiệp vụ 7, hệ thống 16, công nghệ 4. Đã sửa đường viền tiêu đề và phân trang. WPS ẩn được dùng để xuất PDF vì renderer Windows thiếu soffice.exe; sau đó chạy pipeline render_docx.py và Poppler của runtime để tạo ảnh kiểm tra. Comment hiệu chỉnh được giữ trong Word.
- Kiểm tra các đường dẫn nguồn, mã UC/BUC tham chiếu, ZIP/XML Word, đối chiếu hash bản gốc và bản đầu ra. Kết quả máy đọc tại [VERIFICATION.json](VERIFICATION.json).
- Không chạy lại full test ứng dụng vì không sửa runtime. Không tuyên bố kiểm thử toàn bộ nghiệp vụ, hiệu năng, tích hợp thật hoặc nghiệm thu production trong lượt này.

## Duy trì hồ sơ

Sau thay đổi API, schema, vai trò hoặc trạng thái nghiệp vụ, sửa UC liên quan trước rồi cập nhật sơ đồ và công nghệ. Dùng nguồn dưới mỗi UC và danh sách source hash trong VERIFICATION.json để xác định phần bị cũ; vẫn phải đọc source chuẩn bị sửa. Checkpoint hoàn tất ở [CHECKPOINT.md](CHECKPOINT.md). Không dùng các bản gốc Desktop làm mô tả hiện trạng nữa.
