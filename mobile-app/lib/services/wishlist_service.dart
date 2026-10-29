import 'api_service.dart';

class WishlistService {
  static Future<List<Map<String, dynamic>>> getMine() async {
    final data = await ApiService.get('/users/wishlist');
    return (data as List<dynamic>? ?? const [])
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  static Future<bool> isSaved(int bookId) async {
    final data = await ApiService.get('/users/wishlist/$bookId/status');
    return data is Map && data['saved'] == true;
  }

  static Future<void> save(int bookId) async {
    await ApiService.post('/users/wishlist', {'bookId': bookId});
  }

  static Future<void> remove(int bookId) async {
    await ApiService.delete('/users/wishlist/$bookId');
  }
}
