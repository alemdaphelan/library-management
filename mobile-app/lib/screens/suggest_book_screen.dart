import 'package:flutter/material.dart';
import 'package:mobile_app/services/proposal_service.dart';

class SuggestBookScreen extends StatefulWidget {
  const SuggestBookScreen({super.key});

  @override
  State<SuggestBookScreen> createState() => _SuggestBookScreenState();
}

class _SuggestBookScreenState extends State<SuggestBookScreen> {
  final _titleController = TextEditingController();
  final _authorController = TextEditingController();
  final _reasonController = TextEditingController();
  bool _isSubmitting = false;
  bool _isLoadingHistory = true;
  List<Map<String, dynamic>> _history = [];

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    try {
      final data = await ProposalService.getMine();
      if (mounted) setState(() => _history = data);
    } catch (_) {
      // Form remains usable even when history cannot be loaded.
    } finally {
      if (mounted) setState(() => _isLoadingHistory = false);
    }
  }

  void _submit() async {
    final title = _titleController.text.trim();
    final reason = _reasonController.text.trim();
    final author = _authorController.text.trim();

    if (title.isEmpty || reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập tên sách và lý do!')),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    try {
      await ProposalService.create(
        title: title,
        authorName: author.isEmpty ? null : author,
        reason: reason,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã gửi đề xuất thành công.')),
      );
      _titleController.clear();
      _authorController.clear();
      _reasonController.clear();
      await _loadHistory();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Lỗi kết nối máy chủ: $e')));
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Đề xuất sách mới',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black,
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Góp phần làm phong phú Thư viện',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              'Nếu bạn không tìm thấy sách mình cần, hãy gửi yêu cầu để Thư viện bổ sung vào đợt nhập sách tới.',
              style: TextStyle(color: Colors.grey, height: 1.5),
            ),
            const SizedBox(height: 30),
            TextField(
              controller: _titleController,
              decoration: InputDecoration(
                labelText: 'Tên sách (Bắt buộc)',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _authorController,
              decoration: InputDecoration(
                labelText: 'Tác giả',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _reasonController,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: 'Lý do đề xuất (Phục vụ môn học, nghiên cứu...)',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 30),
            SizedBox(
              height: 55,
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
            const Text(
              'LỊCH SỬ ĐỀ XUẤT',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 12),
            if (_isLoadingHistory)
              const Center(child: CircularProgressIndicator())
            else if (_history.isEmpty)
              const Text(
                'Bạn chưa có đề xuất nào.',
                style: TextStyle(color: Colors.grey),
              )
            else
              ..._history.map(
                (proposal) => Card(
                  child: ListTile(
                    title: Text(
                      proposal['title']?.toString() ?? 'Sách chưa đặt tên',
                    ),
                    subtitle: Text(
                      '${proposal['authorName'] ?? 'Chưa rõ tác giả'}\n'
                      '${proposal['adminNote'] ?? proposal['reason'] ?? ''}',
                    ),
                    isThreeLine: true,
                    trailing: _statusChip(
                      proposal['status']?.toString() ?? 'PENDING',
                    ),
                  ),
                ),
              ),
          ],
        ),
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
    _titleController.dispose();
    _authorController.dispose();
    _reasonController.dispose();
    super.dispose();
  }
}
