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
  final Set<int> _removingBookIds = <int>{};

  int? _parseBookId(Map<String, dynamic> book) {
    final raw = book['bookId'] ?? book['id'];
    if (raw is int) return raw;
    if (raw is num) return raw.toInt();
    return int.tryParse(raw?.toString() ?? '');
  }

  @override
  void initState() {
    super.initState();
    WishlistService.wishlistRevision.addListener(_handleWishlistRevisionChanged);
    _load();
  }

  @override
  void dispose() {
    WishlistService.wishlistRevision.removeListener(
      _handleWishlistRevisionChanged,
    );
    super.dispose();
  }

  void _handleWishlistRevisionChanged() {
    if (!mounted || _loading) return;
    if (!WishlistService.hasLoadedSnapshot &&
        WishlistService.savedBookIds.isEmpty) {
      if (_books.isNotEmpty) {
        setState(() => _books = []);
      }
      return;
    }
    final activeIds = WishlistService.savedBookIds;
    final filtered = _books.where((item) {
      final id = _parseBookId(item);
      return id != null &&
          (activeIds.contains(id) || _removingBookIds.contains(id));
    }).toList();
    if (filtered.length != _books.length) {
      setState(() => _books = filtered);
    }
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
      if (mounted) {
        setState(() {
          _books = [];
          _error = error.toString();
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _remove(Map<String, dynamic> book) async {
    final bookId = _parseBookId(book);
    if (bookId == null ||
        _removingBookIds.contains(bookId) ||
        WishlistService.isInFlight(bookId)) {
      return;
    }
    final messenger = ScaffoldMessenger.maybeOf(context);
    final originalIndex = _books.indexWhere(
      (item) => _parseBookId(item) == bookId,
    );
    if (originalIndex < 0) return;
    final removedBook = _books[originalIndex];

    setState(() {
      _removingBookIds.add(bookId);
      _books = List<Map<String, dynamic>>.from(_books)..removeAt(originalIndex);
    });

    try {
      await WishlistService.remove(bookId);
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        const SnackBar(content: Text('Đã bỏ sách khỏi danh sách đã lưu.')),
      );
    } catch (error) {
      if (mounted) {
        setState(() {
          final restored = List<Map<String, dynamic>>.from(_books);
          final targetIndex = originalIndex.clamp(0, restored.length);
          restored.insert(targetIndex, removedBook);
          _books = restored;
        });
      }
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(error.toString()),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _removingBookIds.remove(bookId));
      }
    }
  }

  Future<void> _openDetail(Map<String, dynamic> savedBook) async {
    final bookId = _parseBookId(savedBook);
    if (bookId == null) return;
    final messenger = ScaffoldMessenger.maybeOf(context);
    Map<String, dynamic> detailPayload = Map<String, dynamic>.from(savedBook);
    try {
      final data = await ApiService.get('/books/$bookId');
      if (data is Map) {
        detailPayload = {
          ...detailPayload,
          ...Map<String, dynamic>.from(data),
        };
      }
    } on ApiException catch (error) {
      if (error.statusCode == 404) {
        if (!mounted) return;
        messenger?.hideCurrentSnackBar();
        messenger?.showSnackBar(
          const SnackBar(
            content: Text(
              'Đầu sách này không còn tồn tại hoặc đã ngừng lưu hành.',
            ),
          ),
        );
        await _load();
        return;
      }
    } catch (_) {
      // Fall back to savedBook metadata when offline/detail prefetch fails.
    }
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => DetailScreen(book: detailPayload),
      ),
    );
    if (mounted) {
      await _load();
    }
  }

  String _resolveGenreOrDepartment(Map<String, dynamic> book) {
    final department = book['department']?.toString().trim();
    final categoryObj = book['category'];
    String? category;
    if (categoryObj is Map) {
      category = categoryObj['name']?.toString().trim();
    } else if (categoryObj != null) {
      category = categoryObj.toString().trim();
    }
    if (department != null &&
        department.isNotEmpty &&
        category != null &&
        category.isNotEmpty &&
        department.toLowerCase() != category.toLowerCase()) {
      return '$department · $category';
    }
    if (department != null && department.isNotEmpty) return department;
    if (category != null && category.isNotEmpty) return category;
    return 'Chưa phân loại';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sách đã lưu'),
        actions: [
          IconButton(
            tooltip: 'Làm mới danh sách',
            onPressed: _load,
            icon: const Icon(Icons.refresh),
          ),
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
                  final bookId = _parseBookId(book);
                  final title =
                      book['title']?.toString() ?? 'Tài liệu thư viện';
                  final author =
                      book['author']?.toString() ?? 'Chưa rõ tác giả';
                  final genreOrDept = _resolveGenreOrDepartment(book);
                  final location = book['location']?.toString().trim();
                  final availableCopies =
                      (book['availableCopies'] as num?)?.toInt() ?? 0;
                  final totalCopies =
                      (book['copies'] as num?)?.toInt() ?? availableCopies;
                  final isAvailable = availableCopies > 0;
                  final copyStatusText = totalCopies > 0
                      ? (isAvailable
                            ? 'Còn sách ($availableCopies/$totalCopies bản sẵn sàng)'
                            : 'Tạm hết bản sẵn sàng (0/$totalCopies)')
                      : '$availableCopies bản sẵn sàng';

                  return Card(
                    key: ValueKey('saved-book-card-${bookId ?? index}'),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () => _openDetail(book),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            BookCover(
                              imageUrl:
                                  (book['cover'] ??
                                          book['imageUrlL'] ??
                                          book['imageUrlM'])
                                      ?.toString(),
                              title: title,
                              author: author,
                              width: 56,
                              height: 80,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    title,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 15,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    author,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      color: Colors.grey.shade700,
                                      fontSize: 13,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    genreOrDept,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: Color(0xFF0284c7),
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  if (location != null &&
                                      location.isNotEmpty) ...[
                                    const SizedBox(height: 2),
                                    Text(
                                      'Vị trí: $location',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        color: Colors.grey.shade600,
                                        fontSize: 12,
                                      ),
                                    ),
                                  ],
                                  const SizedBox(height: 6),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: isAvailable
                                          ? Colors.green.shade50
                                          : Colors.orange.shade50,
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    child: Text(
                                      copyStatusText,
                                      style: TextStyle(
                                        fontSize: 11.5,
                                        fontWeight: FontWeight.w600,
                                        color: isAvailable
                                            ? Colors.green.shade800
                                            : Colors.orange.shade900,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              key: ValueKey(
                                'unsave-book-button-${bookId ?? index}',
                              ),
                              tooltip: 'Bỏ lưu',
                              onPressed: () => _remove(book),
                              icon: const Icon(
                                Icons.bookmark,
                                color: Color(0xFF0284c7),
                              ),
                            ),
                          ],
                        ),
                      ),
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
