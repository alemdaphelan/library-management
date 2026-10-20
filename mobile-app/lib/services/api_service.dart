import 'dart:convert';
import 'package:http/http.dart' as http;
import 'auth_service.dart';
import '../config/api_config.dart';

class ApiService {
  static String get baseUrl => ApiConfig.baseUrl;

  static Future<Map<String, String>> _getHeaders() async {
    final token = await AuthService.getToken();
    final headers = <String, String>{'Content-Type': 'application/json'};
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  static Future<dynamic> get(String endpoint) async {
    final headers = await _getHeaders();
    final response = await http.get(
      Uri.parse('$baseUrl$endpoint'),
      headers: headers,
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    } else {
      throw ApiException(response.statusCode, _message(response));
    }
  }

  static Future<dynamic> getPublic(String endpoint) async {
    final response = await http.get(
      Uri.parse('$baseUrl$endpoint'),
      headers: const {'Content-Type': 'application/json'},
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    }
    throw ApiException(response.statusCode, _message(response));
  }

  static Future<dynamic> post(
    String endpoint,
    Map<String, dynamic> data,
  ) async {
    final headers = await _getHeaders();
    final response = await http.post(
      Uri.parse('$baseUrl$endpoint'),
      headers: headers,
      body: json.encode(data),
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    } else {
      throw ApiException(response.statusCode, _message(response));
    }
  }

  static Future<dynamic> patch(
    String endpoint, [
    Map<String, dynamic>? data,
  ]) async {
    final headers = await _getHeaders();
    final response = await http.patch(
      Uri.parse('$baseUrl$endpoint'),
      headers: headers,
      body: json.encode(data ?? const <String, dynamic>{}),
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    }
    throw ApiException(response.statusCode, _message(response));
  }

  static Future<dynamic> put(String endpoint, Map<String, dynamic> data) async {
    final headers = await _getHeaders();
    final response = await http.put(
      Uri.parse('$baseUrl$endpoint'),
      headers: headers,
      body: json.encode(data),
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    }
    throw ApiException(response.statusCode, _message(response));
  }

  static Future<dynamic> delete(
    String endpoint, [
    Map<String, dynamic>? data,
  ]) async {
    final headers = await _getHeaders();
    final response = await http.delete(
      Uri.parse('$baseUrl$endpoint'),
      headers: headers,
      body: json.encode(data ?? const <String, dynamic>{}),
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decodeSuccessBody(response);
    }
    throw ApiException(response.statusCode, _message(response));
  }

  static String _message(http.Response response) {
    final body = utf8.decode(response.bodyBytes).trim();
    if (body.isEmpty) return 'Yêu cầu thất bại (${response.statusCode})';
    try {
      final decoded = json.decode(body);
      if (decoded is Map) {
        return (decoded['detail'] ??
                decoded['message'] ??
                decoded['error'] ??
                body)
            .toString();
      }
    } catch (_) {}
    return body;
  }

  /// Backend endpoints may legitimately return JSON, plain text, or an empty
  /// body. Treat all successful variants as success instead of throwing a
  /// FormatException after the server has already committed the operation.
  static dynamic decodeSuccessBody(http.Response response) {
    final body = utf8.decode(response.bodyBytes).trim();
    if (body.isEmpty) return null;
    try {
      return json.decode(body);
    } on FormatException {
      return body;
    }
  }
}

class ApiException implements Exception {
  final int statusCode;
  final String message;

  const ApiException(this.statusCode, this.message);

  @override
  String toString() => message;
}
