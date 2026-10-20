# Quy định thanh toán, khóa thẻ và miễn giảm

Thủ thư ghi nhận thanh toán bằng tiền mặt hoặc chuyển khoản. Phiếu thu phải tách rõ tiền phạt quá hạn, đền bù, sửa chữa và phí kỹ thuật; có biên nhận và người xác nhận thu tiền.

Sinh viên được thanh toán thành nhiều đợt. Quyền mượn, đặt giữ và gia hạn chỉ được mở lại khi tổng các khoản nợ đến hạn bằng 0 và các tài liệu phải trả đã được xử lý.

Giao dịch đã xuất biên nhận không được xóa. Nếu nhập sai, thủ thư tạo yêu cầu điều chỉnh, Admin phê duyệt và hệ thống sinh giao dịch điều chỉnh ngược có lý do để giữ audit trail.

Hệ thống chỉ đếm vi phạm mượn quá hạn. Nhiều tài liệu cùng quá hạn trong một phiếu và một đợt chỉ tính là một lần vi phạm. Đủ 3 lần trong một năm học thì khóa quyền mượn, đặt giữ và gia hạn trong 3 tháng theo lịch. Người bị khóa vẫn đăng nhập để xem nợ, nhận thông báo và thực hiện trả hoặc đền bù. Hết 3 tháng chỉ mở quyền khi đã trả tài liệu và không còn nợ; bộ đếm đặt lại vào đầu năm học mới.

Miễn giảm chỉ xem xét cho thiên tai, hỏa hoạn ngoài ý muốn hoặc trường hợp đặc biệt có giấy tờ. Thủ thư tiếp nhận hồ sơ, Admin ghi nhận quyết định bên ngoài. Trong trạng thái PENDING_REVIEW, số tiền được đóng băng, email nhắc nợ tạm dừng nhưng quyền mượn, đặt giữ và gia hạn vẫn bị khóa. Khi được duyệt, hệ thống sinh khoản điều chỉnh công nợ, không sửa hoặc xóa giao dịch cũ.
