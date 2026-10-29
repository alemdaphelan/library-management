# B13 — Thống kê tài chính, nhập mới và phần chờ xử lý sách cũ

Phạm vi bàn giao: **Backend tài chính/nhập mới hoàn tất; số thực thi thanh lý/hủy CHỜ ADMIN**. Người dùng đã xác nhận ở B09–B10 rằng Admin chưa làm và yêu cầu ghi rõ phần chờ. Không tạo quyền quyết định/thực thi hoặc giả lập sự kiện xử lý trong B13. Web mới thuộc B15.

## API và quyền

Base `/dashboard/analytics`, JSON trực tiếp, JWT ADMIN/LIBRARIAN, `Cache-Control: no-store`. STUDENT/không đăng nhập bị chặn. Mỗi GET dùng transaction read-only REPEATABLE_READ; GET không thu tiền, cấn cọc, tính phạt, sửa wallet/copy hoặc tạo phiếu nhập.

| GET | Tham số | Nội dung |
|---|---|---|
| `/finance` | from, until, period=MONTH | totals, buckets, feeTypes, currentDebt, coverage, historicalDebtAvailable=false |
| `/finance/entries` | from, until, kind tùy chọn, page=0, size=25 | trang sự kiện dùng để đối chiếu chỉ số |
| `/finance/payments/{id}/allocations` | page=0, size=25 | dòng phân bổ của PAYMENT, eligible theo quy tắc báo cáo; không tìm được PAYMENT trả 404 |
| `/stock-movements` | from, until, period=MONTH | totals nhập, buckets, coverage và trạng thái phần thanh lý/hủy |
| `/stock-movements/receipts` | from, until, page=0, size=25 | phiếu hoàn tất có ngày trong kỳ, quantities và link GET `/inventory/receipts/{id}` |

Ngày `YYYY-MM-DD`, đầu `from` bao gồm/cuối `until` loại trừ, timezone `Asia/Ho_Chi_Minh`; mặc định từ đầu năm hiện tại đến đầu ngày mai. Khoảng tối đa 50 năm, năm 1–9999. `period=MONTH|QUARTER|YEAR`; bucket đầu/cuối cắt theo khoảng chọn, bucket không phát sinh bằng 0. Chỉ lấy sự kiện có thời điểm không sau snapshotAt. `page>=0`, `size=1..100`, ngoài dữ liệu trả trang rỗng; sai tham số trả 400. Các GET riêng là snapshot riêng.

## Tài chính — tách phát sinh và thanh toán

Phạm vi là nghĩa vụ thư viện của **tất cả người dùng có ledger**, không riêng STUDENT như báo cáo B04. Đơn vị tiền là đồng, dùng BigDecimal/decimal, không cộng tròn qua số thực Java. B13 là báo cáo ledger hiện có, không thay thế quy trình nghiệp vụ phạt/thu tiền.

`totals` và mỗi bucket có cùng các trường:

| Trường | Nguồn và thời điểm |
|---|---|
| chargesAccrued | Tổng amount dương của FINE/REPAIR_FEE/COMPENSATION/TECHNICAL_FEE theo createdAt; việc đã trả/được miễn sau đó không xóa phát sinh gốc |
| moneyReceived | `-amount` của PAYMENT âm, COMPLETED, theo **paidAt** của receipt |
| cashReceived / bankReceived / vnpayReceived / otherReceived | Phân rã moneyReceived theo CASH/BANK_TRANSFER/VNPAY/phương thức khác hoặc thiếu |
| allocatedPayments | Phân bổ dương vào bốn loại charge nêu trên, cùng user, từ PAYMENT âm COMPLETED; vào kỳ **paidAt của PAYMENT** |
| depositApplied | Bằng chứng cọc đã cấn, theo depositAppliedAt đã chụp lúc tạo charge |
| waived | `-amount` của WAIVER_ADJUSTMENT COMPLETED theo createdAt |
| adjusted | `-amount` của ADJUSTMENT COMPLETED theo createdAt; có dấu, bút toán amount dương làm số điều chỉnh giảm nợ âm |
| depositTopups | `-amount` của DEPOSIT_TOPUP âm COMPLETED theo paidAt; tách riêng khỏi moneyReceived |

`feeTypes` phân rã chargesAccrued theo cả bốn loại phí. Không cộng charge + PAYMENT thành tiền thu. Cấn cọc đã nhận trước đây không phải tiền mặt/chuyển khoản vừa thu; nạp cọc cũng không phải thu phạt. Miễn/điều chỉnh giảm nợ không phải khoản tiền đã nhận. Không lấy paidAmount của charge làm doanh thu, vì nó gồm cả cọc, thanh toán và miễn giảm. Không cộng thêm lateFineAccrued của LoanDetail hoặc tổng PenaltyCase vì chúng đã có dòng charge.

