import 'package:flutter/foundation.dart';
import 'api_service.dart';
import 'library_policy_service.dart';

class LoanService {
  static String _status(Object? value) {
    final status = value?.toString().trim().toUpperCase() ?? '';
    return switch (status) {
      'CHECKED_OUT' || 'ACTIVE' => 'BORROWING',
      'COMPLETED' => 'RETURNED',
      'CANCELED' => 'CANCELLED',
      _ => status,
    };
  }

  static String _itemStatus(Map<String, dynamic> item) {
    final raw = _status(item['itemStatus']);
    return raw.isEmpty ? _status(item['status']) : raw;
  }

  static bool _received(Map<String, dynamic> item) =>
      item['returnDate'] != null || item['physicalReturnAt'] != null;

  static bool isBorrowingItem(Map<String, dynamic> item) =>
      !_received(item) &&
      const {'BORROWING', 'OVERDUE'}.contains(_status(item['status'])) &&
      const {'BORROWING', 'OVERDUE'}.contains(_itemStatus(item));

  static bool isWaitingPickupItem(Map<String, dynamic> item) =>
      !_received(item) &&
      const {'PENDING', 'RESERVED'}.contains(_status(item['status'])) &&
      const {'PENDING', 'RESERVED'}.contains(_itemStatus(item));

  /// Tăng mỗi khi có thao tác đặt giữ, hủy phiếu hoặc gia hạn thành công
  /// để các màn hình Trang chủ, Tra cứu, Chi tiết và Cá nhân tự đồng bộ.
  static final ValueNotifier<int> loanRevision = ValueNotifier<int>(0);

  static void notifyLoansChanged() {
    loanRevision.value++;
  }

  /// QR nhận sách chỉ có giá trị trong lúc phiếu còn chờ giao tại quầy.
  /// Sau khi thủ thư checkout, trạng thái BORROWING/OVERDUE tuyệt đối không
  /// được tiếp tục hiển thị QR nhận sách cho sinh viên.
  static bool canShowPickupQr(Map<String, dynamic> loan) {
    return isWaitingPickupItem(loan);
  }

  static String historyLabel(Map<String, dynamic> loan) {
    final status = _status(loan['status']);
    final item = _itemStatus(loan);
    if (status == 'CANCELLED' || item == 'CANCELLED') return 'Đã hủy yêu cầu';
    if (status == 'REJECTED') return 'Bị từ chối';
    if (item == 'DAMAGED') {
      return _received(loan) ? 'Đã nhận sách hư hỏng' : 'Đã lập hồ sơ hư hỏng';
    }
    if (item == 'LOST') return 'Đã lập hồ sơ mất sách';
    if (item == 'SETTLED') return 'Đã hoàn tất xử lý';
    if (_received(loan) || item == 'RETURNED' || status == 'RETURNED') {
      return 'Đã trả sách';
    }
    return 'Cần đối chiếu trạng thái';
  }

  /// QR trả sách chỉ xuất hiện cho tài liệu đang mượn hoặc đã quá hạn và
  /// chưa được thủ thư ghi nhận trả.
  static bool canShowReturnQr(Map<String, dynamic> loan) {
    return loan['returnable'] != false && isBorrowingItem(loan);
  }

  static Future<List<Map<String, dynamic>>> getLoans({
    bool activeOnly = false,
    int? policyLoanDays,
  }) async {
    int? resolvedLoanDays = policyLoanDays;
    if (resolvedLoanDays == null) {
      try {
        final policy = await LibraryPolicyService.getPolicy();
        resolvedLoanDays = policy.homeLoanDays;
      } catch (_) {}
    }

    final data = await ApiService.get('/loans/my-loans');
    final loans = (data as List<dynamic>)
        .map(
          (item) => normalizeLoan(
            Map<String, dynamic>.from(item as Map),
            loanDays: resolvedLoanDays,
          ),
        )
        .toList();
    if (!activeOnly) return loans;
    return loans.where(isOpenLoanItem).toList();
  }

  static bool isOpenLoanItem(Map<String, dynamic> item) {
    return isWaitingPickupItem(item) || isBorrowingItem(item);
  }

