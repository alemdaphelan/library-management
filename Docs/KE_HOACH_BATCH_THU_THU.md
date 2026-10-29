# Kế hoạch hoàn thiện chức năng thủ thư theo từng batch

Ngày lập: **28/10/2026**. Baseline mã nguồn đã đối chiếu: **`34d942b`**.
Trạng thái ngày 30/10/2026: **B01–B06, B08, B11–B12, B14–B15 HOÀN TẤT; B16 đã hồi quy/bàn giao phần khả dụng; B13 xong tài chính/nhập; B07 quy tắc và B09–B10/B13 quyết định/thực thi thanh lý còn CHỜ ADMIN.** Đã áp migration 28–35 lên DB local theo yêu cầu, còn 0 pending. Không xác nhận nghiệm thu đủ các phần phụ thuộc. Xem `Docs/BAN_GIAO_BATCH_THU_THU.md`.

Tài liệu này chia toàn bộ phần **Role thủ thư** trong `1.docx` thành 16 batch có đầu ra và điểm dừng riêng. Người dùng sẽ yêu cầu thực hiện từng batch ở các lượt sau; không tự chạy tiếp batch kế tiếp. Mục tiêu là kết thúc được từng phần, kiểm tra được và tiếp tục được trong một chat mới.

Nguồn yêu cầu: `C:\Users\Lenovo\OneDrive\Desktop\1.docx`, mục “Role thủ thư”, 7 gạch đầu dòng. SHA-256 khi đọc: `E5CFB8D6FBD7601A9ED639F016854CA77AB7ABCB8222E0E29B9D91A71B1DC341`.
Các nội dung trong tài liệu được dùng làm yêu cầu nghiệp vụ. Ngày 28/10 chỉ lập kế hoạch; ngày 29/10 người dùng lần lượt yêu cầu B01–B05, B06–B07, B08 rồi B09–B10, mỗi lượt chốt checkpoint và dừng. Chỉ làm batch được gọi ở từng lượt.

## Phạm vi và cách phối hợp

- Phụ trách luồng **LIBRARIAN**, gồm Backend, Web và kiểm thử cần thiết cho các yêu cầu bên dưới. Trang hiện nằm dưới `/admin/...` là giao diện nhân viên dùng chung, không có nghĩa mọi thay đổi ở đó thuộc phần việc của người làm role Admin.
- Không nhận làm các màn hình của Sinh viên hoặc màn hình cấu hình của Admin. Những thay đổi bắt buộc ở hợp đồng API dùng chung phải được ghi rõ để hai người còn lại cập nhật client của họ.
- Không đổi thời hạn/biểu phí thành một hệ thống cấu hình mới trong phạm vi này. Sử dụng chính sách hiện có và tích hợp cấu hình do người làm Admin cung cấp khi sẵn sàng.
- Mỗi batch dùng tiếp code hiện tại; không viết lại các module đã tồn tại chỉ vì yêu cầu nói “thêm”. Chỉ số PASS trong báo cáo cũ không thay thế việc kiểm tra code mới.
- Dữ liệu minh họa phải nằm trong fixture/test hoặc cơ chế seed demo có kiểm soát. Dữ liệu hiển thị vẫn đi qua API/database, không gắn số giả vào UI.

## Truy vết đủ 7 yêu cầu gốc

| Mã | Yêu cầu trong phần thủ thư | Batch thực hiện |
|---|---|---|
| TT-01 | Lập phiếu phạt hư hỏng/mất ngay trong phần trả sách | B02–B03 |
| TT-02 | Export Excel sinh viên hiện còn quá hạn/nợ phạt, lọc theo khoa | B04–B05 |
| TT-03 | Form nhập kho có vị trí; nhập ISBN nhận diện lĩnh vực/ngành và tự gán vị trí theo quy định Admin | B06–B07 |
| TT-04 | Bỏ bước duyệt yêu cầu mượn; hệ thống giữ sách và tự duyệt | B01 |
| TT-05 | Thống kê/gộp đề xuất trùng thành một đầu sách, export Excel gửi khoa | B08 |
| TT-06 | Dashboard mở rộng đầy đủ các thống kê sách, mượn/trả, quá hạn, khoa/ngành, tiền phạt, nhập mới, thanh lý/hủy, tài liệu hiện có; tháng/quý/năm | B11–B15; dữ liệu thanh lý từ B09–B10 |
| TT-07 | Kiểm tra định kỳ và form đề xuất xử lý sách cũ/không còn dùng | B09–B10 |

B16 nghiệm thu xuyên suốt và dữ liệu cho các trường hợp biên. Cụm “Tháng - quý - năng” trong nguồn được hiểu là **tháng/quý/năm**.

## Hiện trạng đã kiểm tra để tránh làm trùng

