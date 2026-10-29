# Nghiệm thu phần thủ thư B01–B16

Ngày kiểm tra: **30/10/2026**, Asia/Saigon. Baseline HEAD `34d942b` + working-tree B01–B16 chưa commit. Người dùng đã yêu cầu B15–B16, xác nhận bổ sung B14, và cho áp hết migration DB local. Báo cáo tách bằng chứng kiểm thử tự động, browser fixture và đọc DB thật; không gọi chúng là một luồng Web-to-live-DB đầy đủ.

## Phạm vi nghiệm thu

| Yêu cầu | Phần đã có / bằng chứng | Phần chưa nghiệm thu đầy đủ |
|---|---|---|
| TT-01 Phạt mất/hỏng ngay quầy trả | B02–B03; ReturnInspectionServiceTest + Week6 mixed return, rollback/replay/cấn cọc và Web specs | Camera/thiết bị thật vẫn là UAT riêng; Flutter người khác phụ trách |
| TT-02 Excel quá hạn/nợ theo khoa | B04–B05; StudentObligationReportIntegrationTest + HTTP/XLSX, Web filters/export/deep link Dashboard | Phân loại hồ sơ nguồn không được bịa; coverage hiện tại quyết định độ đầy đủ dữ liệu |
| TT-03 Nhập ISBN/vị trí tự động theo rule | B06 và B07 độc lập: nhập có shelf, provenance, nhận diện catalog; InventoryService/Week6/Isbn tests | **B07 engine tự xếp vị trí CHỜ ADMIN**, hiện chọn thủ công có giải thích |
| TT-04 Đặt giữ tự duyệt | B01; LoanService, CirculationState/Transaction, Week6 concurrency/rollback, Web queue tương thích | Không bỏ các điều kiện eligibility/hold/FIFO |
| TT-05 Gộp/export đề xuất mua | B08; ProposalSummaryIntegrationTest, HTTP/XLSX và Web specs | Tệp gửi khoa cần thao tác người dùng, không tự gửi; metadata mâu thuẫn cần đối chiếu |
| TT-06 Dashboard mở rộng | B11–B15; real DB analytics/migration, Web tests/build/browser cho ngày/kỳ/phân trang/nguồn/lỗi | **Thanh lý/hủy thực tế CHỜ ADMIN**; không có closing debt lịch sử; dữ liệu cũ thiếu ngày/nguồn hiển thị coverage |
| TT-07 Đề xuất sách cũ từ kiểm kê | B09–B10; DisposalWorkflow/Migration, HTTP/version/replay/atomicity và Web specs | **Admin quyết định/thực thi chưa có**; chỉ lập/sửa/gửi/hủy/theo dõi đề xuất |

B16 hoàn tất công việc hồi quy/bàn giao trong phạm vi triển khai được, **không xác nhận cả 7 yêu cầu đã đáp ứng mọi phụ thuộc**. Các hàng chờ Admin được giữ mở. Không tạo ca “đã thanh lý thật” bằng cách sửa status hoặc seed kết quả giả.

## Cách chạy lại

Tại backend, JAVA_HOME `C:\Program Files\Java\jdk-21`, Docker engine sẵn sàng:

```powershell
.\mvnw.cmd --batch-mode package
```

Tại web-app:

```powershell
npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs
npm.cmd run build
```

Tại root: `./scripts/week12d-preflight.ps1`; Compose validation dùng `.env.example`, không in `.env` thật. Docker pipe/Testcontainers cần chạy ngoài filesystem sandbox của agent. Không mở secret/token trong báo cáo.

## Fixtures và kịch bản biên

- DB fixtures được tạo/rollback trong PostgreSQL Testcontainers, nguồn nằm trong integration tests; không seed vào DB người dùng. Nhiều khoa/ngành: DashboardAnalyticsIntegrationTest (hai khoa, ngành A1/A2/B1, đa ngành/độc lập/lai/chưa gán). ProposalSummaryIntegrationTest giữ ISBN/ấn bản/nguồn và distinct submitter; tên/tác giả không thay định danh ISBN khác.
- Circulation/Week6: giữ tự duyệt → giao → trả bình thường/hỏng/mất → cọc thiếu/đủ → thu một phần/nhiều đợt/replay → report. Lỗi history/operation cuối rollback toàn giao dịch; cạnh tranh last copy/checkout/inspection không nhân bản.
- FinanceStockAnalyticsIntegrationTest và Week6: 200.000 nghĩa vụ, cọc 50.000, 60.000 tiền mặt + 90.000 chuyển khoản, retry cả receipt, nguồn cọc không bị overwrite; miễn/điều chỉnh âm, thu khác kỳ charge, receipt nhiều allocation, sai/lặp allocation hiện discrepancy.
- Nhập/kiểm kê/disposal: ISBN nhận diện → shelf thủ công → phiếu/dòng/copy transaction → audit nguồn thật → draft/sửa/submit/cancel/history. Chặn copy đang loan/hold, một hồ sơ hoạt động/barcode, replay nguyên batch và rollback dòng lỗi. Đoạn tự xếp theo rule → quyết định/thực thi Admin dừng ở phụ thuộc thật.
- Dashboard tests: bucket rỗng/cắt ranh giới, tie/ít có lượt/chưa từng/chưa trong kỳ, snapshot overdue, 0 copies và copy mất liên kết catalog; q literal C++/% ở SQL cùng tổng/list; loading/error/retry/cancel stale request, đổi tab/cùng kỳ, allocation/receipt detail và deep link B04.
- Browser fixture dựng từ `dashboard-test-data.ts` trong repo: hai khoa/nhiều ngành, tài liệu đa ngành/lai/độc lập/chưa phân loại, 26 dòng cho phân trang, receipt/phân bổ, adjustment âm, imports không đủ ngày và disposal unavailable. Mọi API bị intercept; không có mutation DB. Không dùng fixture browser làm bằng chứng API đọc DB thật.

