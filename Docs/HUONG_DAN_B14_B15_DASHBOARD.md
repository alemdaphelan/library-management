# Dashboard thủ thư B14–B15

Đường dẫn hiện tại: **`/admin`** (Dashboard mặc định của staff, không có route `/admin/dashboard`). Quyền ADMIN/LIBRARIAN giữ từ route guard và method security Backend. Dashboard chỉ GET; tải file dùng API có JWT hiện có. Không thu tiền, gán lại dữ liệu nguồn hoặc quyết định thanh lý tại Dashboard.

## Kỳ và trạng thái tải

- F08 (04/11/2026): nhập **Ngày tham chiếu**, chọn nút **Ngày / Tuần / Tháng / Năm**, rồi bấm **Áp dụng kỳ**. Mặc định tháng hiện tại theo giờ Việt Nam. Ngày có 24 khoảng giờ; tuần thứ Hai–Chủ nhật có 7 ngày; tháng có 28/29/30/31 ngày; năm có 12 tháng. Cùng ngữ nghĩa backend F07.
- **Khoảng khác** giữ chọn từ ngày/đến trước ngày và độ chi tiết giờ/ngày/tháng/quý. Ngày kết thúc không được tính vào kỳ. Chặn ngày không tồn tại, khoảng đảo/qua 50 năm hoặc hơn 1.000 cột trước khi gọi API. Các nút/ngày đang sửa chưa tự gửi request.
- Biểu đồ và bảng đọc start/until ISO local datetime, dùng range.bucketUnit do server trả. Nhãn giờ 00:00…23:00, mốc cuối 24:00; ngày dd/MM, tháng MM/yyyy; bảng giờ có ngày đầy đủ. Không chuyển timestamp local sang UTC theo múi giờ máy. Biểu đồ có vùng cuộn ngang bằng chuột/bàn phím; bảng chi tiết giữ đủ số chính xác.
- Ngày chưa áp dụng không thay số đang xem. Mượn/trả, tài chính và nhập dùng cùng kỳ đã áp dụng, kể cả khi chuyển tab. Danh mục/quá hạn/dư nợ luôn là số hiện tại, có nhãn riêng; không dựng số lịch sử từ số dư/tồn hiện tại.
- Mỗi tab tải khi mở; chuyển tab hủy request cũ và tải lại khi quay lại. Giữ kỳ chung, bộ lọc/phân trang riêng của tab được đặt lại khi tab được tạo lại.
- Mỗi phần có loading/error/retry riêng. Đang tải hoặc lỗi không giữ tổng cũ làm tổng mới và không tự biến thành 0. API thành công với 0 vẫn hiển thị 0. Request cũ không ghi đè response mới; rời trang hủy subscriptions.

## Mượn/trả — B14

API B11: circulation, rankings, overdue. Thẻ tổng tách lượt tài liệu giao/nhận vật lý và số phiếu distinct; bảng/biểu đồ cột dọc dùng bucket thật, có số 0 và ngày bucket cắt theo khoảng. Không cộng distinct ticket giữa các bucket thành tổng kỳ.

Năm chế độ xếp hạng: nhiều trong kỳ, ít trong nhóm có lượt, chưa có chứng cứ toàn lịch sử, không ghi nhận trong kỳ, đang mượn hiện tại. 25 dòng/trang tại server, các trang đi hết tập kết quả; đồng hạng giữ position. Link đầu sách mở `/book/{id}`.

Quá hạn hiện tại STUDENT có mã người/phiếu/barcode/hạn trả; link quầy mở đúng LOAN_TICKET và mode return. Link báo cáo mở `/admin/borrows?view=report&q=<MSSV hoặc tên>&reportMode=OVERDUE`, kích hoạt ngay tab B04/B05 với bộ lọc ban đầu; không lấy kỳ Dashboard lọc báo cáo hiện tại. Thiếu MSSV dùng tên có thể trùng nên đối chiếu người/phiếu.

Coverage B11 luôn hiện cảnh báo khi thiếu chứng cứ ngày hoặc đầu đã xóa; không gọi dữ liệu không ghi ngày là đầy đủ.

## Danh mục/khoa ngành — B15

API B12: academic-catalog, holdings, materials, copies/assets/classification. Bộ lọc riêng khoa/ngành/type/state/từ khóa, bấm **Áp dụng danh mục**. Tổng/list gửi cùng bộ lọc đã áp dụng; đổi chưa áp thì chặn phân trang. Tìm kiếm `q` tối đa 200 ký tự, literal không wildcard, case-insensitive theo tên/tác giả hoặc ID chính xác, thực hiện ở DB trước tổng/phân trang. Không tải tất cả đầu sách rồi lọc client.

Loại PHYSICAL/DIGITAL/HYBRID là loại chính xác. State chọn đầu có bản ở trạng thái đó nhưng các chỉ số của đầu vẫn gồm tất cả bản. Một tài liệu nhiều ngành được liệt kê một lần trong tổng, nhóm khoa/ngành có thể chồng lấp. File gắn BOOK không đếm thành đầu riêng; DIGITAL độc lập có nguồn riêng. Nhóm chưa phân loại giữ rõ, không suy từ recommender.