| Khu vực | Đã có trong code | Khoảng trống cần xử lý |
|---|---|---|
| Đặt giữ | `LoanService` tạo phiếu PENDING ở cả luồng đơn và batch; đã khóa bản sao; có state machine và lịch sử | Tự chuyển RESERVED đúng transaction, thay UI chờ duyệt; giữ kiểm tra điều kiện mượn và FIFO |
| Quầy trả | Web `circulation.ts` gọi `completeAutomaticReturn` ngay sau khi quét; DTO trả chỉ có barcode, ghi chú và idempotency | Cần bước xác nhận tình trạng trước khi hoàn tất; không trả bình thường trước rồi mới tạo phạt |
| Mất/hỏng | `PenaltyCaseService` có hồ sơ, đền bù, cấn trừ tiền cọc, công nợ và trạng thái LOST/MAINTENANCE | Kết nối với quầy trả theo một thao tác nghiệp vụ nhất quán. Service hiện từ chối lập hồ sơ nếu `returnDate` đã có |
| Quá hạn/nợ | Trang `borrows` có dữ liệu phiếu, tìm kiếm có chuỗi khoa và lọc trạng thái/ngày | Chưa có báo cáo tổng hợp theo sinh viên, bộ lọc khoa riêng và file `.xlsx` theo yêu cầu. `totalLateFine` không đại diện toàn bộ dư nợ |
| Nhập kho | Có nhà cung cấp, ISBN/bookId, số lượng, đơn giá; sinh bản sao AVAILABLE | DTO chưa có vị trí; `InventoryService` chưa gán shelf cho bản sao mới |
| Vị trí/phân ngành | Có tầng/kệ/ngăn, category và taxonomy gợi ý sách | `ShelfEntity` chưa chứa quy tắc xếp theo ngành. Mapping taxonomy hiện tại không tự chứng minh đã có đủ danh mục khoa/ngành chuẩn |
| Đề xuất mua | Có lưu từng đề xuất, duyệt/từ chối; duyệt có thể tạo đầu sách | Chưa có báo cáo gộp trùng/export; cần giữ liên kết đến từng đề xuất gốc |
| Dashboard | Có tổng bản sao, quá hạn, top 5, hoạt động gần đây và thống kê 7 ngày | Chưa đủ các lát cắt yêu cầu; thống kê 7 ngày hiện đếm ngày tạo phiếu, không phải lượt giao/trả thực tế |
| Dashboard số liệu | `getStats()` đang đếm bản sao `BORROWED`, cộng tiền type `FINE` | Cần đối chiếu trạng thái/giao dịch thật khi viết thống kê mới, vì circulation dùng BORROWING và phạt có nhiều loại giao dịch |
| Kiểm tra sách cũ | Có kiểm kê barcode | Chưa thấy luồng đề xuất thanh lý/hủy trong phạm vi code catalog/inventory/UI đã rà soát; cần mô hình có lịch sử |

Đây là đối chiếu tĩnh các file liên quan, chưa phải kiểm thử chạy toàn hệ thống.

## Các hợp đồng dùng chung cần thống nhất khi bắt đầu

Ghi nhận vào tài liệu bàn giao ngay ở batch liên quan; không cần chờ tất cả các role xong mới làm các batch độc lập.

1. **Ngành và khoa:** B04 bổ sung đã đối chiếu nguồn HUIT và seed 17 khoa/39 ngành, gồm alias của `UserEntity.department` hiện có trong repo; migration 30 chuẩn hóa khác dấu/hoa thường/khoảng trắng/dấu câu. Theo yêu cầu người dùng, mục tiêu là phân loại hết: endpoint classification-coverage kiểm tra toàn bộ sinh viên, chỉ xác nhận đủ khi complete=true. Giá trị trống/không xác định phải sửa nguồn; không gán một khoa giả hoặc coi “Chưa phân loại” là kết quả đã hoàn tất. Xem API B04 và checkpoint bổ sung.
2. **Quy tắc vị trí của Admin:** cần nhận được quy tắc có ID/version, lĩnh vực/ngành/category được áp dụng, ngăn đích, trạng thái hiệu lực và độ ưu tiên. Schema/API cụ thể phải đối chiếu với người làm Admin trước khi cố định; kế hoạch này không giả định API đó đã có.
3. **Sinh viên đặt giữ:** phản hồi sau B01 là RESERVED khi giữ thành công; WAITLIST khi hết bản sao theo luồng hiện tại. Người phụ trách Sinh viên cập nhật nhãn/hiển thị ở Web/Mobile. Tự duyệt giữ chỗ **không phải** tự giao sách BORROWING.
4. **Xử lý sách cũ:** thủ thư lập đề xuất, người có thẩm quyền quyết định. Phần phê duyệt của Admin do người phụ trách Admin làm; cần thống nhất trạng thái và cách ghi nhận kết quả thực tế để Dashboard đọc được. Chưa có quyết định thì không tự thanh lý/hủy.
5. **Tiền phạt:** dùng các giao dịch/allocations đã ghi nhận; miễn giảm/điều chỉnh không phải tiền đã thu. Code hiện có cấn trừ cọc; ví dụ tổng phạt đúng 200.000, cọc khả dụng 50.000 thì phải thấy đã cấn 50.000, còn nợ 150.000 và tổng nghĩa vụ 200.000, không đếm cọc thành một lần thu nữa.

## Lịch batch và phụ thuộc

| Batch | Phạm vi chính | Phụ thuộc | Trạng thái |
|---|---|---|---|
| B01 | Tự duyệt đặt giữ và bỏ thao tác duyệt thủ công trên giao diện thủ thư | Không | HOÀN TẤT 29/10/2026 |
| B02 | Backend nhận trả có mất/hỏng, tạo phạt nhất quán | Không | HOÀN TẤT 29/10/2026 |
| B03 | Form tình trạng/phiếu phạt tại quầy trả | B02 | HOÀN TẤT 29/10/2026 |
| B04 | Backend báo cáo quá hạn/công nợ và xuất Excel | Mapping khoa được ghi nhận | HOÀN TẤT 29/10/2026 |
| B05 | Bộ lọc khoa, xem báo cáo và tải Excel trên Web | B04 | HOÀN TẤT 29/10/2026 |
| B06 | Nhập kho có chọn vị trí và lưu vào bản sao | Không | HOÀN TẤT 29/10/2026 |
| B07 | ISBN -> phân loại -> vị trí theo quy tắc Admin | B06 + hợp đồng/quy tắc Admin | ĐÃ NHẬN DIỆN; CHỜ ADMIN, CHƯA HOÀN TẤT |
| B08 | Gộp đề xuất mua trùng và export Excel theo khoa | Mapping khoa; dữ liệu đề xuất hiện có | HOÀN TẤT 29/10/2026 |
| B09 | Backend đề xuất xử lý sách cũ từ kiểm kê | Hợp đồng vòng đời/phê duyệt | XONG PHẦN THỦ THƯ; CHỜ QUYẾT ĐỊNH/THỰC THI ADMIN |
| B10 | Form và theo dõi đề xuất xử lý sách cũ | B09 | XONG PHẦN THỦ THƯ 29/10; HIỂN THỊ PHẦN CHỜ ADMIN |
| B11 | Backend thống kê mượn/trả/xếp hạng/quá hạn | Tận dụng B04 cho báo cáo quá hạn | HOÀN TẤT BACKEND |
| B12 | Backend thống kê khoa/ngành và danh mục sách/tài liệu | Mapping khoa/ngành | HOÀN TẤT BACKEND |
| B13 | Backend thống kê phạt, nhập mới, thanh lý/hủy | B09 + dữ liệu kết quả thực tế | XONG TÀI CHÍNH/NHẬP; THỰC THI THANH LÝ/HỦY CHỜ ADMIN |
| B14 | Dashboard Web cho mượn/trả và xếp hạng | B11 | HOÀN TẤT 30/10 |
| B15 | Dashboard Web cho danh mục, tài chính, nhập/xuất vòng đời | B12–B13 | XONG UI; THỰC THI THANH LÝ/HỦY HIỆN CHỜ ADMIN |
| B16 | Nghiệm thu tổng hợp, dữ liệu biên và bàn giao | Các batch chức năng đã xong | ĐÃ HỒI QUY/BÀN GIAO PHẦN KHẢ DỤNG; PHỤ THUỘC ADMIN GIỮ MỞ |

