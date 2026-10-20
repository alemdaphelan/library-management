# CHECKLIST KIỂM THỬ TOÀN BỘ HỆ THỐNG

## 1. Cách ghi kết quả

Mỗi ca dùng một trong bốn trạng thái:

- `PASS`: kết quả đúng kỳ vọng.
- `FAIL`: kết quả sai và có thể tái hiện.
- `BLOCKED`: thiếu dữ liệu, tài khoản hoặc dịch vụ phụ thuộc.
- `N/A`: ngoài phạm vi hoặc không áp dụng trên thiết bị đang test.

Ghi mã lỗi tương ứng ở cột ghi chú. Không thay đổi mô tả kỳ vọng sau khi đã chạy để làm cho kết quả thành PASS.

## 2. Môi trường và smoke

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| ENV-01 | `docker compose up -d --build` | Các dịch vụ bắt buộc khởi động; Backend healthy |  |  |
| ENV-02 | Mở `/actuator/health` | HTTP 200, trạng thái `UP`, không lộ chi tiết nhạy cảm |  |  |
| ENV-03 | Chạy `week11-smoke.ps1` | Manifest hợp lệ, health UP, catalog hợp lệ |  |  |
| ENV-04 | Mở Swagger và Mailpit | Hai giao diện truy cập được |  |  |
| ENV-05 | Mở Web ở 4200 | Không có lỗi trắng trang/CORS |  |  |
| ENV-06 | Mở Mobile | Splash và đăng nhập hiển thị đúng |  |  |
| ENV-07 | Kiểm tra bản build Firebase | Android package ID là `vn.edu.huit.library`; build không còn dùng `com.example.mobile_app` |  |  |

## 3. Xác thực và phân quyền

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| AUTH-01 | Đăng nhập đúng với Student | Nhận phiên và vào Portal, không vào Admin |  |  |
| AUTH-02 | Đăng nhập đúng với Librarian | Vào màn hình nghiệp vụ nhân viên |  |  |
| AUTH-03 | Đăng nhập đúng với Admin | Vào đầy đủ khu vực quản trị |  |  |
| AUTH-04 | Sai mật khẩu | Không đăng nhập, thông báo an toàn |  |  |
| AUTH-05 | Logout rồi gọi API bảo vệ | Token cũ không còn sử dụng được |  |  |
| AUTH-06 | Refresh token | Cấp access token hợp lệ; token sai/hết hạn bị từ chối |  |  |
| AUTH-07 | Đổi mật khẩu | Mật khẩu cũ hết hiệu lực, mật khẩu mới dùng được |  |  |
| AUTH-08 | Quên mật khẩu → OTP → reset | Mailpit nhận OTP; OTP đúng/sai/hết hạn được xử lý đúng |  |  |
| AUTH-09 | Student gọi API Admin/Librarian | HTTP 403, dữ liệu không đổi |  |  |
| AUTH-10 | Truy cập route Admin trực tiếp bằng Student | Guard chặn hoặc điều hướng phù hợp |  |  |

## 4. Catalog sách và vị trí

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| CAT-01 | Tìm theo tiêu đề/tác giả/thể loại | Kết quả phù hợp, phân trang đúng |  |  |
| CAT-02 | Mở chi tiết sách | Hiển thị metadata, bản sao và vị trí |  |  |
| CAT-03 | Tìm từ khóa không tồn tại | Danh sách rỗng hợp lệ, không lỗi |  |  |
| CAT-04 | Librarian/Admin tạo sách hợp lệ | Sách mới xuất hiện trong catalog |  |  |
| CAT-05 | Tạo/cập nhật dữ liệu thiếu hoặc sai | Validation rõ ràng, không ghi dữ liệu lỗi |  |  |
| CAT-06 | Cập nhật và xóa sách | Dữ liệu thay đổi đúng; ràng buộc liên quan được tôn trọng |  |  |
| CAT-07 | CRUD tầng/dãy kệ/ngăn | Quan hệ vị trí và hiển thị trên chi tiết đúng |  |  |
| CAT-08 | Cập nhật barcode/trạng thái/vị trí bản sao | Đúng bản sao, không trùng barcode |  |  |

