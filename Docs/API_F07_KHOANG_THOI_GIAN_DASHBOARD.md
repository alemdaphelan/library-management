# F07 — Khoảng thời gian Dashboard

Ngày cập nhật: 04/11/2026. Áp dụng backend mượn/trả, tài chính và nhập kho; giao diện chọn kỳ/nhãn giờ đã được đồng bộ trong F08; hướng dẫn tại [Dashboard Web](HUONG_DAN_B14_B15_DASHBOARD.md). Web gửi from/until/period chung cho báo cáo và danh sách chi tiết, không trộn date với khoảng ngày.

Ba GET staff giữ JWT ADMIN/LIBRARIAN, JSON trực tiếp và `Cache-Control: no-store`:

- `/dashboard/analytics/circulation`
- `/dashboard/analytics/finance`
- `/dashboard/analytics/stock-movements`

## Chọn kỳ và độ chi tiết

`date=YYYY-MM-DD&period=DAY|WEEK|MONTH|YEAR` chọn trọn kỳ chứa ngày tham chiếu. Tuần từ thứ Hai đến đầu thứ Hai kế tiếp, kể cả giao năm. Mặc định period=MONTH.

| period | Khoảng kỳ khi dùng date | range.bucketUnit | Các cột |
|---|---|---|---|
| DAY | 00:00 ngày chọn → 00:00 ngày sau | HOUR | 24 giờ, 00–01 đến 23–24 |
| WEEK | Thứ Hai → đầu thứ Hai kế tiếp | DAY | 7 ngày |
| MONTH | Đầu tháng → đầu tháng sau | DAY | 28/29/30/31 ngày |
| YEAR | 01/01 → 01/01 năm sau | MONTH | 12 tháng |

`QUARTER` vẫn là tùy chọn nâng cao cũ: `date` chọn trọn quý, bucketUnit=QUARTER, một cột mỗi quý; không đổi sang độ chi tiết khác trong F07.

Ví dụ `?date=2026-01-04&period=WEEK` trả range từ 2025-12-29 đến 2026-01-05, 7 cột. `?date=2024-02-29&period=MONTH` có 29 cột.

Vẫn hỗ trợ `from=YYYY-MM-DD&until=YYYY-MM-DD&period=...` cho khoảng tùy chọn; đầu bao gồm, cuối loại trừ, cột đầu/cuối cắt theo khoảng chọn. **period nay là chế độ xem**, không còn đồng nghĩa độ rộng một cột. Không truyền `date` cùng `from` hoặc `until` (400). Không truyền ngày nào vẫn giữ mặc định từ đầu năm đến đầu ngày mai. Khoảng tối đa 50 năm, năm 1–9999 và **tối đa 1000 cột thực tế**, vì vậy khoảng DAY dài hơn 41 ngày bị từ chối. Sai ngày/enum/khoảng trả 400. Giới hạn cột chỉ áp biểu đồ; xếp hạng và danh sách chi tiết phân trang vẫn hỗ trợ khoảng tối đa 50 năm.

## Hợp đồng JSON thay đổi

`range` bổ sung `bucketUnit`. `range.from/until` vẫn là ngày. Mọi `buckets[].start/until` chuyển từ ngày sang **local datetime ISO**, ví dụ:

```json
{
  "start": "2026-01-01T23:00:00",
  "until": "2026-01-02T00:00:00",
  "counts": { "checkoutItems": 1, "physicalReturnItems": 0, "checkoutTickets": 1, "physicalReturnTickets": 0 }
}
```

Timezone luôn `Asia/Ho_Chi_Minh`; timestamp nghiệp vụ hiện có trong DB là local timestamp, không coi là UTC để cộng thêm 7 giờ. PostgreSQL xử lý timestamp không timezone; không phụ thuộc timezone phiên DB. Java xác định ngày hiện tại/snapshot bằng zone Việt Nam. Cột trống giữ 0, kể cả cột chưa đến trong kỳ hiện tại; sự kiện sau snapshotAt bị loại. Mốc cuối 24h được biểu diễn bằng 00:00 ngày sau.

Thời điểm nguồn giữ nguyên: checkout_at, physical_return_at; charge created_at, receipt PAYMENT/DEPOSIT_TOPUP paid_at, deposit_applied_at, waiver/adjustment created_at; goods_receipts.completed_at. Không dùng ngày tạo phiếu mượn thay giao sách, hoặc ngày thanh toán bồi thường thay nhận sách vật lý.

## Đối chiếu tổng

Tổng lượt giao/nhận **cuốn**, mọi trường Money (kể cả điều chỉnh có dấu), số phiếu nhập và số bản nhập bằng tổng các cột. Khoảng nửa mở giúp sự kiện đúng 01:00 hoặc đầu ngày/tháng chỉ vào một cột/kỳ.

Các chỉ số distinct có thể lặp giữa cột: một phiếu trả sách nhiều giờ/ngày chỉ tính một lần trong tổng kỳ; một đầu sách nhập nhiều đợt chỉ tính một đầu sách trong tổng kỳ. Không cộng checkoutTickets/physicalReturnTickets/importedTitles của các cột để suy tổng distinct. firstRecordedImportTitles dùng cùng bằng chứng lịch sử cũ, không suy từ ngày tạo catalog.

Nợ/tồn/quá hạn hiện tại vẫn là snapshot hiện tại, không tạo lịch sử giả. Coverage thiếu chứng cứ giữ nguyên; số thanh lý/hủy vẫn null/WAITING_ADMIN_WORKFLOW. GET read-only REPEATABLE_READ, không sửa dữ liệu hay ghi tiền. Không cần migration.

Source: dashboard `AnalyticsBuckets`, DTO CirculationAnalytics/FinanceStockAnalytics, ba repositories, hai services/controllers. Kiểm thử tập trung: `AnalyticsCalendarIntegrationTest`, `DashboardAnalyticsIntegrationTest`, `FinanceStockAnalyticsIntegrationTest`. MockMvc standalone kiểm tra binding/JSON với service và PostgreSQL thật; không phải kiểm thử xác thực HTTP/JWT mới.