Có thể làm B02/B04/B06/B08 trong lúc chờ hợp đồng Admin. Batch bị thiếu API/quy tắc phải ghi rõ phần chờ và chưa đánh dấu hoàn tất; không tạo response giả để tuyên bố đã tích hợp.

## B01 Tự duyệt đặt giữ

**Đầu ra:** Giữ thành công -> RESERVED ngay; thủ thư không còn thao tác duyệt cho yêu cầu mới.

- Sửa cả `reserveOrBorrowBook` và luồng `reserveBatch` trong `LoanService`; giữ nguyên kiểm tra thẻ, nợ/quá hạn, số tài liệu, hàng đợi và khóa chống hai người giữ cùng bản sao.
- Ghi lịch sử rõ đây là tự duyệt của hệ thống, không gán một thủ thư giả. Không bỏ state machine hoặc tự chuyển BORROWING.
- Cập nhật quầy `pages/admin/circulation`, trang `borrows` và thông báo liên quan; chỉ bỏ duyệt **phiếu mượn**, không bỏ duyệt đề xuất mua sách.
- Xử lý phiếu PENDING cũ theo cách tương thích, kiểm tra lại điều kiện trước khi chuyển; không cập nhật SQL hàng loạt bỏ qua tồn kho/quyền/hết hạn. Giữ API cũ nếu client khác còn cần, có phương án chuyển tiếp rõ ràng.
- Ghi hợp đồng phản hồi/nhãn để người phụ trách Sinh viên cập nhật phía họ.

**Kiểm tra để xong:** đơn/batch, hết sách, hàng đợi, yêu cầu trùng, hai người giữ bản cuối, giới hạn ba tài liệu, nợ/thẻ khóa, phiếu cũ; UI giao sách nhận RESERVED bình thường. Chạy test circulation liên quan và Web bị ảnh hưởng.

**Dừng:** hoàn tất thay đổi tự duyệt; chưa làm trả/phạt hoặc báo cáo.

## B02 Backend trả sách có hồ sơ mất hỏng

**Đầu ra:** Một thao tác quầy ghi nhận tình trạng từng tài liệu và tạo hồ sơ phạt qua service hiện có.

- Thiết kế DTO/hợp đồng tương thích cho tình trạng bình thường, hỏng nhẹ, hỏng nặng, mất; có `loanDetailId` để báo mất khi không có cuốn sách để quét.
- Điều phối trong transaction, tái sử dụng `PenaltyCaseService`, `LateFineService`, ledger và idempotency; không gọi trả thường trước vì `returnDate` sẽ chặn tạo hồ sơ phạt.
- Tài liệu bình thường trở lại kho; mất/hỏng giữ đúng LOST/MAINTENANCE và quy trình khép hồ sơ hiện có. Không ép mọi tài liệu gặp sự cố thành RETURNED rồi nhả waitlist.
- Trả kết quả có mã hồ sơ, số tiền phát sinh/cấn cọc/còn nợ và trạng thái; giữ dữ liệu từng tài liệu khi một phiếu có nhiều bản sao.
- Không tính lại biểu phí bằng công thức frontend. Bao gồm lỗi thiếu biểu phí, thông tin đền bù/chứng cứ không hợp lệ và việc chưa xử lý xong sách thay thế.

**Kiểm tra để xong:** rollback nếu lập phạt lỗi; gửi lại cùng key không lập phạt/cấn cọc hai lần; xung đột key; incident trùng; trả một phần; người không có quyền; không phát sách mất/hỏng cho hàng đợi. Chạy unit và integration transaction liên quan.

**Dừng:** Backend + test + hợp đồng API sẵn sàng, chưa thay UI quầy.

## B03 Form phiếu phạt tại quầy trả

**Đầu ra:** Thủ thư xác nhận tình trạng trước khi hoàn tất trả, xem được phiếu phạt khi có sự cố.

- Thay luồng quét là tự trả ngay bằng bước chọn/xác nhận tình trạng; tái sử dụng trường/form từ `pages/admin/penalties` nếu phù hợp.
- Hỗ trợ báo mất từ chi tiết phiếu/người mượn, không yêu cầu phải có barcode vật lý trước mặt.
- Hiển thị đúng trường theo mức hỏng/cách đền bù; xem kết quả tổng phạt, đã dùng cọc, còn nợ, mã hồ sơ và liên kết sang màn xử lý phạt.
- Khóa thao tác khi gửi, giữ cùng idempotency key khi retry cùng giao dịch; hủy form không thay trạng thái. Cập nhật hướng dẫn quét/nhãn tránh nói mọi bản trả đã về kho.
- Bàn giao thay đổi hợp đồng cho Mobile; nâng cấp màn Mobile đầy đủ không tự nhận vào batch này.

