import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import 'package:mobile_app/services/loan_service.dart';

void main() {
  testWidgets(
    'borrowing header and status remain readable on a narrow screen with large text',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.8)),
            child: child!,
          ),
          home: Scaffold(
            appBar: AppBar(
              titleSpacing: 0,
              title: const BorrowedScreenAppBarTitle(),
              actions: const [
                IconButton(onPressed: null, icon: Icon(Icons.refresh)),
                IconButton(onPressed: null, icon: Icon(Icons.logout)),
              ],
            ),
            body: const ColoredBox(
              color: Color(0xFF0284C7),
              child: Padding(
                padding: EdgeInsets.all(16),
                child: BorrowingLimitSummary(
                  openItems: 2,
                  maximumItems: 3,
                  remainingSlots: 1,
                  cardStatus: 'ĐANG CHỜ XÁC MINH THẺ',
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Quản lý mượn & Đặt giữ'), findsOneWidget);
      expect(find.text('ĐANG CHỜ XÁC MINH THẺ'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  test('pickup QR is available only before checkout', () {
    expect(
      LoanService.canShowPickupQr({
        'status': 'RESERVED',
        'itemStatus': 'RESERVED',
      }),
      isTrue,
    );
    expect(
      LoanService.canShowPickupQr({
        'status': 'PENDING',
        'itemStatus': 'PENDING',
      }),
      isTrue,
    );
    expect(
      LoanService.canShowPickupQr({
        'status': 'BORROWING',
        'itemStatus': 'BORROWING',
      }),
      isFalse,
    );
    expect(
      LoanService.canShowPickupQr({
        'status': 'OVERDUE',
        'itemStatus': 'BORROWING',
      }),
      isFalse,
    );
  });

  test('return QR is available only while an item is still on loan', () {
    expect(
      LoanService.canShowReturnQr({
        'status': 'BORROWING',
        'itemStatus': 'BORROWING',
      }),
      isTrue,
    );
    expect(
      LoanService.canShowReturnQr({
        'status': 'OVERDUE',
        'itemStatus': 'OVERDUE',
      }),
      isTrue,
    );
    expect(
      LoanService.canShowReturnQr({
        'status': 'RESERVED',
        'itemStatus': 'RESERVED',
      }),
      isFalse,
    );
    expect(
      LoanService.canShowReturnQr({
        'status': 'RETURNED',
        'itemStatus': 'RETURNED',
        'returnDate': '2026-11-02T12:00:00',
      }),
      isFalse,
    );
  });

  testWidgets(
    'DetailScreen displays title, author, category, department, location, and available copies',
    (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: DetailScreen(
            book: {
              'bookId': 16,
              'title': 'Cấu trúc dữ liệu và giải thuật',
              'author': 'Nguyễn Văn A',
              'category': {'name': 'Công nghệ thông tin'},
              'department': 'Khoa Công nghệ Thông tin',
              'location': 'Tầng 2 / Kệ CNTT / Ngăn Giải thuật',
              'availableCopies': 2,
              'copies': 5,
              'isbn': '9786040000016',
            },
          ),
        ),
      );
      await tester.pump();

      expect(
        find.text('Cấu trúc dữ liệu và giải thuật'),
        findsAtLeastNWidgets(1),
      );
      expect(find.text('Tác giả: Nguyễn Văn A'), findsOneWidget);
      expect(find.text('Thể loại: Công nghệ thông tin'), findsOneWidget);
      expect(
        find.text('Chuyên ngành: Khoa Công nghệ Thông tin'),
        findsOneWidget,
      );
      expect(find.text('Tầng 2 / Kệ CNTT / Ngăn Giải thuật'), findsOneWidget);
      expect(find.text('Bản sao sẵn sàng: 2/5'), findsOneWidget);
      expect(find.text('ĐĂNG KÝ MƯỢN SÁCH'), findsOneWidget);
    },
  );

  testWidgets(
    'DetailScreen blocks borrowing when book has 0 physical copies and switches to waitlist when physical copies are all borrowed',
    (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: DetailScreen(
            book: {
              'bookId': 99,
              'title': 'Sách chưa có bản sao vật lý',
              'author': 'HUIT',
              'availableCopies': 0,
              'copies': 0,
              'isDigital': false,
            },
          ),
        ),
      );
      await tester.pump();

      expect(find.text('KHÔNG CÒN BẢN SAO ĐỂ MƯỢN'), findsOneWidget);
      final disabledButton = tester.widget<ElevatedButton>(
        find.byType(ElevatedButton),
      );
      expect(disabledButton.onPressed, isNull);

      await tester.pumpWidget(
        const MaterialApp(
          home: DetailScreen(
            book: {
              'bookId': 100,
              'title': 'Sách đang được mượn hết',
              'author': 'HUIT',
              'availableCopies': 0,
              'copies': 3,
              'isDigital': false,
            },
          ),
        ),
      );
      await tester.pump();

      expect(find.text('ĐĂNG KÝ HÀNG ĐỢI (WAITLIST)'), findsOneWidget);
      final waitlistButton = tester.widget<ElevatedButton>(
        find.byType(ElevatedButton),
      );
      expect(waitlistButton.onPressed, isNotNull);
    },
  );

  test(
    'LoanService.normalizeLoan separates waiting pickup, active borrowing, overdue, and cancelled/returned history using Backend policy days',
    () {
      final reserved = LoanService.normalizeLoan({
        'ticketId': 101,
        'detailId': 201,
        'bookId': 16,
        'bookTitle': 'Clean Architecture',
        'status': 'RESERVED',
        'itemStatus': 'PENDING',
        'createdAt': '2026-11-01T09:00:00',
        'pickupDeadline': '2026-11-01T17:00:00',
        'dueDate': '2026-11-11T17:00:00',
        'daysRemaining': 10,
      }, loanDays: 14);

      expect(reserved['isWaitingPickup'], isTrue);
      expect(reserved['isBorrowing'], isFalse);
      expect(reserved['isHistory'], isFalse);
      expect(reserved['pickupDeadlineDisplay'], '17:00 01/11/2026');
      expect(reserved['dueDateDisplay'], '11/11/2026');
      expect(LoanService.isOpenLoanItem(reserved), isTrue);
      expect(reserved['progress'], closeTo((14 - 10) / 14, 0.001));

      final overdue = LoanService.normalizeLoan({
        'ticketId': 102,
        'detailId': 202,
        'bookId': 17,
        'bookTitle': 'Trí tuệ nhân tạo',
        'status': 'BORROWING',
        'itemStatus': 'BORROWING',
        'createdAt': '2026-10-15T09:00:00',
        'dueDate': '2026-10-25T17:00:00',
        'daysRemaining': -3,
      }, loanDays: 10);

      expect(overdue['status'], 'OVERDUE');
      expect(overdue['isBorrowing'], isTrue);
      expect(overdue['isOverdue'], isTrue);
      expect(LoanService.isOpenLoanItem(overdue), isTrue);

      final cancelled = LoanService.normalizeLoan({
        'ticketId': 103,
        'detailId': 203,
        'bookId': 18,
        'bookTitle': 'Cơ sở dữ liệu',
        'status': 'CANCELLED',
        'itemStatus': 'CANCELLED',
        'createdAt': '2026-11-01T08:00:00',
      }, loanDays: 10);

      expect(cancelled['isWaitingPickup'], isFalse);
      expect(cancelled['isBorrowing'], isFalse);
      expect(cancelled['isHistory'], isTrue);
      expect(LoanService.isOpenLoanItem(cancelled), isFalse);
    },
  );
}
