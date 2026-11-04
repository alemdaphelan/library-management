import 'package:flutter/foundation.dart';
import 'api_service.dart';
import 'auth_service.dart';

class WishlistService {
  static final ValueNotifier<int> wishlistRevision = ValueNotifier<int>(0);
  static final Set<int> _savedBookIds = <int>{};
  static final Set<int> _inFlightBookIds = <int>{};
  static bool _hasLoadedSnapshot = false;

  static Set<int> get savedBookIds => Set<int>.unmodifiable(_savedBookIds);
  static int get savedCount => _savedBookIds.length;
  static bool get hasLoadedSnapshot => _hasLoadedSnapshot;

  static bool isSavedSync(int? bookId) =>
      bookId != null && _savedBookIds.contains(bookId);

  static bool isInFlight(int? bookId) =>
      bookId != null && _inFlightBookIds.contains(bookId);

  static void clearCache() {
    final hadState =
        _savedBookIds.isNotEmpty ||
        _inFlightBookIds.isNotEmpty ||
        _hasLoadedSnapshot;
    _savedBookIds.clear();
    _inFlightBookIds.clear();
    _hasLoadedSnapshot = false;
    if (hadState) {
      wishlistRevision.value++;
    }
  }

  static int? _parseBookId(dynamic raw) {
    if (raw is int) return raw;
    if (raw is num) return raw.toInt();
    if (raw == null) return null;
    return int.tryParse(raw.toString());
  }

  static Future<List<Map<String, dynamic>>> getMine() async {
    final token = await AuthService.getToken();
    if (token == null || token.isEmpty) {
      clearCache();
      throw const ApiException(
        401,
        'Vui lòng đăng nhập để xem danh sách sách đã lưu.',
      );
    }
    final data = await ApiService.get('/users/wishlist');
    final rawItems = (data as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();

    final deduplicated = <Map<String, dynamic>>[];
    final nextSavedIds = <int>{};
    for (final item in rawItems) {
      final bookId = _parseBookId(item['bookId'] ?? item['id']);
      if (bookId == null) continue;
      if (nextSavedIds.add(bookId)) {
        deduplicated.add(item);
      }
    }

    _savedBookIds
      ..clear()
      ..addAll(nextSavedIds);
    _hasLoadedSnapshot = true;
    wishlistRevision.value++;
    return deduplicated;
  }

  static Future<bool> isSaved(int bookId) async {
    final token = await AuthService.getToken();
    if (token == null || token.isEmpty) {
      final removed = _savedBookIds.remove(bookId);
      if (removed) {
        wishlistRevision.value++;
      }
      return false;
    }
    final data = await ApiService.get('/users/wishlist/$bookId/status');
    final saved = data is Map && data['saved'] == true;
    final changed = saved
        ? _savedBookIds.add(bookId)
        : _savedBookIds.remove(bookId);
    if (changed) {
      wishlistRevision.value++;
    }
    return saved;
  }

  static Future<Map<String, dynamic>?> save(int bookId) async {
    if (_inFlightBookIds.contains(bookId)) {
      return null;
    }
    final wasSaved = _savedBookIds.contains(bookId);
    _inFlightBookIds.add(bookId);
    _savedBookIds.add(bookId);
    wishlistRevision.value++;

    try {
      final token = await AuthService.getToken();
      if (token == null || token.isEmpty) {
        throw const ApiException(
          401,
          'Vui lòng đăng nhập để lưu sách vào danh sách yêu thích.',
        );
      }
      final data = await ApiService.post('/users/wishlist', {'bookId': bookId});
      return data is Map ? Map<String, dynamic>.from(data) : null;
    } catch (e) {
      if (!wasSaved) {
        _savedBookIds.remove(bookId);
      }
      wishlistRevision.value++;
      rethrow;
    } finally {
      _inFlightBookIds.remove(bookId);
    }
  }

  static Future<void> remove(int bookId) async {
    if (_inFlightBookIds.contains(bookId)) {
      return;
    }
    final wasSaved = _savedBookIds.contains(bookId);
    _inFlightBookIds.add(bookId);
    _savedBookIds.remove(bookId);
    wishlistRevision.value++;

    try {
      final token = await AuthService.getToken();
      if (token == null || token.isEmpty) {
        throw const ApiException(
          401,
          'Vui lòng đăng nhập để thao tác danh sách sách đã lưu.',
        );
      }
      await ApiService.delete('/users/wishlist/$bookId');
    } catch (e) {
      if (wasSaved) {
        _savedBookIds.add(bookId);
      }
      wishlistRevision.value++;
      rethrow;
    } finally {
      _inFlightBookIds.remove(bookId);
    }
  }
}