**Kiểm tra để xong:** quét, nhập tay, hủy, retry/lỗi mạng, gửi trùng, trả thường, hỏng/mất; UI không thông báo “về kho” cho LOST/MAINTENANCE. Chạy Web spec liên quan và build.

**Đã chốt:** form Web và biên nhận hoàn tất; 147 test Web PASS, build PASS. Hướng dẫn và hợp đồng bàn giao Mobile: `Docs/HUONG_DAN_B03_QUAY_TRA_SACH.md`. Chi tiết bằng chứng/giới hạn ở checkpoint B03; không tự làm B04.

## B04 Backend báo cáo quá hạn công nợ và Excel

**Đầu ra:** API xem và tải `.xlsx` từ cùng bộ lọc, dành cho thủ thư.

- Hai tập dữ liệu độc lập: **còn quá hạn**, **còn nợ**; thêm chế độ hợp để lấy sinh viên có ít nhất một điều kiện. Người đã trả nhưng còn nợ vẫn xuất hiện ở tập công nợ.
- Lọc khoa bằng định danh/mapping thống nhất; kết quả một sinh viên một dòng tổng hợp, kèm sheet chi tiết phiếu/tài liệu và khoản nợ nếu cần.
- Cột tối thiểu: MSSV, họ tên, khoa/ngành nếu có, số tài liệu quá hạn, hạn/ngày quá hạn, tổng nghĩa vụ phạt, đã thanh toán/cấn cọc, miễn giảm/điều chỉnh, dư nợ và thời điểm chốt báo cáo. Phân biệt tổng hợp theo sinh viên với chi tiết theo khoản.
- Dùng ledger/transactions, không cộng `totalLateFine` rồi coi đó là dư nợ; tránh nhân số tiền do join nhiều chi tiết.
- Dùng Apache POI đã có trong Backend; file là Excel thật. Xuất toàn bộ kết quả bộ lọc, không chỉ trang hiện xem; xử lý an toàn chuỗi bắt đầu bằng ký tự công thức Excel.

**Kiểm tra để xong:** không nợ/quá hạn, chỉ nợ, chỉ quá hạn, cả hai, đã trả vẫn nợ, thanh toán một phần, cấn cọc, miễn giảm, nhiều phiếu cùng sinh viên, thiếu khoa, danh sách rỗng; đọc lại `.xlsx` để kiểm tra dữ liệu/quyền truy cập.

**Đã chốt, gồm yêu cầu bổ sung phân loại hết:** Backend preview/export/faculties và classification-coverage hoàn tất, 51 test liên quan PASS. Mapping ở `academic_faculties` / `academic_department_mappings`; migration 30 seed 17 khoa/39 ngành đối chiếu HUIT, nhận tên/mã ngành/tên khoa và CNTT của repo, chuẩn hóa dấu/hoa thường/khoảng trắng/dấu câu. Đạt phân loại hết DB thực khi coverage.complete=true; dữ liệu thiếu/sai phải sửa nguồn, không gán khoa giả. Hợp đồng B05: `Docs/API_B04_BAO_CAO_QUA_HAN_CONG_NO.md`. Chưa làm Web B05.

## B05 Web xem lọc và export quá hạn công nợ

**Đầu ra:** Màn báo cáo/thẻ báo cáo ở `pages/admin/borrows` có bộ lọc khoa và nút tải Excel hoạt động.

- Bộ chọn khoa riêng, chế độ quá hạn/nợ/hợp, tìm MSSV/họ tên, phân trang; hiển thị thời điểm dữ liệu và đơn vị tiền.
- Preview và export dùng cùng điều kiện; export không bị giới hạn bởi phân trang. Tên file có loại báo cáo/thời điểm, có loading/lỗi/không có dữ liệu.
- Giữ đường dẫn mở chi tiết phiếu và sổ nợ; không làm mất các thao tác quản lý hiện có.

**Kiểm tra để xong:** đổi khoa/chế độ, reset lọc, tải Excel, danh sách rỗng và lỗi quyền/network. Chạy Web test và build; đối chiếu file tải với số liệu API.

**Đã chốt:** tab báo cáo trong borrows, bộ lọc/phân trang server, kiểm tra phân loại toàn bộ sinh viên, chi tiết/link phiếu và tải Blob theo bộ lọc đã xem. 173 Web test/build PASS; browser fixture tải file từ exporter POI và đọc lại đủ 26 sinh viên dù preview 10 dòng, số tiền khớp API. Hướng dẫn/giới hạn nghiệm thu: `Docs/HUONG_DAN_B05_BAO_CAO_WEB.md`. Dừng trước B06.

## B06 Nhập kho có vị trí

**Đầu ra:** Mỗi dòng nhập chọn được tầng/kệ/ngăn; các barcode sinh ra lưu đúng ngăn.

- Mở rộng `ImportReceiptRequestDTO`, DTO hiển thị và `InventoryService` cùng `services/imports.service.ts`, `pages/admin/imports`.
- Chọn ngăn cho từng dòng sách, hiển thị đủ đường dẫn tầng/kệ/ngăn và nguồn nhập/nhà cung cấp hiện có. Chốt validation để không nhập thành công nhưng bản sao không có vị trí.
- Lưu vị trí gắn với bản sao và thông tin cần tra cứu lại của dòng phiếu nhập; không chỉ lưu lựa chọn tạm trên UI.
- Kiểm tra shelf tồn tại, thuộc cây vị trí hợp lệ và được phép dùng. Lỗi bất kỳ dòng nào phải rollback phiếu/chi tiết/bản sao.

**Kiểm tra để xong:** nhiều dòng/ngăn, số lượng >1, ISBN/bookId, vị trí thiếu/sai/đã xóa, lỗi dòng cuối không để lại bản sao mồ côi. Chạy inventory test và Web test/build liên quan.

