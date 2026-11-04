import 'package:flutter/material.dart';
import '../services/api_service.dart';

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
      ]);
      if (!mounted) return;
      final rawCases = results[0];
      final rawLedger = results[1];

      final parsedCases = (rawCases is List)
          ? rawCases.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];

      Map<String, dynamic> parsedWallet = {};
      List<Map<String, dynamic>> parsedEntries = [];
      if (rawLedger is Map) {
        if (rawLedger['wallet'] is Map) {
          parsedWallet = Map<String, dynamic>.from(rawLedger['wallet'] as Map);
        }
        final list = rawLedger['entries'] ?? rawLedger['transactions'];
        if (list is List) {
          parsedEntries = list
              .map((e) => Map<String, dynamic>.from(e as Map))
              .toList();
        }
      }

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
    final value = num.tryParse(amount?.toString() ?? '0') ?? 0;
    return '${value.toStringAsFixed(0)} đ';
  }

  @override
  Widget build(BuildContext context) {
    final totalDebt = _wallet['totalDebt'] ?? 0;
    final depositBalance = _wallet['depositBalance'] ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Khoản phạt & Công nợ',
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
                    child: Row(
                      children: [
                        Expanded(
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
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
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
                    'SỔ CÔNG NỢ & PHÍ TRẢ TRỄ',
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
              Text(
                'Hồ sơ #$caseId (${item['barcode'] ?? ''})',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              Text(
                status,
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
    final type = (entry['type'] ?? '').toString().toUpperCase();
    final reason = entry['reason']?.toString() ?? 'Giao dịch thư viện';
    final status = (entry['status'] ?? '').toString().toUpperCase();
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
          const Icon(Icons.receipt_long, color: Colors.deepOrange),
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
              ],
            ),
          ),
          Text(
            _formatMoney(entry['amount']),
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              color: Color(0xFF0f172a),
            ),
          ),
        ],
      ),
    );
  }
}