Thống kê bản trong các đầu đã chọn có thể nhỏ hơn toàn kho khi copy không quy được về catalog. `holdings.coverage` mới có **totalPhysicalCopies, copiesWithoutCurrentCatalog, linkedDigitalFilesWithoutCurrentCatalog** trên toàn DB, không theo bộ lọc. Khi có nguồn mất liên kết, UI cảnh báo riêng và không tự gán/xóa nguồn. Không gắn nhãn số bản trong đầu đã chọn là toàn bộ tồn kho đã phân loại.

Nút **Vị trí / tệp / nguồn** tải bản sao/tệp phân trang, vị trí tầng/kệ/ngăn, access level và download có JWT. Panel nguồn phân loại đọc version/source/actor/thời gian và before/after history, không tự ghi mapping. Việc gán ngành có nguồn qua API B12 giữ nguyên; UI này chỉ tra cứu. Download là thao tác tường minh của staff, dùng Blob/file saver, không điều hướng đến storageKey.

## Tài chính và nhập/xử lý — B15

API B13 tách tiền phát sinh, tiền mới thu, cấn cọc, miễn, điều chỉnh có dấu, nạp cọc. Không cộng các chiều thành doanh thu; phân rã CASH/BANK_TRANSFER/VNPAY/OTHER và allocations để đối chiếu. Bấm chỉ tiêu lọc entries, 25 dòng/trang; PAYMENT mở allocations riêng. Dư nợ ledger/wallet/chênh luôn hiện tại, không gọi là closing debt của kỳ. Coverage thiếu nguồn/ngày hoặc lệch phân bổ hiện cảnh báo và phần giải thích chi tiết.

Nhập mới theo phiếu COMPLETED/completedAt, tách phiếu/đầu distinct/lần nhập đầu ghi nhận/số bản. Đếm từ receipt quantities, không từ Book.createdAt hay số copy đang còn. Chứng từ có detail thực B06: nhà cung cấp/dòng/vị trí lúc nhập/barcode còn lưu. Thiếu ngày cũ có cảnh báo; “0 trong kỳ” không có nghĩa chưa từng nhập.

Thanh lý/hủy **Chờ Admin — chưa có số liệu thực thi**; null hiện “Chưa có dữ liệu”, không 0. Link mở kiểm kê/hồ sơ đề xuất, không biến đề xuất SUBMITTED hoặc raw copy retired status thành kết quả thực thi.

## Vị trí mã và kiểm tra

- `web-app/src/app/pages/admin/dashboard/`: Dashboard điều phối kỳ/tab, bốn child `*-dashboard.{ts,html}`, `analytics-shared.ts` tải/cancel/pager, `analytics.css` responsive.
- `web-app/src/app/services/dashboard-analytics.{service,models}.ts`; service spec và dashboard spec; `dashboard-test-data.ts` là fixture tổng hợp dùng trong tests, không là dữ liệu demo ghi vào DB.
- B04 deep link: `borrows.ts/html`, initialQuery/initialMode của StudentObligationReport; query không chạy mutation.
- Backend mở rộng B15: HoldingsAnalytics.Filter query, controller/service/repository tổng/list có q; Summary.coverage cho nguồn mất liên kết. Không migration mới sau 35.
- Lệnh Web tại web-app: `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`, `npm.cmd run build`. `.codex-tmp/b01-vitest.config.mjs` chỉ giới hạn 1 worker cho môi trường này; CI vẫn có thể dùng `npm test -- --watch=false`.
- Báo cáo nghiệm thu mới và phần chờ: `TestChucNang/24_NGHIEM_THU_THU_THU_B01_B16.md`; checkpoint cuối: `Docs/BAN_GIAO_BATCH_THU_THU.md`.


## Bàn giao F08

- Dashboard dùng từ tiếng Việt cho chỉ tiêu, số hiện tại, nguồn và khoản thu/phân bổ; vẫn giữ cảnh báo thiếu chứng cứ, chênh lệch tiền và Chờ Admin cho kết quả thanh lý/hủy. Không thay số tiền hay quy tắc cộng distinct.
- Web tiếp tục gửi **from/until/period** đã tính theo lịch cho cả ba báo cáo và các danh sách chi tiết. Không gửi thêm date cùng from/until, tránh lỗi 400 F07. Đổi tab giữ kỳ đã áp dụng. Sửa lựa chọn hiển thị nhắc chưa áp dụng, không dùng kỳ nháp để tải dữ liệu.
- Cần backend source F07 và Web source F08 khi chạy thử. Không có migration trong hai batch này; không thay bản đang chạy chỉ bằng sửa source. Nghiệm thu backend đã có tại F07; kiểm tra Web/API fixture và bằng chứng lượt mới ghi trong Docs/KE_HOACH_FIX_LOI_V2.md.
