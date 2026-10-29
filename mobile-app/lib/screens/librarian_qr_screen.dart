import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../services/auth_service.dart';
import '../services/librarian_qr_service.dart';

class LibrarianQrScreen extends StatefulWidget {
  const LibrarianQrScreen({super.key});

  @override
  State<LibrarianQrScreen> createState() => _LibrarianQrScreenState();
}

class _LibrarianQrScreenState extends State<LibrarianQrScreen> {
  final MobileScannerController _scannerController = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
  );
  final TextEditingController _barcodesController = TextEditingController();
  final TextEditingController _returnNoteController = TextEditingController();

  bool _checkingRole = true;
  bool _authorized = false;
  bool _resolving = false;
  bool _submitting = false;
  String? _payload;
  String? _error;
  String? _success;
  Map<String, dynamic>? _resolved;

  @override
  void initState() {
    super.initState();
    unawaited(_checkRole());
  }

  Future<void> _checkRole() async {
    final user = await AuthService.getCurrentUser();
    final role = (user?['role'] ?? user?['roleId'] ?? '')
        .toString()
        .toUpperCase();
    if (!mounted) return;
    setState(() {
      _authorized = role == 'LIBRARIAN' || role == 'ADMIN';
      _checkingRole = false;
    });
  }

  void _onDetect(BarcodeCapture capture) {
    if (_resolving || _resolved != null) return;
    final value = capture.barcodes
        .map((barcode) => barcode.rawValue?.trim())
        .whereType<String>()
        .firstWhere((value) => value.isNotEmpty, orElse: () => '');
    if (value.isNotEmpty) unawaited(_resolve(value));
  }

  Future<void> _resolve(String payload) async {
    if (_resolving) return;
    setState(() {
      _resolving = true;
      _error = null;
      _success = null;
    });
    await _scannerController.stop();
    try {
      final result = await LibrarianQrService.resolve(payload);
      final items = (result['items'] as List<dynamic>? ?? const []);
      final barcodes = items
          .map((item) => item is Map ? item['barcode']?.toString() : null)
          .whereType<String>()
          .where((value) => value.isNotEmpty)
          .join(', ');
      if (!mounted) return;
      setState(() {
        _payload = payload;
        _resolved = result;
        _barcodesController.text = barcodes;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = _friendlyError(error));
    } finally {
      if (mounted) setState(() => _resolving = false);
    }
  }

  Future<void> _resetScanner() async {
    setState(() {
      _payload = null;
      _resolved = null;
      _error = null;
      _success = null;
      _barcodesController.clear();
      _returnNoteController.clear();
    });
    await _scannerController.start();
  }

  Future<void> _showManualInput() async {
    final controller = TextEditingController();
    final payload = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Nhập dữ liệu QR'),
        content: TextField(
          controller: controller,
          autofocus: true,
          minLines: 2,
          maxLines: 5,
          decoration: const InputDecoration(
            hintText: 'Dán chuỗi HUITQR1...',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Xác thực'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (payload != null && payload.isNotEmpty) await _resolve(payload);
  }

  List<String> get _barcodes => _barcodesController.text
      .split(RegExp(r'[,;\n]'))
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .toSet()
      .toList();

  Future<void> _performAction(String action) async {
    if (_submitting || _resolved == null) return;
    final barcodes = _barcodes;
    if (barcodes.isEmpty) {
      setState(() => _error = 'Cần ít nhất một barcode sách để xử lý.');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
      _success = null;
    });
    try {
      final Map<String, dynamic> response;
      if (action == 'CHECKOUT') {
        final ticketId = (_resolved!['ticketId'] as num?)?.toInt();
        if (ticketId == null) throw Exception('Phiếu mượn không hợp lệ.');
        response = await LibrarianQrService.checkout(ticketId, barcodes);
      } else {
        response = await LibrarianQrService.returnBooks(
          barcodes,
          note: _returnNoteController.text,
        );
      }
      if (!mounted) return;
      setState(() {
        _success =
            response['message']?.toString() ??
            (action == 'CHECKOUT'
                ? 'Checkout thành công.'
                : 'Trả sách thành công.');
      });
      if (_payload != null) await _resolve(_payload!);
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = _friendlyError(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _friendlyError(Object error) {
    final message = error.toString().replaceFirst('Exception: ', '').trim();
    if (message.contains('410') || message.toLowerCase().contains('hết hạn')) {
      return 'Mã QR đã hết hạn. Yêu cầu bạn đọc tạo mã mới.';
    }
    return message.isEmpty ? 'Không thể xác thực mã QR.' : message;
  }

  @override
  void dispose() {
    _scannerController.dispose();
    _barcodesController.dispose();
    _returnNoteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Quét QR tại quầy')),
      body: _checkingRole
          ? const Center(child: CircularProgressIndicator())
          : !_authorized
          ? const _AccessDenied()
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (_resolved == null) _buildScanner(),
                if (_resolving)
                  const Padding(
                    padding: EdgeInsets.all(20),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                if (_error != null)
                  _MessageCard(
                    icon: Icons.error_outline,
                    color: Colors.red,
                    message: _error!,
                  ),
                if (_success != null)
                  _MessageCard(
                    icon: Icons.check_circle_outline,
                    color: Colors.green,
                    message: _success!,
                  ),
                if (_resolved != null) _buildResolvedCard(),
              ],
            ),
    );
  }

  Widget _buildScanner() {
    return Column(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: SizedBox(
            height: 320,
            child: Stack(
              fit: StackFit.expand,
              children: [
                MobileScanner(
                  controller: _scannerController,
                  onDetect: _onDetect,
                ),
                Center(
                  child: Container(
                    width: 220,
                    height: 220,
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.white, width: 3),
                      borderRadius: BorderRadius.circular(20),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Đặt mã QR phiếu mượn vào khung. Mã giả, bị sửa hoặc hết hạn sẽ bị Backend từ chối.',
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _showManualInput,
                icon: const Icon(Icons.keyboard),
                label: const Text('Nhập mã thủ công'),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filledTonal(
              tooltip: 'Bật/tắt đèn',
              onPressed: _scannerController.toggleTorch,
              icon: const Icon(Icons.flashlight_on_outlined),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildResolvedCard() {
    final result = _resolved!;
    final type = result['type']?.toString() ?? '';
    final actions = (result['allowedActions'] as List<dynamic>? ?? const [])
        .map((value) => value.toString())
        .toSet();
    final items = result['items'] as List<dynamic>? ?? const [];

    return Card(
      margin: const EdgeInsets.only(top: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.verified, color: Colors.green),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    type == 'LOAN_TICKET'
                        ? 'Phiếu #${result['ticketId']} · ${result['ticketStatus']}'
                        : 'Thẻ bạn đọc hợp lệ',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                IconButton(
                  tooltip: 'Quét mã khác',
                  onPressed: _resetScanner,
                  icon: const Icon(Icons.qr_code_scanner),
                ),
              ],
            ),
            const Divider(),
            Text('Bạn đọc: ${result['readerName'] ?? 'Chưa cập nhật'}'),
            Text('MSSV: ${result['mssv'] ?? '—'}'),
            if (type == 'USER_CARD') ...[
              Text('Trạng thái thẻ: ${result['cardStatus'] ?? '—'}'),
              Text(
                'Số phiếu đang hoạt động: ${result['activeLoanCount'] ?? 0}',
              ),
              Text('Công nợ: ${result['totalDebt'] ?? 0} đ'),
            ],
            if (items.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text(
                'Tài liệu',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              ...items.map((item) {
                final map = Map<String, dynamic>.from(item as Map);
                return ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.menu_book_outlined),
                  title: Text(map['bookTitle']?.toString() ?? 'Tài liệu'),
                  subtitle: Text('Barcode: ${map['barcode'] ?? '—'}'),
                );
              }),
            ],
            if (actions.contains('CHECKOUT') || actions.contains('RETURN')) ...[
              const SizedBox(height: 8),
              TextField(
                controller: _barcodesController,
                decoration: const InputDecoration(
                  labelText: 'Barcode sách (ngăn cách bằng dấu phẩy)',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
            if (actions.contains('RETURN')) ...[
              const SizedBox(height: 10),
              TextField(
                controller: _returnNoteController,
                maxLength: 500,
                decoration: const InputDecoration(
                  labelText: 'Tình trạng khi trả (không bắt buộc)',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
            const SizedBox(height: 12),
            if (actions.contains('CHECKOUT'))
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _submitting
                      ? null
                      : () => _performAction('CHECKOUT'),
                  icon: const Icon(Icons.output),
                  label: Text(
                    _submitting ? 'Đang xử lý...' : 'Xác nhận cho mượn',
                  ),
                ),
              ),
            if (actions.contains('RETURN'))
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _submitting
                      ? null
                      : () => _performAction('RETURN'),
                  icon: const Icon(Icons.assignment_return),
                  label: Text(
                    _submitting ? 'Đang xử lý...' : 'Xác nhận trả sách',
                  ),
                ),
              ),
            if (actions.contains('REVIEW'))
              const Text(
                'Phiếu đang chờ duyệt. Hãy duyệt hoặc từ chối trên màn hình quản lý phiếu.',
              ),
          ],
        ),
      ),
    );
  }
}

class _AccessDenied extends StatelessWidget {
  const _AccessDenied();

  @override
  Widget build(BuildContext context) => const Center(
    child: Padding(
      padding: EdgeInsets.all(24),
      child: Text(
        'Chức năng quét QR chỉ dành cho thủ thư hoặc quản trị viên.',
        textAlign: TextAlign.center,
      ),
    ),
  );
}

class _MessageCard extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String message;

  const _MessageCard({
    required this.icon,
    required this.color,
    required this.message,
  });

  @override
  Widget build(BuildContext context) => Card(
    color: color.withValues(alpha: 0.08),
    child: ListTile(
      leading: Icon(icon, color: color),
      title: Text(message),
    ),
  );
}