**Đã chốt 29/10:** chọn tầng/kệ/ngăn riêng từng dòng, lưu ngăn trên mọi bản sao và snapshot đường dẫn/barcode theo dòng phiếu; GET chi tiết đọc lại sau lưu. Migration 31, rollback toàn phiếu và hai phiếu đồng thời cùng ngăn đã kiểm thử trên PostgreSQL Testcontainers. 56 Backend + 186 Web test, production build và browser fixture desktop/mobile PASS. API/giới hạn: `Docs/API_B06_B07_NHAP_KHO_VI_TRI.md`.

## B07 Nhận diện ISBN và tự chọn vị trí theo quy tắc Admin

**Đầu ra:** Nhập ISBN -> tìm metadata/phân loại -> đề xuất ngăn theo quy tắc đang hiệu lực; xác nhận nhập lưu đúng ngăn.

- Trước khi code, đối chiếu contract với người làm Admin; không tự tạo một bộ quy tắc hard-code song song. Nếu chưa có nơi quản lý quy tắc, ghi phần phụ thuộc, chưa coi B07 hoàn thành.
- Ưu tiên catalog/taxonomy đã có. ISBN là định danh ấn bản, không tự mã hóa khoa/ngành; ISBN chưa biết cần metadata/phân loại có nguồn hoặc yêu cầu bổ sung, không đoán ngành chỉ từ các chữ số.
- Hiển thị lĩnh vực/ngành, quy tắc khớp và vị trí. Xử lý thiếu metadata, nhiều quy tắc khớp, không có quy tắc, ngăn không còn hợp lệ và quy tắc đổi trước khi bấm lưu.
- Kiểm tra lại ở server tại lúc nhập. Nếu cho phép thủ thư đổi vị trí, phải tuân theo quyền/quy tắc đã thống nhất và ghi lý do; không cho UI vượt qua chính sách.
- Không tự triển khai dịch vụ metadata trả phí. Nếu cần nguồn ngoài, chọn nguồn/cách dùng trong phạm vi đã thống nhất, xử lý lỗi mạng/rate limit và không đặt secret ở client.

**Kiểm tra để xong:** ISBN có sẵn/chưa biết/không hợp lệ, hai ấn bản, phân loại mơ hồ, quy tắc xung đột/đổi giữa chừng; không tạo bản sao sai ngăn hoặc giả nhận diện thành công.

**Checkpoint 29/10 — CHƯA HOÀN TẤT:** người dùng xác nhận Admin chưa làm quy tắc. Đã có API staff tra ISBN-10/13 hoặc bookId, nhận diện ấn bản trong catalog và hiển thị category/DDC/subject kèm nguồn. Luôn trả `WAITING_ADMIN_RULES`, `suggestedShelfId=null`; UI yêu cầu chọn vị trí thủ công. Chưa có engine, kiểm tra rule hiệu lực/xung đột/thay đổi hoặc override. Bản nháp hợp đồng cần Admin chốt nằm trong `Docs/API_B06_B07_NHAP_KHO_VI_TRI.md`. Dừng trước B08.

## B08 Tổng hợp đề xuất mua và export gửi khoa

**Đầu ra:** Thủ thư xem các đầu sách được đề xuất theo nhóm và xuất Excel phục vụ gửi khoa; việc gửi file thực tế do người dùng thực hiện.

- Gộp bằng ISBN chuẩn hóa khi có. Khi thiếu ISBN, dùng khóa có tên/tác giả/ấn bản/năm phù hợp; không gộp hai ấn bản khác nhau chỉ vì trùng tên, không ép hai ISBN khác nhau thành một sách.
- Giữ từng đề xuất/sinh viên gốc; nhóm có số lượt, số người, danh sách khoa và mã đề xuất để truy vết. Gộp báo cáo không xóa bản ghi hoặc tự duyệt hàng loạt.
- Lọc theo khoa/khoảng ngày/trạng thái. Nếu một đầu sách được nhiều khoa đề xuất, trong một báo cáo tổng vẫn một dòng với phân bố theo khoa; báo cáo một khoa chỉ đếm người/đề xuất thuộc khoa đó.
- Thêm bảng tổng hợp và export `.xlsx` ở `pages/admin/suggestions`; tải đủ tập kết quả và giữ duyệt/từ chối từng đề xuất hiện tại.

**Kiểm tra để xong:** hai sinh viên cùng ISBN -> một dòng, số lượt 2; cùng tên khác ấn bản không gộp; không ISBN; khoảng trắng/dấu; nhiều khoa; cùng người gửi lặp; file rỗng và chuỗi công thức. Chạy proposal/report tests và Web spec/build.

**Đã chốt 29/10:** Backend tổng hợp read-only theo ISBN/metadata thận trọng, lọc khoa/ngày/trạng thái trước đếm lượt/người, giữ nguồn gốc; Web tab Tổng hợp / Excel, phân trang nhóm, mở drawer từng đề xuất và tải đủ ba sheet. 57 Backend + 199 Web test, production build, browser desktop/mobile PASS; file tải đã được POI đối chiếu đủ 26 nhóm/27 nguồn so với preview 10 dòng, không có formula cell. Contract/giới hạn: `Docs/API_B08_TONG_HOP_DE_XUAT_MUA.md`. Không gửi file tự động. Dừng trước B09; B07 vẫn chờ Admin.

## B09 Backend đề xuất xử lý sách cũ

**Đầu ra:** Lưu được hồ sơ đề xuất xử lý từng bản sao từ kiểm kê, có lý do và lịch sử.

- Phân biệt đề xuất mua sách `book_proposals` với đề xuất xử lý tồn kho; không tái dùng bảng mua sách sai nghĩa.
- Dữ liệu: mã bản sao/đầu sách, kỳ hoặc phiếu kiểm kê nguồn, tình trạng/chứng cứ, lý do cũ/lỗi thời/không còn dùng, phương án đề xuất sửa/chuyển/thanh lý/hủy, người lập và thời điểm.
- API lập/xem chi tiết/danh sách/lịch sử trong phạm vi thủ thư; trạng thái và giao diện tích hợp quyết định thống nhất với role Admin. Có xử lý gửi trùng và cập nhật khi còn ở bước được phép.
- Không nhận xử lý thực tế cho bản sao đang mượn/giữ; không xóa lịch sử để “thanh lý”. Chặn nhiều hồ sơ hoạt động xung đột trên cùng barcode.
- Kết quả thanh lý/hủy thực tế cần mã sự kiện/ngày có hiệu lực, người chịu trách nhiệm và liên kết quyết định; chỉ kết quả đó được dùng cho số liệu B13. Xây phần ghi nhận của thủ thư nếu hợp đồng quyền cho phép; phần quyết định Admin do người khác làm.

