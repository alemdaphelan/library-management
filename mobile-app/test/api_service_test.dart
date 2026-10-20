import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mobile_app/services/api_service.dart';

void main() {
  test('decodes successful JSON responses', () {
    final response = http.Response('{"status":"RETURNED"}', 200);

    expect(ApiService.decodeSuccessBody(response), {'status': 'RETURNED'});
  });

  test('accepts successful plain text and empty responses', () {
    expect(
      ApiService.decodeSuccessBody(
        http.Response.bytes(utf8.encode('Đã hủy phiếu mượn'), 200),
      ),
      'Đã hủy phiếu mượn',
    );
    expect(ApiService.decodeSuccessBody(http.Response('', 204)), isNull);
  });
}
