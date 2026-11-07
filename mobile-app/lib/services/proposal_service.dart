import 'api_service.dart';

class ProposalService {
  static Future<List<Map<String, dynamic>>> getMajors() async {
    try {
      final data = await ApiService.get('/proposals/majors');
      if (data is! List) {
        throw const FormatException('Danh mục ngành trả về không hợp lệ.');
      }
      return data.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } on ApiException catch (error) {
      if (error.statusCode == 404) {
        throw const ApiException(
          404,
          'Máy chủ chưa hỗ trợ danh mục ngành cho đề xuất. Vui lòng liên hệ thư viện để cập nhật hệ thống.',
        );
      }
      rethrow;
    }
  }

  static Future<Map<String, dynamic>> create({
    required String title,
    required String reason,
    String? authorName,
    String? publisher,
    String? isbn,
    int? publicationYear,
    String? edition,
    double? estimatedPrice,
    String? externalUrl,
    String? majorId,
    String? note,
  }) async {
    final data = await ApiService.post('/proposals', {
      'title': title,
      if (authorName?.trim().isNotEmpty == true)
        'authorName': authorName!.trim(),
      if (publisher?.trim().isNotEmpty == true) 'publisher': publisher!.trim(),
      if (isbn?.trim().isNotEmpty == true) 'isbn': isbn!.trim(),
      'publicationYear': ?publicationYear,
      if (edition?.trim().isNotEmpty == true) 'edition': edition!.trim(),
      'estimatedPrice': ?estimatedPrice,
      if (externalUrl?.trim().isNotEmpty == true)
        'externalUrl': externalUrl!.trim(),
      if (majorId?.trim().isNotEmpty == true) 'majorId': majorId!.trim(),
      'reason': reason,
      if (note?.trim().isNotEmpty == true) 'note': note!.trim(),
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<List<Map<String, dynamic>>> getMine() async {
    final data = await ApiService.get('/proposals/my');
    return (data as List<dynamic>)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  static Future<List<Map<String, dynamic>>> getAllForAdmin() async {
    final data = await ApiService.get('/proposals/admin');
    if (data is! List) return [];
    return data.map((item) => Map<String, dynamic>.from(item as Map)).toList();
  }

  static Future<Map<String, dynamic>> updateStatus({
    required int proposalId,
    required String status,
    String? adminNote,
  }) async {
    final data = await ApiService.put('/proposals/admin/$proposalId/status', {
      'status': status,
      if (adminNote != null && adminNote.trim().isNotEmpty)
        'adminNote': adminNote.trim(),
    });
    return Map<String, dynamic>.from(data as Map);
  }
}
