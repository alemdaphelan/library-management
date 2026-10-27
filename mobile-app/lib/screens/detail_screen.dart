import 'package:flutter/material.dart';
import '../widgets/book_cover.dart';
import '../services/loan_service.dart';
import '../services/wishlist_service.dart';
import 'borrowed_screen.dart';

class DetailScreen extends StatefulWidget {
  final Map<String, dynamic>? book;

  const DetailScreen({super.key, this.book});

  @override
  State<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends State<DetailScreen> {
  bool _isSaved = false;
  bool _saving = false;

  int? get _bookId {
    final raw = widget.book?['bookId'] ?? widget.book?['id'];
    return raw is num ? raw.toInt() : int.tryParse(raw?.toString() ?? '');
  }

  @override
  void initState() {
    super.initState();
    _loadSavedStatus();
  }

  Future<void> _loadSavedStatus() async {
    final id = _bookId;
    if (id == null) return;
    try {
      final saved = await WishlistService.isSaved(id);
      if (mounted) setState(() => _isSaved = saved);
    } catch (_) {
      // The detail page remains usable when wishlist status cannot be loaded.
    }
  }

  Future<void> _toggleSaved() async {
    final id = _bookId;
    if (id == null || _saving) return;
    setState(() => _saving = true);
    try {
      if (_isSaved) {
        await WishlistService.remove(id);
      } else {
        await WishlistService.save(id);
      }
      if (!mounted) return;
      setState(() => _isSaved = !_isSaved);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isSaved
                ? 'Đã lưu vào danh sách quan tâm.'
                : 'Đã bỏ khỏi danh sách quan tâm.',
          ),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showBorrowModal(
    BuildContext context,
    String title,
    String author,
    String category,
    String shelfLocation,
  ) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0284c7).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.bookmark_add_rounded,
                      color: Color(0xFF0284c7),
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 14),
                  const Expanded(
                    child: Text(
                      'Xác nhận đăng ký mượn',
                      style: TextStyle(
                        fontSize: 19,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.grey.shade50,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  children: [
                    _buildInfoRow('Tên sách:', title, isBold: true),
                    const Divider(height: 18),
                    _buildInfoRow('Tác giả:', author),
                    const Divider(height: 18),
                    _buildInfoRow('Vị trí:', shelfLocation),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Nơi nhận:',
                      'Quầy thủ thư - Tầng 2 (Thư viện HUIT)',
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Thời gian giữ:',
                      '24 giờ kể từ thời điểm đăng ký',
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Thời hạn mượn:',
                      '10 ngày theo quy định thư viện',
                      highlightColor: Colors.green.shade700,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(ctx),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text(
                        'Hủy bỏ',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: () async {
                        Navigator.pop(ctx);
                        _processBorrow(context, title, author);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0284c7),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text(
                        'XÁC NHẬN MƯỢN',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
            ],
          ),
        );
      },
    );
  }

