import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../services/librarian_qr_service.dart';

typedef TicketQrIssuer = Future<Map<String, dynamic>> Function(int ticketId);

enum TicketQrPurpose { pickup, returnBook }

Future<void> showTicketQrDialog({
  required BuildContext context,
  required int ticketId,
  TicketQrPurpose purpose = TicketQrPurpose.pickup,
  TicketQrIssuer issueTicket = LibrarianQrService.issueTicket,
}) async {
  final messenger = ScaffoldMessenger.maybeOf(context);
  try {
    final response = await issueTicket(ticketId);
    if (!context.mounted) return;

    final payload = response['payload']?.toString().trim() ?? '';
    if (!LibrarianQrService.isSignedPayloadFormat(payload)) {
      throw const FormatException('Backend không trả về mã QR phiếu hợp lệ.');
    }

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => TicketQrDialog(
        ticketId: ticketId,
        payload: payload,
        expiresAt: response['expiresAt']?.toString(),
        purpose: purpose,
        onCopied: () {
          messenger?.hideCurrentSnackBar();
          messenger?.showSnackBar(
            const SnackBar(
              content: Text('Đã sao chép mã HUITQR1 để thủ thư nhập thủ công.'),
            ),
          );
        },
      ),
    );
  } catch (error) {
    if (!context.mounted) return;
    messenger?.hideCurrentSnackBar();
    messenger?.showSnackBar(SnackBar(content: Text(_friendlyQrError(error))));
  }
}

class TicketQrDialog extends StatelessWidget {
  final int ticketId;
  final String payload;
  final String? expiresAt;
  final TicketQrPurpose purpose;
  final VoidCallback? onCopied;

  const TicketQrDialog({
    super.key,
    required this.ticketId,
    required this.payload,
    this.expiresAt,
    this.purpose = TicketQrPurpose.pickup,
    this.onCopied,
  });

  @override
  Widget build(BuildContext context) {
    final expiryText = _formatExpiry(expiresAt);
    final qrSize = MediaQuery.sizeOf(context).width.clamp(180.0, 230.0);
    final isReturn = purpose == TicketQrPurpose.returnBook;

    return AlertDialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      title: Text(
        '${isReturn ? 'QR trả sách' : 'QR nhận sách'} – Phiếu #$ticketId',
      ),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: SizedBox.square(
                  dimension: qrSize,
                  child: QrImageView(data: payload),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                isReturn
                    ? 'Đưa mã này cho thủ thư quét để tiếp nhận và kiểm tra sách trả. Nếu camera không hoạt động, hãy sao chép chuỗi bên dưới để thủ thư nhập thủ công.'
                    : 'Đưa mã này cho thủ thư quét tại quầy để xác nhận giao sách. Nếu camera không hoạt động, hãy sao chép chuỗi bên dưới để thủ thư nhập thủ công.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 13),
              ),
              const SizedBox(height: 12),
              const Text(
                'Mã nhập thủ công',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 6),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey.shade300),
                ),
                child: SelectableText(
                  payload,
                  key: const Key('ticket-qr-payload'),
                  style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                expiryText == null
                    ? 'Mã có thời hạn ngắn. Nếu hết hạn, hãy mở lại để tạo mã mới.'
                    : 'Mã hết hạn lúc $expiryText. Nếu hết hạn, hãy mở lại để tạo mã mới.',
                key: const Key('ticket-qr-expiry'),
                style: TextStyle(fontSize: 12, color: Colors.orange.shade900),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                key: const Key('copy-ticket-qr-payload'),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: payload));
                  onCopied?.call();
                },
                icon: const Icon(Icons.copy),
                label: const Text('Sao chép mã HUITQR1'),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Đóng'),
        ),
      ],
    );
  }
}

String? _formatExpiry(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  final parsed = DateTime.tryParse(raw.trim());
  if (parsed == null) return raw.trim();
  final local = parsed.toLocal();
  String twoDigits(int value) => value.toString().padLeft(2, '0');
  return '${twoDigits(local.hour)}:${twoDigits(local.minute)} '
      '${twoDigits(local.day)}/${twoDigits(local.month)}/${local.year}';
}

String _friendlyQrError(Object error) {
  if (error is FormatException) return error.message;
  return error.toString().replaceFirst('Exception: ', '');
}
