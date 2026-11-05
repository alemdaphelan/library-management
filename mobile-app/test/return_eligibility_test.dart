import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/loan_service.dart';
import 'package:mobile_app/services/librarian_qr_service.dart';

void main() {
  test(
    'handled items cannot be returned even while their ticket is borrowing',
    () {
      for (final status in [
        'DAMAGED',
        'LOST',
        'RETURNED',
        'SETTLED',
        'CANCELLED',
      ]) {
        final item = {'status': 'BORROWING', 'itemStatus': status};
        expect(LoanService.canShowReturnQr(item), isFalse);
        expect(LibrarianQrService.isReturnable(item), isFalse);
      }
      final physical = {
        'status': 'BORROWING',
        'itemStatus': 'BORROWING',
        'physicalReturnAt': '2026-11-05',
      };
      expect(LoanService.canShowReturnQr(physical), isFalse);
      expect(LibrarianQrService.isReturnable(physical), isFalse);
      // Payment alone does not prove physical return.
      expect(
        LoanService.canShowReturnQr({
          'status': 'BORROWING',
          'itemStatus': 'BORROWING',
          'caseStatus': 'PAID',
        }),
        isTrue,
      );
    },
  );

  test('retry uses the same explicit operation key and body', () async {
    SharedPreferences.setMockInitialValues({'huit_lib_token': 'test'});
    final bodies = <Map<String, dynamic>>[];
    ApiService.testClient = MockClient((r) async {
      bodies.add(jsonDecode(r.body) as Map<String, dynamic>);
      return bodies.length == 1
          ? http.Response('Unavailable', 503)
          : http.Response('{"message":"replayed"}', 200);
    });
    try {
      final items = [
        {'loanDetailId': 91, 'condition': 'NORMAL'},
      ];
      await expectLater(
        LibrarianQrService.inspectAndReturn(items, idempotencyKey: 'same-key'),
        throwsException,
      );
      await LibrarianQrService.inspectAndReturn(
        items,
        idempotencyKey: 'same-key',
      );
      expect(bodies[0], bodies[1]);
    } finally {
      ApiService.testClient = null;
    }
  });
}
