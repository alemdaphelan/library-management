import 'api_service.dart';

class ProposalService {
  static Future<Map<String, dynamic>> create({
    required String title,
    required String reason,
    String? authorName,
  }) async {
    final data = await ApiService.post('/proposals', {
      'title': title,
      'authorName': authorName?.trim(),
      'reason': reason,
    });
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<List<Map<String, dynamic>>> getMine() async {
    final data = await ApiService.get('/proposals/my');
    return (data as List<dynamic>)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }
}
