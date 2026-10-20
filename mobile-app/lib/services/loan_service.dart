import 'api_service.dart';

class LoanService {
  static Future<List<Map<String, dynamic>>> getLoans({
    bool activeOnly = false,
  }) async {
    final data = await ApiService.get('/loans/my-loans');
    final loans = (data as List<dynamic>)
        .map((item) => _normalize(Map<String, dynamic>.from(item as Map)))
        .toList();
    if (!activeOnly) return loans;
    return loans
        .where(
          (item) => const {
            'PENDING',
            'RESERVED',
            'BORROWING',
            'OVERDUE',
          }.contains(item['status']),
        )
        .toList();
  }

  static Future<Map<String, dynamic>> reserveBook(
    int bookId, {
    String ticketType = 'BORROW',
    String? note,
  }) async {
    final pickupDate = DateTime.now().add(const Duration(days: 1));
    final data = await ApiService.post('/loans/reserve', {
      'bookId': bookId,
      'ticketType': ticketType,
      'pickupDate': _dateOnly(pickupDate),
      if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<Map<String, dynamic>> renewLoan(
    int ticketId,
    int detailId,
  ) async {
    final data = await ApiService.post(
      '/loans/renew/$ticketId/items/$detailId',
      {},
    );
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<void> cancelReservation(int ticketId) async {
    await ApiService.post('/loans/cancel/$ticketId', {});
  }

  static Map<String, dynamic> _normalize(Map<String, dynamic> loan) {
    final dueDate = _parseDate(loan['dueDate']);
    final createdAt = _parseDate(loan['createdAt']);
    final daysRemaining = loan['daysRemaining'] is num
        ? (loan['daysRemaining'] as num).toInt()
        : dueDate?.difference(DateTime.now()).inDays;
    return {
      ...loan,
      'title': loan['bookTitle'] ?? 'Tài liệu thư viện',
      'coverUrl': loan['bookCover'],
      'borrowDate': _displayDate(createdAt),
      'dueDateDisplay': _displayDate(dueDate),
      'remainingDays': daysRemaining ?? 0,
      'ticketCode': 'PM-${loan['ticketId']}',
      'progress': daysRemaining == null
          ? 0.0
          : ((10 - daysRemaining) / 10).clamp(0.0, 1.0),
    };
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }

  static String _displayDate(DateTime? value) {
    if (value == null) return '—';
    return '${value.day.toString().padLeft(2, '0')}/'
        '${value.month.toString().padLeft(2, '0')}/${value.year}';
  }

  static String _dateOnly(DateTime value) =>
      '${value.year}-${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';
}
