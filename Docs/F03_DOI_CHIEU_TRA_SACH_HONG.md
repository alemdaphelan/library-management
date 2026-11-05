# F03 — đối chiếu lượt trả sách hỏng

Nhận sách hỏng tại quầy kết thúc việc giữ sách, kể cả hồ sơ phạt/chờ thay thế còn mở. Chi tiết giữ DAMAGED; bản sao vẫn MAINTENANCE hoặc LOST (hỏng nặng theo chính sách đền bù), không tự đưa ra cho mượn. Thanh toán sau đó không đổi ngày nhận lại. LOST chỉ có SETTLED khi giải quyết hồ sơ, không được tính là lượt nhận trả vật lý.

Migration `36-damaged-physical-return.yaml` được includeAll của Liquibase nạp khi backend cập nhật khởi động. Nó chỉ dùng `physical_return_at` đã có: đồng bộ `return_date`, đóng phiếu đã hoàn tất mọi cuốn và thêm lịch sử sửa trạng thái với lý do F03. Phiếu còn cuốn BORROWING/OVERDUE/LOST chưa nhận không bị đóng. Không sửa tiền, kho, hồ sơ phạt, timestamp nhận gốc hoặc JSON biên nhận cũ. Biên nhận retry vẫn là snapshot tại lần xử lý ban đầu.

Đây là sửa dữ liệu có nguồn chứng minh; không có rollback tự động. Sao lưu DB trước khi triển khai; nếu phải đảo sửa dữ liệu, đối chiếu backup và lịch sử F03. Không chạy lại migration cũ bằng cách sửa checksum.

Đọc dữ liệu cần xác minh bằng SQL sau (không sửa dữ liệu):

```sql
SELECT d.detail_id,d.ticket_id,d.barcode,d.return_status,d.return_date,
       d.physical_return_at,d.physical_return_time_source,
       p.case_id,p.incident_type,p.status AS case_status,p.reported_at
FROM loan_details d
LEFT JOIN penalty_cases p ON p.loan_detail_id=d.detail_id
WHERE d.return_status IN ('DAMAGED','LOST','SETTLED')
  AND d.physical_return_at IS NULL
ORDER BY d.ticket_id,d.detail_id,p.case_id;
```

Với dòng thiếu mốc vật lý, thủ thư phải kiểm chứng biên nhận quầy/chứng cứ nhận sách và đúng detail_id/barcode trước khi sửa có lịch sử. Ngày báo sự cố, ngày thanh toán, return_date của SETTLED hay trạng thái DAMAGED riêng lẻ không chứng minh đã nhận sách. Không suy đoán sách/mã vạch từ offset dữ liệu. Nút gia hạn luôn ẩn cho DAMAGED/LOST/SETTLED/RETURNED dù dữ liệu lịch sử chưa được xác minh.

Đối chiếu chỉ đọc DB local trong F03 tìm thấy detail 47 / ticket 33: DAMAGED, return_date null, physical_return_at `2026-10-31 10:22:53.499292`, nguồn LIVE_DAMAGED_RECEIPT; ticket BORROWING. Đây là trường hợp migration có đủ chứng cứ tự đối chiếu. Trạng thái triển khai/migration thực tế được ghi tại checkpoint F03 trong `KE_HOACH_FIX_LOI_V2.md`; không dùng kết quả test container để khẳng định DB local đã được cập nhật.