## 5. Sinh viên đặt giữ hàng đợi và gia hạn

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| CIR-01 | Student đặt giữ sách còn bản sao | Tạo phiếu `PENDING`, bản sao được giữ đúng |  |  |
| CIR-02 | Đặt cùng đầu sách lần nữa | Không tạo lượt trùng |  |  |
| CIR-03 | Đầu sách hết bản sao, tham gia hàng đợi | Tạo lượt FIFO; mỗi người tối đa một lượt/đầu sách |  |  |
| CIR-04 | Student xem phiếu của mình | Chỉ thấy dữ liệu thuộc tài khoản hiện tại |  |  |
| CIR-05 | Student hủy phiếu PENDING/RESERVED | Phiếu hủy và bản sao/quyền giữ được giải phóng |  |  |
| CIR-06 | Gia hạn đúng một tài liệu | Hạn mới = hạn cũ +10 ngày, số lần gia hạn tăng |  |  |
| CIR-07 | Gia hạn lần thứ hai | Bị chặn |  |  |
| CIR-08 | Gia hạn khi quá hạn/còn nợ/thẻ khóa/có hàng đợi | Bị chặn đúng lý do |  |  |
| CIR-09 | Giữ chỗ quá 48 giờ | Scheduler hủy/chuyển người tiếp theo theo quy tắc |  |  |

## 6. Thủ thư duyệt QR checkout và return

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| QR-01 | Librarian duyệt phiếu PENDING | Phiếu chuyển `RESERVED`, có lịch sử trạng thái |  |  |
| QR-02 | Librarian từ chối và nhập lý do | Phiếu `REJECTED`, bản sao được giải phóng |  |  |
| QR-03 | Student sinh QR thẻ/phiếu | Payload có chữ ký và hạn dùng |  |  |
| QR-04 | Student cố resolve QR | HTTP 403 |  |  |
| QR-05 | Librarian quét QR hợp lệ của phiếu RESERVED | Server trả hành động `CHECKOUT` |  |  |
| QR-06 | Quét QR giả, sửa nội dung hoặc hết hạn | Bị từ chối, không đổi trạng thái |  |  |
| QR-07 | Checkout với đúng QR/barcode | Phiếu và chi tiết `BORROWING`, bản sao không còn available |  |  |
| QR-08 | Gửi lại cùng idempotency key | Không tạo thao tác trùng |  |  |
| QR-09 | Hai checkout đồng thời cùng bản sao | Chỉ một giao dịch thành công |  |  |
| QR-10 | Quét phiếu BORROWING/OVERDUE | Server trả hành động `RETURN` |  |  |
| QR-11 | Return từng barcode | Chỉ tài liệu tương ứng được trả; phiếu chỉ `RETURNED` khi đã trả đủ |  |  |
| QR-12 | Student tự gọi return/checkout | HTTP 403, dữ liệu không đổi |  |  |

## 7. Quá hạn mất hỏng vi phạm công nợ

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| PEN-01 | Tài liệu quá hạn 1 ngày | Tiền phạt tính từ ngày kế tiếp hạn trả, riêng từng tài liệu |  |  |
| PEN-02 | Nhiều tài liệu cùng phiếu quá hạn | Tiền tính từng tài liệu; bộ đếm vi phạm chỉ tăng một lần/đợt |  |  |
| PEN-03 | Báo mất sau khi quá hạn | Phạt quá hạn dừng ở ngày báo mất; cộng đền bù và phí kỹ thuật |  |  |
| PEN-04 | Hư hỏng nhẹ | Thủ thư nhập tiền sửa theo biểu phí và bắt buộc ghi chú |  |  |
| PEN-05 | Hư hỏng nặng | Xử lý như mất; phí kỹ thuật cộng một lần |  |  |
| PEN-06 | Tài liệu cũ có giá bìa | Việt ngữ x5, ngoại văn x3; phí kỹ thuật không nhân hệ số |  |  |
| PEN-07 | Tài liệu không có giá bìa | Ánh xạ biểu phí Admin theo loại tài liệu |  |  |
| PEN-08 | Thanh toán một phần | Giảm đúng công nợ; quyền mượn vẫn khóa nếu còn nợ đến hạn |  |  |
| PEN-09 | Thanh toán hết | Công nợ đến hạn về 0; chỉ mở quyền khi không còn điều kiện khóa khác |  |  |
| PEN-10 | Phiếu thu | Tách phạt quá hạn, đền bù, sửa chữa, kỹ thuật và người thu |  |  |
| PEN-11 | Librarian tạo yêu cầu điều chỉnh | Không xóa giao dịch gốc; trạng thái chờ Admin |  |  |
| PEN-12 | Admin duyệt điều chỉnh | Sinh giao dịch ngược có lý do/audit |  |  |
| PEN-13 | Miễn giảm PENDING_REVIEW | Đóng băng khoản phạt/nhắc nợ; mượn/gia hạn/đặt giữ vẫn bị chặn |  |  |
| PEN-14 | Ba lần quá hạn trong năm học | Khóa quyền 3 tháng; vẫn đăng nhập/xem nợ/trả/đền bù được |  |  |
| PEN-15 | Hết thời hạn khóa | Chỉ mở khi đã trả sách và không còn nợ; reset năm học đúng |  |  |

