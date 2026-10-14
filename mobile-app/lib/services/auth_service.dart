import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class AuthService {
  // Use 10.0.2.2 for Android emulator
  static const String baseUrl = 'http://10.0.2.2:8080/api/v1/auth';
  static const String tokenKey = 'huit_lib_token';
  static const String userKey = 'huit_lib_user_profile';

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
    if (token != null) {
      try {
        await http.post(
          Uri.parse('$baseUrl/logout'),
          headers: {'Authorization': 'Bearer $token'},
        );
      } catch (e) {
        // ignore network error
      }
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(tokenKey);
    await prefs.remove(userKey);
  }

  static Future<Map<String, dynamic>?> getCurrentUser() async {
    final cached = await getCachedUser();
    if (cached != null) return cached;

    final token = await getToken();
    if (token == null) return null;

    try {
      final response = await http.get(
        Uri.parse('$baseUrl/me'),
        headers: {'Authorization': 'Bearer $token'},
      );

      if (response.statusCode == 200) {
        final profile = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
        await saveUserProfile(profile);
        return profile;
      }
    } catch (e) {
      // ignore
    }
    return null;
  }
}
