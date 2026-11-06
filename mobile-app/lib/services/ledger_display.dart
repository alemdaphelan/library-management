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

  static bool isCredit(Object? value) {
    final type = value?.toString().trim().toUpperCase();
    return type == 'DEPOSIT_TOPUP' ||
        type == 'PAYMENT' ||
        type == 'WAIVER' ||
        type == 'WAIVER_ADJUSTMENT';
  }

  static bool isDebit(Object? value) {
    final type = value?.toString().trim().toUpperCase();
    return type == 'FINE' ||
        type == 'LATE_FINE' ||
        type == 'OVERDUE_FINE' ||
        type == 'REPAIR_FEE' ||
        type == 'REPAIR' ||
        type == 'COMPENSATION' ||
        type == 'TECHNICAL_FEE';
  }

  static bool isPositiveImpact(dynamic rawAmount, Object? type) {
    final t = type?.toString().trim().toUpperCase();
    if (t == 'DEPOSIT_TOPUP' || t == 'PAYMENT') return true;
    if (isDebit(t)) return false;
    final val = num.tryParse(rawAmount?.toString() ?? '0') ?? 0;
    // In ledger accounting: negative numbers are credits (money deposited or debt reduced)
    return val < 0;
  }

  static String formatSignedAmount(dynamic rawAmount, Object? type) {
    final val = num.tryParse(rawAmount?.toString() ?? '');
    if (val == null) return '0 đ';
    final absVal = val.abs();
    final formatted = absVal
        .toStringAsFixed(0)
        .replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (Match m) => '${m[1]}.',
        );

    final t = type?.toString().trim().toUpperCase();
    if (t == 'DEPOSIT_TOPUP' || t == 'PAYMENT') {
      return '+$formatted đ';
    } else if (isDebit(t)) {
      return '-$formatted đ';
    } else if (val < 0) {
      return '+$formatted đ';
    } else if (val > 0) {
      return '-$formatted đ';
    }
    return '$formatted đ';
  }

  static String typeLabel(Object? value) =>
      switch (value?.toString().toUpperCase()) {
        'DEPOSIT_TOPUP' => 'Nộp tiền thế chân',
        'DEPOSIT_ADJUSTMENT' => 'Điều chỉnh tiền thế chân',
        'REPAIR_FEE' || 'REPAIR' => 'Phí sửa chữa',
        'FINE' || 'LATE_FINE' || 'OVERDUE_FINE' => 'Khoản phạt quá hạn',
        'PAYMENT' => 'Thanh toán',
        'COMPENSATION' => 'Đền bù tài liệu',
        'TECHNICAL_FEE' => 'Phí xử lý kỹ thuật',
        'WAIVER' || 'WAIVER_ADJUSTMENT' => 'Miễn giảm',
        'ADJUSTMENT' => 'Điều chỉnh giao dịch',
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
        'PENDING_REVIEW' => 'Đang chờ duyệt',
        'SETTLED' => 'Đã quyết toán',
        _ => value?.toString() ?? 'Chưa cập nhật',
      };
}