## 8. Nhập kho kiểm kê dashboard và đề xuất

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| INV-01 | CRUD nhà cung cấp | Dữ liệu đúng và validation hoạt động |  |  |
| INV-02 | Lập phiếu nhập | Tăng đúng bản sao/barcode và liên kết nhà cung cấp |  |  |
| INV-03 | Xem thống kê nhập kho | Số liệu lấy từ API thật |  |  |
| INV-04 | Tạo kỳ kiểm kê và quét barcode | Phân loại đúng khớp, thiếu, thừa, sai vị trí |  |  |
| INV-05 | Hoàn tất kiểm kê | Lưu kết quả và không cho sửa sai quy trình |  |  |
| DASH-01 | Dashboard Admin/Librarian | Tổng số liệu, hoạt động và sách phổ biến nhất quán với DB |  |  |
| PROP-01 | Student gửi và xem đề xuất sách | Đề xuất được lưu đúng tài khoản |  |  |
| PROP-02 | Librarian/Admin duyệt đề xuất | Cập nhật trạng thái và ghi chú đúng quyền |  |  |

## 9. Tài liệu số và thông báo

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| DIG-01 | Librarian/Admin upload tài liệu hợp lệ | File và metadata được lưu, có thể liên kết sách |  |  |
| DIG-02 | Upload file sai loại/quá giới hạn | Bị từ chối an toàn |  |  |
| DIG-03 | Student xem/tải tài liệu được phép | Tải đúng file và có log truy cập |  |  |
| DIG-04 | Student upload/sửa/xóa tài liệu | HTTP 403 |  |  |
| DIG-05 | Librarian/Admin sửa/xóa tài liệu | Metadata/storage nhất quán |  |  |
| NOTI-01 | Xem thông báo cá nhân | Chỉ thấy thông báo của chính tài khoản |  |  |
| NOTI-02 | Đánh dấu đã đọc | Trạng thái cập nhật đúng |  |  |
| NOTI-03 | Nhắc hạn/quá hạn/phạt/khóa/mở | Scheduler không gửi trùng cùng mốc |  |  |
| NOTI-04 | Đăng nhập Mobile trên thiết bị có Firebase config | Token thiết bị được lưu, không lộ token trên UI/log |  |  |
| NOTI-05 | Nhận push khi app đang mở | Có local notification đúng nội dung |  |  |
| NOTI-06 | Nhận push khi app nền/đã đóng | Hệ điều hành hiển thị notification; thông báo vẫn có trong danh sách app |  |  |
| NOTI-07 | Logout rồi phát sinh thông báo | Token đã hủy không còn nhận push của tài khoản cũ |  |  |

## 10. Gợi ý sách

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| REC-01 | Student mới chưa có lịch sử | Có gợi ý theo ngành + phổ biến |  |  |
| REC-02 | Student có lịch sử | Gợi ý Hybrid kèm điểm/lý do; loại sách đã mượn |  |  |
| REC-03 | Sách mới chưa có lượt mượn | Vẫn có thể xuất hiện nhờ nội dung/ngành |  |  |
| REC-04 | Mở chi tiết sách | Có danh sách sách tương tự |  |  |
| REC-05 | Gọi lặp lại | Kết quả cache nhất quán trong TTL |  |  |
| REC-06 | Student gọi endpoint đánh giá Admin | HTTP 403 |  |  |
| REC-07 | Admin chạy evaluation | Có ba phương pháp, disclaimer proxy và metric hợp lệ |  |  |

