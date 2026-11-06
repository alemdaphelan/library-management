import 'package:flutter/material.dart';
import '../services/proposal_service.dart';

class LibrarianProposalScreen extends StatefulWidget {
  const LibrarianProposalScreen({super.key});

  @override
  State<LibrarianProposalScreen> createState() =>
      _LibrarianProposalScreenState();
}

class _LibrarianProposalScreenState extends State<LibrarianProposalScreen> {
  bool _isLoading = true;
  bool _isProcessing = false;
  String _errorMessage = '';

  List<Map<String, dynamic>> _proposals = [];
  List<Map<String, dynamic>> _majors = [];

  String? _selectedMajorId;
  String _selectedTab = 'PENDING'; // 'PENDING', 'APPROVED', 'REJECTED', 'ALL'
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = '';
    });

    try {
      final results = await Future.wait([
        ProposalService.getAllForAdmin(),
        ProposalService.getMajors().catchError((_) => <Map<String, dynamic>>[]),
      ]);

      if (!mounted) return;
      setState(() {
        _proposals = results[0];
        _majors = results[1];
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = 'Không thể tải danh sách đề xuất: $e';
      });
    }
  }

  int get _pendingCount =>
      _proposals.where((p) => (p['status'] ?? '').toString().toUpperCase() == 'PENDING').length;
  int get _approvedCount =>
      _proposals.where((p) => (p['status'] ?? '').toString().toUpperCase() == 'APPROVED').length;
  int get _rejectedCount =>
      _proposals.where((p) => (p['status'] ?? '').toString().toUpperCase() == 'REJECTED').length;

  /// Lấy danh sách các ngành học có trong danh sách đề xuất hoặc từ danh mục chính thức
  List<Map<String, String>> get _availableMajorOptions {
    final Map<String, String> map = {};
    for (final m in _majors) {
      final id = m['majorId']?.toString();
      final name = m['majorName']?.toString();
      if (id != null && name != null && id.isNotEmpty) {
        map[id] = name;
      }
    }
    // Bổ sung các ngành có trong proposals nếu chưa có trong catalog
    for (final p in _proposals) {
      final id = p['majorId']?.toString();
      final name = p['majorName']?.toString();
      if (id != null && name != null && id.isNotEmpty) {
        map.putIfAbsent(id, () => name);
      }
    }
    final list = map.entries
        .map((e) => {'id': e.key, 'name': e.value})
        .toList();
    list.sort((a, b) => a['name']!.compareTo(b['name']!));
    return list;
  }

  List<Map<String, dynamic>> get _filteredProposals {
    return _proposals.where((p) {
      // 1. Lọc theo trạng thái tab
      final status = (p['status'] ?? 'PENDING').toString().toUpperCase();
      if (_selectedTab != 'ALL' && status != _selectedTab) {
        return false;
      }

      // 2. Lọc theo ngành học (Dropdown)
      if (_selectedMajorId != null && _selectedMajorId!.isNotEmpty) {
        final pMajorId = p['majorId']?.toString();
        if (pMajorId != _selectedMajorId) {
          return false;
        }
      }

      // 3. Lọc theo từ khóa tìm kiếm
      if (_searchQuery.trim().isNotEmpty) {
        final q = _searchQuery.trim().toLowerCase();
        final title = (p['title'] ?? '').toString().toLowerCase();
        final author = (p['authorName'] ?? '').toString().toLowerCase();
        final userName = (p['userName'] ?? '').toString().toLowerCase();
        final userMssv = (p['userMssv'] ?? '').toString().toLowerCase();
        final majorName = (p['majorName'] ?? '').toString().toLowerCase();
        if (!title.contains(q) &&
            !author.contains(q) &&
            !userName.contains(q) &&
            !userMssv.contains(q) &&
            !majorName.contains(q)) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  Future<void> _handleApprove(Map<String, dynamic> proposal) async {
    final proposalId = (proposal['proposalId'] ?? proposal['id']) as num?;
    if (proposalId == null) return;

    final noteController = TextEditingController();
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.check_circle_outline, color: Colors.green),
            SizedBox(width: 8),
            Text('Duyệt đề xuất sách'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Xác nhận duyệt mua sách: "${proposal['title']}"?',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: noteController,
              decoration: const InputDecoration(
                labelText: 'Ghi chú cho sinh viên (tùy chọn)',
                hintText: 'Ví dụ: Thư viện sẽ mua trong đợt tới...',
                border: OutlineInputBorder(),
              ),
              maxLines: 2,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.green),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Xác nhận duyệt'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _isProcessing = true);
    try {
      await ProposalService.updateStatus(
        proposalId: proposalId.toInt(),
        status: 'APPROVED',
        adminNote: noteController.text.trim().isNotEmpty
            ? noteController.text.trim()
            : null,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Đã duyệt đề xuất "${proposal['title']}" thành công!'),
          backgroundColor: Colors.green,
        ),
      );
      _loadData();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lỗi khi duyệt đề xuất: $e'), backgroundColor: Colors.red),
      );
    } finally {
      if (mounted) setState(() => _isProcessing = false);
    }
  }

  Future<void> _handleReject(Map<String, dynamic> proposal) async {
    final proposalId = (proposal['proposalId'] ?? proposal['id']) as num?;
    if (proposalId == null) return;

    final noteController = TextEditingController();
    String? formError;

    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.cancel_outlined, color: Colors.red),
              SizedBox(width: 8),
              Text('Từ chối đề xuất'),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Từ chối đề xuất: "${proposal['title']}"',
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: noteController,
                decoration: InputDecoration(
                  labelText: 'Lý do từ chối *',
                  hintText: 'Nhập lý do gửi phản hồi cho sinh viên...',
                  errorText: formError,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 3,
                onChanged: (_) {
                  if (formError != null) {
                    setDialogState(() => formError = null);
                  }
                },
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () {
                if (noteController.text.trim().isEmpty) {
                  setDialogState(() {
                    formError = 'Vui lòng nhập lý do từ chối';
                  });
                  return;
                }
                Navigator.pop(ctx, true);
              },
              child: const Text('Xác nhận từ chối'),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true) return;

    setState(() => _isProcessing = true);
    try {
      await ProposalService.updateStatus(
        proposalId: proposalId.toInt(),
        status: 'REJECTED',
        adminNote: noteController.text.trim(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Đã từ chối đề xuất "${proposal['title']}"'),
          backgroundColor: Colors.orange.shade800,
        ),
      );
      _loadData();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lỗi khi từ chối đề xuất: $e'), backgroundColor: Colors.red),
      );
    } finally {
      if (mounted) setState(() => _isProcessing = false);
    }
  }

  void _showDetailModal(Map<String, dynamic> proposal) {
    final status = (proposal['status'] ?? 'PENDING').toString().toUpperCase();
    final isPending = status == 'PENDING';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.75,
        minChildSize: 0.4,
        maxChildSize: 0.95,
        expand: false,
        builder: (_, scrollController) => Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          child: ListView(
            controller: scrollController,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      proposal['title']?.toString() ?? 'Không rõ tên sách',
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0f172a),
                      ),
                    ),
                  ),
                  _buildStatusChip(status),
                ],
              ),
              if (proposal['authorName'] != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    'Tác giả: ${proposal['authorName']}',
                    style: TextStyle(fontSize: 15, color: Colors.grey.shade700),
                  ),
                ),
              const Divider(height: 24),
              _detailRow('Sinh viên đề xuất', proposal['userName']),
              _detailRow('MSSV', proposal['userMssv']),
              _detailRow('Email', proposal['userEmail']),
              _detailRow('Ngành học', proposal['majorName']),
              _detailRow('Nhà xuất bản', proposal['publisher']),
              _detailRow('Năm xuất bản', proposal['publicationYear']?.toString()),
              _detailRow('Phiên bản', proposal['edition']),
              _detailRow('ISBN', proposal['isbn']),
              if (proposal['estimatedPrice'] != null)
                _detailRow(
                  'Giá dự kiến',
                  '${proposal['estimatedPrice']} VNĐ',
                ),
              if (proposal['externalUrl'] != null)
                _detailRow('Link tham khảo', proposal['externalUrl']),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFf8f9ff),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.blue.shade100),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Lý do đề xuất của sinh viên:',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                        color: Color(0xFF0284c7),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      proposal['reason']?.toString() ?? 'Chưa cung cấp lý do',
                      style: const TextStyle(fontSize: 14),
                    ),
                  ],
                ),
              ),
              if (proposal['note'] != null && proposal['note'].toString().trim().isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'Ghi chú bổ sung: ${proposal['note']}',
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                ),
              ],
              if (proposal['adminNote'] != null &&
                  proposal['adminNote'].toString().trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: status == 'APPROVED' ? Colors.green.shade50 : Colors.red.shade50,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: status == 'APPROVED'
                          ? Colors.green.shade200
                          : Colors.red.shade200,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Phản hồi từ thủ thư:',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: status == 'APPROVED' ? Colors.green.shade800 : Colors.red.shade800,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(proposal['adminNote'].toString()),
                    ],
                  ),
                ),
              ],
              if (isPending) ...[
                const SizedBox(height: 24),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.red,
                          side: const BorderSide(color: Colors.red),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        icon: const Icon(Icons.close),
                        label: const Text('Từ chối'),
                        onPressed: () {
                          Navigator.pop(ctx);
                          _handleReject(proposal);
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: Colors.green,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        icon: const Icon(Icons.check),
                        label: const Text('Duyệt đề xuất'),
                        onPressed: () {
                          Navigator.pop(ctx);
                          _handleApprove(proposal);
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _detailRow(String label, dynamic value) {
    final text = value?.toString().trim() ?? '';
    if (text.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130,
            child: Text(
              '$label:',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
            ),
          ),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusChip(String status) {
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

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredProposals;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Duyệt đề xuất sách',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Tải lại',
            onPressed: _isLoading ? null : _loadData,
          ),
        ],
      ),
      body: Column(
        children: [
          // Filter & Search Header Card
          Container(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            decoration: BoxDecoration(
              color: Colors.white,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.04),
                  blurRadius: 4,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Column(
              children: [
                // 1. DROPDOWN LỌC NGÀNH HỌC (Yêu cầu chính của người dùng)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFf8f9ff),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: const Color(0xFF0284c7).withValues(alpha: 0.3)),
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String?>(
                      isExpanded: true,
                      value: _selectedMajorId != null &&
                              !_availableMajorOptions.any((m) => m['id'] == _selectedMajorId)
                          ? null
                          : _selectedMajorId,
                      icon: const Icon(Icons.arrow_drop_down, color: Color(0xFF0284c7)),
                      hint: const Row(
                        children: [
                          Icon(Icons.school_outlined, size: 20, color: Color(0xFF0284c7)),
                          SizedBox(width: 8),
                          Text(
                            'Tất cả ngành học',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF0284c7),
                            ),
                          ),
                        ],
                      ),
                      items: [
                        const DropdownMenuItem<String?>(
                          value: null,
                          child: Row(
                            children: [
                              Icon(Icons.all_inclusive, size: 18, color: Colors.grey),
                              SizedBox(width: 8),
                              Text('Tất cả ngành học', style: TextStyle(fontWeight: FontWeight.bold)),
                            ],
                          ),
                        ),
                        ..._availableMajorOptions.map(
                          (m) => DropdownMenuItem<String?>(
                            value: m['id'],
                            child: Row(
                              children: [
                                const Icon(Icons.school, size: 18, color: Color(0xFF0284c7)),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    m['name']!,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(fontSize: 14),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                      onChanged: (val) {
                        setState(() => _selectedMajorId = val);
                      },
                    ),
                  ),
                ),

                const SizedBox(height: 8),

                // 2. Ô Tìm kiếm
                TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: 'Tìm theo tên sách, tác giả, sinh viên, MSSV...',
                    hintStyle: TextStyle(fontSize: 13, color: Colors.grey.shade500),
                    prefixIcon: const Icon(Icons.search, size: 20),
                    suffixIcon: _searchQuery.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear, size: 18),
                            onPressed: () {
                              _searchController.clear();
                              setState(() => _searchQuery = '');
                            },
                          )
                        : null,
                    contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 12),
                    filled: true,
                    fillColor: Colors.grey.shade50,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide(color: Colors.grey.shade300),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide(color: Colors.grey.shade300),
                    ),
                  ),
                  onChanged: (val) => setState(() => _searchQuery = val),
                ),

                const SizedBox(height: 8),

                // 3. Status Filter Tabs (Chờ duyệt, Đã duyệt, Đã từ chối, Tất cả)
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _tabChip('PENDING', 'Chờ duyệt', _pendingCount, Colors.orange),
                      const SizedBox(width: 6),
                      _tabChip('APPROVED', 'Đã duyệt', _approvedCount, Colors.green),
                      const SizedBox(width: 6),
                      _tabChip('REJECTED', 'Từ chối', _rejectedCount, Colors.red),
                      const SizedBox(width: 6),
                      _tabChip('ALL', 'Tất cả', _proposals.length, Colors.blueGrey),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Proposal List
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage.isNotEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.error_outline, size: 48, color: Colors.red),
                              const SizedBox(height: 12),
                              Text(_errorMessage, textAlign: TextAlign.center),
                              const SizedBox(height: 12),
                              ElevatedButton(
                                onPressed: _loadData,
                                child: const Text('Thử lại'),
                              ),
                            ],
                          ),
                        ),
                      )
                    : filtered.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.inbox_outlined, size: 64, color: Colors.grey.shade400),
                                const SizedBox(height: 12),
                                Text(
                                  _selectedMajorId != null
                                      ? 'Không có đề xuất nào thuộc ngành đã chọn'
                                      : 'Không tìm thấy đề xuất nào',
                                  style: TextStyle(
                                    fontSize: 15,
                                    color: Colors.grey.shade600,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ],
                            ),
                          )
                        : RefreshIndicator(
                            onRefresh: _loadData,
                            child: ListView.builder(
                              padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                              itemCount: filtered.length,
                              itemBuilder: (ctx, idx) {
                                final item = filtered[idx];
                                return _buildProposalCard(item);
                              },
                            ),
                          ),
          ),
        ],
      ),
    );
  }

  Widget _tabChip(String tabKey, String label, int count, MaterialColor color) {
    final isSelected = _selectedTab == tabKey;
    return ChoiceChip(
      selected: isSelected,
      onSelected: (_) => setState(() => _selectedTab = tabKey),
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label),
          if (count > 0) ...[
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color: isSelected ? Colors.white : color.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                '$count',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: isSelected ? color.shade800 : color.shade700,
                ),
              ),
            ),
          ],
        ],
      ),
      selectedColor: color.shade600,
      labelStyle: TextStyle(
        color: isSelected ? Colors.white : Colors.black87,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        fontSize: 12,
      ),
    );
  }

  Widget _buildProposalCard(Map<String, dynamic> item) {
    final status = (item['status'] ?? 'PENDING').toString().toUpperCase();
    final isPending = status == 'PENDING';
    final title = item['title']?.toString() ?? 'Chưa rõ tên sách';
    final author = item['authorName']?.toString();
    final userName = item['userName']?.toString();
    final userMssv = item['userMssv']?.toString();
    final majorName = item['majorName']?.toString() ?? 'Chưa gán ngành';

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: isPending
              ? Colors.orange.shade200
              : Colors.grey.shade200,
          width: isPending ? 1.5 : 1,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => _showDetailModal(item),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Hàng tiêu đề + chip trạng thái
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0f172a),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  _buildStatusChip(status),
                ],
              ),

              if (author != null && author.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  'Tác giả: $author',
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                ),
              ],

              const SizedBox(height: 8),

              // Thông tin sinh viên đề xuất
              Row(
                children: [
                  const Icon(Icons.person_outline, size: 16, color: Colors.grey),
                  const SizedBox(width: 4),
                  Text(
                    userName != null && userMssv != null
                        ? '$userName ($userMssv)'
                        : (userName ?? userMssv ?? 'Sinh viên'),
                    style: TextStyle(fontSize: 13, color: Colors.grey.shade800),
                  ),
                ],
              ),

              const SizedBox(height: 6),

              // Chip Ngành học
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFF0284c7).withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.school, size: 14, color: Color(0xFF0284c7)),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Text(
                        majorName,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF0284c7),
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),

              if (item['reason'] != null && item['reason'].toString().trim().isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'Lý do: ${item['reason']}',
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade600, fontStyle: FontStyle.italic),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],

              if (item['adminNote'] != null && item['adminNote'].toString().trim().isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(
                  'Phản hồi: ${item['adminNote']}',
                  style: TextStyle(
                    fontSize: 12,
                    color: status == 'APPROVED' ? Colors.green.shade700 : Colors.red.shade700,
                  ),
                ),
              ],

              // Các nút hành động nếu đang chờ duyệt
              if (isPending) ...[
                const SizedBox(height: 12),
                const Divider(height: 1),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red,
                        side: BorderSide(color: Colors.red.shade300),
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        visualDensity: VisualDensity.compact,
                      ),
                      icon: const Icon(Icons.close, size: 16),
                      label: const Text('Từ chối'),
                      onPressed: _isProcessing ? null : () => _handleReject(item),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.green,
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        visualDensity: VisualDensity.compact,
                      ),
                      icon: const Icon(Icons.check, size: 16),
                      label: const Text('Duyệt'),
                      onPressed: _isProcessing ? null : () => _handleApprove(item),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