**Kiểm tra để xong:** sai barcode, trùng đề xuất, trạng thái mượn/giữ, dữ liệu kiểm kê thay đổi, truy cập trái quyền, migration và lịch sử tồn kho. Không mặc định đề xuất được duyệt.

**Checkpoint 29/10 — phần thủ thư hoàn tất:** migration 32 và API lập lô nháp/sửa/gửi/hủy, list/detail/history, nguồn kiểm kê hoàn tất và fingerprint, version, khóa/chống hồ sơ hoạt động trùng, replay cùng request key, rollback toàn lô. Không thay đổi copy hoặc xóa lịch sử. Người dùng xác nhận Admin chưa làm; decision/actual execution/event schema còn chờ, chưa có dữ liệu thực thi cho B13. 53 Backend test PASS. Contract và phần chờ: `Docs/API_B09_B10_XU_LY_SACH_CU.md`.

## B10 Web form xử lý sách cũ từ kiểm kê

**Đầu ra:** Ở kiểm kê/chi tiết bản sao, thủ thư chọn sách và lập form đề xuất, theo dõi kết quả.

- Dùng dữ liệu kiểm kê thật, chọn nhiều bản sao có validation theo từng dòng; nhập lý do/tình trạng/phương án và chứng cứ theo cơ chế tệp/URL được thống nhất.
- Trang danh sách/chi tiết có trạng thái, người lập, ngày, lịch sử và kết quả nhận từ luồng có thẩm quyền.
- Gửi thành công chỉ là lập đề xuất; không hiển thị “đã thanh lý” khi chưa có kết quả thực tế. Cho biết bản sao nào không đủ điều kiện.

**Kiểm tra để xong:** tạo từ kiểm kê và mở lại hồ sơ, chọn trùng, hủy form, API lỗi, trạng thái cập nhật. Chạy Web spec/build; mô tả rõ thao tác nào còn chờ role Admin.

**Checkpoint 29/10 — phần thủ thư hoàn tất:** tab/entry từ kiểm kê, chọn nhiều barcode thật với blocker và nội dung riêng, URL chứng cứ, danh sách có lọc/phân trang, chi tiết/lịch sử và đúng quyền owner. Yêu cầu lập nháp không rõ kết quả giữ key/body theo user để retry qua reload, không tự gửi. 215 Web test/build và browser fixture desktop/mobile PASS, đã kiểm mất phản hồi/retry không trùng và giữ nguyên copy. UI ghi rõ chưa có kết quả thực tế; Admin quyết định/thực thi chưa tích hợp. Dừng trước B11.

## B11 Backend Dashboard mượn trả và xếp hạng

**Đầu ra:** API phân tích theo khoảng thời gian và tháng/quý/năm, đúng lượt mượn/trả thực tế.

- Thống kê lượt giao sách, lượt trả, xu hướng theo thời gian; không đếm tạo reservation là một lần mượn. Định nghĩa rõ đơn vị lượt tài liệu và số phiếu để một phiếu ba cuốn không bị hiểu là cùng chỉ số.
- Top mượn nhiều nhất, ít nhất trong nhóm đã có lượt, chưa từng mượn và top đang được mượn tại thời điểm xem. Phân biệt “chưa được mượn trong kỳ” với “chưa từng được mượn”.
- Danh sách quá hạn hiện tại: sinh viên/phiếu/tài liệu/hạn trả, liên kết báo cáo B04. Không nhầm snapshot hiện tại với danh sách quá hạn tại một ngày quá khứ.
- Dùng timestamp nghiệp vụ và trạng thái chuẩn; rà `BORROWED`/`BORROWING`. Lấp bucket thời gian rỗng bằng 0; lọc ngày theo khoảng đầu bao gồm/cuối không bao gồm và timezone thống nhất.
- Truy vấn tổng hợp/phân trang ở DB, tránh tải cả lịch sử lên UI; chỉ thêm index khi truy vấn cần và kiểm tra kế hoạch truy vấn phù hợp.

**Kiểm tra để xong:** đặt nhưng không nhận, hủy, nhiều cuốn/phiếu, trả một phần, trả khác tháng mượn, ranh giới tháng/quý/năm, đồng hạng, sách chưa có bản sao, dữ liệu rỗng.

**Checkpoint 29/10 — Backend hoàn tất:** timestamp giao/nhận vật lý có nguồn và backfill thận trọng, tách items/tickets, bucket tháng/quý/năm, năm chế độ xếp hạng, quá hạn STUDENT hiện tại, phân trang SQL và coverage dữ liệu cũ. DTO Dashboard cũ giữ tương thích, weekly/trending loại reservation. Contract và kiểm chứng: `Docs/API_B11_B12_DASHBOARD.md`, `Docs/BAN_GIAO_BATCH_THU_THU.md`. Web mới thuộc B14.

## B12 Backend Dashboard khoa ngành và tài liệu hiện có

**Đầu ra:** Thống kê đầu sách/bản sao/tài liệu số theo khoa/ngành và danh sách tài liệu có phân trang.