  Widget _buildInfoRow(
    String label,
    String value, {
    bool isBold = false,
    Color? highlightColor,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 100,
          child: Text(
            label,
            style: const TextStyle(color: Colors.grey, fontSize: 13),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: highlightColor ?? Colors.black87,
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _processBorrow(
    BuildContext context,
    String title,
    String author,
  ) async {
    // Show quick progress loading
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) =>
          const Center(child: CircularProgressIndicator(color: Colors.white)),
    );

    Map<String, dynamic> newLoan;
    try {
      final bookId = _bookId;
      if (bookId == null) {
        throw Exception('Không xác định được mã sách.');
      }
      newLoan = await LoanService.reserveBook(bookId);
    } catch (error) {
      if (!context.mounted) return;
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
      return;
    }

    if (!context.mounted) return;
    Navigator.pop(context);

    // Show Success Dialog
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        contentPadding: const EdgeInsets.all(24),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.green.shade50,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.check_circle_rounded,
                color: Colors.green,
                size: 54,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Đăng ký thành công!',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              'Phiếu mượn: #${newLoan['ticketId'] ?? '—'}',
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                color: Color(0xFF0284c7),
                fontSize: 15,
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.amber.shade50,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.amber.shade200),
              ),
              child: const Text(
                'Sách sẽ được giữ tại Quầy thủ thư Tầng 2 trong 24 giờ. Vui lòng mang thẻ SV để nhận sách.',
                style: TextStyle(fontSize: 12, color: Colors.black87),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(dialogCtx),
                    style: OutlinedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text('Đóng'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.pop(dialogCtx);
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const BorrowedScreen(),
                        ),
                      );
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0284c7),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text('Xem sách mượn'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final book = widget.book;
    final title = book?['title'] ?? 'Tài liệu thư viện';
    final author = book?['author'] ?? 'Chưa rõ tác giả';
    final publisher = book?['publisher'] ?? 'Chưa cập nhật nhà xuất bản';
    final year = book?['publishYear']?.toString() ?? 'Chưa cập nhật';
    final categoryObj = book?['category'];
    final category = categoryObj is Map
        ? (categoryObj['name'] ?? 'Công nghệ thông tin')
        : (categoryObj?.toString() ?? 'Công nghệ thông tin');
    final availableCopies = (book?['availableCopies'] as num?)?.toInt() ?? 0;
    final totalCopies = (book?['copies'] as num?)?.toInt() ?? 0;
    final isAvailable = availableCopies > 0;
    final imageUrl =
        book?['imageUrlL'] ??
        book?['imageUrlM'] ??
        book?['imageUrlS'] ??
        book?['coverUrl'];
    final shelfLocation = (book?['location']?.toString().isNotEmpty ?? false)
        ? book!['location'].toString()
        : 'Chưa xếp vị trí';

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Chi tiết sách',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            tooltip: _isSaved ? 'Bỏ lưu' : 'Lưu sách',
            icon: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    _isSaved ? Icons.bookmark : Icons.bookmark_outline,
                    color: _isSaved ? const Color(0xFF0284c7) : null,
                  ),
            onPressed: _bookId == null || _saving ? null : _toggleSaved,
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Cover Image Header with BookCover fallback
            Container(
              width: double.infinity,
              height: 260,
              color: const Color(0xFF0f172a).withValues(alpha: 0.04),
              child: Center(
                child: Hero(
                  tag: 'book_cover_${book?['isbn'] ?? title}',
                  child: BookCover(
                    imageUrl: imageUrl,
                    title: title,
                    author: author,
                    width: 170,
                    height: 230,
                    borderRadius: 12,
                  ),
                ),
              ),
            ),

            // Book Info
            Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          title,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                            height: 1.2,
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 5,
                        ),
                        decoration: BoxDecoration(
                          color: isAvailable
                              ? Colors.green.shade50
                              : Colors.red.shade50,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isAvailable
                                ? Colors.green.shade200
                                : Colors.red.shade200,
                          ),
                        ),
                        child: Text(
                          isAvailable ? 'Có sẵn' : 'Đã mượn hết',
                          style: TextStyle(
                            color: isAvailable
                                ? Colors.green.shade700
                                : Colors.red.shade700,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'Tác giả: $author',
                    style: const TextStyle(fontSize: 16, color: Colors.grey),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    'Năm xuất bản: $year | $publisher',
                    style: const TextStyle(fontSize: 14, color: Colors.grey),
                  ),

                  const SizedBox(height: 25),

                  // Location Card
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.grey.shade200),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.04),
                          blurRadius: 10,
                          offset: const Offset(0, 5),
                        ),
                      ],
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.location_on,
                          color: Color(0xFF0284c7),
                          size: 32,
                        ),
                        const SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'VỊ TRÍ LƯU TRỮ TRONG THƯ VIỆN',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.grey,
                                ),
                              ),
                              const SizedBox(height: 5),
                              Text(
                                shelfLocation,
                                style: const TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              Text(
                                'Mã định danh: ISBN ${book?['isbn'] ?? 'Chưa cập nhật'}',
                                style: const TextStyle(fontSize: 14),
                              ),
                              Text(
                                'Bản sao sẵn sàng: $availableCopies/$totalCopies',
                                style: const TextStyle(fontSize: 14),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 30),
                  const Text(
                    'TÓM TẮT',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    (book?['description'] ??
                            book?['summary'] ??
                            'Tài liệu học tập và nghiên cứu được lưu trữ tại Thư viện Trường Đại học Công Thương TP. Hồ Chí Minh (HUIT).')
                        .toString(),
                    style: TextStyle(
                      fontSize: 15,
                      height: 1.6,
                      color: Colors.black87,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: SizedBox(
            height: 55,
            child: ElevatedButton(
              onPressed: isAvailable
                  ? () {
                      _showBorrowModal(
                        context,
                        title,
                        author,
                        category,
                        shelfLocation,
                      );
                    }
                  : null,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0284c7),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                'ĐĂNG KÝ MƯỢN SÁCH',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
