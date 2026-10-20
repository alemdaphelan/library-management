import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../config/api_config.dart';

class AuthService {
  static String get baseUrl => '${ApiConfig.baseUrl}/api/v1/auth';
  static const String tokenKey = 'huit_lib_token';
  static const String userKey = 'huit_lib_user_profile';
  static const String pushTokenKey = 'huit_lib_push_token';

  static Future<void> saveToken(String token) async {
    final prefs = await SharedPreferences.getInstance();
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
      final pushToken = prefs.getString(pushTokenKey);
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
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(tokenKey);
    await prefs.remove(userKey);
    await prefs.remove(pushTokenKey);
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
    final token = await getToken();
    if (token == null) throw Exception('Phiên đăng nhập đã hết hạn.');
    final response = await http.put(
      Uri.parse('$baseUrl/me'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'fullName': fullName.trim(), 'phone': phone.trim()}),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception(utf8.decode(response.bodyBytes));
    }
    final updated =
        jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    final cached = await getCachedUser();
    final merged = <String, dynamic>{...?cached, ...updated};
    await saveUserProfile(merged);
    return merged;
  }
}