- Tách số đầu sách, số bản sao vật lý, bản đang sẵn sàng, đang mượn/giữ, mất/hỏng/ngừng khai thác và tài liệu số. Không cộng sách điện tử + file đính kèm thành hai đầu sách nếu thực chất cùng một tài liệu.
- Áp dụng mapping chuẩn, có nhóm chưa phân loại. Nếu một sách phục vụ nhiều ngành, hiển thị cách đếm/quan hệ rõ: tổng từng ngành có thể chồng lấp, tổng toàn thư viện phải distinct theo định danh.
- Bộ lọc khoa/ngành, loại tài liệu, trạng thái; danh sách trả được sách và tài liệu số, có vị trí hoặc mức truy cập/liên kết phù hợp.
- Không coi taxonomy recommender là sơ đồ quản lý khoa/ngành đã hoàn chỉnh; bổ sung mapping sau khi thống nhất contract, giữ dữ liệu hiện có tương thích.

**Kiểm tra để xong:** sách nhiều ngành, không ngành, nhiều bản sao, sách liên kết tài liệu số, tài liệu số độc lập, tài liệu ngừng khai thác; tổng distinct khớp danh sách.

**Checkpoint 29/10 — Backend hoàn tất:** API holdings/material/copy/asset, tổng distinct và nhóm chồng lấp, loại/trạng thái hiện tại. Người dùng cho thủ thư gán nhiều ngành kèm nguồn/lịch sử: catalog từ B04, version/khóa/replay/rollback và audit trước/sau. File gắn BOOK không cộng thêm đầu tài liệu; không tự suy ngành, không dựng kết quả thanh lý hay vòng đời số. Contract/giới hạn: `Docs/API_B11_B12_DASHBOARD.md`. Web mới thuộc B15.

## B13 Backend Dashboard tài chính nhập mới và thanh lý

**Đầu ra:** API tách đầy đủ phạt phát sinh/đã thu/còn nợ, số sách nhập mới và số bản sao thanh lý/hủy theo tháng/quý/năm.

- Tài chính đọc nghĩa vụ và thanh toán/allocations thực tế, tách thu tiền mặt/chuyển khoản/cấn cọc, miễn giảm/điều chỉnh. Không cộng cả charge và payment thành doanh thu hai lần; không dùng riêng type `FINE` để bỏ sót mất/hỏng/phí kỹ thuật.
- Ghi rõ số phát sinh trong kỳ, số tiền thực nhận trong kỳ, dư nợ hiện tại hoặc dư tại ngày chốt. Nếu chưa đủ dữ liệu tái tạo số dư quá khứ thì không gắn nhãn “dư nợ cuối kỳ” cho wallet hiện tại.
- Test ví dụ tổng phạt 200.000/cọc 50.000 -> đã cấn 50.000/còn nợ 150.000, rồi thanh toán một phần và phần còn lại. Tách nạp cọc khỏi tiền thu phạt; thể hiện điều chỉnh/miễn giảm riêng.
- Nhập mới: số đầu sách mới distinct và số bản sao nhập theo ngày phiếu hoàn tất; không lấy `BookEntity.createdAt` thay toàn bộ luồng nhập. Bảo đảm batch nhiều cuốn một đầu sách tính đúng.
- Thanh lý/hủy: chỉ đếm bản sao có sự kiện hoàn tất thực tế của B09/hợp đồng Admin, không đếm đề xuất PENDING/REJECTED. Giữ dữ liệu lịch sử để thống kê lại.

**Kiểm tra để xong:** cấn cọc thiếu/đủ, tiền đã thu khác tiền phạt đã phát sinh, trả nhiều đợt, miễn giảm, điều chỉnh ngược, giao dịch lặp, nhập nhiều đợt, đề xuất chưa duyệt, kết quả ngoài kỳ. Truy vấn có test đối chiếu từng khoản.

**Checkpoint 29/10 — xong tài chính/nhập; thực thi chờ Admin:** API tài chính tách charge/receipt/allocations/cọc/miễn giảm/điều chỉnh/nạp cọc, debt hiện tại và coverage; API nhập theo completedAt/receipt quantities, distinct đầu nhập/lần đầu ghi nhận. Migration 35 giữ bằng chứng cấn cọc mới và ngày hoàn tất nhập; không đoán dữ liệu cũ. 71 Backend test PASS, gồm luồng thật 200.000/cọc 50.000/thu hai đợt/replay, DB/Liquibase/HTTP và hồi quy. Thanh lý/hủy trả null + WAITING_ADMIN_WORKFLOW, chưa nghiệm thu chỉ số thực thi; không đếm đề xuất/status copy thành thực thi. Hợp đồng/giới hạn: `Docs/API_B13_TAI_CHINH_NHAP_XU_LY.md`. Dừng trước B14.

## B14 Dashboard Web mượn trả và xu hướng

**Đầu ra:** Phần Dashboard mượn/trả sử dụng API B11, thao tác xem được bằng role thủ thư.

- Bộ lọc thời gian tháng/quý/năm/khoảng ngày; hiển thị mốc dữ liệu và đơn vị phiếu/lượt tài liệu.
- Biểu đồ xu hướng giao/trả; bảng top nhiều/ít/chưa mượn và top đang mượn; danh sách quá hạn mở được chi tiết/báo cáo.
- Loading/lỗi/rỗng, phân trang, hiển thị giới hạn top rõ ràng. Kiểm tra quyền truy cập API Dashboard, không chỉ ẩn menu.

**Kiểm tra để xong:** đổi kỳ, bucket 0, dữ liệu khớp API, click mở chi tiết, lỗi tải từng phần; Web spec/build.

**Checkpoint 30/10:** Dashboard ở `/admin`, tab Mượn/trả dùng B11, chung kỳ/ngày/period, biểu đồ/table bucket thật, 5 ranking server paging, current overdue link quầy/B04 deep link. Loading/error/retry/cancel request cũ và cảnh báo lịch sử thiếu chứng cứ. Hướng dẫn: `Docs/HUONG_DAN_B14_B15_DASHBOARD.md`.

## B15 Dashboard Web danh mục tài chính nhập và thanh lý

**Đầu ra:** Các phần Dashboard còn lại dùng B12–B13, đáp ứng toàn bộ TT-06.

