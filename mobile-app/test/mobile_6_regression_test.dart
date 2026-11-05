import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/loan_service.dart';
import 'package:mobile_app/services/proposal_service.dart';
import 'support/mobile_ui_fixture.dart';

http.Response _json(Object? value, [int status = 200]) => http.Response(
  jsonEncode(value),
  status,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'huit_lib_token': 'test',
      'huit_lib_user_profile': jsonEncode(uiUser),
    });
  });
  tearDown(() => ApiService.testClient = null);

  final loans = <Map<String, dynamic>>[
    {
      'ticketId': 27,
      'detailId': 1,
      'bookId': 1,
      'bookTitle': 'Sách đang mượn',
      'status': 'BORROWING',
      'itemStatus': ' checked_out ',
      'daysRemaining': 9,
      'createdAt': '2026-11-04T10:00:00',
      'dueDate': '2026-11-14T10:00:00',
    },
    {
      'ticketId': 26,
      'detailId': 2,
      'bookId': 2,
      'bookTitle': 'Sách hỏng đã nhận',
      'status': 'BORROWING',
      'itemStatus': 'DAMAGED',
      'caseStatus': 'PAID',
      'daysRemaining': 9,
      'physicalReturnAt': '2026-11-05T10:00:00',
      'dueDate': '2026-11-14T10:00:00',
    },
    {
      'ticketId': 25,
      'detailId': 3,
      'bookId': 3,
      'bookTitle': 'Phiếu đã hủy',
      'status': 'CANCELLED',
      'itemStatus': 'CANCELLED',
      'daysRemaining': 9,
      'dueDate': '2026-11-14T10:00:00',
    },
  ];

  test(
    'Home active-only and Borrowed classification agree for legacy and processed items',
    () async {
      ApiService.testClient = MockClient((r) async => _json(loans));
      final active = await LoanService.getLoans(
        activeOnly: true,
        policyLoanDays: 10,
      );
      expect(active.map((e) => e['detailId']).toList(), [1]);
      final all = loans
          .map((e) => LoanService.normalizeLoan(e, loanDays: 10))
          .toList();
      expect(all.where((e) => e['isBorrowing'] == true).length, active.length);
      expect(all[1]['isHistory'], isTrue);
      expect(LoanService.historyLabel(all[1]), 'Đã nhận sách hư hỏng');
      expect(LoanService.canShowReturnQr(all[1]), isFalse);
      expect(LoanService.canShowReturnQr(all[0]), isTrue);
    },
  );

  test('payment alone never implies physical return', () {
    final paid = {
      'status': 'BORROWING',
      'itemStatus': 'BORROWING',
      'caseStatus': 'PAID',
    };
    expect(LoanService.isBorrowingItem(paid), isTrue);
    expect(LoanService.isOpenLoanItem(paid), isTrue);
    final completed = {
      'status': 'BORROWING',
      'itemStatus': 'COMPLETED',
      'daysRemaining': 9,
    };
    expect(LoanService.isOpenLoanItem(completed), isFalse);
    expect(LoanService.historyLabel(completed), 'Đã trả sách');
  });

  testWidgets(
    'Borrowed shows one active item and history never repeats a remaining-days badge',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final fallback = mobileUiClient();
      ApiService.testClient = MockClient((r) async {
        if (r.url.path.endsWith('/loans/my-loans')) return _json(loans);
        if (r.url.path.endsWith('/my-eligibility')) {
          return http.Response(
            '{"maximumItems":3,"openItems":1,"remainingSlots":2}',
            200,
          );
        }
        return fallback.get(r.url);
      });
      await tester.pumpWidget(const MaterialApp(home: BorrowedScreen()));
      await tester.pumpAndSettle();
      expect(find.text('Đang mượn (1)'), findsOneWidget);
      expect(find.text('Lịch sử mượn & Đã hủy (2)'), findsOneWidget);
      expect(find.text('Còn 9 ngày'), findsOneWidget);
      expect(find.text('Đã nhận sách hư hỏng'), findsOneWidget);
      expect(tester.takeException(), isNull);
      fallback.close();
    },
  );

  testWidgets(
    'deposit receipt explains 100k credit and keeps remaining wallet at 50k',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      ApiService.testClient = MockClient((r) async {
        if (r.url.path.endsWith('/library-card')) {
          return http.Response('{"depositBalance":50000,"totalDebt":0}', 200);
        }
        if (r.url.path.endsWith('/my-ledger')) {
          return http.Response(
            jsonEncode([
              {
                'type': 'DEPOSIT_TOPUP',
                'amount': -100000,
                'status': 'COMPLETED',
                'reason':
                    'Thiết lập tiền thế chân 100.000đ cho tài khoản kiểm thử Mobile/UAT',
              },
              {
                'type': 'REPAIR_FEE',
                'amount': 50000,
                'status': 'PAID',
                'reason': 'Phí sửa chữa',
              },
            ]),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }
        return http.Response('[]', 200);
      });
      await tester.pumpWidget(const MaterialApp(home: PenaltiesScreen()));
      await tester.pumpAndSettle();
      expect(find.text('+100000 đ'), findsOneWidget);
      expect(find.text('-100000 đ'), findsNothing);
      expect(find.text('Số dư thế chân ban đầu'), findsOneWidget);
      expect(
        find.textContaining('không phải một lần trừ tiền mới'),
        findsOneWidget,
      );
      expect(
        find.textContaining('tài khoản kiểm thử Mobile/UAT'),
        findsNothing,
      );
      expect(find.text('50000 đ'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('Phí sửa chữa'), 160);
      expect(find.text('50000 đ'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'missing major endpoint is distinct from empty catalog and can be retried',
    (tester) async {
      bool ready = false;
      ApiService.testClient = MockClient((r) async {
        if (r.url.path.endsWith('/proposals/majors')) {
          return ready
              ? http.Response(
                  '[{"majorId":"IT","majorName":"Công nghệ thông tin"}]',
                  200,
                  headers: {'content-type': 'application/json; charset=utf-8'},
                )
              : http.Response('{"message":"Not found"}', 404);
        }
        return http.Response('[]', 200);
      });
      await tester.pumpWidget(const MaterialApp(home: SuggestBookScreen()));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Máy chủ chưa hỗ trợ danh mục ngành'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Máy chủ chưa có dữ liệu ngành học'),
        findsNothing,
      );
      ready = true;
      await tester.ensureVisible(find.text('Thử lại'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Thử lại'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      expect(find.text('Công nghệ thông tin'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  test(
    'malformed major payload is rejected rather than replaced with recommendation categories',
    () async {
      ApiService.testClient = MockClient((r) async => http.Response('{}', 200));
      await expectLater(ProposalService.getMajors(), throwsFormatException);
    },
  );
}
