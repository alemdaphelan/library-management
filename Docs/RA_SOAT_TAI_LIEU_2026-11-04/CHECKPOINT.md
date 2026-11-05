# Checkpoint rà soát hồ sơ đã hoàn tất

Mốc đối chiếu: 04/11/2026, nhánh `main`, HEAD `756870b`. Có thay đổi chưa commit từ các batch trước; hồ sơ phải phản ánh cả source đang làm việc, không chỉ HEAD.

## Phạm vi

Sửa ba tài liệu Word và ba sơ đồ do người dùng cung cấp. Giữ nguyên bản gốc ở thư mục `Desktop/library-managment`. Không xóa chức năng, bảng hay dữ liệu của ứng dụng để ép khớp tài liệu.

## Tiến độ

- Đợt 1 đã đọc sáu bản gốc, context và source theo từng module. Đã chụp metadata cấu trúc PostgreSQL ở chế độ chỉ đọc: 66 bảng, 622 cột, 48 FK. Container PostgreSQL đã dừng lại.
- Đợt 2 hoàn tất sáu file. Bản nghiệp vụ có 15 BUC đang mô tả và 4 mã gốc chờ/ngoài phạm vi. Bản hệ thống có 27 UC gốc hiệu chỉnh, 15 UC mới UC40–UC54 và 12 mã gốc chờ/ngoài phạm vi. README đối chiếu đủ 39 mã UC gốc và giải thích từng thay đổi lớn.
- Đợt 3 hoàn tất ngày 05/11/2026. Cả ba sơ đồ parse/render PASS bằng Mermaid 11.4.1. Đã kết xuất và xem đủ 27 trang Word (7 nghiệp vụ, 16 hệ thống, 4 công nghệ); kiểm tra lại các trang thay đổi ở lượt hiệu chỉnh cuối. ZIP/XML, tham chiếu mã, đường dẫn, metadata ERD, migration filenames và hash nguồn/đầu ra PASS. Kết quả tại VERIFICATION.json.

## Điểm đã xác minh cần giữ

- Ba role là ADMIN, LIBRARIAN, STUDENT. Tác giả và nhà xuất bản đang là chuỗi trong books.
- Không có dịch vụ Python AI hay pipeline WebSocket STOMP đã hoàn thiện. Gợi ý chạy trong Java; RAG là module trong backend.
- Góp ý và bàn giao ca chỉ có endpoint khung. Điểm danh không tự kích hoạt thẻ. Hoàn cọc và chốt sổ kế toán chưa có luồng hoàn chỉnh.
- Thanh lý mới dừng ở đề xuất; duyệt và thực hiện của Admin còn chờ. Bảng cũ phòng, kiosk, ca trực, sync vẫn tồn tại vật lý.
- Trả vật lý, xác nhận mất hỏng và thanh toán là các sự kiện khác nhau; báo mất của sinh viên chưa phải quyết định thu tiền.
- Quy tắc ngăn không có trường ưu tiên. Đề xuất sách cũ cần kiểm kê hoàn tất và dòng quét nhận diện. Nhánh sách của báo cáo sinh viên là quá hạn chưa trả.
- API sửa trạng thái bản sao trực tiếp chưa kiểm tra phiếu mở; đổi ngăn chưa khóa xung đột lưu thông. Đã ghi thành giới hạn code, không tự sửa runtime trong task tài liệu.

## Đầu ra và trạng thái dừng

Sáu file cuối nằm cùng thư mục checkpoint. Đọc README để xem đường dẫn tải, bảng đối chiếu và phần cần code riêng; PROJECT_CONTEXT.md đã dẫn tới bộ hồ sơ này. Bản gốc được kiểm tra hash và giữ nguyên. Không chạy full test ứng dụng vì không sửa runtime, không khởi chạy Web/backend, không áp migration và không commit/push. Không còn bước tiếp tục tự động trong phạm vi rà soát sáu file; chỉ thực hiện task code mới khi người dùng yêu cầu.
