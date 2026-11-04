import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/widgets/ticket_qr_dialog.dart';

void main() {
  testWidgets('TicketQrDialog displays QR payload, copy fallback, and expiry', (
    tester,
  ) async {
    const payload = 'HUITQR1.encoded-claims.encoded-signature';

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: TicketQrDialog(
            ticketId: 21,
            payload: payload,
            expiresAt: '2026-11-02T05:40:00Z',
          ),
        ),
      ),
    );

    expect(find.text('QR nhận sách – Phiếu #21'), findsOneWidget);
    expect(find.byKey(const Key('ticket-qr-payload')), findsOneWidget);
    expect(find.text(payload), findsOneWidget);
    expect(find.text('Sao chép mã HUITQR1'), findsOneWidget);
    expect(find.byKey(const Key('ticket-qr-expiry')), findsOneWidget);
  });

  testWidgets('TicketQrDialog identifies a return QR', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: TicketQrDialog(
            ticketId: 22,
            payload: 'HUITQR1.claims.signature',
            purpose: TicketQrPurpose.returnBook,
          ),
        ),
      ),
    );

    expect(find.text('QR trả sách – Phiếu #22'), findsOneWidget);
    expect(
      find.textContaining('tiếp nhận và kiểm tra sách trả'),
      findsOneWidget,
    );
  });
}
