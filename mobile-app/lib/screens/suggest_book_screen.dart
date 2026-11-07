import 'package:flutter/material.dart';

import '../services/proposal_service.dart';

class SuggestBookScreen extends StatefulWidget {
  final String? initialProposalId;

  const SuggestBookScreen({super.key, this.initialProposalId});

  @override
  State<SuggestBookScreen> createState() => _SuggestBookScreenState();
}

class _SuggestBookScreenState extends State<SuggestBookScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _author = TextEditingController();
  final _publisher = TextEditingController();
  final _isbn = TextEditingController();
  final _publicationYear = TextEditingController();
  final _edition = TextEditingController();
  final _estimatedPrice = TextEditingController();
  final _externalUrl = TextEditingController();
  final _reason = TextEditingController();
  final _note = TextEditingController();

  String? _selectedMajor;
  List<Map<String, dynamic>> _majors = [];
  bool _isSubmitting = false;
  bool _isLoadingHistory = true;
  bool _historyFailed = false;
  bool _isLoadingMajors = true;
  bool _majorLoadFailed = false;
  String? _majorError;
  bool _showAdditionalInformation = false;
  List<Map<String, dynamic>> _history = [];

  @override
  void initState() {
    super.initState();
    _loadHistory();
    _loadMajors();
  }

  Future<void> _loadMajors() async {
    if (mounted) {
      setState(() {
        _isLoadingMajors = true;
        _majorLoadFailed = false;
        _majorError = null;
      });
    }
    try {
      final majors = await ProposalService.getMajors();
      if (!mounted) return;
      setState(() {
        _majors = majors;
        if (!_majors.any((m) => m['majorId'] == _selectedMajor)) {
          _selectedMajor = null;
        }
      });
    } catch (error) {
      if (mounted) {
        setState(() {
          _majorLoadFailed = true;
          _majorError = error.toString().replaceFirst('Exception: ', '');
        });
      }
    } finally {
      if (mounted) setState(() => _isLoadingMajors = false);
    }
  }

  Future<void> _loadHistory() async {
    if (mounted) {
      setState(() {
        _isLoadingHistory = true;
        _historyFailed = false;
      });
    }
    try {
      final data = await ProposalService.getMine();
      if (mounted) {
        setState(() => _history = data);
        final targetId = widget.initialProposalId?.trim();
        if (targetId != null && targetId.isNotEmpty) {
          final exists = data.any(
            (item) =>
                (item['proposalId'] ?? item['id'])?.toString() == targetId,
          );
          if (!exists) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (!mounted) return;
              ScaffoldMessenger.maybeOf(context)?.showSnackBar(
                SnackBar(
                  content: Text(
                    'Đề xuất sách #$targetId không còn tồn tại hoặc bạn không có quyền truy cập.',
                  ),
                  backgroundColor: Colors.orange.shade800,
                ),
              );
            });
          }
        }
      }
    } catch (_) {
      if (mounted) setState(() => _historyFailed = true);
    } finally {
      if (mounted) setState(() => _isLoadingHistory = false);
    }
  }

  Future<void> _submit() async {
    if (_isSubmitting ||
        _isLoadingMajors ||
        _majorLoadFailed ||
        _majors.isEmpty) {
      return;
    }
    if (!_formKey.currentState!.validate()) return;
    setState(() => _isSubmitting = true);
    try {
      await ProposalService.create(
        title: _title.text.trim(),
        authorName: _author.text,
        publisher: _publisher.text,
        isbn: _isbn.text,
        publicationYear: int.tryParse(_publicationYear.text.trim()),
        edition: _edition.text,
        estimatedPrice: _parsePrice(_estimatedPrice.text),
        externalUrl: _externalUrl.text,
        majorId: _selectedMajor,
        reason: _reason.text.trim(),
        note: _note.text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã gửi đề xuất thành công.')),
      );
      _clearForm();
      await _loadHistory();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  double? _parsePrice(String value) {
    final normalized = value
        .replaceAll(RegExp(r'[^0-9.,]'), '')
        .replaceAll(',', '.');
    return normalized.isEmpty ? null : double.tryParse(normalized);
  }

  void _clearForm() {
    for (final controller in _controllers) {
      controller.clear();
    }
    setState(() {
      _selectedMajor = null;
      _showAdditionalInformation = false;
    });
  }

  List<TextEditingController> get _controllers => [
    _title,
    _author,
    _publisher,
    _isbn,
    _publicationYear,
    _edition,
    _estimatedPrice,
    _externalUrl,
    _reason,
    _note,
  ];

  InputDecoration _decoration(String label, IconData icon) => InputDecoration(
    labelText: label,
    prefixIcon: Icon(icon),
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    filled: true,
    fillColor: Colors.white,
  );

  Widget _field(
    TextEditingController controller,
    String label,
    IconData icon, {
    TextInputType? keyboardType,
    int maxLines = 1,
    String? Function(String?)? validator,
  }) => TextFormField(
    controller: controller,
    keyboardType: keyboardType,
    maxLines: maxLines,
    decoration: _decoration(label, icon),
    validator: validator,
  );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Đề xuất sách mới',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: RefreshIndicator(
        onRefresh: _loadHistory,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(20),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Góp phần làm phong phú Thư viện',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Đơn được gửi đến Thủ thư/Admin phụ trách duyệt đề xuất. '
                  'Khi được chấp nhận, sách mới được ghi vào danh mục; bản sao chỉ có sau khi thư viện nhập kho.',
                  style: TextStyle(color: Colors.grey, height: 1.5),
                ),
                const SizedBox(height: 24),
                _field(
                  _title,
                  'Tên sách *',
                  Icons.menu_book_outlined,
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'Vui lòng nhập tên sách'
                      : null,
                ),
                const SizedBox(height: 14),
                _field(
                  _reason,
                  'Lý do đề xuất *',
                  Icons.notes,
                  maxLines: 4,
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'Vui lòng nhập lý do đề xuất'
                      : null,
                ),
                const SizedBox(height: 24),
                const Text(
                  'THÔNG TIN KHUYẾN KHÍCH',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Các thông tin này giúp thư viện xác minh tài liệu nhanh hơn.',
                  style: TextStyle(color: Colors.grey),
                ),
                const SizedBox(height: 14),
                _field(_author, 'Tác giả', Icons.person_outline),
                const SizedBox(height: 14),
                _majorSelector(),
                const SizedBox(height: 14),
                _additionalInformation(),
                const SizedBox(height: 24),
                SizedBox(
                  height: 54,
                  child: ElevatedButton(
                    onPressed:
                        _isSubmitting ||
                            _isLoadingMajors ||
                            _majorLoadFailed ||
                            _majors.isEmpty
                        ? null
                        : _submit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0284c7),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: _isSubmitting
                        ? const CircularProgressIndicator(color: Colors.white)
                        : const Text(
                            'GỬI ĐỀ XUẤT',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                  ),
                ),
                const SizedBox(height: 32),
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'LỊCH SỬ ĐỀ XUẤT',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: _loadHistory,
                      icon: const Icon(Icons.refresh),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                if (_isLoadingHistory)
                  const Center(child: CircularProgressIndicator())
                else if (_historyFailed)
                  const Text('Không tải được lịch sử. Nhấn làm mới để thử lại.')
                else if (_history.isEmpty)
                  const Text(
                    'Bạn chưa có đề xuất nào.',
                    style: TextStyle(color: Colors.grey),
                  )
                else
                  ..._history.map(_proposalCard),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _majorSelector() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      DropdownButtonFormField<String>(
        key: ValueKey('proposal-major-$_selectedMajor'),
        initialValue: _selectedMajor,
        isExpanded: true,
        decoration: _decoration(
          _isLoadingMajors ? 'Đang tải ngành...' : 'Ngành học *',
          Icons.school_outlined,
        ),
        items: _majors
            .map(
              (m) => DropdownMenuItem<String>(
                value: m['majorId'] as String,
                child: Text(
                  m['majorName'] as String,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            )
            .toList(),
        onChanged: _isLoadingMajors || _isSubmitting
            ? null
            : (value) => setState(() => _selectedMajor = value),
        validator: (value) => value == null ? 'Vui lòng chọn ngành học' : null,
      ),
      if (_majorLoadFailed || (!_isLoadingMajors && _majors.isEmpty)) ...[
        Text(
          _majorLoadFailed
              ? (_majorError ??
                    'Không tải được danh mục ngành. Vui lòng thử lại.')
              : 'Máy chủ chưa có dữ liệu ngành học. Vui lòng liên hệ thư viện.',
        ),
        TextButton(onPressed: _loadMajors, child: const Text('Thử lại')),
      ],
    ],
  );

  Widget _additionalInformation() {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade300),
      ),
      child: ExpansionTile(
        initiallyExpanded: _showAdditionalInformation,
        onExpansionChanged: (expanded) =>
            setState(() => _showAdditionalInformation = expanded),
        leading: const Icon(Icons.add_circle_outline),
        title: const Text(
          'Thông tin bổ sung',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: const Text('Không bắt buộc'),
        childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
        children: [
          _field(_publisher, 'Nhà xuất bản', Icons.business_outlined),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: _field(_isbn, 'ISBN', Icons.qr_code_2)),
              const SizedBox(width: 12),
              Expanded(
                child: _field(
                  _publicationYear,
                  'Năm xuất bản',
                  Icons.calendar_today_outlined,
                  keyboardType: TextInputType.number,
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) return null;
                    final year = int.tryParse(value.trim());
                    return year == null || year < 1000 || year > 2100
                        ? 'Năm không hợp lệ'
                        : null;
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          _field(_edition, 'Phiên bản', Icons.layers_outlined),
          const SizedBox(height: 14),
          _field(
            _estimatedPrice,
            'Giá dự kiến (VNĐ)',
            Icons.payments_outlined,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            validator: (value) {
              if (value == null || value.trim().isEmpty) return null;
              final price = _parsePrice(value);
              return price == null || price < 0 ? 'Giá không hợp lệ' : null;
            },
          ),
          const SizedBox(height: 14),
          _field(
            _externalUrl,
            'Liên kết tham khảo',
            Icons.link,
            keyboardType: TextInputType.url,
            validator: (value) {
              if (value == null || value.trim().isEmpty) return null;
              final uri = Uri.tryParse(value.trim());
              return uri == null || !uri.hasScheme || !uri.hasAuthority
                  ? 'Liên kết không hợp lệ'
                  : null;
            },
          ),
          const SizedBox(height: 14),
          _field(
            _note,
            'Ghi chú thêm',
            Icons.sticky_note_2_outlined,
            maxLines: 2,
          ),
        ],
      ),
    );
  }

  Widget _proposalCard(Map<String, dynamic> proposal) {
    final status = proposal['status']?.toString() ?? 'PENDING';
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ExpansionTile(
        initiallyExpanded:
            widget.initialProposalId != null &&
            widget.initialProposalId ==
                (proposal['proposalId'] ?? proposal['id'])?.toString(),
        title: Text(proposal['title']?.toString() ?? 'Sách chưa đặt tên'),
        subtitle: Text(proposal['authorName']?.toString() ?? 'Chưa rõ tác giả'),
        trailing: _statusChip(status),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: [
          _historyLine('Nhà xuất bản', proposal['publisher']),
          _historyLine('ISBN', proposal['isbn']),
          _historyLine('Năm xuất bản', proposal['publicationYear']),
          _historyLine('Phiên bản', proposal['edition']),
          _historyLine(
            'Ngành học',
            proposal['majorName'] ?? 'Đề xuất cũ chưa gán ngành',
          ),
          if (proposal['majorId'] == null)
            _historyLine('Thể loại cũ', proposal['genre']),
          _historyLine('Lý do', proposal['reason']),
          _historyLine('Ghi chú', proposal['note']),
          if ((proposal['adminNote']?.toString().trim().isNotEmpty ?? false))
            _historyLine(
              'Phản hồi thư viện',
              proposal['adminNote'],
              emphasize: true,
            ),
        ],
      ),
    );
  }

  Widget _historyLine(String label, dynamic value, {bool emphasize = false}) {
    final text = value?.toString().trim() ?? '';
    if (text.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 115,
            child: Text('$label:', style: const TextStyle(color: Colors.grey)),
          ),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontWeight: emphasize ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _statusChip(String status) {
    final normalized = status.toUpperCase();
    final color = normalized == 'APPROVED'
        ? Colors.green
        : normalized == 'REJECTED'
        ? Colors.red
        : Colors.orange;
    final label = normalized == 'APPROVED'
        ? 'Đã duyệt'
        : normalized == 'REJECTED'
        ? 'Từ chối'
        : 'Chờ duyệt';
    return Chip(
      label: Text(label),
      backgroundColor: color.withValues(alpha: 0.1),
      side: BorderSide(color: color.withValues(alpha: 0.3)),
    );
  }

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }
}
