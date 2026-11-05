# F09 — Đọc tài liệu trong ngăn và phục hồi khi tải lỗi

Phạm vi: `/admin/shelf-map`, API `/locations`. Giữ nguyên quyền ADMIN/LIBRARIAN và quyền sửa quy tắc chủ đề của Admin. Không tự đối chiếu bản sao sang một đầu sách khác.

## Đọc dữ liệu

- `GET /locations/shelves/{id}/books/page?page=0&size=50`
- `GET /locations/unplaced-copies?page=0&size=50`
- `GET /locations/shelves/{id}/books` giữ tương thích danh sách cũ; Web dùng API phân trang.

Trang trả `{items,total,page,size}`. `page>=0`, `size=1..100` và `page*size<=2147483647` để không vượt giới hạn offset của Hibernate; sai trả 400, ngăn không tồn tại trả 404. Content/count cùng snapshot REPEATABLE_READ. Ngoài trang cuối trả items rỗng, vẫn giữ total.

Mỗi dòng giữ barcode, bookId gốc (có thể null), title/isbn/status và catalogMatched. Truy vấn LEFT JOIN hiện có giữ bản sao mất liên kết đầu sách; không đổi ID để ghép với catalog mới. Dòng catalogMatched=false hoặc bookId=null không có liên kết mở đầu sách trên Web. Chưa đối chiếu catalog và chưa xếp ngăn là hai tình trạng độc lập; bản sao có vị trí vẫn thuộc ngăn đó dù mất catalog.

## Phục hồi trên Web

- Danh sách ngăn và bản sao chưa xếp có loading/error/retry riêng. Khi tải lỗi không hiển thị tổng cũ, số 0 hoặc câu “ngăn chưa có tài liệu” như một kết quả thành công.
- Thử lại dùng đúng ngăn/trang đang chọn; thành công xóa lỗi của phần đó. Đổi ngăn hủy request trang trước, bỏ phản hồi cũ; rời màn hình hủy các request đọc.
- GET trang hết thời gian sau 15 giây, dừng trạng thái chờ và có nút Thử lại. Không tự lặp yêu cầu lỗi.
- Khi trang cuối đã mất do dữ liệu thay đổi, tải trang cuối hợp lệ. Danh sách có total=0 trở lại trang đầu. Không gửi trang âm.

## Kiểm tra có thể chạy lại

`ShelfLocationIntegrationTest` khởi động HTTP thật + Hibernate/PostgreSQL tạm, dùng tài khoản Basic chỉ dành cho harness để kiểm tra method roles. Đây không thay thế nghiệm thu JWT toàn ứng dụng. Kiểm tra catalog khớp/mất/null, phân trang ổn định, ngăn rỗng/chưa xếp, lỗi tham số và quyền.

Probe DB local cần bật rõ `-Df09.live=true`, chỉ chạy `ShelfLocationIntegrationTest#liveReadOnlyProbe`, và cấp TASK_DB_NAME/USER/PASSWORD qua biến môi trường. Probe đặt `default_transaction_read_only=on`, ddl-auto=none, tắt Liquibase và xác nhận chế độ chỉ đọc trước kiểm tra. Không chạy fixture ghi dữ liệu trong chế độ live. Không ghi thông tin kết nối vào tài liệu.

Có thể truyền `-Df09.ui-script=../.codex-tmp/f09-real-ui.cjs` trong môi trường đã có driver kiểm tra: Edge tải Web build thật, chuyển GET vị trí tới harness đọc DB local. Profile trình duyệt là dữ liệu kiểm thử; Basic staff thuộc harness, không đăng nhập/ghi tài khoản thật. Driver chỉ chèn một lỗi HTTP để kiểm tra retry, sau đó dùng kết quả API/DB thật. Kết quả và giới hạn nghiệm thu của lượt F09 ghi tại `Docs/KE_HOACH_FIX_LOI_V2.md`.