  static Future<Map<String, dynamic>> getEligibility({int? bookId}) async {
    final path = bookId == null
        ? '/loans/my-eligibility'
        : '/loans/my-eligibility?bookId=$bookId';
    final data = await ApiService.get(path);
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<List<Map<String, dynamic>>> getMyWaitlist() async {
    final data = await ApiService.get('/waitlist/my');
    if (data is! List) return const <Map<String, dynamic>>[];
    return data.map((item) => Map<String, dynamic>.from(item as Map)).toList();
  }

  static Future<Map<String, dynamic>> reserveBook(
    int bookId, {
    String ticketType = 'BORROW',
    String? note,
  }) async {
    // Gửi ngày đăng ký hiện tại; Backend sẽ tự dời hạn nhận sang ngày mở cửa
    // gần nhất nếu đăng ký sau giờ đóng cửa hoặc vào ngày thư viện nghỉ.
    final pickupDate = DateTime.now();
    final data = await ApiService.post('/loans/reserve', {
      'bookId': bookId,
      'ticketType': ticketType,
      'pickupDate': _dateOnly(pickupDate),
      if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
    });
    final result = Map<String, dynamic>.from(data as Map);
    if (result['success'] == false) {
      throw ApiException(
        409,
        result['message']?.toString() ??
            'Không thể đăng ký mượn hoặc đặt giữ tài liệu này.',
      );
    }
    notifyLoansChanged();
    return result;
  }

  static Future<Map<String, dynamic>> renewLoan(
    int ticketId,
    int detailId,
  ) async {
    final data = await ApiService.post(
      '/loans/renew/$ticketId/items/$detailId',
      {},
    );
    final result = Map<String, dynamic>.from(data as Map);
    notifyLoansChanged();
    return result;
  }

  static Future<void> cancelReservation(int ticketId) async {
    try {
      await ApiService.post('/loans/cancel/$ticketId', {});
      notifyLoansChanged();
    } catch (error) {
      // Nếu Backend đã hủy thành công nhưng phản hồi mạng/giao diện gặp lỗi,
      // kiểm tra lại danh sách phiếu từ Backend để đồng bộ đúng trạng thái.
      try {
        final loans = await getLoans();
        final matching = loans
            .where((item) => (item['ticketId'] as num?)?.toInt() == ticketId)
            .toList();
        if (matching.isNotEmpty &&
            matching.every(
              (item) =>
                  item['status'] == 'CANCELLED' ||
                  item['itemStatus'] == 'CANCELLED',
            )) {
          notifyLoansChanged();
          return;
        }
      } catch (_) {}
      rethrow;
    }
  }

  static Map<String, dynamic> normalizeLoan(
    Map<String, dynamic> loan, {
    int? loanDays,
  }) {
    final dueDate = _parseDate(loan['dueDate']);
    final pickupDeadline = _parseDate(
      loan['pickupDeadline'] ?? loan['pickupDate'],
    );
    final createdAt = _parseDate(loan['createdAt']);
    final returnDate = _parseDate(loan['returnDate']);
    final rawStatus = _status(loan['status'] ?? 'PENDING');
    final itemStatus = _itemStatus(loan);

    final int? daysRemaining = loan['daysRemaining'] is num
        ? (loan['daysRemaining'] as num).toInt()
        : dueDate?.difference(DateTime.now()).inDays;

    final String effectiveStatus =
        (rawStatus == 'BORROWING' &&
            isBorrowingItem(loan) &&
            ((daysRemaining != null && daysRemaining < 0) ||
                (dueDate != null && dueDate.isBefore(DateTime.now()))))
        ? 'OVERDUE'
        : rawStatus;

    final int? effectiveLoanDays =
        loanDays ??
        ((createdAt != null && dueDate != null)
            ? dueDate.difference(createdAt).inDays
            : null);

    final double progress =
        (daysRemaining == null ||
            effectiveLoanDays == null ||
            effectiveLoanDays <= 0)
        ? 0.0
        : ((effectiveLoanDays - daysRemaining) / effectiveLoanDays).clamp(
            0.0,
            1.0,
          );

    final bool isWaitingPickup = isWaitingPickupItem(loan);
    final bool isBorrowing = isBorrowingItem(loan);

    final bool isHistory = !isWaitingPickup && !isBorrowing;

    return {
      ...loan,
      'status': effectiveStatus,
      'itemStatus': itemStatus,
      'title': loan['bookTitle'] ?? loan['title'] ?? 'Tài liệu thư viện',
      'coverUrl': loan['bookCover'] ?? loan['coverUrl'],
      'borrowDate': formatDate(createdAt),
      'pickupDeadlineDisplay': formatDateTime(pickupDeadline),
      'dueDateDisplay': formatDate(dueDate),
      'returnDateDisplay': formatDateTime(returnDate),
      'physicalReturnDateDisplay': formatDateTime(loan['physicalReturnAt']),
      'remainingDays': daysRemaining ?? 0,
      'ticketCode': 'PM-${loan['ticketId']}',
      'progress': progress,
      'isWaitingPickup': isWaitingPickup,
      'isBorrowing': isBorrowing,
      'isHistory': isHistory,
      'isOverdue':
          isBorrowing &&
          (effectiveStatus == 'OVERDUE' || (daysRemaining ?? 0) < 0),
    };
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }

  static String formatDate(dynamic value) {
    final date = value is DateTime ? value : _parseDate(value);
    if (date == null) return '—';
    return '${date.day.toString().padLeft(2, '0')}/'
        '${date.month.toString().padLeft(2, '0')}/${date.year}';
  }

  static String formatDateTime(dynamic value) {
    final date = value is DateTime ? value : _parseDate(value);
    if (date == null) return '—';
    return '${date.hour.toString().padLeft(2, '0')}:'
        '${date.minute.toString().padLeft(2, '0')} '
        '${date.day.toString().padLeft(2, '0')}/'
        '${date.month.toString().padLeft(2, '0')}/${date.year}';
  }

  static String _dateOnly(DateTime value) =>
      '${value.year}-${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';
}