## 11. Chatbot RAG

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| RAG-01 | Hỏi quy định gia hạn | Trả đúng 1 lần, +10 ngày, có nguồn |  |  |
| RAG-02 | Hỏi mất/hỏng và phí kỹ thuật | Trả đúng quy tắc, có citation |  |  |
| RAG-03 | Hỏi catalog cụ thể | Trả đúng sách/tác giả nếu có dữ liệu |  |  |
| RAG-04 | Câu ngoài phạm vi | Từ chối do không đủ bằng chứng |  |  |
| RAG-05 | Prompt injection/yêu cầu lộ key | HTTP 400 hoặc chặn đúng, không lộ system prompt/secret |  |  |
| RAG-06 | Tắt/không có Gemini key | Retrieval và extractive fallback vẫn hoạt động |  |  |
| RAG-07 | Gemini timeout/quota/thiếu citation | Chuyển fallback có nguồn |  |  |
| RAG-08 | Gửi quá nhanh/quá dài | Rate limit/validation hoạt động |  |  |
| RAG-09 | Student gọi status/reindex/evaluate | HTTP 403 |  |  |
| RAG-10 | Admin reindex và evaluate | Có trạng thái/chunk và báo cáo 14 ca |  |  |

## 12. Mobile và trải nghiệm giao diện

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| MOB-01 | Đăng nhập/logout/đổi mật khẩu | Luồng hoạt động, phiên được xử lý đúng |  |  |
| MOB-02 | Home/search/detail | Dữ liệu thật, ảnh lỗi có placeholder |  |  |
| MOB-03 | Phiếu mượn/gia hạn | Trạng thái và thao tác từng tài liệu đúng |  |  |
| MOB-04 | Thông báo | Tải và đánh dấu đã đọc được |  |  |
| MOB-05 | Đề xuất sách | Tạo/xem đề xuất được |  |  |
| MOB-06 | Gợi ý cá nhân hóa | Danh sách/lý do hiển thị đúng |  |  |
| MOB-07 | Librarian QR screen | Camera/quyền camera/xử lý payload hợp lệ và lỗi |  |  |
| MOB-08 | Cập nhật họ tên/số điện thoại | Lưu Backend, tải lại app vẫn hiển thị dữ liệu mới |  |  |
| MOB-09 | Tìm/lọc theo chuyên ngành | Danh sách ngành từ API; kết hợp đúng với từ khóa/thể loại |  |  |
| MOB-10 | Kiểm tra dữ liệu hồ sơ thiếu | Hiển thị “Chưa cập nhật”, không hiện MSSV/khoa/email/tiền phạt mẫu |  |  |
| UI-01 | Web desktop và kích thước hẹp | Không tràn chữ, nút chính dùng được |  |  |
| UI-02 | Màu sắc/chữ/trạng thái tải/lỗi | Nhất quán xanh dương–trắng, dễ đọc |  |  |
| UI-03 | Nút và route ngoài phạm vi | Không xuất hiện chức năng đặt phòng hoặc nút giả |  |  |

## 13. Kiểm thử kỹ thuật bổ sung

| ID | Ca kiểm thử | Kết quả mong đợi | Trạng thái | Ghi chú/lỗi |
|---|---|---|---|---|
| TECH-01 | Backend `mvnw test` | Toàn bộ test đạt |  |  |
| TECH-02 | Web test + production build | Toàn bộ test và build đạt |  |  |
| TECH-03 | Flutter analyze + test + build APK | Không lỗi analyze/test; APK tạo được |  |  |
| TECH-04 | Load test 200 request/concurrency 20 | Không lỗi; p95 trong ngưỡng nội bộ 1500 ms |  |  |
| TECH-05 | Backup và restore vào DB tạm | Restore thành công, không ghi đè DB chính |  |  |
| TECH-06 | Kiểm tra CORS origin lạ | Origin lạ không được cấp CORS credential |  |  |
| TECH-07 | Quét repository | Không có secret, `.env`, file build/tạm hoặc dữ liệu cá nhân |  |  |

## 14. Điều kiện chấp nhận

- Không còn lỗi Critical/High mở trong luồng đăng nhập, checkout/return, công nợ, QR hoặc phân quyền.
- Tất cả ca bắt buộc có trạng thái PASS hoặc có quyết định chấp nhận rủi ro được ghi rõ.
- Ca BLOCKED phải ghi điều kiện cần bổ sung và người chịu trách nhiệm.
- Quality gate cuối đạt trên commit bàn giao.
- Word/PPT chỉ dùng ảnh và số liệu từ đúng commit đã nghiệm thu.
