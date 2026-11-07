import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/api_service.dart';
import '../services/ledger_display.dart';

class PenaltiesScreen extends StatefulWidget {
  final String? initialCaseId;
  final String? initialTransactionId;

  const PenaltiesScreen({
    super.key,
    this.initialCaseId,
    this.initialTransactionId,
  });

  @override
  State<PenaltiesScreen> createState() => _PenaltiesScreenState();
}

class _PenaltiesScreenState extends State<PenaltiesScreen> {
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _cases = [];
  Map<String, dynamic> _wallet = {};
  List<Map<String, dynamic>> _entries = [];
  bool _checkedInitialReference = false;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final results = await Future.wait([
        ApiService.get('/api/v1/penalty-cases/my'),
        ApiService.get('/api/v1/transactions/my-ledger'),
        ApiService.get('/api/v1/library-card'),
      ]);
      if (!mounted) return;
      final rawCases = results[0];
      final rawLedger = results[1];

      final parsedCases = (rawCases is List)
          ? rawCases.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];

      final rawCard = results[2];
      if (rawCard is! Map || rawLedger is! List || rawCases is! List) {
        throw Exception('Dữ liệu công nợ không hợp lệ. Vui lòng tải lại.');
      }
      final parsedWallet = Map<String, dynamic>.from(rawCard);
      final parsedEntries = rawLedger
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();

      setState(() {
        _cases = parsedCases;
        _wallet = parsedWallet;
        _entries = parsedEntries;
        _loading = false;
      });

      _verifyReferencedItemIfNeeded();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString().replaceFirst('Exception: ', '');
        _loading = false;
      });
    }
  }

  void _verifyReferencedItemIfNeeded() {
    if (_checkedInitialReference || !mounted) return;
    _checkedInitialReference = true;

    final targetCaseId = widget.initialCaseId?.trim();
    if (targetCaseId != null && targetCaseId.isNotEmpty) {
      final found = _cases.any(
        (c) => (c['caseId'] ?? c['id'])?.toString() == targetCaseId,
      );
      if (!found) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          ScaffoldMessenger.maybeOf(context)?.showSnackBar(
            SnackBar(
              content: Text(
                'Hồ sơ khoản phạt #$targetCaseId không còn tồn tại hoặc bạn không có quyền truy cập.',
              ),
              backgroundColor: Colors.orange.shade800,
            ),
          );
        });
      }
    }
  }

  String _formatMoney(dynamic amount) {
    final value = num.tryParse(amount?.toString() ?? '');
    if (value == null) return 'Chưa có dữ liệu';
    final formatted = value
        .toStringAsFixed(0)
        .replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (Match m) => '${m[1]}.',
        );
    return '$formatted đ';
  }

  String _generateUuid() {
    final random = Random();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40; // RFC 4122 v4
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}';
  }

  void _showPaymentSheet({required bool isDebtPayment}) {
    final totalDebt = num.tryParse(_wallet['totalDebt']?.toString() ?? '0') ?? 0;
    final depositBalance =
        num.tryParse(_wallet['depositBalance']?.toString() ?? '0') ?? 0;

    final defaultAmount = isDebtPayment ? totalDebt : 100000;

    final amountController = TextEditingController(
      text: defaultAmount > 0 ? defaultAmount.toStringAsFixed(0) : '50000',
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        bool isSubmitting = false;
        String? sheetError;

        return StatefulBuilder(
          builder: (context, setSheetState) {
            final enteredAmount =
                num.tryParse(amountController.text.trim().replaceAll('.', '')) ?? 0;

            return Container(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).viewInsets.bottom,
              ),
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
              ),
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        CircleAvatar(
                          radius: 18,
                          backgroundColor: isDebtPayment
                              ? Colors.amber.shade100
                              : Colors.green.shade100,
                          child: Icon(
                            isDebtPayment
                                ? Icons.payment
                                : Icons.account_balance_wallet,
                            color: isDebtPayment
                                ? Colors.amber.shade900
                                : Colors.green.shade800,
                            size: 20,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                isDebtPayment
                                    ? 'Thanh toán nợ phạt'
                                    : 'Nạp tiền thế chân',
                                style: const TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF0F172A),
                                ),
                              ),
                              Text(
                                isDebtPayment
                                    ? 'Tổng nợ hiện tại: ${_formatMoney(totalDebt)}'
                                    : 'Số dư cọc hiện tại: ${_formatMoney(depositBalance)}',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.grey.shade600,
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close),
                          onPressed: () => Navigator.pop(ctx),
                        ),
                      ],
                    ),
                    const Divider(height: 24),

                    // Amount input
                    Text(
                      isDebtPayment
                          ? 'Số tiền muốn thanh toán (VNĐ)'
                          : 'Số tiền muốn nạp (VNĐ)',
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 13),
                    ),
                    const SizedBox(height: 6),
                    TextField(
                      controller: amountController,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10)),
                        prefixIcon: const Icon(Icons.attach_money),
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 12),
                      ),
                      onChanged: (_) => setSheetState(() {}),
                    ),
                    const SizedBox(height: 8),

                    // Quick chips
                    Wrap(
                      spacing: 8,
                      children: (isDebtPayment
                              ? [
                                  if (totalDebt > 0) totalDebt,
                                  if (totalDebt > 50000) 50000,
                                  if (totalDebt > 100000) 100000,
                                ]
                              : [50000, 100000, 200000, 500000])
                          .map((val) {
                        final isFull = isDebtPayment && val == totalDebt;
                        return ActionChip(
                          label: Text(isFull
                              ? 'Toàn bộ nợ (${_formatMoney(val)})'
                              : _formatMoney(val)),
                          onPressed: () {
                            setSheetState(() {
                              amountController.text = val.toStringAsFixed(0);
                            });
                          },
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),

                    if (sheetError != null)
                      Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: Colors.red.shade50,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: Colors.red.shade200),
                        ),
                        child: Text(
                          sheetError!,
                          style: TextStyle(
                              color: Colors.red.shade900, fontSize: 12),
                        ),
                      ),

                    // Clean Automatic Payment Card
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.green.shade50,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.green.shade200),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.bolt, color: Colors.green.shade800),
                              const SizedBox(width: 8),
                              Text(
                                'Cổng thanh toán tự động',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 14,
                                  color: Colors.green.shade900,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            isDebtPayment
                                ? 'Hệ thống sẽ thanh toán trực tuyến ${_formatMoney(enteredAmount)} và tự động cấn trừ công nợ ngay lập tức.'
                                : 'Hệ thống sẽ nạp trực tiếp ${_formatMoney(enteredAmount)} vào Quỹ tiền thế chân của thẻ bạn.',
                            style: TextStyle(
                                color: Colors.green.shade900, fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),

                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isDebtPayment
                            ? Colors.amber.shade700
                            : Colors.green.shade700,
                        foregroundColor:
                            isDebtPayment ? Colors.black87 : Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10)),
                      ),
                      onPressed: isSubmitting
                          ? null
                          : () async {
                              if (enteredAmount <= 0) {
                                setSheetState(() => sheetError =
                                    'Vui lòng nhập số tiền hợp lệ.');
                                return;
                              }
                              if (isDebtPayment && enteredAmount > totalDebt) {
                                setSheetState(() => sheetError =
                                    'Số tiền trả không được vượt quá số nợ hiện tại.');
                                return;
                              }

                              setSheetState(() {
                                isSubmitting = true;
                                sheetError = null;
                              });

                              try {
                                final capsRes = await ApiService.get(
                                    '/api/v1/online-payments/capabilities');
                                final mockEnabled = (capsRes is Map) &&
                                    capsRes['mockEnabled'] == true;

                                if (mockEnabled) {
                                  // Run Mock payment with valid RFC 4122 UUID
                                  final sessionRes = await ApiService.post(
                                    '/api/v1/mock-payments/sessions',
                                    {
                                      'amount': enteredAmount,
                                      'purpose': isDebtPayment
                                          ? 'DEBT'
                                          : 'DEPOSIT',
                                      'idempotencyKey': _generateUuid(),
                                    },
                                  );
                                  final ref = sessionRes is Map
                                      ? sessionRes['reference']?.toString()
                                      : null;
                                  if (ref != null) {
                                    await ApiService.post(
                                      '/api/v1/mock-payments/sessions/$ref/finish',
                                      {'outcome': 'SUCCESS'},
                                    );
                                  }
                                } else {
                                  // Try online VNPAY session
                                  await ApiService.post(
                                    '/api/v1/online-payments/sessions',
                                    {
                                      'amount': enteredAmount,
                                      'purpose': isDebtPayment
                                          ? 'DEBT'
                                          : 'DEPOSIT',
                                    },
                                  );
                                }

                                if (!mounted) return;
                                Navigator.pop(ctx);
                                await _loadData();

                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      'Thanh toán ${_formatMoney(enteredAmount)} thành công! '
                                      '${isDebtPayment ? 'Công nợ đã được cấn trừ.' : 'Tiền thế chân đã được cộng.'}',
                                    ),
                                    backgroundColor: Colors.green.shade800,
                                  ),
                                );
                              } catch (e) {
                                setSheetState(() {
                                  isSubmitting = false;
                                  sheetError = e
                                      .toString()
                                      .replaceFirst('Exception: ', '');
                                });
                              }
                            },
                      icon: isSubmitting
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                  color: Colors.white, strokeWidth: 2),
                            )
                          : const Icon(Icons.check_circle_outline),
                      label: Text(
                        isSubmitting
                            ? 'Đang thanh toán...'
                            : (isDebtPayment
                                ? 'Xác nhận trả nợ ngay'
                                : 'Xác nhận nạp tiền ngay'),
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final totalDebt = _wallet['totalDebt'];
    final depositBalance = _wallet['depositBalance'];

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: MediaQuery.textScalerOf(context).scale(20) > 26
            ? 96
            : 56,
        title: const Text(
          'Khoản phạt & Công nợ',
          maxLines: 2,
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            onPressed: _loadData,
            icon: const Icon(Icons.refresh),
            tooltip: 'Tải lại',
          ),
        ],
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      size: 48,
                      color: Colors.redAccent,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 14),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: _loadData,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            )
          : RefreshIndicator(
              onRefresh: _loadData,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0f172a),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        LayoutBuilder(
                          builder: (context, constraints) {
                            final stacked =
                                constraints.maxWidth < 340 &&
                                MediaQuery.textScalerOf(context).scale(16) > 22;
                            final width = stacked
                                ? constraints.maxWidth
                                : (constraints.maxWidth - 12) / 2;
                            return Wrap(
                              spacing: 12,
                              runSpacing: 16,
                              children: [
                                SizedBox(
                                  width: width,
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Text(
                                        'Tổng công nợ hiện tại',
                                        style: TextStyle(
                                          color: Colors.white70,
                                          fontSize: 12,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        _formatMoney(totalDebt),
                                        style: const TextStyle(
                                          color: Colors.amberAccent,
                                          fontSize: 20,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                SizedBox(
                                  width: width,
                                  child: Column(
                                    crossAxisAlignment: stacked
                                        ? CrossAxisAlignment.start
                                        : CrossAxisAlignment.end,
                                    children: [
                                      const Text(
                                        'Tiền thế chân còn lại',
                                        style: TextStyle(
                                          color: Colors.white70,
                                          fontSize: 12,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
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
                            );
                          },
                        ),
                        const Divider(color: Colors.white24, height: 24),
                        Row(
                          children: [
                            Expanded(
                              child: ElevatedButton.icon(
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: (num.tryParse(totalDebt?.toString() ?? '0') ?? 0) > 0
                                      ? Colors.amber.shade600
                                      : Colors.white12,
                                  foregroundColor: (num.tryParse(totalDebt?.toString() ?? '0') ?? 0) > 0
                                      ? Colors.black87
                                      : Colors.white38,
                                  padding: const EdgeInsets.symmetric(vertical: 10),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                ),
                                onPressed: (num.tryParse(totalDebt?.toString() ?? '0') ?? 0) > 0
                                    ? () => _showPaymentSheet(isDebtPayment: true)
                                    : null,
                                icon: const Icon(Icons.payment, size: 18),
                                label: const Text(
                                  'Thanh toán nợ',
                                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: ElevatedButton.icon(
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.green.shade700,
                                  foregroundColor: Colors.white,
                                  padding: const EdgeInsets.symmetric(vertical: 10),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                ),
                                onPressed: () => _showPaymentSheet(isDebtPayment: false),
                                icon: const Icon(Icons.add_circle_outline, size: 18),
                                label: const Text(
                                  'Nạp tiền cọc',
                                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Text(
                    'HỒ SƠ ĐỀN BÙ / MẤT HỎNG',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                      color: Color(0xFF0f172a),
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (_cases.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'Bạn không có hồ sơ đền bù/mất hỏng nào.',
                        style: TextStyle(color: Colors.grey),
                      ),
                    )
                  else
                    ..._cases.map(_buildCaseCard),
                  const SizedBox(height: 20),
                  const Text(
                    'SỔ GIAO DỊCH & TIỀN THẾ CHÂN',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                      color: Color(0xFF0f172a),
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (_entries.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'Chưa phát sinh giao dịch phí phạt hoặc công nợ.',
                        style: TextStyle(color: Colors.grey),
                      ),
                    )
                  else
                    ..._entries.map(_buildEntryCard),
                ],
              ),
            ),
    );
  }

  Widget _buildCaseCard(Map<String, dynamic> item) {
    final caseId = (item['caseId'] ?? item['id'])?.toString() ?? '';
    final isHighlighted =
        widget.initialCaseId != null && widget.initialCaseId == caseId;
    final status = (item['status'] ?? 'OUTSTANDING').toString().toUpperCase();
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isHighlighted ? const Color(0xFF0284c7) : Colors.grey.shade200,
          width: isHighlighted ? 2 : 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  'Hồ sơ #$caseId (${item['barcode'] ?? ''})',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                LedgerDisplay.statusLabel(status),
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: status == 'PAID' || status == 'WAIVED'
                      ? Colors.green
                      : Colors.redAccent,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(item['description']?.toString() ?? ''),
          const SizedBox(height: 6),
          Text(
            'Tổng phí: ${_formatMoney(item['totalAmount'])} • Đã thanh toán: ${_formatMoney(item['paidAmount'])}',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
          ),
        ],
      ),
    );
  }

  Widget _buildEntryCard(Map<String, dynamic> entry) {
    final type = LedgerDisplay.typeLabel(entry['type']);
    final reason = LedgerDisplay.title(entry);
    final status = LedgerDisplay.statusLabel(entry['status']);
    final explanation = LedgerDisplay.explanation(entry);
    final rawAmount = entry['amount'];
    final rawType = entry['type'];
    final isPositive = LedgerDisplay.isPositiveImpact(rawAmount, rawType);
    final formattedAmount = LedgerDisplay.formatSignedAmount(rawAmount, rawType);

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
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
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  reason,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 4),
                Text(
                  '$type • Trạng thái: $status',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
                if (explanation != null) ...[
                  const SizedBox(height: 6),
                  Text(explanation, style: const TextStyle(fontSize: 12)),
                ],
              ],
            ),
          ),
          Text(
            formattedAmount,
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: isPositive
                  ? Colors.green.shade700
                  : Colors.red.shade700,
            ),
          ),
        ],
      ),
    );
  }
}
