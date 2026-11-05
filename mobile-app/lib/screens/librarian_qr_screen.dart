import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../services/auth_service.dart';
import '../services/librarian_qr_service.dart';
import '../services/loan_service.dart';

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
  final Map<int, _ReturnInspectionDraft> _returnDrafts = {};

  bool _checkingRole = true;
  bool _authorized = false;
  bool _resolving = false;
  bool _submitting = false;
  String? _error;
  String? _success;
  Map<String, dynamic>? _resolved;
  String? _operationKey;
  String? _pendingAction;
  List<Map<String, dynamic>>? _pendingReturnItems;

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
    if (_resolving || _submitting) return;
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
          .join('\n');
      if (!mounted) return;
      setState(() {
        _resolved = result;
        _operationKey = null;
        _pendingAction = null;
        _pendingReturnItems = null;
        _barcodesController.text = barcodes;
        _returnDrafts
          ..clear()
          ..addEntries(
            items.whereType<Map>().where(LibrarianQrService.isReturnable).map((
              item,
            ) {
              final detailId = (item['detailId'] as num?)?.toInt();
              return detailId == null
                  ? null
                  : MapEntry(detailId, _ReturnInspectionDraft());
            }).whereType<MapEntry<int, _ReturnInspectionDraft>>(),
          );
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = _friendlyError(error));
    } finally {
      if (mounted) {
        setState(() => _resolving = false);
        if (_resolved == null) await _scannerController.start();
      }
    }
  }

  Future<void> _resetScanner() async {
    if (_submitting || _resolving) return;
    setState(() {
      _resolved = null;
      _error = null;
      _success = null;
      _barcodesController.clear();
      _returnDrafts.clear();
      _operationKey = null;
      _pendingAction = null;
      _pendingReturnItems = null;
    });
    await _scannerController.start();
  }

  Future<void> _showManualInput() async {
    if (_submitting || _resolving) return;
    await _scannerController.stop();
    if (!mounted) return;

    final payload = await showDialog<String>(
      context: context,
      builder: (context) => const LibrarianQrManualInputDialog(),
    );
    if (!mounted) return;

    if (payload == null) {
      await _scannerController.start();
      return;
    }
    await _resolve(payload);
  }

  List<String> get _barcodes => _barcodesController.text
      .split(RegExp(r'[,;\n]'))
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .toSet()
      .toList();

  Future<void> _performAction(String action) async {
    if (_submitting || _resolved == null) return;
    if (_pendingAction != null && _pendingAction != action) return;
    FocusScope.of(context).unfocus();
    final barcodes = _barcodes;
    if (action == 'CHECKOUT' && barcodes.isEmpty) {
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
        _pendingAction = action;
        _operationKey ??= LibrarianQrService.operationKey('checkout');
        response = await LibrarianQrService.checkout(
          ticketId,
          barcodes,
          idempotencyKey: _operationKey,
        );
      } else {
        final inspectionItems =
            _pendingReturnItems ?? _buildReturnInspectionPayload();
        if (inspectionItems.isEmpty) {
          throw Exception(
            'Không còn tài liệu đủ điều kiện nhận trả. Hãy quét lại phiếu.',
          );
        }
        _pendingReturnItems = inspectionItems;
        _pendingAction = action;
        _operationKey ??= LibrarianQrService.operationKey('return-inspection');
        response = await LibrarianQrService.inspectAndReturn(
          inspectionItems,
          idempotencyKey: _operationKey,
        );
      }
      if (!mounted) return;
      LoanService.notifyLoansChanged();
      final successMessage = action == 'CHECKOUT'
          ? response['message']?.toString() ??
                'Đã giao sách và chuyển phiếu sang trạng thái đang mượn.'
          : _returnReceiptMessage(response);
      setState(() {
        _resolved = null;
        _success = successMessage;
        _barcodesController.clear();
        _returnDrafts.clear();
        _operationKey = null;
        _pendingAction = null;
        _pendingReturnItems = null;
      });
      await _scannerController.start();
    } catch (error) {
      if (!mounted) return;
      final message = _friendlyError(error);
      final staleCheckout =
          action == 'CHECKOUT' &&
          (message.toUpperCase().contains('BORROWING') ||
              message.toLowerCase().contains('đã được giao'));
      if (staleCheckout) {
        LoanService.notifyLoansChanged();
        setState(() {
          _resolved = null;
          _barcodesController.clear();
          _returnDrafts.clear();
          _operationKey = null;
          _pendingAction = null;
          _pendingReturnItems = null;
          _error =
              'Phiếu đã được giao sách trước đó và hiện ở trạng thái đang mượn. '
              'Vui lòng quét phiếu khác.';
        });
        await _scannerController.start();
      } else {
        setState(
          () => _error = _pendingAction == null
              ? message
              : '$message\nThử lại sẽ gửi đúng nội dung và mã xử lý trước đó. Quét lại phiếu để kiểm tra trạng thái mới nhất.',
        );
      }
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

  List<Map<String, dynamic>> _buildReturnInspectionPayload() {
    final result = <Map<String, dynamic>>[];
    for (final entry in _returnDrafts.entries) {
      final draft = entry.value;
      final note = draft.note.trim();
      if (draft.condition != 'NORMAL' && note.isEmpty) {
        throw Exception(
          'Vui lòng ghi rõ tình trạng hư hỏng/mất cho từng tài liệu.',
        );
      }
      if (draft.condition == 'DAMAGED_LIGHT' && draft.repairFee <= 0) {
        throw Exception('Hư hỏng nhẹ phải có mức phí sửa chữa lớn hơn 0.');
      }
      if (draft.condition == 'DAMAGED_LIGHT' &&
          draft.pagesAffected != null &&
          (draft.pagesAffected! < 1 || draft.pagesAffected! > 10)) {
        throw Exception('Hư hỏng nhẹ chỉ áp dụng từ 1 đến 10 trang.');
      }
      final cashForeign =
          draft.isLossLike &&
          draft.stillInMarket == 'NO' &&
          draft.settlement == 'CASH' &&
          draft.languageCategory == 'FOREIGN' &&
          (draft.coverPrice ?? 0) > 0;
      if (cashForeign &&
          (draft.originalCurrency.trim().isEmpty ||
              (draft.fxRate ?? 0) <= 0 ||
              draft.fxSource.trim().isEmpty)) {
        throw Exception(
          'Tài liệu ngoại văn có giá bìa cần mã tiền tệ, tỷ giá và nguồn tỷ giá.',
        );
      }

      final item = <String, dynamic>{
        'loanDetailId': entry.key,
        'condition': draft.backendCondition,
        if (note.isNotEmpty) 'returnConditionNote': note,
      };
      if (draft.condition != 'NORMAL') {
        item['penalty'] = draft.toPenalty(entry.key);
      }
      result.add(item);
    }
    if (result.isEmpty) {
      throw Exception('Phiếu không có tài liệu hợp lệ để nhận trả.');
    }
    return result;
  }

  String _returnReceiptMessage(Map<String, dynamic> response) {
    final items = (response['items'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .toList();
    num lateFine = 0;
    num penalty = 0;
    num depositApplied = 0;
    num debtAdded = 0;
    for (final item in items) {
      lateFine += (item['lateFineAdded'] as num?) ?? 0;
      penalty += (item['penaltyAmount'] as num?) ?? 0;
      depositApplied += (item['depositApplied'] as num?) ?? 0;
      debtAdded += (item['debtAdded'] as num?) ?? 0;
    }
    final details = <String>[
      if (lateFine > 0) 'phạt quá hạn ${_money(lateFine)}',
      if (penalty > 0) 'phạt/đền bù ${_money(penalty)}',
      if (depositApplied > 0) 'đã trừ thế chân ${_money(depositApplied)}',
      if (debtAdded > 0) 'công nợ tăng ${_money(debtAdded)}',
    ];
    return 'Đã nhận trả ${items.length} tài liệu'
        '${details.isEmpty ? '.' : ': ${details.join(', ')}.'}';
  }

  String _money(num value) => '${value.toStringAsFixed(0)} đ';

  String _formatDate(dynamic value) {
    final parsed = DateTime.tryParse(value?.toString() ?? '');
    if (parsed == null) return '—';
    final local = parsed.toLocal();
    return '${local.day.toString().padLeft(2, '0')}/'
        '${local.month.toString().padLeft(2, '0')}/${local.year} '
        '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}';
  }

  @override
  void dispose() {
    _scannerController.dispose();
    _barcodesController.dispose();
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
                  onPressed: _submitting || _resolving ? null : _resetScanner,
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
                final detailId = (map['detailId'] as num?)?.toInt();
                return Card.outlined(
                  margin: const EdgeInsets.only(top: 8),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.menu_book_outlined),
                          title: Text(
                            map['bookTitle']?.toString() ?? 'Tài liệu',
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          subtitle: Text('Barcode: ${map['barcode'] ?? '—'}'),
                        ),
                        _InfoRow(
                          label: 'Giá trị tài liệu hiện tại',
                          value:
                              map['priceKnown'] == true &&
                                  map['bookValue'] != null
                              ? '${map['bookValue']} ${map['bookValueCurrency'] ?? 'VND'} (${map['bookValueSource'] ?? ''})'
                              : 'Chưa xác định giá',
                        ),
                        _InfoRow(
                          label: map['borrowedAt'] != null
                              ? 'Ngày mượn'
                              : 'Ngày đăng ký',
                          value: _formatDate(
                            map['borrowedAt'] ?? map['createdAt'],
                          ),
                        ),
                        _InfoRow(
                          label: 'Hạn trả',
                          value: _formatDate(map['dueDate']),
                        ),
                        if (map['returnDate'] != null)
                          _InfoRow(
                            label: 'Ngày trả',
                            value: _formatDate(map['returnDate']),
                          ),
                        if (actions.contains('RETURN') &&
                            detailId != null &&
                            LibrarianQrService.isReturnable(map))
                          AbsorbPointer(
                            absorbing: _submitting || _pendingAction != null,
                            child: _buildReturnInspectionEditor(detailId),
                          ),
                      ],
                    ),
                  ),
                );
              }),
            ],
            if (actions.contains('CHECKOUT') || actions.contains('RETURN')) ...[
              const SizedBox(height: 8),
              TextField(
                controller: _barcodesController,
                readOnly:
                    _submitting ||
                    _pendingAction != null ||
                    actions.contains('RETURN'),
                minLines: 1,
                maxLines: items.length > 1 ? items.length : 1,
                decoration: InputDecoration(
                  labelText: items.length > 1
                      ? 'Barcode các sách'
                      : 'Barcode sách',
                  helperText: items.length > 1 ? 'Mỗi barcode một dòng.' : null,
                  border: const OutlineInputBorder(),
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

  Widget _buildReturnInspectionEditor(int detailId) {
    final draft = _returnDrafts.putIfAbsent(
      detailId,
      _ReturnInspectionDraft.new,
    );
    final incident = draft.condition != 'NORMAL';
    final lostLike =
        draft.condition == 'LOST' || draft.condition == 'DAMAGED_HEAVY';
    final needsCashDetails =
        lostLike && draft.stillInMarket == 'NO' && draft.settlement == 'CASH';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(height: 24),
        DropdownButtonFormField<String>(
          initialValue: draft.condition,
          isExpanded: true,
          itemHeight: null,
          selectedItemBuilder: (context) => const [
            Text('Bình thường', overflow: TextOverflow.ellipsis),
            Text('Hư hỏng nhẹ', overflow: TextOverflow.ellipsis),
            Text('Hư hỏng nặng', overflow: TextOverflow.ellipsis),
            Text('Mất tài liệu', overflow: TextOverflow.ellipsis),
          ],
          decoration: const InputDecoration(
            labelText: 'Tình trạng tài liệu khi trả',
            border: OutlineInputBorder(),
          ),
          items: const [
            DropdownMenuItem(value: 'NORMAL', child: Text('Bình thường')),
            DropdownMenuItem(
              value: 'DAMAGED_LIGHT',
              child: Text('Hư hỏng nhẹ (phí sửa chữa)'),
            ),
            DropdownMenuItem(
              value: 'DAMAGED_HEAVY',
              child: Text('Hư hỏng nặng (xử lý như mất)'),
            ),
            DropdownMenuItem(value: 'LOST', child: Text('Mất tài liệu')),
          ],
          onChanged: _submitting
              ? null
              : (value) => setState(() {
                  draft.condition = value ?? 'NORMAL';
                  draft.resetDependentFields();
                }),
        ),
        if (draft.condition == 'DAMAGED_LIGHT') ...[
          const SizedBox(height: 10),
          TextFormField(
            initialValue: draft.repairFee == 0
                ? ''
                : draft.repairFee.toStringAsFixed(0),
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Phí sửa chữa (VNĐ)',
              border: OutlineInputBorder(),
            ),
            onChanged: (value) =>
                draft.repairFee = double.tryParse(value.trim()) ?? 0,
          ),
          const SizedBox(height: 10),
          TextFormField(
            initialValue: draft.pagesAffected?.toString() ?? '',
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Số trang bị ảnh hưởng (nếu có)',
              border: OutlineInputBorder(),
            ),
            onChanged: (value) =>
                draft.pagesAffected = int.tryParse(value.trim()),
          ),
        ],
        if (lostLike) ...[
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: draft.stillInMarket,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: 'Tài liệu còn lưu hành trên thị trường?',
              border: OutlineInputBorder(),
            ),
            items: const [
              DropdownMenuItem(value: 'YES', child: Text('Có')),
              DropdownMenuItem(value: 'NO', child: Text('Không')),
            ],
            onChanged: (value) => setState(() {
              draft.stillInMarket = value ?? 'YES';
              if (draft.stillInMarket == 'YES') {
                draft.settlement = 'REPLACEMENT';
              }
            }),
          ),
          if (draft.stillInMarket == 'NO') ...[
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: draft.settlement,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Cách đền bù',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(
                  value: 'REPLACEMENT',
                  child: Text('Mua tài liệu thay thế'),
                ),
                DropdownMenuItem(
                  value: 'CASH',
                  child: Text('Đền bù bằng tiền'),
                ),
              ],
              onChanged: (value) =>
                  setState(() => draft.settlement = value ?? 'REPLACEMENT'),
            ),
          ],
        ],
        if (needsCashDetails) ...[
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: draft.languageCategory,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: 'Phân loại ngôn ngữ',
              border: OutlineInputBorder(),
            ),
            items: const [
              DropdownMenuItem(value: 'VIETNAMESE', child: Text('Việt ngữ')),
              DropdownMenuItem(value: 'FOREIGN', child: Text('Ngoại văn')),
            ],
            onChanged: (value) =>
                setState(() => draft.languageCategory = value ?? 'VIETNAMESE'),
          ),
          const SizedBox(height: 10),
          TextFormField(
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Giá bìa (để trống nếu không có)',
              border: OutlineInputBorder(),
            ),
            onChanged: (value) =>
                draft.coverPrice = double.tryParse(value.trim()),
          ),
          if (draft.languageCategory == 'FOREIGN') ...[
            const SizedBox(height: 10),
            TextFormField(
              decoration: const InputDecoration(
                labelText: 'Mã tiền tệ (ví dụ USD)',
                border: OutlineInputBorder(),
              ),
              onChanged: (value) => draft.originalCurrency = value.trim(),
            ),
            const SizedBox(height: 10),
            TextFormField(
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Tỷ giá quy đổi',
                border: OutlineInputBorder(),
              ),
              onChanged: (value) =>
                  draft.fxRate = double.tryParse(value.trim()),
            ),
            const SizedBox(height: 10),
            TextFormField(
              decoration: const InputDecoration(
                labelText: 'Nguồn tỷ giá',
                border: OutlineInputBorder(),
              ),
              onChanged: (value) => draft.fxSource = value.trim(),
            ),
          ],
        ],
        const SizedBox(height: 10),
        TextFormField(
          initialValue: draft.note,
          maxLength: 500,
          decoration: InputDecoration(
            labelText: incident
                ? 'Ghi chú tình trạng (bắt buộc)'
                : 'Ghi chú khi trả (không bắt buộc)',
            border: const OutlineInputBorder(),
          ),
          onChanged: (value) => draft.note = value,
        ),
        const Text(
          'Tiền quá hạn được hệ thống tự tính. Khoản hư hỏng/mất sẽ tự trừ '
          'tiền thế chân trước, phần còn lại ghi vào công nợ bạn đọc.',
          style: TextStyle(fontSize: 12, color: Colors.black54),
        ),
      ],
    );
  }
}

