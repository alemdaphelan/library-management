import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../config/api_config.dart';
import 'push_notification_service.dart';
import 'wishlist_service.dart';

class AuthService {
  static String get baseUrl => '${ApiConfig.baseUrl}/api/v1/auth';
  static const String tokenKey = 'huit_lib_token';
  static const String userKey = 'huit_lib_user_profile';
  static const String pushTokenKey = 'huit_lib_push_token';
  static final ValueNotifier<int> profileRevision = ValueNotifier<int>(0);

  /// Normalizes the role shape returned by different authentication payloads.
  /// The backend may expose either `STUDENT` or `ROLE_STUDENT`; historical
  /// `STAFF` accounts are treated as librarians for navigation purposes.
  static String normalizeRoleValue(Object? value) {
    var role = value?.toString().trim().toUpperCase() ?? '';
    if (role.startsWith('ROLE_')) {
      role = role.substring('ROLE_'.length);
    }
    if (role == 'STAFF') return 'LIBRARIAN';
    return role;
  }

  static String roleFromProfile(Map<String, dynamic>? profile) {
    if (profile == null) return '';
    final directRole = normalizeRoleValue(
      profile['role'] ??
          profile['roleId'] ??
          profile['roleName'] ??
          profile['authority'] ??
          profile['userType'],
    );
    if (directRole.isNotEmpty) return directRole;

    final authorities = profile['authorities'];
    if (authorities is List) {
      for (final authority in authorities) {
        final value = authority is Map
            ? authority['authority'] ?? authority['role'] ?? authority['name']
            : authority;
        final normalized = normalizeRoleValue(value);
        if (normalized.isNotEmpty) return normalized;
      }
    }
    return '';
  }

  static Future<String> getCurrentRole({bool forceRefresh = false}) async {
    return roleFromProfile(await getCurrentUser(forceRefresh: forceRefresh));
  }

  static bool isStudentRole(String role) =>
      normalizeRoleValue(role) == 'STUDENT';

  static bool isStaffRole(String role) {
    final normalized = normalizeRoleValue(role);
    return normalized == 'ADMIN' || normalized == 'LIBRARIAN';
  }

  /// Maps technical database/auth role strings to user-friendly Vietnamese labels.
  static String roleDisplayName(Object? value) {
    final normalized = normalizeRoleValue(value);
    switch (normalized) {
      case 'STUDENT':
        return 'Sinh viên';
      case 'LIBRARIAN':
        return 'Thủ thư';
      case 'ADMIN':
        return 'Quản trị viên';
      case 'LECTURER':
      case 'TEACHER':
        return 'Giảng viên';
      default:
        return normalized.isNotEmpty ? normalized : 'Sinh viên';
    }
  }

  static Future<void> saveToken(String token) async {
    final prefs = await SharedPreferences.getInstance();
    final previousToken = prefs.getString(tokenKey);
    if (previousToken != token) {
      WishlistService.clearCache();
    }
    await prefs.setString(tokenKey, token);
  }

  static Future<void> saveUserProfile(Map<String, dynamic> user) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(userKey, jsonEncode(user));
  }

  static Future<Map<String, dynamic>?> getCachedUser() async {
    final prefs = await SharedPreferences.getInstance();
    final str = prefs.getString(userKey);
    if (str != null) {
      try {
        return jsonDecode(str);
      } catch (_) {}
    }
    return null;
  }

  static Future<String?> getToken() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(tokenKey);
  }

  static Future<void> logout() async {
    final token = await getToken();
    final profile = await getCachedUser();
    final refreshToken = profile?['refreshToken']?.toString();
    if (token != null) {
      final prefs = await SharedPreferences.getInstance();
      final pushToken =
          prefs.getString(pushTokenKey) ??
          PushNotificationService.lastKnownToken;
      if (pushToken != null && pushToken.isNotEmpty) {
        try {
          await http.delete(
            Uri.parse('${ApiConfig.baseUrl}/api/v1/notifications/devices'),
            headers: {
              'Authorization': 'Bearer $token',
              'Content-Type': 'application/json',
            },
            body: jsonEncode({'token': pushToken}),
          );
        } catch (_) {
          // The local session must still be cleared when the network is down.
        }
      }
      try {
        await http.post(
          Uri.parse('$baseUrl/logout'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({'refreshToken': refreshToken}),
        );
      } catch (e) {
        // ignore network error
      }
    }
    await clearLocalSession();
  }

  /// Clears credentials without calling the API. This is required immediately
  /// after a password change because the backend invalidates the token that
  /// authorized that very request.
  static Future<void> clearLocalSession() async {
    WishlistService.clearCache();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(tokenKey);
    await prefs.remove(userKey);
    await prefs.remove(pushTokenKey);
    profileRevision.value++;
  }

  static Future<Map<String, dynamic>?> getCurrentUser({
    bool forceRefresh = false,
  }) async {
    final cached = await getCachedUser();
    if (!forceRefresh && cached != null) return cached;

    final token = await getToken();
    if (token == null) return null;

    try {
      final response = await http.get(
        Uri.parse('$baseUrl/me'),
        headers: {'Authorization': 'Bearer $token'},
      );

      if (response.statusCode == 200) {
        final profile =
            jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
        final merged = <String, dynamic>{...?cached, ...profile};
        await saveUserProfile(merged);
        return merged;
      }
    } catch (e) {
      // ignore
    }
    return null;
  }

  static Future<Map<String, dynamic>> updateProfile({
    required String fullName,
    required String phone,
  }) async {
    final normalizedName = fullName.trim();
    final normalizedPhone = normalizePhone(phone);
    if (normalizedName.isEmpty) {
      throw Exception('Họ tên không được để trống.');
    }
    if (!isValidPhone(phone)) {
      throw Exception(
        'Số điện thoại phải gồm 10 chữ số bắt đầu bằng 0 hoặc mã quốc gia +84.',
      );
    }
    final token = await getToken();
    if (token == null) throw Exception('Phiên đăng nhập đã hết hạn.');
    final response = await http
        .put(
          Uri.parse('$baseUrl/me'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({
            'fullName': normalizedName,
            'phone': normalizedPhone,
          }),
        )
        .timeout(const Duration(seconds: 20));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception(_extractErrorMessage(response));
    }
    final updated =
        jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    final cached = await getCachedUser();
    final merged = <String, dynamic>{...?cached, ...updated};
    await saveUserProfile(merged);
    profileRevision.value++;
    return merged;
  }

  static String normalizePhone(String rawPhone) {
    var phone = rawPhone.trim().replaceAll(RegExp(r'[\s().-]'), '');
    if (phone.startsWith('+84')) {
      phone = '0${phone.substring(3)}';
    }
    return phone;
  }

  static bool isValidPhone(String rawPhone) {
    final phone = normalizePhone(rawPhone);
    return phone.isEmpty || RegExp(r'^0\d{9}$').hasMatch(phone);
  }

  static String _extractErrorMessage(http.Response response) {
    final body = utf8.decode(response.bodyBytes);
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        return (decoded['detail'] ?? decoded['message'] ?? body).toString();
      }
    } catch (_) {}
    return body.isEmpty ? 'Không thể cập nhật thông tin cá nhân.' : body;
  }
}
