import 'api_service.dart';

class RecommendationItem {
  final int bookId;
  final String title;
  final String author;
  final String? publisher;
  final String? categoryName;
  final String? imageUrl;
  final double score;
  final double contentScore;
  final double departmentScore;
  final double popularityScore;
  final int borrowCount;
  final String reason;

  const RecommendationItem({
    required this.bookId,
    required this.title,
    required this.author,
    required this.publisher,
    required this.categoryName,
    required this.imageUrl,
    required this.score,
    required this.contentScore,
    required this.departmentScore,
    required this.popularityScore,
    required this.borrowCount,
    required this.reason,
  });

  factory RecommendationItem.fromJson(Map<String, dynamic> json) {
    return RecommendationItem(
      bookId: (json['bookId'] as num?)?.toInt() ?? 0,
      title: json['title']?.toString() ?? 'Tài liệu thư viện',
      author: json['author']?.toString() ?? 'Đang cập nhật',
      publisher: json['publisher']?.toString(),
      categoryName: json['categoryName']?.toString(),
      imageUrl: json['imageUrl']?.toString(),
      score: (json['score'] as num?)?.toDouble() ?? 0,
      contentScore: (json['contentScore'] as num?)?.toDouble() ?? 0,
      departmentScore: (json['departmentScore'] as num?)?.toDouble() ?? 0,
      popularityScore: (json['popularityScore'] as num?)?.toDouble() ?? 0,
      borrowCount: (json['borrowCount'] as num?)?.toInt() ?? 0,
      reason: json['reason']?.toString() ?? 'Gợi ý từ thư viện',
    );
  }

  Map<String, dynamic> toBookMap() {
    return {
      'bookId': bookId,
      'title': title,
      'author': author,
      'publisher': publisher,
      'category': categoryName,
      'coverUrl': imageUrl,
      'recommendationReason': reason,
      'recommendationScore': score,
    };
  }
}

class RecommendationResult {
  final String method;
  final bool dataSufficient;
  final int candidateCount;
  final List<RecommendationItem> items;

  const RecommendationResult({
    required this.method,
    required this.dataSufficient,
    required this.candidateCount,
    required this.items,
  });

  factory RecommendationResult.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] as List<dynamic>? ?? const [];
    return RecommendationResult(
      method: json['method']?.toString() ?? 'POPULARITY_BASELINE',
      dataSufficient: json['dataSufficient'] == true,
      candidateCount: (json['candidateCount'] as num?)?.toInt() ?? 0,
      items: rawItems
          .whereType<Map>()
          .map(
            (item) =>
                RecommendationItem.fromJson(Map<String, dynamic>.from(item)),
          )
          .where((item) => item.bookId > 0)
          .toList(),
    );
  }
}

class RecommendationService {
  static Future<RecommendationResult> getRecommendations({
    int limit = 10,
  }) async {
    try {
      final response = await ApiService.get(
        '/books/recommendations/personalized?limit=$limit',
      );
      return RecommendationResult.fromJson(
        Map<String, dynamic>.from(response as Map),
      );
    } catch (_) {
      final response = await ApiService.getPublic(
        '/books/recommendations/popular?limit=$limit',
      );
      return RecommendationResult.fromJson(
        Map<String, dynamic>.from(response as Map),
      );
    }
  }
}
