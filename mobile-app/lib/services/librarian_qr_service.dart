import 'dart:math';

import 'api_service.dart';

class LibrarianQrService {
  static Future<Map<String, dynamic>> issueMyCard() async {
    final data = await ApiService.get('/api/v1/qr/cards/me');
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<Map<String, dynamic>> issueTicket(int ticketId) async {
    final data = await ApiService.get('/api/v1/qr/tickets/$ticketId');
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<Map<String, dynamic>> resolve(String payload) async {
    final data = await ApiService.post('/api/v1/qr/resolve', {
      'payload': payload.trim(),
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<Map<String, dynamic>> checkout(
    int ticketId,
    List<String> barcodes,
  ) async {
    final data = await ApiService.post('/loans/checkout', {
      'idempotencyKey': _operationKey('checkout'),
      'ticketId': ticketId,
      'barcodes': _normalizeBarcodes(barcodes),
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<Map<String, dynamic>> returnBooks(
    List<String> barcodes, {
    String? note,
  }) async {
    final data = await ApiService.post('/loans/return', {
      'idempotencyKey': _operationKey('return'),
      'barcodes': _normalizeBarcodes(barcodes),
      if (note != null && note.trim().isNotEmpty)
        'returnConditionNote': note.trim(),
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static List<String> _normalizeBarcodes(List<String> values) => values
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .toSet()
      .toList();

  static String _operationKey(String operation) {
    final random = Random.secure().nextInt(1 << 32).toRadixString(16);
    return 'mobile-$operation-${DateTime.now().microsecondsSinceEpoch}-$random';
  }
}
