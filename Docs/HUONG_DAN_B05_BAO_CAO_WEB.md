# B05 — Xem và tải báo cáo quá hạn / công nợ trên Web

Ngày: **29/10/2026**. Dành cho thủ thư/Admin tại `/admin/borrows` → **Báo cáo quá hạn & công nợ**. Backend sử dụng hợp đồng [API B04](API_B04_BAO_CAO_QUA_HAN_CONG_NO.md), cần migration 29–30 trên môi trường chạy.

## Cách dùng

1. Mở tab báo cáo. Mặc định xem **Quá hạn hoặc còn nợ**, mọi khoa, 25 sinh viên/trang. Đây là tập hợp sinh viên có ít nhất một trong hai điều kiện, không yêu cầu đồng thời cả hai.
2. Chọn loại báo cáo, khoa từ API, nhập MSSV/họ tên, chọn số dòng rồi bấm **Xem báo cáo**. Bộ lọc ngày tạo/trạng thái phiếu ở tab danh sách phiếu không ảnh hưởng báo cáo này. **Đặt lại bộ lọc** trở về mặc định và trang đầu.
3. Xem số sinh viên phù hợp toàn bộ bộ lọc, thời điểm chốt/timezone và đơn vị VND. **Trang trước / Trang tiếp** dùng phân trang Backend; không chia trang lại từ danh sách phiếu đang tải.
4. Các cột tiền tách nghĩa vụ đã ghi sổ, thanh toán/cấn cọc, miễn, điều chỉnh và dư nợ ledger. Tiền là dữ liệu server, gồm cả khoản cũ; không lấy tổng phí trễ của phiếu làm dư nợ. Ví chưa có hiện “Chưa có ví”, khác ví có nợ 0. Chênh ví/ledger hoặc số đối soát bất thường được đánh dấu **Cần đối soát**.
5. Bấm **Chi tiết** trên dòng sinh viên để xem tài liệu còn quá hạn và các khoản sổ nợ. **Xem phiếu #...** mở panel phiếu hiện có ngay trong trang, đóng panel trở lại báo cáo với bộ lọc giữ nguyên. Có liên kết quầy trả/báo mất, hồ sơ phạt và **Xem / thu nợ**. Người đã trả hết sách vẫn có thể còn công nợ.
6. **Tải Excel toàn bộ kết quả** dùng đúng mode/khoa/từ khóa của báo cáo đã xem, không gửi page/size. Nếu đang sửa bộ lọc, cần bấm Xem trước khi tải/chuyển trang. Khi xuất, khóa bộ lọc và nút tải để chặn gửi lặp. Tên file lấy từ Content-Disposition; thiếu header có tên dự phòng gồm loại báo cáo và mốc thời gian báo cáo đã xem.
7. File gồm ba sheet Tong hop / Qua han / So no theo B04. Xuất là snapshot mới tại thời điểm server tạo file nên có thể khác lúc preview nếu phát sinh nghiệp vụ xen giữa. Thông báo “Đã yêu cầu tải” xác nhận trình duyệt nhận lệnh tải, không giả nhận rằng người dùng đã lưu file thành công.

## Phân loại đầy đủ và lỗi dữ liệu

- Khối phân loại đọc `classification-coverage` cho **toàn bộ sinh viên**, kể cả không nợ/quá hạn. Chỉ hiện đã phân loại X/X khi API xác nhận complete=true và có sinh viên; 0 sinh viên không được coi là minh chứng đã phân loại đủ dữ liệu thật.
- Khi còn thiếu, mở khối cảnh báo để xem department nguồn và số lượng cần sửa. Lúc này bộ lọc có thêm **Cần bổ sung phân loại**. Danh mục khoa và mapping đều từ Backend, không tự ghép faculty từ chuỗi department ở Web.
- Lỗi kiểm tra phân loại hiển thị riêng, không biến thành tỷ lệ 100%; có nút kiểm tra lại. Lỗi tải danh mục khoa có nút tải lại và vẫn cho xem mọi khoa, không dựng một danh sách khoa giả.
- Lỗi preview xóa kết quả cũ để không gắn dữ liệu cũ với bộ lọc mới, có nút thử lại. Kết quả rỗng có thông báo và không cho tải file không có dữ liệu. Lỗi tải Excel không xóa bảng đã xem; lỗi quyền yêu cầu tài khoản thủ thư/Admin, lỗi kết nối có thể thử lại. Phản hồi lỗi JSON/Blob không được lưu thành file Excel.

## Phần danh sách phiếu giữ lại

Tab **Danh sách phiếu mượn** vẫn có tìm kiếm, lọc ngày/trạng thái, chi tiết, mở quầy và từ chối giữ chỗ có lý do. Nhãn tổng `totalLateFine` đổi thành **Phí trễ lũy kế**, kèm giải thích gồm phần có thể đã thanh toán; số đó không còn mang tên “Nợ phạt”. Mở tab báo cáo lần đầu mới gọi API báo cáo; đổi tab không xóa form/bảng báo cáo đã tải.

## Kiểm tra trong B05

- **173/173 Web tests PASS**, 45 file, không loại test. Bao gồm 26 test tăng từ B03 cho API query/Blob/headers, filename và cleanup, filter/paging/reset, xuất theo bộ lọc đã xem, rỗng/lỗi/quyền/retry, kiểm tra phân loại, liên kết và giữ thao tác phiếu.
- **Production build PASS**, còn warning cũ CSS budget portal home/history và CommonJS qrcode; tests có warning sourcemap ZXing.
- **Browser QA cô lập PASS**: Edge headless với API fixture và hồ sơ tạm; desktop 1440px/Web hẹp 390px, bộ lọc, phân trang, modal phiếu, giữ bộ lọc, export filename, empty/error/retry/reset, không lỗi runtime. Đã xem ảnh kiểm tra bố cục; bảng số tiền cuộn trong khung, không ép rộng toàn trang.
- File fixture tạo bằng **chính exporter POI B04** từ 26 sinh viên. Trình duyệt tải file ở lúc preview 10 dòng; mở lại `.xlsx` bằng POI xác nhận đủ 26 sinh viên/cả ba sheet, MSSV và các số nghĩa vụ/thanh toán-cọc/miễn/điều chỉnh/nợ khớp JSON API fixture.
- Đây là kiểm tra trình duyệt với API fixture, không phải nghiệm thu Web xuyên suốt DB đang dùng. Không thay Backend/schema/Flutter trong B05, không chạy lại Maven suite hoặc restart/deploy server/DB. Fixture, script, ảnh và log kiểm tra chỉ ở `.codex-tmp/b05-*` được Git ignore; không có dữ liệu giả trong UI sản phẩm.

Lệnh từ `web-app/`:

```powershell
npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs
npm.cmd run build
```

Cấu hình một worker giống B01. Checkpoint và điểm tiếp tục B06 ở [BAN_GIAO_BATCH_THU_THU.md](BAN_GIAO_BATCH_THU_THU.md).
