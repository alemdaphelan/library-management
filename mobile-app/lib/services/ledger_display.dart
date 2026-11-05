/// Ledger debits/credits are accounting signs, not changes to the wallet shown
/// above the list. Keep amounts intact and explain deposit credits separately.
class LedgerDisplay {
  static bool isDepositTopUp(Map<String, dynamic> entry) =>
      entry['type']?.toString().trim().toUpperCase() == 'DEPOSIT_TOPUP';

  static bool isOpeningDeposit(Map<String, dynamic> entry) =>
      isDepositTopUp(entry) &&
      (entry['dedupKey']?.toString().startsWith('UAT-DEPOSIT-') == true ||
          (entry['reason']?.toString().startsWith('Thiết lập tiền thế chân') ==
                  true &&
              entry['reason']?.toString().contains(
                    'tài khoản kiểm thử Mobile/UAT',
                  ) ==
                  true));

  static String title(Map<String, dynamic> entry) {
    if (isOpeningDeposit(entry)) return 'Số dư thế chân ban đầu';
    if (isDepositTopUp(entry)) return 'Nộp tiền thế chân';
    return entry['reason']?.toString() ?? 'Giao dịch thư viện';
  }

  static String? explanation(Map<String, dynamic> entry) =>
      isOpeningDeposit(entry)
      ? 'Khoản khởi tạo số dư thế chân cho tài khoản kiểm thử, không phải một lần trừ tiền mới. Số dư còn lại ở đầu màn hình đã tính các khoản đã sử dụng.'
      : isDepositTopUp(entry)
      ? 'Khoản nộp vào quỹ thế chân, không phải khoản phạt. Số dư còn lại ở đầu màn hình đã tính các khoản đã sử dụng.'
      : null;

  static String typeLabel(Object? value) =>
      switch (value?.toString().toUpperCase()) {
        'DEPOSIT_TOPUP' => 'Nộp tiền thế chân',
        'DEPOSIT_ADJUSTMENT' => 'Điều chỉnh tiền thế chân',
        'REPAIR_FEE' => 'Phí sửa chữa',
        'FINE' => 'Khoản phạt',
        'PAYMENT' => 'Thanh toán',
        'COMPENSATION' => 'Đền bù tài liệu',
        _ => 'Giao dịch thư viện',
      };

  static String statusLabel(Object? value) =>
      switch (value?.toString().toUpperCase()) {
        'PAID' => 'Đã thanh toán',
        'COMPLETED' => 'Hoàn tất',
        'OUTSTANDING' || 'UNPAID' => 'Chưa thanh toán',
        'PARTIALLY_PAID' => 'Thanh toán một phần',
        'CANCELLED' => 'Đã hủy',
        'WAIVED' => 'Đã miễn giảm',
        _ => value?.toString() ?? 'Chưa cập nhật',
      };
}