- Bảng/biểu đồ khoa/ngành; số đầu sách/bản sao và tài liệu số; danh sách tất cả sách/tài liệu với tìm kiếm/lọc/phân trang, không tải mọi dòng một lượt.
- Các chỉ số phạt phát sinh/đã thu/cấn cọc/còn nợ/miễn giảm/điều chỉnh với nhãn rõ; có drill-down đến danh sách khoản liên quan.
- Thống kê nhập mới, thanh lý, hủy theo kỳ và liên kết chứng từ/hồ sơ. Đồng bộ bộ lọc thời gian, ghi rõ chỉ số là snapshot hay theo kỳ.
- Đối chiếu số ở thẻ tổng, biểu đồ và danh sách; không hiển thị số 0 như một kết quả thật khi API lỗi hoặc phân loại chưa sẵn sàng.

**Kiểm tra để xong:** nhiều ngành, số tiền âm/điều chỉnh, cùng kỳ giữa các phần, dữ liệu rỗng/chưa mapping, hồ sơ chưa hoàn tất; Web spec/build và kiểm tra giao diện.

**Checkpoint 30/10:** ba tab Danh mục/Tài chính/Nhập-xử lý dùng B12–B13; q ở SQL cùng tổng/list, phân trang và drill-down, hiện source/copy/file/receipt/allocation. Dư nợ hiện tại riêng, signed adjustment, null thanh lý không biến thành 0, coverage bản sao không có catalog. Không mutation Dashboard. Hướng dẫn và giới hạn: `Docs/HUONG_DAN_B14_B15_DASHBOARD.md`.

## B16 Dữ liệu biên hồi quy và bàn giao

**Đầu ra:** Checklist chạy lại được cho đủ TT-01–TT-07; dữ liệu demo có nguồn, kết quả test mới và context cập nhật.

- Seed/fixture bao phủ ít nhất hai khoa/nhiều ngành, sách nhiều người đề xuất, không ISBN/khác ấn bản, sách chưa mượn, mượn nhiều, trả một phần, quá hạn, chỉ còn nợ, cọc nhỏ hơn phạt, nhiều lần thu, miễn giảm/điều chỉnh, vị trí chưa có quy tắc, sách cũ và đề xuất thanh lý chưa/đã xử lý.
- Unit/integration test quan trọng phải được thêm/chạy trong chính batch chức năng; B16 bổ sung hồi quy xuyên luồng, không dồn toàn bộ việc test đến cuối.
- Kịch bản: đặt giữ tự duyệt -> giao -> trả mất/hỏng -> cấn cọc/còn nợ -> xuất báo cáo -> Dashboard; nhập ISBN -> vị trí theo rule -> kiểm kê -> đề xuất xử lý -> quyết định đúng quyền -> thống kê vòng đời.
- Kiểm tra LIBRARIAN làm đúng phần việc; STUDENT không truy cập báo cáo/ghi tồn kho; không chiếm thao tác cấu hình/duyệt chỉ dành Admin.
- Chạy Backend test/package, Web test/build và kiểm tra migration/preflight phù hợp. Mobile chỉ chạy kiểm tra liên quan nếu hợp đồng dùng chung có ảnh hưởng; phần nghiệm thu client Sinh viên được bàn giao đúng người phụ trách.
- Lưu báo cáo kết quả và lỗi còn lại trong `TestChucNang/`; cập nhật `PROJECT_CONTEXT.md`, hợp đồng và checklist. Chỉ đánh dấu hoàn tất yêu cầu khi có bằng chứng và phụ thuộc thực sự đã được đáp ứng.

**Checkpoint 30/10 — đã hồi quy/bàn giao phần khả dụng:** 283 Backend test/package, 230 Web test/build, preflight và browser fixture desktop/mobile PASS. Đã backup/áp migration 28–35 vào DB local, 81 tổng/0 pending, read-only reports PASS; không deploy runtime. Source counts không đổi, coverage báo 269922 copy chưa khớp catalog và dữ liệu cũ thiếu ngày/nguồn. Checklist/bằng chứng và phần Admin giữ mở: `TestChucNang/24_NGHIEM_THU_THU_THU_B01_B16.md`. Không tuyên bố nghiệm thu trọn vẹn các phần Admin chưa triển khai.

## Quy tắc kết thúc mỗi lượt để tiếp tục được

1. Đầu lượt đọc `PROJECT_CONTEXT.md`, phần batch được gọi ở đây và bàn giao gần nhất; kiểm tra git diff liên quan vì hai người khác có thể đã đổi code. Không rà lại toàn repository.
2. Chỉ thực hiện batch được yêu cầu. Không tự chuyển B02 sau B01 hoặc gộp cả Dashboard vào một lượt. Nếu batch phát sinh lớn, tách tiếp thành `Bxx.a/Bxx.b`, ghi ranh giới và bàn giao trước khi mở rộng.
3. Mỗi điểm dừng giữ code nhất quán/build được ở phần đã làm; nếu chưa đạt, ghi rõ lỗi và lệnh tái hiện, không gọi là xong. Không bảo đảm số token cố định cho một batch.
4. Ghi checkpoint sau từng phần Backend/UI/test đủ độc lập, không đợi sát cuối lượt. Cập nhật dòng trạng thái trong bảng và ghi bàn giao ngắn tại `Docs/BAN_GIAO_BATCH_THU_THU.md` khi bắt đầu triển khai batch đầu tiên.
5. Bàn giao gồm: batch/sub-batch; việc đã xong; file và schema/API thay đổi; test thực chạy/kết quả; phần dở; phụ thuộc; bước kế tiếp cụ thể. Không lưu transcript hoặc secret.
6. Không tự commit/push, triển khai, chạy tải/restore dữ liệu hoặc gửi file Excel cho khoa trong kế hoạch này. Các thao tác đó chỉ làm khi người dùng yêu cầu/phạm vi lượt thực hiện cho phép.

Câu gọi mẫu cho lượt tiếp theo: **“Đọc context và kế hoạch thủ thư, làm B01 thôi, test phần liên quan rồi ghi checkpoint và dừng.”**
