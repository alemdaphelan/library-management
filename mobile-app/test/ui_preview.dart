// Local UI review only: real app widgets with synthetic API data, no live backend.
// flutter run -d web-server -t test/ui_preview.dart --web-port 8765
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mobile_app/screens/profile_screen.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/screens/librarian_qr_screen.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/widgets/ticket_qr_dialog.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'support/mobile_ui_fixture.dart';
import 'support/fake_scanner.dart';

void main() {
  final client = mobileUiClient();
  http.runWithClient(() async {
    WidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({
      'huit_lib_token': 'ui-preview',
      'huit_lib_user_profile': jsonEncode(uiUser),
    });
    ApiService.testClient = client;
    MobileScannerPlatform.instance = UiFakeScanner();
    runApp(
      MaterialApp(
        title: 'HUIT Mobile UI review',
        theme: ThemeData(
          useMaterial3: true,
          colorSchemeSeed: const Color(0xFF0284c7),
          fontFamily: 'Roboto',
        ),
        home: const _ReviewMenu(),
      ),
    );
  }, () => client);
}

class _ReviewMenu extends StatelessWidget {
  const _ReviewMenu();

  @override
  Widget build(BuildContext context) {
    final screens = <String, Widget>{
      'Hồ sơ': const ProfileScreen(),
      'Công nợ': const PenaltiesScreen(),
      'Đề xuất': const SuggestBookScreen(),
      'Chi tiết sách': const DetailScreen(book: uiBook),
      'Phiếu mượn': const BorrowedScreen(),
    };
    return Scaffold(
      appBar: AppBar(title: const Text('Kiểm tra HUIT Mobile')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'DỮ LIỆU GIẢ LẬP — không kết nối DB, không gửi push hoặc giao dịch thật.',
          ),
          const SizedBox(height: 16),
          for (final entry in screens.entries)
            FilledButton(
              onPressed: () => Navigator.of(
                context,
              ).push(MaterialPageRoute<void>(builder: (_) => entry.value)),
              child: Text(entry.key),
            ),
          FilledButton(
            onPressed: () async {
              final prefs = await SharedPreferences.getInstance();
              await prefs.setString(
                'huit_lib_user_profile',
                jsonEncode({
                  ...uiUser,
                  'role': 'LIBRARIAN',
                  'roleId': 'LIBRARIAN',
                }),
              );
              if (!context.mounted) return;
              await Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const LibrarianQrScreen(),
                ),
              );
              await prefs.setString(
                'huit_lib_user_profile',
                jsonEncode(uiUser),
              );
            },
            child: const Text('Thủ thư nhận trả (camera giả lập)'),
          ),
          OutlinedButton(
            onPressed: () => showDialog<void>(
              context: context,
              builder: (_) => const TicketQrDialog(
                ticketId: 123,
                payload: 'HUITQR1.test-claims.test-signature',
                purpose: TicketQrPurpose.returnBook,
              ),
            ),
            child: const Text('QR trả sách'),
          ),
          OutlinedButton(
            onPressed: () => showDialog<String>(
              context: context,
              builder: (_) => const LibrarianQrManualInputDialog(),
            ),
            child: const Text('Nhập QR thủ công'),
          ),
        ],
      ),
    );
  }
}
