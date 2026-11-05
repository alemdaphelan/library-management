import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/screens/librarian_qr_screen.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/widgets/edit_profile_sheet.dart';
import 'package:mobile_app/widgets/ticket_qr_dialog.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/mobile_ui_fixture.dart';
import 'support/fake_scanner.dart';

void main() {
  final captureKey = GlobalKey();
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'huit_lib_token': 'ui-test',
      'huit_lib_user_profile': '{}',
    });
    ApiService.testClient = mobileUiClient();
  });
  tearDown(() => ApiService.testClient = null);

  Future<void> draw(
    WidgetTester tester,
    Widget screen, {
    double width = 320,
    double scale = 1,
  }) async {
    tester.view.physicalSize = Size(width, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final sdk = Platform.environment['FLUTTER_ROOT'];
    if (sdk != null) {
      final file = File(
        '$sdk/bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
      );
      if (file.existsSync()) {
        await (FontLoader('Roboto')..addFont(
              Future.value(ByteData.sublistView(file.readAsBytesSync())),
            ))
            .load();
      }
      final icons = File(
        '$sdk/bin/cache/artifacts/material_fonts/materialicons-regular.otf',
      );
      if (icons.existsSync()) {
        final loader = FontLoader('MaterialIcons');
        loader.addFont(
          Future.value(ByteData.sublistView(icons.readAsBytesSync())),
        );
        await loader.load();
      }
    }
    await tester.pumpWidget(
      RepaintBoundary(
        key: captureKey,
        child: MaterialApp(
          theme: ThemeData(useMaterial3: true, fontFamily: 'Roboto'),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: screen,
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();
  }

  Future<void> capture(WidgetTester tester, String name) async {
    final dir = Platform.environment['MOBILE_UI_CAPTURE_DIR'];
    if (dir == null) return;
    await tester.pump();
    await tester.runAsync(() async {
      final boundary = tester.renderObject<RenderRepaintBoundary>(
        find.byKey(captureKey),
      );
      final image = await boundary.toImage(pixelRatio: 1);
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      await Directory(dir).create(recursive: true);
      await File('$dir/$name.png').writeAsBytes(bytes!.buffer.asUint8List());
      image.dispose();
    });
  }

  for (final scale in [1.0, 1.8]) {
    testWidgets('librarian return form at 320px text $scale', (tester) async {
      final previous = MobileScannerPlatform.instance;
      MobileScannerPlatform.instance = UiFakeScanner();
      addTearDown(() => MobileScannerPlatform.instance = previous);
      await (await SharedPreferences.getInstance()).setString(
        'huit_lib_user_profile',
        '{"roleId":"LIBRARIAN","fullName":"Thủ thư kiểm thử"}',
      );
      await draw(tester, const LibrarianQrScreen(), scale: scale);
      final manual = find.text('Nhập mã thủ công');
      await tester.ensureVisible(manual);
      await tester.pumpAndSettle();
      await tester.tap(manual);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('librarianQrManualInput')),
        'HUITQR1.test.claims',
      );
      await tester.tap(find.byKey(const Key('librarianQrManualSubmit')));
      await tester.pumpAndSettle();
      final condition = find.byType(DropdownButtonFormField<String>).first;
      await tester.ensureVisible(condition);
      await tester.pumpAndSettle();
      await tester.tap(condition);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hư hỏng nhẹ (phí sửa chữa)').last);
      await tester.pumpAndSettle();
      expect(find.text('Phí sửa chữa (VNĐ)'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await capture(tester, 'librarian-return-$scale');
      await tester.ensureVisible(condition);
      await tester.pumpAndSettle();
      await tester.tap(condition);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hư hỏng nặng (xử lý như mất)').last);
      await tester.pumpAndSettle();
      var field = find.byType(DropdownButtonFormField<String>).at(1);
      await tester.ensureVisible(field);
      await tester.pumpAndSettle();
      await tester.tap(field);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Không').last);
      await tester.pumpAndSettle();
      field = find.byType(DropdownButtonFormField<String>).at(2);
      await tester.ensureVisible(field);
      await tester.pumpAndSettle();
      await tester.tap(field);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đền bù bằng tiền').last);
      await tester.pumpAndSettle();
      expect(find.text('Phân loại ngôn ngữ'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('penalties at 320px text $scale', (tester) async {
      await draw(tester, const PenaltiesScreen(), scale: scale);
      expect(find.text('450000 đ'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await capture(tester, 'penalties-$scale');
    });

    testWidgets('proposal major dropdown at 320px text $scale', (tester) async {
      await draw(tester, const SuggestBookScreen(), scale: scale);
      final dropdown = find.byType(DropdownButtonFormField<String>);
      await tester.ensureVisible(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(
        find.text('Công nghệ thông tin và hệ thống thông tin quản lý').last,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await capture(tester, 'proposal-$scale');
    });

    testWidgets('borrow confirmation fits 320px text $scale', (tester) async {
      await draw(tester, const DetailScreen(book: uiBook), scale: scale);
      final button = find.byType(ElevatedButton).last;
      expect(tester.widget<ElevatedButton>(button).onPressed, isNotNull);
      await tester.tap(button);
      await tester.pumpAndSettle();
      expect(find.text('Giá cuốn này:'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await capture(tester, 'borrow-sheet-$scale');
    });
  }

  testWidgets('profile sheet fits keyboard and large text', (tester) async {
    await draw(
      tester,
      Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              builder: (_) => const EditProfileSheet(user: uiUser),
            ),
            child: const Text('Edit'),
          ),
        ),
      ),
      scale: 1.8,
    );
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    addTearDown(tester.view.resetViewInsets);
    await tester.enterText(
      find.byKey(const ValueKey('profile-phone-field')),
      '+84 356996264',
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('profile-save-button')),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await capture(tester, 'profile-keyboard');
  });

  testWidgets('ticket QR scrolls on narrow large-text screen', (tester) async {
    await draw(
      tester,
      const Scaffold(
        body: TicketQrDialog(
          ticketId: 123,
          payload: 'HUITQR1.test-claims.test-signature',
          purpose: TicketQrPurpose.returnBook,
        ),
      ),
      scale: 1.8,
    );
    expect(tester.takeException(), isNull);
    await capture(tester, 'ticket-qr');
  });
}
