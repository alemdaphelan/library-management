import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/ledger_display.dart';

class LibrarianPaymentScreen extends StatefulWidget {
  final String? initialMssv;

  const LibrarianPaymentScreen({super.key, this.initialMssv});

  @override
  State<LibrarianPaymentScreen> createState() => _LibrarianPaymentScreenState();
}

class _LibrarianPaymentScreenState extends State<LibrarianPaymentScreen>
    with SingleTickerProviderStateMixin {
  final TextEditingController _mssvController = TextEditingController();
  final TextEditingController _debtAmountController = TextEditingController();
  final TextEditingController _debtNoteController = TextEditingController(
    text: 'Thu tiền mặt tại quầy',
  );
  final TextEditingController _depositAmountController = TextEditingController(
    text: '100000',
  );
  final TextEditingController _depositNoteController = TextEditingController(
    text: 'Nạp tiền thế chân tại quầy',
  );

  late TabController _tabController;
  bool _loading = false;
  bool _submitting = false;
  String? _error;
  String? _success;
  Map<String, dynamic>? _readerProfile;

  String _debtMethod = 'CASH';
  String _depositMethod = 'CASH';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    if (widget.initialMssv != null && widget.initialMssv!.trim().isNotEmpty) {
      _mssvController.text = widget.initialMssv!.trim();
      unawaited(_searchReader(_mssvController.text));
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    _mssvController.dispose();
    _debtAmountController.dispose();
    _debtNoteController.dispose();
    _depositAmountController.dispose();
    _depositNoteController.dispose();
    super.dispose();
  }

  String _formatMoney(dynamic amount) {
    final value = num.tryParse(amount?.toString() ?? '');
    if (value == null) return '0 đ';
    final formatted = value
        .toStringAsFixed(0)
        .replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (Match m) => '${m[1]}.',
        );
    return '$formatted đ';
  }

  Future<void> _searchReader(String mssv) async {
    final cleanMssv = mssv.trim();
    if (cleanMssv.isEmpty) {
      setState(() => _error = 'Vui lòng nhập MSSV bạn đọc.');
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final res = await ApiService.get(
        '/users/students/${Uri.encodeComponent(cleanMssv)}/profile',
      );
      if (!mounted) return;
      if (res is! Map) {
        throw Exception('Dữ liệu bạn đọc không đúng định dạng.');
      }

      final profile = Map<String, dynamic>.from(res);
      final card = profile['card'] as Map<String, dynamic>? ?? {};
      final totalDebt = num.tryParse(card['totalDebt']?.toString() ?? '0') ?? 0;

      setState(() {
        _readerProfile = profile;
        _loading = false;
        _debtAmountController.text = totalDebt > 0
            ? totalDebt.toStringAsFixed(0)
            : '';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _readerProfile = null;
        _error = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  void _openQrScanner() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          height: MediaQuery.of(ctx).size.height * 0.7,
          decoration: const BoxDecoration(
            color: Colors.black,
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              AppBar(
                backgroundColor: Colors.black,
                foregroundColor: Colors.white,
                title: const Text('Quét mã thẻ / MSSV bạn đọc'),
                centerTitle: true,
                leading: IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(ctx),
                ),
              ),
              Expanded(
                child: MobileScanner(
                  onDetect: (capture) {
                    final value = capture.barcodes
                        .map((b) => b.rawValue?.trim())
                        .whereType<String>()
                        .firstWhere((v) => v.isNotEmpty, orElse: () => '');
                    if (value.isNotEmpty) {
                      Navigator.pop(ctx);
                      _mssvController.text = value;
                      _searchReader(value);
                    }
                  },
                ),
              ),
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Hướng camera về phía mã QR hoặc mã vạch trên thẻ bạn đọc',
                  style: TextStyle(color: Colors.white70, fontSize: 13),
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _submitDebtPayment() async {
    final student = _readerProfile?['student'] as Map<String, dynamic>?;
    final card = _readerProfile?['card'] as Map<String, dynamic>?;
    if (student == null || card == null) return;

    final userId = student['userId']?.toString();
    if (userId == null || userId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Không tìm thấy ID người dùng.')),
      );
      return;
    }

    final amountText = _debtAmountController.text.trim().replaceAll('.', '');
    final amount = double.tryParse(amountText);
    final totalDebt = num.tryParse(card['totalDebt']?.toString() ?? '0') ?? 0;

    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập số tiền thu hợp lệ.')),
      );
      return;
    }

    if (amount > totalDebt) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Số tiền thu (${_formatMoney(amount)}) không được vượt quá tổng công nợ (${_formatMoney(totalDebt)}).',
          ),
        ),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xác nhận thu tiền phạt'),
        content: Text(
          'Thu ${_formatMoney(amount)} bằng ${_debtMethod == 'CASH' ? 'Tiền mặt' : 'Chuyển khoản'} '
          'từ sinh viên ${student['fullName']} (${student['mssv']})?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF006699),
              foregroundColor: Colors.white,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _submitting = true;
      _error = null;
      _success = null;
    });

    try {
      final idempotencyKey =
          'PAY_${DateTime.now().millisecondsSinceEpoch}_${Random().nextInt(999999)}';
      final payload = {
        'userId': userId,
        'amount': amount,
        'method': _debtMethod,
        'idempotencyKey': idempotencyKey,
        'note': _debtNoteController.text.trim().isNotEmpty
            ? _debtNoteController.text.trim()
            : 'Thu tiền phạt tại quầy',
      };

      final res = await ApiService.post(
        '/api/v1/transactions/payments',
        payload,
      );
      final receiptNumber = res is Map ? res['receiptNumber'] ?? '' : '';

      if (!mounted) return;
      setState(() {
        _submitting = false;
        _success =
            'Đã lập biên nhận thành công${receiptNumber.isNotEmpty ? ' #$receiptNumber' : ''}!';
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(_success!),
          backgroundColor: Colors.green.shade800,
        ),
      );

      await _searchReader(student['mssv']);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _error = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _submitDepositTopUp() async {
    final student = _readerProfile?['student'] as Map<String, dynamic>?;
    if (student == null) return;

    final userId = student['userId']?.toString();
    if (userId == null || userId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Không tìm thấy ID người dùng.')),
      );
      return;
    }

    final amountText = _depositAmountController.text.trim().replaceAll('.', '');
    final amount = double.tryParse(amountText);

    if (amount == null || amount < 1000) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Số tiền nạp tối thiểu là 1.000 đ.')),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xác nhận nạp tiền thế chân'),
        content: Text(
          'Nạp ${_formatMoney(amount)} tiền thế chân cho sinh viên ${student['fullName']} (${student['mssv']}) '
          'bằng ${_depositMethod == 'CASH' ? 'Tiền mặt' : 'Chuyển khoản'}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.green.shade700,
              foregroundColor: Colors.white,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Xác nhận nạp'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _submitting = true;
      _error = null;
      _success = null;
    });

    try {
      final idempotencyKey =
          'DEP_${DateTime.now().millisecondsSinceEpoch}_${Random().nextInt(999999)}';
      final payload = {
        'userId': userId,
        'amount': amount,
        'method': _depositMethod,
        'idempotencyKey': idempotencyKey,
        'note': _depositNoteController.text.trim().isNotEmpty
            ? _depositNoteController.text.trim()
            : 'Nạp tiền thế chân tại quầy',
      };

      await ApiService.post('/api/v1/transactions/deposit-topup', payload);

      if (!mounted) return;
      setState(() {
        _submitting = false;
        _success = 'Nạp ${_formatMoney(amount)} tiền thế chân thành công!';
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(_success!),
          backgroundColor: Colors.green.shade800,
        ),
      );

      await _searchReader(student['mssv']);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _error = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final student = _readerProfile?['student'] as Map<String, dynamic>?;
    final card = _readerProfile?['card'] as Map<String, dynamic>?;
    final totalDebt = num.tryParse(card?['totalDebt']?.toString() ?? '0') ?? 0;
    final depositBalance =
        num.tryParse(card?['depositBalance']?.toString() ?? '0') ?? 0;
    final ledger =
        (_readerProfile?['ledger'] as List?)
            ?.map((e) => Map<String, dynamic>.from(e as Map))
            .toList() ??
        [];

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Thu phí & Cọc tại quầy',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.qr_code_scanner),
            tooltip: 'Quét mã thẻ / MSSV',
            onPressed: _openQrScanner,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Search box
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _mssvController,
                    decoration: InputDecoration(
                      hintText: 'Nhập MSSV (ví dụ: 2001230430)...',
                      prefixIcon: const Icon(Icons.person_search),
                      suffixIcon: _mssvController.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear),
                              onPressed: () {
                                _mssvController.clear();
                                setState(() {
                                  _readerProfile = null;
                                  _error = null;
                                  _success = null;
                                });
                              },
                            )
                          : null,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                      ),
                    ),
                    textInputAction: TextInputAction.search,
                    onSubmitted: _searchReader,
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF006699),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 14,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  onPressed: _loading
                      ? null
                      : () => _searchReader(_mssvController.text),
                  child: _loading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Tìm'),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Alerts
            if (_error != null)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.red.shade200),
                ),
                child: Row(
                  children: [
                    Icon(Icons.error_outline, color: Colors.red.shade700),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Colors.red.shade900,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            if (_success != null)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.green.shade200),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.check_circle_outline,
                      color: Colors.green.shade700,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _success!,
                        style: TextStyle(
                          color: Colors.green.shade900,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            // Prompt when no reader selected
            if (_readerProfile == null && !_loading)
              Container(
                margin: const EdgeInsets.only(top: 24),
                padding: const EdgeInsets.all(28),
                decoration: BoxDecoration(
                  color: Colors.grey.shade50,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  children: [
                    Icon(
                      Icons.badge_outlined,
                      size: 56,
                      color: Colors.grey.shade400,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Kiểm tra và xử lý tài chính tại quầy',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.grey.shade800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Nhập MSSV hoặc bấm biểu tượng quét QR góc trên để tra cứu hồ sơ bạn đọc, thu phạt hoặc nạp cọc thế chân.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),

            // Loaded Reader Card
            if (_readerProfile != null) ...[
              Card(
                elevation: 0,
                color: const Color(0xFF0F172A),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 22,
                            backgroundColor: Colors.white24,
                            child: const Icon(
                              Icons.person,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  student?['fullName'] ?? 'Chưa rõ họ tên',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'MSSV: ${student?['mssv'] ?? ''} • ${student?['department'] ?? ''}',
                                  style: const TextStyle(
                                    color: Colors.white70,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: card?['cardStatus'] == 'ACTIVE'
                                  ? Colors.green.withOpacity(0.2)
                                  : Colors.orange.withOpacity(0.2),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              card?['cardStatus'] == 'ACTIVE'
                                  ? 'Thẻ hoạt động'
                                  : 'Thẻ tạm khóa',
                              style: TextStyle(
                                color: card?['cardStatus'] == 'ACTIVE'
                                    ? Colors.greenAccent
                                    : Colors.orangeAccent,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const Divider(color: Colors.white24, height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'Tổng nợ hiện tại',
                                  style: TextStyle(
                                    color: Colors.white70,
                                    fontSize: 11,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _formatMoney(totalDebt),
                                  style: TextStyle(
                                    color: totalDebt > 0
                                        ? Colors.amberAccent
                                        : Colors.white,
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                const Text(
                                  'Tiền thế chân còn lại',
                                  style: TextStyle(
                                    color: Colors.white70,
                                    fontSize: 11,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _formatMoney(depositBalance),
                                  style: const TextStyle(
                                    color: Colors.greenAccent,
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Action Tabs (Thu nợ phạt vs Nạp tiền cọc)
              Container(
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: TabBar(
                  controller: _tabController,
                  indicator: BoxDecoration(
                    color: const Color(0xFF006699),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  labelColor: Colors.white,
                  unselectedLabelColor: Colors.grey.shade700,
                  labelStyle: const TextStyle(fontWeight: FontWeight.bold),
                  tabs: const [
                    Tab(
                      icon: Icon(Icons.receipt_long, size: 20),
                      text: 'Thu nợ phạt',
                    ),
                    Tab(
                      icon: Icon(Icons.account_balance_wallet, size: 20),
                      text: 'Nạp tiền thế chân',
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Tab contents
              SizedBox(
                height: 380,
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    // Tab 1: Thu nợ phạt
                    _buildDebtPaymentTab(totalDebt),

                    // Tab 2: Nạp cọc
                    _buildDepositTopUpTab(),
                  ],
                ),
              ),

              // Transaction history list
              if (ledger.isNotEmpty) ...[
                const SizedBox(height: 16),
                const Text(
                  'LỊCH SỬ GIAO DỊCH GẦN ĐÂY',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 8),
                ...ledger.take(5).map((item) {
                  final rawType = item['type']?.toString().toUpperCase() ?? '';
                  final type = LedgerDisplay.typeLabel(rawType);
                  final amount = item['amount'];
                  final status = LedgerDisplay.statusLabel(item['status']);
                  final reason = LedgerDisplay.title(item);
                  final isPositive = LedgerDisplay.isPositiveImpact(
                    amount,
                    rawType,
                  );
                  final formattedAmount = LedgerDisplay.formatSignedAmount(
                    amount,
                    rawType,
                  );

                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: Colors.grey.shade200),
                    ),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 16,
                          backgroundColor: isPositive
                              ? Colors.green.shade50
                              : Colors.red.shade50,
                          child: Icon(
                            isPositive
                                ? Icons.arrow_downward
                                : Icons.arrow_upward,
                            size: 16,
                            color: isPositive
                                ? Colors.green.shade700
                                : Colors.red.shade700,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                reason,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 13,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                'Loại: $type • Trạng thái: $status',
                                style: TextStyle(
                                  color: Colors.grey.shade600,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Text(
                          formattedAmount,
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: isPositive
                                ? Colors.green.shade700
                                : Colors.red.shade700,
                          ),
                        ),
                      ],
                    ),
                  );
                }),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildDebtPaymentTab(num totalDebt) {
    if (totalDebt <= 0) {
      return Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: Colors.green.shade50,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.green.shade200),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.check_circle_outline,
              size: 48,
              color: Colors.green.shade700,
            ),
            const SizedBox(height: 8),
            Text(
              'Bạn đọc không có công nợ cần thu',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 15,
                color: Colors.green.shade900,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Tất cả các khoản phí phạt đã được thanh toán hoặc cấn trừ.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.green.shade800, fontSize: 12),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _debtAmountController,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(
                      labelText: 'Số tiền thu (VNĐ)',
                      prefixIcon: Icon(Icons.attach_money),
                      border: OutlineInputBorder(),
                      contentPadding: EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 12,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: () {
                    _debtAmountController.text = totalDebt.toStringAsFixed(0);
                  },
                  child: const Text('Thu hết'),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Method selection
            Wrap(
              spacing: 8,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text(
                  'Phương thức: ',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                ChoiceChip(
                  label: const Text('Tiền mặt (CASH)'),
                  selected: _debtMethod == 'CASH',
                  onSelected: (val) {
                    if (val) setState(() => _debtMethod = 'CASH');
                  },
                ),
                ChoiceChip(
                  label: const Text('Chuyển khoản'),
                  selected: _debtMethod == 'BANK_TRANSFER',
                  onSelected: (val) {
                    if (val) setState(() => _debtMethod = 'BANK_TRANSFER');
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),

            TextField(
              controller: _debtNoteController,
              decoration: const InputDecoration(
                labelText: 'Ghi chú thu tiền',
                border: OutlineInputBorder(),
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
              ),
            ),
            const SizedBox(height: 16),

            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF006699),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              onPressed: _submitting ? null : _submitDebtPayment,
              icon: _submitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.check),
              label: Text(
                _submitting ? 'Đang xử lý...' : 'Xác nhận thu tiền xóa nợ',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDepositTopUpTab() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _depositAmountController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Số tiền nạp thế chân (VNĐ)',
                prefixIcon: Icon(Icons.add_card),
                border: OutlineInputBorder(),
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 12,
                ),
              ),
            ),
            const SizedBox(height: 8),

            // Quick chips
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: [50000, 100000, 200000, 500000].map((val) {
                return ActionChip(
                  label: Text(_formatMoney(val)),
                  onPressed: () {
                    setState(() {
                      _depositAmountController.text = val.toString();
                    });
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 12),

            Wrap(
              spacing: 8,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text(
                  'Phương thức: ',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                ChoiceChip(
                  label: const Text('Tiền mặt (CASH)'),
                  selected: _depositMethod == 'CASH',
                  onSelected: (val) {
                    if (val) setState(() => _depositMethod = 'CASH');
                  },
                ),
                ChoiceChip(
                  label: const Text('Chuyển khoản'),
                  selected: _depositMethod == 'BANK_TRANSFER',
                  onSelected: (val) {
                    if (val) setState(() => _depositMethod = 'BANK_TRANSFER');
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),

            TextField(
              controller: _depositNoteController,
              decoration: const InputDecoration(
                labelText: 'Ghi chú nạp tiền',
                border: OutlineInputBorder(),
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
              ),
            ),
            const SizedBox(height: 16),

            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green.shade700,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              onPressed: _submitting ? null : _submitDepositTopUp,
              icon: _submitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.add_circle_outline),
              label: Text(
                _submitting ? 'Đang xử lý...' : 'Xác nhận nạp tiền cọc',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