class _ReturnInspectionDraft {
  String condition = 'NORMAL';
  String note = '';
  double repairFee = 0;
  int? pagesAffected;
  String stillInMarket = 'YES';
  String settlement = 'REPLACEMENT';
  String languageCategory = 'VIETNAMESE';
  double? coverPrice;
  String originalCurrency = '';
  double? fxRate;
  String fxSource = '';

  bool get isLossLike => condition == 'LOST' || condition == 'DAMAGED_HEAVY';

  String get backendCondition {
    if (condition == 'LOST') return 'LOST';
    if (condition.startsWith('DAMAGED')) return 'DAMAGED';
    return 'NORMAL';
  }

  void resetDependentFields() {
    repairFee = 0;
    pagesAffected = null;
    stillInMarket = 'YES';
    settlement = 'REPLACEMENT';
    languageCategory = 'VIETNAMESE';
    coverPrice = null;
    originalCurrency = '';
    fxRate = null;
    fxSource = '';
  }

  Map<String, dynamic> toPenalty(int detailId) {
    final lightDamage = condition == 'DAMAGED_LIGHT';
    final cashSettlement =
        isLossLike && stillInMarket == 'NO' && settlement == 'CASH';
    return <String, dynamic>{
      'loanDetailId': detailId,
      'incidentType': condition == 'LOST' ? 'LOST' : 'DAMAGED',
      if (condition.startsWith('DAMAGED'))
        'damageSeverity': lightDamage ? 'LIGHT' : 'HEAVY',
      if (pagesAffected != null) 'pagesAffected': pagesAffected,
      'description': note.trim(),
      'materialType': 'BOOK',
      'languageCategory': languageCategory,
      if (isLossLike) 'stillInMarket': stillInMarket == 'YES',
      'settlementMethod': lightDamage ? 'REPAIR' : settlement,
      if (lightDamage) 'repairFee': repairFee,
      if (cashSettlement && coverPrice != null) 'coverPrice': coverPrice,
      if (cashSettlement && languageCategory == 'FOREIGN') ...{
        if (originalCurrency.trim().isNotEmpty)
          'originalCurrency': originalCurrency.trim().toUpperCase(),
        if (fxRate != null) 'fxRate': fxRate,
        if (fxSource.trim().isNotEmpty) 'fxSource': fxSource.trim(),
      },
    };
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 4),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 95,
          child: Text(label, style: const TextStyle(color: Colors.black54)),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w500),
          ),
        ),
      ],
    ),
  );
}

