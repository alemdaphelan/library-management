import 'package:flutter/material.dart';

import '../services/wishlist_service.dart';
import '../services/api_service.dart';
import '../widgets/book_cover.dart';
import 'detail_screen.dart';

class SavedBooksScreen extends StatefulWidget {
  const SavedBooksScreen({super.key});

  @override
  State<SavedBooksScreen> createState() => _SavedBooksScreenState();
}

class _SavedBooksScreenState extends State<SavedBooksScreen> {
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _books = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final books = await WishlistService.getMine();
      if (mounted) setState(() => _books = books);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _remove(Map<String, dynamic> book) async {
    final bookId = (book['bookId'] as num?)?.toInt();
    if (bookId == null) return;
    try {
      await WishlistService.remove(bookId);
      if (!mounted) return;
      setState(() => _books.removeWhere((item) => item['bookId'] == bookId));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã bỏ sách khỏi danh sách đã lưu.')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  Future<void> _openDetail(Map<String, dynamic> savedBook) async {
    final bookId = (savedBook['bookId'] as num?)?.toInt();
    if (bookId == null) return;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );
    try {
      final data = await ApiService.get('/books/$bookId');
      if (!mounted) return;
      Navigator.pop(context);
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) =>
              DetailScreen(book: Map<String, dynamic>.from(data as Map)),
        ),
      );
      await _load();
    } catch (error) {
      if (!mounted) return;
      Navigator.pop(context);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sách đã lưu'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _ErrorState(message: _error!, onRetry: _load)
          : _books.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.bookmark_border, size: 64, color: Colors.grey),
                    SizedBox(height: 12),
                    Text(
                      'Bạn chưa lưu cuốn sách nào.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey),
                    ),
                  ],
                ),
              ),
            )
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: _books.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final book = _books[index];
                  final title =
                      book['title']?.toString() ?? 'Tài liệu thư viện';
                  final author =
                      book['author']?.toString() ?? 'Chưa rõ tác giả';
                  return Card(
                    clipBehavior: Clip.antiAlias,
                    child: ListTile(
                      contentPadding: const EdgeInsets.all(12),
                      leading: BookCover(
                        imageUrl: book['cover']?.toString(),
                        title: title,
                        author: author,
                        width: 54,
                        height: 76,
                      ),
                      title: Text(
                        title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      subtitle: Text(
                        '$author\n${book['category'] ?? 'Chưa phân loại'} · '
                        '${book['availableCopies'] ?? 0} bản sẵn sàng',
                      ),
                      isThreeLine: true,
                      trailing: IconButton(
                        tooltip: 'Bỏ lưu',
                        onPressed: () => _remove(book),
                        icon: const Icon(
                          Icons.bookmark,
                          color: Color(0xFF0284c7),
                        ),
                      ),
                      onTap: () => _openDetail(book),
                    ),
                  );
                },
              ),
            ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off, size: 54, color: Colors.grey),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }
}