PAYMENT là receipt tổng, được đếm **một lần** dù có nhiều allocation. allocatedPayments là chiều đối chiếu khác của cùng receipt, không cộng vào moneyReceived. Các allocation sai nguồn/cùng người/loại phí/trạng thái/số tiền được loại khỏi chỉ số và báo invalidAllocationRows; phân bổ lặp hoặc thiếu gây chênh lệch receiptMinusEligibleAllocations. Không tự sửa ledger khi GET.

Các transaction loại khác như REFUND hoặc trạng thái không có hợp đồng receipt trên không được tự biến thành thu phạt hay bù trừ vào chỉ số. Báo cáo không cung cấp chỉ tiêu dòng tiền ròng sau refund. Bút toán điều chỉnh có dấu được đọc để phản ánh dữ liệu, không bổ sung API tạo bút toán đảo trong B13.

### Dư nợ và coverage

`currentDebt` luôn là **snapshot hiện tại**, độc lập kỳ lọc:

- ledgerOutstanding: tổng max(amount-paidAmount,0) của charge đang OUTSTANDING/PARTIALLY_PAID/PARTIALLY_ADJUSTED/PARTIALLY_WAIVED.
- walletDebt: tổng wallets.totalDebt hiện tại; walletMinusLedger là chênh lệch cần đối chiếu.
- satisfiedIncludingReductions: tổng paidAmount của charge, gồm cả miễn/điều chỉnh; không gắn nhãn tiền thực thu.

`historicalDebtAvailable=false`: không có số dư đầu/cuối kỳ lịch sử đáng tin cậy. Không dùng wallet hiện tại hoặc lấy phát sinh kỳ trừ tiền thu kỳ để suy dư nợ cuối kỳ. Thu trong kỳ có thể trả cho charge kỳ trước.

Coverage là toàn bộ dữ liệu đang lưu, không chỉ kỳ lọc:

- chargesWithoutIssueTime: charge thiếu createdAt.
- chargesWithoutDepositEvidence: charge thiếu thông tin cọc ban đầu.
- depositApplicationsWithoutTime: biết số cọc dương nhưng thiếu thời điểm cấn.
- paymentsWithoutPaidTime: PAYMENT/DEPOSIT_TOPUP hợp lệ nhưng thiếu paidAt.
- reductionsWithoutIssueTime: miễn/điều chỉnh thiếu createdAt.
- paymentAllocationMismatches và receiptMinusEligibleAllocations: số receipt lệch và tổng tiền receipt trừ allocations hợp lệ, có dấu.
- invalidAllocationRows: allocation sai điều kiện nguồn (kể cả thiếu nguồn).

Thiếu ngày không vào bucket/tổng kỳ. Thiếu nguồn cọc không được suy ra từ phương thức hiện tại; đọc coverage trước khi kết luận lịch sử đầy đủ.

### Đối chiếu ví dụ

| Thao tác | Phát sinh gốc | Cọc đã cấn | Tiền mới thu | Nợ hiện tại |
|---|---:|---:|---:|---:|
| Charge 200.000, cọc đang có 50.000 | 200.000 | 50.000 | 0 | 150.000 |
| Thu tiền mặt 60.000 | 200.000 | 50.000 | 60.000 | 90.000 |
| Thu chuyển khoản 90.000 | 200.000 | 50.000 | 150.000 | 0 |

Nếu charge/cọc ở tháng 1, receipt ở tháng 2/3 thì chúng ở đúng các tháng tương ứng. Đổi paymentMethod/paidAt của charge sau các lần thu không đổi chứng cứ cọc ban đầu. Retry receipt cùng idempotency key của luồng thu tiền hiện có không thêm PAYMENT/allocations.

`/finance/entries` có `kind=CHARGE|PAYMENT|DEPOSIT_APPLICATION|WAIVER|ADJUSTMENT|DEPOSIT_TOPUP`, transactionId, at, feeType, amount, method, receiptNumber, parentTransactionId. CHARGE và DEPOSIT_APPLICATION có thể cùng transactionId nhưng là hai chỉ tiêu khác nhau. **Không cộng amount của mọi kind thành một tổng doanh thu.** Trang sắp theo at/transactionId/kind ổn định; lọc kind để đối chiếu tổng tương ứng. allocations phân trang riêng, có eligible; không trả proofDocumentUrl hoặc dữ liệu storage nội bộ.

API Dashboard cũ giữ field/DTO `fineCollected` nhưng B13 sửa nghĩa thành **tiền PAYMENT thực nhận lũy kế có paidAt đến hiện tại**, gồm bốn loại nghĩa vụ qua receipt, không gồm cấn/nạp cọc hoặc charge phát sinh. Phương thức khác vẫn là tiền đã nhận và hiện riêng trong API mới. Khi làm B15 cần cập nhật nhãn/help của card để diễn giải đúng. Không giữ lỗi cũ cộng toàn bộ FINE charge thành “đã thu”.