/// Dialog nhập dự phòng khi thiết bị không quét được camera.
///
/// Controller thuộc vòng đời của dialog để tránh dispose trong lúc hiệu ứng
/// đóng route vẫn đang sử dụng TextField.
class LibrarianQrManualInputDialog extends StatefulWidget {
  const LibrarianQrManualInputDialog({super.key});

  @override
  State<LibrarianQrManualInputDialog> createState() =>
      _LibrarianQrManualInputDialogState();
}

class _LibrarianQrManualInputDialogState
    extends State<LibrarianQrManualInputDialog> {
  final TextEditingController _controller = TextEditingController();
  String? _errorText;

  void _submit() {
    final payload = _controller.text.trim();
    if (payload.isEmpty) {
      setState(() => _errorText = 'Vui lòng nhập dữ liệu QR.');
      return;
    }
    if (!LibrarianQrService.isSignedPayloadFormat(payload)) {
      setState(
        () => _errorText = payload.toUpperCase().startsWith('BC-')
            ? 'Đây là barcode bản sao sách, không phải QR phiếu/thẻ HUIT.'
            : 'QR phải có định dạng HUITQR1.dữ-liệu.chữ-ký.',
      );
      return;
    }
    Navigator.of(context).pop(payload);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    scrollable: true,
    title: const Text('Nhập dữ liệu QR'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          key: const Key('librarianQrManualInput'),
          controller: _controller,
          autofocus: true,
          minLines: 2,
          maxLines: 5,
          decoration: InputDecoration(
            hintText: 'Dán chuỗi HUITQR1...',
            errorText: _errorText,
            border: const OutlineInputBorder(),
          ),
          onSubmitted: (_) => _submit(),
        ),
        const SizedBox(height: 8),
        const Text(
          'HUITQR1 là QR có chữ ký của phiếu mượn hoặc thẻ bạn đọc. '
          'Barcode bản sao sách có dạng BC-... và không nhập tại đây.',
          style: TextStyle(fontSize: 12),
        ),
      ],
    ),
    actions: [
      TextButton(
        key: const Key('librarianQrManualCancel'),
        onPressed: () => Navigator.of(context).pop(),
        child: const Text('Hủy'),
      ),
      FilledButton(
        key: const Key('librarianQrManualSubmit'),
        onPressed: _submit,
        child: const Text('Xác thực'),
      ),
    ],
  );
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