## DB local đã migrate

- Target có sẵn: `library_postgres`, DB `huit_library`, volume `library-management_postgres_data`.
- Backup trước áp: `backups/librarian-pre-b14-b16-20261030.dump`, 19.135.149 byte, pg_restore list đọc được, Git ignored. Backup chứa dữ liệu riêng, không đưa vào Git/tài liệu công khai.
- Liquibase validate/checksum PASS, pending đúng 8 changeSets 28–35. Update **00:03:15 +07 ngày 30/10**, chạy 8, trước đó 73, tổng **81**, còn **0** pending, lock=false. Không clear checksums, không sửa lịch sử, không chạy seeder/restore/truncate.
- Source counts trước/sau: books **269.925**, book_copies **269.939**, loan_tickets **27**, transactions **14**, giữ nguyên. Schema mới giữ dữ liệu cũ thiếu chứng cứ là null.
- Repository thật được đọc bằng transaction **read-only REPEATABLE_READ**, rollback cuối; tổng materials khớp totalMaterials của trang, không tải toàn bộ raw dataset vào UI. Coverage phát hiện copy không quy về catalog, trường hợp cọc cũ không rõ, phiếu hoàn tất không có ngày. Không tự chữa nguồn để làm đẹp kết quả.
- Runtime: chỉ PostgreSQL được bật từ trạng thái stopped để migrate/kiểm tra. Không triển khai image backend cũ hoặc tự khởi động các dịch vụ khác. Jar và Web bundle mới đã build; muốn dùng UI/API mới cần chạy đúng backend/client đã build, không dùng image cũ.

## Kết quả cuối

| Gate ngày 30/10 | Kết quả mới | Bằng chứng ignored |
|---|---|---|
| Backend toàn bộ test + package | **283/283 PASS**, 0 lỗi/skip, BUILD SUCCESS 00:24:49 +07 | b16-backend-final-package.log |
| Web toàn bộ test | **230/230 PASS**, 51 files | b16-web-final-tests.log |
| Web production build | PASS; chỉ warning cũ CSS portal/qrcode | b16-web-final-build.log |
| Preflight + Compose config | PASS, 0 lỗi; 3 warning CSV lớn có trước | b16-preflight.log |
| Browser Edge production fixture | PASS desktop 1440/mobile 390, 26 API calls, **0 mutations/0 runtime errors**, đã xem ảnh | b16-dashboard-ui.log, b14-circulation-*.png, b15-*-*.png |
| Migration DB local + đọc báo cáo thật | 8 update / 81 tổng / 0 pending / lock=false; source counts không đổi; read-only check PASS | b16-migration-plan.log, b16-migration-update.log, b16-db-read-check.log |

Logs/ảnh/công cụ tạm ở `.codex-tmp/` ignored. Checkpoint `Docs/BAN_GIAO_BATCH_THU_THU.md` ghi chi tiết probe đã sửa và ranh giới bằng chứng. Gate PASS không đóng các phụ thuộc Admin.

Snapshot local thực tế: 269931 materials chưa gán ngành có nguồn; 269939 copies toàn DB, 17 thuộc catalog hiện có và **269922 chưa quy thuộc catalog**. Đã thêm API/UI global coverage, giữ nguyên nguồn. Có 2 checkout thiếu ngày, 1 normal return thiếu ngày, 1 charge thiếu nguồn cọc, 2 completed receipts thiếu ngày; không đoán các mốc này. Đây là giới hạn dữ liệu cần đối chiếu, không phải lỗi migration làm mất row.

## Các phụ thuộc và giới hạn còn mở

1. Admin B07: schema/API quy tắc xếp theo ngành, priority/effective dates/override; B06 hiện chọn shelf thủ công.
2. Admin B09–B10/B13: quyền/quyết định và event thực thi có ID/ngày hiệu lực/barcode-book/actor/decision. Không đếm hồ sơ SUBMITTED/CANCELLED hoặc copy RETIRED như kết quả.
3. Dữ liệu local cũ: copy mất liên kết catalog; tài liệu chưa gán ngành có nguồn; ngày giao/nhận/hoàn tất nhập/cấn cọc thiếu. Cần đối chiếu nguồn, không suy từ tên/ngày tạo/hạn trả. Chưa tái tạo số dư lịch sử hoặc vòng đời số ngừng khai thác.
4. Chưa load-test production lớn; SQL aggregates có thể quét nguồn lớn. Browser fixture + DB tests + live read-only check không phải E2E giao dịch Web → DB đang dùng. Không ghi giao dịch demo để thử live.
5. Không đổi Flutter; tìm trong mobile services/test không có caller Dashboard analytics. Camera/device, VNPAY sandbox/Firebase acceptance lịch sử vẫn bàn giao đúng người phụ trách; không tính là PASS mới của B16.

Không commit/push hoặc gửi báo cáo tới người khác. Hướng dẫn sử dụng UI: `Docs/HUONG_DAN_B14_B15_DASHBOARD.md`; các hợp đồng B11–B13 vẫn là nguồn định nghĩa nghiệp vụ.
