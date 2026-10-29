import 'api_service.dart';

class ProposalService {
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
    String? genre,
    String urgencyLevel = 'NORMAL',
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
      if (genre?.trim().isNotEmpty == true) 'genre': genre!.trim(),
      'urgencyLevel': urgencyLevel,
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
}
