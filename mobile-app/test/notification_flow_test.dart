import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/notifications_screen.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/services/notification_service.dart';
import 'package:mobile_app/services/push_notification_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'huit_lib_token': 'test-jwt-token',
    });
  });

  group('NotificationService.resolveNavigationTarget', () {
    test('routes loan and reservation notifications to BorrowedScreen', () {
      final approved = NotificationService.resolveNavigationTarget({
        'type': 'LOAN_APPROVED',
        'referenceType': 'LOAN_TICKET',
        'referenceId': '42',
      });
      expect(approved.destination, NotificationDestination.borrowed);
      expect(approved.ticketId, '42');

      final waitlist = NotificationService.resolveNavigationTarget({
        'type': 'WAITLIST_AVAILABLE',
        'referenceType': 'WAITLIST',
        'referenceId': '15',
      });
      expect(waitlist.destination, NotificationDestination.borrowed);
      expect(waitlist.waitlistId, '15');

      final overdue = NotificationService.resolveNavigationTarget({
        'type': 'LOAN_OVERDUE',
        'referenceType': 'LOAN_DETAIL',
        'referenceId': '88',
      });
      expect(overdue.destination, NotificationDestination.borrowed);
      expect(overdue.detailId, '88');

      final dueToday = NotificationService.resolveNavigationTarget({
        'type': 'DUE_TODAY',
        'referenceType': 'LOAN_DETAIL',
        'referenceId': '89',
      });
      expect(dueToday.destination, NotificationDestination.borrowed);
      expect(dueToday.detailId, '89');
    });

    test('routes book proposal notifications to SuggestBookScreen', () {
      final proposal = NotificationService.resolveNavigationTarget({
        'type': 'PROPOSAL_APPROVED',
        'referenceType': 'BOOK_PROPOSAL',
        'referenceId': '7',
      });
      expect(proposal.destination, NotificationDestination.suggestBook);
      expect(proposal.proposalId, '7');
    });

    test('routes penalty and fine notifications to PenaltiesScreen', () {
      final penaltyCase = NotificationService.resolveNavigationTarget({
        'type': 'PENALTY_CASE_CREATED',
        'referenceType': 'PENALTY_CASE',
        'referenceId': '12',
      });
      expect(penaltyCase.destination, NotificationDestination.penalties);
      expect(penaltyCase.penaltyCaseId, '12');

      final fine = NotificationService.resolveNavigationTarget({
        'type': 'FINE',
        'referenceType': 'TRANSACTION',
        'referenceId': '99',
      });
      expect(fine.destination, NotificationDestination.penalties);
      expect(fine.penaltyCaseId, isNull);
    });

    test('routes general and circulation lock notifications to NotificationsScreen', () {
      final locked = NotificationService.resolveNavigationTarget({
        'type': 'CIRCULATION_LOCKED',
        'referenceType': 'USER',
        'referenceId': '00000000-0000-0000-0000-002001230219',
      });
      expect(locked.destination, NotificationDestination.notifications);
    });
  });

  testWidgets(
    'NotificationsScreen loads notifications, marks single and all as read, and updates revision',
    (WidgetTester tester) async {
      final markedIds = <String>[];
      var markAllCalled = false;
      final notifications = <Map<String, dynamic>>[
        {
          'notifId': 101,
          'type': 'CIRCULATION_LOCKED',
          'referenceType': 'USER',
          'referenceId': '00000000-0000-0000-0000-002001230219',
          'content': 'Thẻ thư viện của bạn đã tạm khóa quyền mượn.',
          'isRead': false,
          'sentAt': '2026-11-01T08:00:00',
        },
        {
          'notifId': 102,
          'type': 'DUE_TODAY',
          'referenceType': 'LOAN_DETAIL',
          'referenceId': '501',
          'content': 'Sách Cấu trúc dữ liệu đến hạn trả hôm nay.',
          'isRead': false,
          'sentAt': '2026-11-01T09:00:00',
        },
      ];

      final mockClient = MockClient((http.Request request) async {
        final path = request.url.path;
        if (path.endsWith('/api/v1/library-card')) {
          return http.Response('{"depositBalance":0,"totalDebt":0}', 200);
        }
        if (request.method == 'GET' && path.endsWith('/api/v1/notifications')) {
          return http.Response.bytes(
            utf8.encode(jsonEncode(notifications)),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }
        if (request.method == 'PATCH' &&
            path.endsWith('/api/v1/notifications/101/read')) {
          markedIds.add('101');
          notifications[0]['isRead'] = true;
          return http.Response.bytes(
            utf8.encode(jsonEncode(notifications[0])),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }
        if (request.method == 'PATCH' &&
            path.endsWith('/api/v1/notifications/read-all')) {
          markAllCalled = true;
          for (final item in notifications) {
            item['isRead'] = true;
          }
          return http.Response.bytes(
            utf8.encode(jsonEncode({'updatedCount': 1})),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }
        return http.Response('[]', 200);
      });

      final initialRevision = PushNotificationService.notificationRevision.value;

      await http.runWithClient(() async {
        await tester.pumpWidget(
          const MaterialApp(home: NotificationsScreen()),
        );
        await tester.pumpAndSettle();

        expect(find.text('Quyền mượn đã bị khóa'), findsOneWidget);
        expect(find.text('Sách đến hạn trả hôm nay'), findsOneWidget);
        expect(find.text('Đọc tất cả'), findsOneWidget);

        // Tap first notification (CIRCULATION_LOCKED) -> marks 101 as read
        await tester.tap(find.text('Quyền mượn đã bị khóa'));
        await tester.pumpAndSettle();

        expect(markedIds, contains('101'));
        expect(
          PushNotificationService.notificationRevision.value,
          greaterThan(initialRevision),
        );

        // Tap "Đọc tất cả" -> marks remaining unread notifications as read
        await tester.tap(find.text('Đọc tất cả'));
        await tester.pumpAndSettle();

        expect(markAllCalled, isTrue);
        expect(find.text('Đọc tất cả'), findsNothing);
      }, () => mockClient);
    },
  );

  testWidgets(
    'Deleted or inaccessible referenced objects on PenaltiesScreen, SuggestBookScreen, and BorrowedScreen show clear Vietnamese message without crashing',
    (WidgetTester tester) async {
      final mockClient = MockClient((http.Request request) async {
        final path = request.url.path;
        if (path.endsWith('/api/v1/library-card')) {
          return http.Response('{"depositBalance":0,"totalDebt":0}', 200);
        }
        if (path.endsWith('/api/v1/penalty-cases/my') ||
            path.endsWith('/api/v1/transactions/my-ledger') ||
            path.endsWith('/api/v1/proposals/my') ||
            path.endsWith('/loans/my') ||
            path.endsWith('/loans/waitlist/my')) {
          return http.Response('[]', 200);
        }
        if (path.endsWith('/api/v1/auth/me')) {
          return http.Response(
            jsonEncode({'roleId': 'STUDENT', 'mssv': '2001230219'}),
            200,
          );
        }
        if (path.endsWith('/loans/my-eligibility') ||
            path.endsWith('/api/v1/policies/current')) {
          return http.Response('{}', 200);
        }
        return http.Response('[]', 200);
      });

      await http.runWithClient(() async {
        await tester.pumpWidget(
          MaterialApp(
            key: const ValueKey('penalties'),
            home: const PenaltiesScreen(initialCaseId: '999999'),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.text(
            'Hồ sơ khoản phạt #999999 không còn tồn tại hoặc bạn không có quyền truy cập.',
          ),
          findsOneWidget,
        );

        await tester.pumpWidget(
          MaterialApp(
            key: const ValueKey('suggest'),
            home: const SuggestBookScreen(initialProposalId: '999999'),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.text(
            'Đề xuất sách #999999 không còn tồn tại hoặc bạn không có quyền truy cập.',
          ),
          findsOneWidget,
        );

        await tester.pumpWidget(
          MaterialApp(
            key: const ValueKey('borrowed'),
            home: const BorrowedScreen(initialTicketId: '999999'),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.text(
            'Phiếu mượn hoặc yêu cầu đặt giữ được tham chiếu không còn tồn tại hoặc bạn không có quyền truy cập.',
          ),
          findsOneWidget,
        );
      }, () => mockClient);
    },
  );
}
