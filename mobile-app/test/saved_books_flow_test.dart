import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import 'package:mobile_app/screens/saved_books_screen.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/wishlist_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

http.Response _jsonResponse(Object body, [int statusCode = 200]) {
  return http.Response.bytes(
    utf8.encode(jsonEncode(body)),
    statusCode,
    headers: const {'content-type': 'application/json; charset=utf-8'},
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'huit_lib_token': 'test-jwt-student-2001230219',
    });
    WishlistService.clearCache();
    ApiService.testClient = null;
  });

  tearDown(() {
    WishlistService.clearCache();
    ApiService.testClient = null;
  });

  testWidgets(
    'DetailScreen optimistically saves/unsaves, blocks rapid duplicate taps, and syncs with WishlistService',
    (WidgetTester tester) async {
      int postSaveCalls = 0;
      int deleteCalls = 0;
      final saveCompleter = Completer<http.Response>();

      ApiService.testClient = MockClient((request) async {
        final path = request.url.path;
        if (request.method == 'GET' &&
            path.endsWith('/users/wishlist/16/status')) {
          return _jsonResponse({'saved': false});
        }
        if (request.method == 'POST' && path.endsWith('/users/wishlist')) {
          postSaveCalls++;
          return saveCompleter.future;
        }
        if (request.method == 'DELETE' && path.endsWith('/users/wishlist/16')) {
          deleteCalls++;
          return http.Response('', 204);
        }
        if (request.method == 'GET' && path.endsWith('/books/16')) {
          return _jsonResponse({
            'bookId': 16,
            'title': 'Cấu trúc dữ liệu và giải thuật',
            'author': 'Nguyễn Văn A',
            'availableCopies': 2,
            'copies': 5,
          });
        }
        return _jsonResponse({});
      });

      await tester.pumpWidget(
        const MaterialApp(
          home: DetailScreen(
            book: {
              'bookId': 16,
              'title': 'Cấu trúc dữ liệu và giải thuật',
              'author': 'Nguyễn Văn A',
              'availableCopies': 2,
              'copies': 5,
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.bookmark_outline), findsOneWidget);
      expect(find.byIcon(Icons.bookmark), findsNothing);

      final saveButton = find.byKey(const ValueKey('detail-save-book-button'));

      // Tap rapidly 3 times while POST /users/wishlist is still in flight
      await tester.tap(saveButton);
      await tester.pump();
      await tester.tap(saveButton);
      await tester.tap(saveButton);
      await tester.pump();

      // UI must flip to saved icon immediately (optimistic update)
      expect(find.byIcon(Icons.bookmark), findsOneWidget);
      expect(WishlistService.isSavedSync(16), isTrue);
      // Only 1 request must have been sent despite 3 rapid taps
      expect(postSaveCalls, 1);

      saveCompleter.complete(
        _jsonResponse({
          'wishlistId': 'w-16',
          'bookId': 16,
          'title': 'Cấu trúc dữ liệu và giải thuật',
          'author': 'Nguyễn Văn A',
          'availableCopies': 2,
          'copies': 5,
        }),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.bookmark), findsOneWidget);
      expect(find.text('Đã lưu vào danh sách sách đã lưu.'), findsOneWidget);

      // Now tap again to unsave
      await tester.tap(saveButton);
      await tester.pumpAndSettle();

      expect(deleteCalls, 1);
      expect(find.byIcon(Icons.bookmark_outline), findsOneWidget);
      expect(WishlistService.isSavedSync(16), isFalse);
    },
  );

  testWidgets(
    'DetailScreen rolls back optimistic save and shows Vietnamese error when API fails',
    (WidgetTester tester) async {
      ApiService.testClient = MockClient((request) async {
        final path = request.url.path;
        if (request.method == 'GET' &&
            path.endsWith('/users/wishlist/25/status')) {
          return _jsonResponse({'saved': false});
        }
        if (request.method == 'POST' && path.endsWith('/users/wishlist')) {
          return _jsonResponse({'message': 'Không tìm thấy đầu sách #25'}, 404);
        }
        return _jsonResponse({});
      });

      await tester.pumpWidget(
        const MaterialApp(
          home: DetailScreen(
            book: {
              'bookId': 25,
              'title': 'Sách kiểm thử rollback',
              'author': 'HUIT',
              'availableCopies': 1,
              'copies': 1,
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.bookmark_outline), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('detail-save-book-button')));
      await tester.pumpAndSettle();

      // Must rollback to unsaved and display Vietnamese error message
      expect(find.byIcon(Icons.bookmark_outline), findsOneWidget);
      expect(find.byIcon(Icons.bookmark), findsNothing);
      expect(WishlistService.isSavedSync(25), isFalse);
      expect(find.text('Không tìm thấy đầu sách #25'), findsOneWidget);
    },
  );

  testWidgets(
    'SavedBooksScreen displays real book metadata, optimistic unsave, rollback on failure, and syncs back to DetailScreen',
    (WidgetTester tester) async {
      bool failNextDelete = true;
      final serverWishlist = <Map<String, dynamic>>[
        {
          'wishlistId': 'w-101',
          'bookId': 101,
          'title': 'Trí tuệ nhân tạo hiện đại',
          'author': 'PGS.TS Nguyễn Văn C',
          'category': 'Trí tuệ nhân tạo',
          'department': 'Công nghệ thông tin',
          'location': 'Tầng 3 / Dãy B / Kệ 02',
          'availableCopies': 2,
          'copies': 4,
          'status': 'available',
        },
      ];

      ApiService.testClient = MockClient((request) async {
        final path = request.url.path;
        if (request.method == 'GET' && path.endsWith('/users/wishlist')) {
          return _jsonResponse(serverWishlist);
        }
        if (request.method == 'DELETE' &&
            path.endsWith('/users/wishlist/101')) {
          if (failNextDelete) {
            failNextDelete = false;
            return _jsonResponse({
              'message': 'Không thể kết nối máy chủ thư viện.',
            }, 503);
          }
          serverWishlist.removeWhere((item) => item['bookId'] == 101);
          return http.Response('', 204);
        }
        return _jsonResponse({});
      });

      await tester.pumpWidget(const MaterialApp(home: SavedBooksScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Trí tuệ nhân tạo hiện đại'), findsAtLeastNWidgets(1));
      expect(find.text('PGS.TS Nguyễn Văn C'), findsAtLeastNWidgets(1));
      expect(
        find.text('Công nghệ thông tin · Trí tuệ nhân tạo'),
        findsOneWidget,
      );
      expect(find.text('Vị trí: Tầng 3 / Dãy B / Kệ 02'), findsOneWidget);
      expect(find.text('Còn sách (2/4 bản sẵn sàng)'), findsOneWidget);
      expect(WishlistService.isSavedSync(101), isTrue);

      // 1st attempt fails -> optimistic removal rolls back and restores item
      await tester.tap(find.byKey(const ValueKey('unsave-book-button-101')));
      await tester.pumpAndSettle();

      expect(find.text('Trí tuệ nhân tạo hiện đại'), findsAtLeastNWidgets(1));
      expect(find.text('Không thể kết nối máy chủ thư viện.'), findsOneWidget);
      expect(WishlistService.isSavedSync(101), isTrue);

      // 2nd attempt succeeds -> item is removed and empty state is shown
      await tester.tap(find.byKey(const ValueKey('unsave-book-button-101')));
      await tester.pumpAndSettle();

      expect(find.text('Trí tuệ nhân tạo hiện đại'), findsNothing);
      expect(find.text('Bạn chưa lưu cuốn sách nào.'), findsOneWidget);
      expect(WishlistService.isSavedSync(101), isFalse);
    },
  );

  test(
    'Switching accounts or logging out clears WishlistService session cache immediately',
    () async {
      ApiService.testClient = MockClient((request) async {
        if (request.method == 'GET' &&
            request.url.path.endsWith('/users/wishlist')) {
          return _jsonResponse([
            {
              'wishlistId': 'w-1',
              'bookId': 77,
              'title': 'Sách của tài khoản A',
              'author': 'HUIT',
              'availableCopies': 1,
            },
          ]);
        }
        return _jsonResponse({});
      });

      final listA = await WishlistService.getMine();
      expect(listA, hasLength(1));
      expect(WishlistService.isSavedSync(77), isTrue);
      expect(WishlistService.savedCount, 1);

      // Switch to another token -> cache must clear immediately
      await AuthService.saveToken('token-account-B');
      expect(WishlistService.isSavedSync(77), isFalse);
      expect(WishlistService.savedCount, 0);

      // Clear local session (logout) -> unauthenticated save must fail with 401
      await AuthService.clearLocalSession();
      await expectLater(
        () => WishlistService.save(77),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 401),
        ),
      );
      expect(WishlistService.isSavedSync(77), isFalse);
    },
  );
}
