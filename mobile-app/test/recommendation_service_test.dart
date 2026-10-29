import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/services/recommendation_service.dart';

void main() {
  test(
    'parses hybrid recommendation response and maps item to book detail',
    () {
      final result = RecommendationResult.fromJson({
        'method': 'HYBRID_PERSONALIZED',
        'dataSufficient': true,
        'candidateCount': 3000,
        'items': [
          {
            'bookId': 42,
            'title': 'Cấu trúc dữ liệu',
            'author': 'Nguyễn Văn A',
            'publisher': 'NXB Giáo dục',
            'categoryName': 'Công nghệ thông tin',
            'imageUrl': 'https://example.test/cover.jpg',
            'score': 0.72,
            'contentScore': 0.8,
            'departmentScore': 0.9,
            'popularityScore': 0.4,
            'borrowCount': 12,
            'reason': 'Phù hợp với chuyên ngành Công nghệ thông tin',
          },
        ],
      });

      expect(result.method, 'HYBRID_PERSONALIZED');
      expect(result.dataSufficient, isTrue);
      expect(result.items, hasLength(1));
      expect(result.items.first.score, 0.72);
      expect(result.items.first.toBookMap()['bookId'], 42);
      expect(result.items.first.toBookMap()['category'], 'Công nghệ thông tin');
    },
  );

  test('drops recommendation entries without a valid book id', () {
    final result = RecommendationResult.fromJson({
      'method': 'POPULARITY_BASELINE',
      'items': [
        {'title': 'Thiếu mã sách'},
      ],
    });

    expect(result.items, isEmpty);
  });
}