## Nhập mới

Chỉ lấy goods_receipts.status=COMPLETED có **completedAt** trong kỳ. Dòng hợp lệ cần bookId và quantity>0; đếm từ receipt_details để giữ số liệu ngay cả khi copy sau này mất/xóa. Không suy số nhập từ số copy hiện còn hoặc Book.createdAt.

- completedReceipts: số phiếu hoàn tất có ngày trong kỳ.
- importedTitles: số bookId distinct được nhập trong kỳ, không phải số bản sao và không nhất thiết là đầu mới lần đầu.
- firstRecordedImportTitles: đầu có lần nhập hoàn tất sớm nhất được ghi nhận nằm trong kỳ, và không có phiếu hoàn tất thiếu ngày của đầu đó. Đây là **lần nhập đầu trong lịch sử phiếu đang lưu**, không chứng minh thời điểm đầu sách được tạo hoặc chưa có tồn kho trước khi hệ thống ghi phiếu.
- importedCopies: tổng quantity hợp lệ. Nhiều dòng hoặc nhiều đợt cùng đầu không nhân số đầu distinct.

importedTitles không cộng được từ từng bucket nếu cùng đầu nhập nhiều tháng; tổng kỳ dùng distinct độc lập. firstRecordedImportTitles chỉ thuộc bucket của lần đầu đủ chứng cứ. Phiếu PENDING/CANCELLED không vào số hoàn tất; phiếu có ngày ngoài kỳ/tương lai không vào tổng kỳ.

Coverage: completedReceiptsWithoutTime (phiếu hoàn tất thiếu ngày), invalidDetailRows (dòng hoàn tất thiếu bookId/quantity hoặc quantity<=0), titlesWithUndatedImport (đầu có dòng quantity>0 trong phiếu hoàn tất thiếu ngày). Dữ liệu thiếu ngày giữ hiển thị giới hạn, không lấy createdAt thay thế.

## Thanh lý/hủy chờ Admin

`disposals.status=WAITING_ADMIN_WORKFLOW`, có reason; liquidatedCopies/destroyedCopies là **null**, cả ở tổng lẫn bucket. Null nghĩa là chưa có nguồn thực thi để báo cáo, không phải 0 bản đã xử lý. Không đọc DRAFT/SUBMITTED/CANCELLED của disposal_proposals hay copy.status RETIRED để tạo số hoàn tất. Không thêm API ghi kết quả thay phần Admin.

Khi Admin có contract, cần schema/event ID duy nhất, thời điểm thực thi có hiệu lực, barcode/book snapshot, phương án, actor và liên kết quyết định. Sau đó bổ sung adapter tổng hợp có kiểm trùng/ngoài kỳ/lịch sử, tests và migration mới. B13 hiện chưa nghiệm thu phần chỉ số thực thi này.

## Schema, kiểm chứng và triển khai

Migration mới **35-finance-import-analytics-evidence.yaml**:

- transactions.deposit_applied_amount/deposit_applied_at nullable; LateFineService/PenaltyCaseService chụp một lần lúc tạo charge, gồm cả amount=0 khi không cấn. DebtLedgerService không ghi đè hai trường này khi thu/miễn/điều chỉnh.
- Chỉ backfill deposit amount=0 khi charge cũ paidAmount=0. Positive/null paidAmount cũ giữ unknown; không suy cọc còn dư bằng phép trừ thiếu chứng cứ.
- goods_receipts.completed_at nullable; InventoryService ghi sau khi toàn bộ dòng/bản sao được tạo thành công, trong cùng transaction. Phiếu cũ COMPLETED giữ completedAt=null vì chưa có lịch sử hoàn tất.
- Rollback bỏ ba cột mới, giữ ledger/phiếu nguồn. Không sửa migration 01–34, không tạo index nếu chưa có bằng chứng query-plan cần thiết.

Implementation: FinanceStockAnalyticsController/Service, FinanceAnalyticsRepository, ImportAnalyticsRepository, DTO FinanceStockAnalytics trong modules/dashboard. Truy vấn tổng hợp/phân trang ở DB; chỉ số tổng kỳ và bucket độc lập, không tải toàn ledger lên service/UI. Chưa load-test production lớn; các truy vấn nhiều nhánh có thể quét toàn nguồn.

Tests có DB PostgreSQL/Liquibase thật, HTTP/JWT và luồng nghiệp vụ thực tế. Lệnh/kết quả lượt cuối ghi trong `Docs/BAN_GIAO_BATCH_THU_THU.md`. Không thay Web/Flutter, không deploy/áp migration DB người dùng, không commit/push. Khi triển khai cần áp 35 cùng backend mới; rollback code đồng bộ với rollback cột. Dừng trước B14.
