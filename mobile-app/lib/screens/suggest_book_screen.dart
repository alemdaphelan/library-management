import 'package:flutter/material.dart';

import '../services/proposal_service.dart';

class SuggestBookScreen extends StatefulWidget {
  const SuggestBookScreen({super.key});

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
  final _genre = TextEditingController();
  final _reason = TextEditingController();
  final _note = TextEditingController();

  String _urgency = 'NORMAL';
  bool _isSubmitting = false;
  bool _isLoadingHistory = true;
  List<Map<String, dynamic>> _history = [];

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    if (mounted) setState(() => _isLoadingHistory = true);
    try {
      final data = await ProposalService.getMine();
      if (mounted) setState(() => _history = data);
    } catch (_) {
      // Keep the form usable even if the history endpoint is unavailable.
    } finally {
      if (mounted) setState(() => _isLoadingHistory = false);
    }
  }

  Future<void> _submit() async {
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
        genre: _genre.text,
        urgencyLevel: _urgency,
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
    setState(() => _urgency = 'NORMAL');
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
    _genre,
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
                  'Điền càng đầy đủ, thư viện càng dễ xác minh và xử lý đề xuất của bạn.',
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
                _field(_author, 'Tác giả', Icons.person_outline),
                const SizedBox(height: 14),
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
                          if (value == null || value.trim().isEmpty) {
                            return null;
                          }
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
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: _field(
                        _edition,
                        'Phiên bản',
                        Icons.layers_outlined,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _field(
                        _genre,
                        'Thể loại',
                        Icons.category_outlined,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                _field(
                  _estimatedPrice,
                  'Giá dự kiến (VNĐ)',
                  Icons.payments_outlined,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) return null;
                    final price = _parsePrice(value);
                    return price == null || price < 0
                        ? 'Giá không hợp lệ'
                        : null;
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
                DropdownButtonFormField<String>(
                  initialValue: _urgency,
                  decoration: _decoration(
                    'Mức độ cần thiết',
                    Icons.priority_high,
                  ),
                  items: const [
                    DropdownMenuItem(value: 'LOW', child: Text('Thấp')),
                    DropdownMenuItem(
                      value: 'NORMAL',
                      child: Text('Bình thường'),
                    ),
                    DropdownMenuItem(value: 'HIGH', child: Text('Cao')),
                  ],
                  onChanged: (value) =>
                      setState(() => _urgency = value ?? 'NORMAL'),
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
                const SizedBox(height: 14),
                _field(
                  _note,
                  'Ghi chú thêm',
                  Icons.sticky_note_2_outlined,
                  maxLines: 2,
                ),
                const SizedBox(height: 24),
                SizedBox(
                  height: 54,
                  child: ElevatedButton(
                    onPressed: _isSubmitting ? null : _submit,
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

  Widget _proposalCard(Map<String, dynamic> proposal) {
    final status = proposal['status']?.toString() ?? 'PENDING';
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ExpansionTile(
        title: Text(proposal['title']?.toString() ?? 'Sách chưa đặt tên'),
        subtitle: Text(proposal['authorName']?.toString() ?? 'Chưa rõ tác giả'),
        trailing: _statusChip(status),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: [
          _historyLine('Nhà xuất bản', proposal['publisher']),
          _historyLine('ISBN', proposal['isbn']),
          _historyLine('Năm xuất bản', proposal['publicationYear']),
          _historyLine('Phiên bản', proposal['edition']),
          _historyLine('Thể loại', proposal['genre']),
          _historyLine('Mức ưu tiên', proposal['urgencyLevel']),
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
