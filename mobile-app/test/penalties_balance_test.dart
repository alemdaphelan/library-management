import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/services/api_service.dart';

void main() {
  setUp(
    () => SharedPreferences.setMockInitialValues({'huit_lib_token': 'test'}),
  );
  tearDown(() => ApiService.testClient = null);

  testWidgets('reads card balances and list ledger without inventing zero', (
    tester,
  ) async {
    ApiService.testClient = MockClient((r) async {
      if (r.url.path.endsWith('/library-card')) {
        return http.Response(
          jsonEncode({'depositBalance': 450000, 'totalDebt': 25000}),
          200,
        );
      }
      if (r.url.path.endsWith('/my-ledger')) {
        return http.Response(
          jsonEncode([
            {
              'transactionId': 1,
              'type': 'FINE',
              'amount': 50000,
              'status': 'PAID',
              'reason': 'Repair receipt',
            },
          ]),
          200,
        );
      }
      return http.Response('[]', 200);
    });
    await tester.pumpWidget(const MaterialApp(home: PenaltiesScreen()));
    await tester.pumpAndSettle();
    expect(find.text('450000 đ'), findsOneWidget);
    expect(find.text('25000 đ'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Repair receipt'), 200);
    expect(find.text('Repair receipt'), findsOneWidget);
    expect(find.text('50000 đ'), findsOneWidget);
  });

  testWidgets('failed balance request shows error rather than zero balance', (
    tester,
  ) async {
    ApiService.testClient = MockClient(
      (r) async => r.url.path.endsWith('/library-card')
          ? http.Response('{"message":"Balance unavailable"}', 503)
          : http.Response('[]', 200),
    );
    await tester.pumpWidget(const MaterialApp(home: PenaltiesScreen()));
    await tester.pumpAndSettle();
    expect(find.textContaining('Balance unavailable'), findsOneWidget);
    expect(find.text('0 đ'), findsNothing);
  });
}
