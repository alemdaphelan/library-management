import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/screens/librarian_qr_screen.dart';
import 'package:mobile_app/services/librarian_qr_service.dart';

void main() {
  test('HUIT signed QR and physical-copy barcode are separate formats', () {
    expect(
      LibrarianQrService.isSignedPayloadFormat(
        'HUITQR1.encoded-claims.encoded-signature',
      ),
      isTrue,
    );
    expect(LibrarianQrService.isSignedPayloadFormat('BC-541278-001'), isFalse);
    expect(
      LibrarianQrService.isSignedPayloadFormat('HUITQR1.invalid'),
      isFalse,
    );
  });

  testWidgets('manual QR dialog can be cancelled repeatedly without crashing', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: ElevatedButton(
              onPressed: () => showDialog<String>(
                context: context,
                builder: (_) => const LibrarianQrManualInputDialog(),
              ),
              child: const Text('Mở nhập QR'),
            ),
          ),
        ),
      ),
    );

    for (var attempt = 0; attempt < 2; attempt++) {
      await tester.tap(find.text('Mở nhập QR'));
      await tester.pumpAndSettle();
      expect(find.text('Nhập dữ liệu QR'), findsOneWidget);

      await tester.tap(find.byKey(const Key('librarianQrManualCancel')));
      await tester.pumpAndSettle();
      expect(find.text('Nhập dữ liệu QR'), findsNothing);
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('manual QR dialog rejects barcode and accepts signed QR', (
    tester,
  ) async {
    String? result;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: ElevatedButton(
              onPressed: () async {
                result = await showDialog<String>(
                  context: context,
                  builder: (_) => const LibrarianQrManualInputDialog(),
                );
              },
              child: const Text('Mở nhập QR'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Mở nhập QR'));
    await tester.pumpAndSettle();
    final input = find.byKey(const Key('librarianQrManualInput'));

    await tester.enterText(input, 'BC-541278-001');
    await tester.tap(find.byKey(const Key('librarianQrManualSubmit')));
    await tester.pump();
    expect(
      find.text('Đây là barcode bản sao sách, không phải QR phiếu/thẻ HUIT.'),
      findsOneWidget,
    );

    const signedPayload = 'HUITQR1.encoded-claims.encoded-signature';
    await tester.enterText(input, signedPayload);
    await tester.tap(find.byKey(const Key('librarianQrManualSubmit')));
    await tester.pumpAndSettle();

    expect(result, signedPayload);
    expect(find.text('Nhập dữ liệu QR'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
