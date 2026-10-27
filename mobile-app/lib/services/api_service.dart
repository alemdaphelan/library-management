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
      throw ApiException(response.statusCode, extractErrorMessage(response));
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
    throw ApiException(response.statusCode, extractErrorMessage(response));
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
      throw ApiException(response.statusCode, extractErrorMessage(response));
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
    throw ApiException(response.statusCode, extractErrorMessage(response));
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
    throw ApiException(response.statusCode, extractErrorMessage(response));
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
    throw ApiException(response.statusCode, extractErrorMessage(response));
  }

  static String extractErrorMessage(http.Response response) {
    final body = utf8.decode(response.bodyBytes).trim();
    try {
      final decoded = json.decode(body);
      if (decoded is Map) {
        for (final key in const ['detail', 'message', 'reason']) {
          final value = decoded[key]?.toString().trim();
          if (value != null && value.isNotEmpty && value != 'null') {
            return value;
          }
        }
        final errors = decoded['errors'];
        if (errors is Map && errors.isNotEmpty) {
          final value = errors.values.first;
          if (value is List && value.isNotEmpty) return value.first.toString();
          if (value != null) return value.toString();
        }
        final error = decoded['error']?.toString().trim();
        const genericHttpLabels = {
          'bad request',
          'unauthorized',
          'forbidden',
          'not found',
          'conflict',
          'internal server error',
          'service unavailable',
        };
        if (error != null &&
            error.isNotEmpty &&
            !genericHttpLabels.contains(error.toLowerCase())) {
          return error;
        }
      }
    } catch (_) {}
    if (body.isNotEmpty && !body.startsWith('{') && !body.startsWith('[')) {
      return body;
    }
    return switch (response.statusCode) {
      400 => 'Dữ liệu gửi lên chưa hợp lệ. Vui lòng kiểm tra lại.',
      401 => 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
      403 => 'Bạn không có quyền thực hiện thao tác này.',
      404 => 'Không tìm thấy dữ liệu yêu cầu.',
      409 =>
        'Yêu cầu không thể thực hiện do trạng thái dữ liệu đã thay đổi. Vui lòng làm mới và thử lại.',
      503 => 'Dịch vụ đang tạm thời gián đoạn. Vui lòng thử lại sau.',
      _ => 'Yêu cầu thất bại (${response.statusCode}). Vui lòng thử lại.',
    };
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
