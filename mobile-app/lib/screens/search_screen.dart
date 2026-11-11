import 'package:flutter/material.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import '../services/api_service.dart';
import '../services/loan_service.dart';
import '../services/wishlist_service.dart';
import '../widgets/book_cover.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  List<dynamic> allBooks = [];
  List<dynamic> books = [];
  bool isLoading = true;
  bool isFetchingMore = false;
  String searchQuery = '';
  String? selectedDepartment;
  List<Map<String, dynamic>> departments = [];

  int currentPage = 0;
  int pageSize = 10;
  int totalPages = 1;
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    LoanService.loanRevision.addListener(_handleLoanRevisionChanged);
    WishlistService.wishlistRevision.addListener(
      _handleWishlistRevisionChanged,
    );
    if (!WishlistService.hasLoadedSnapshot) {
      WishlistService.getMine().catchError((_) => <Map<String, dynamic>>[]);
    }
    fetchDepartments();
    fetchBooks(reset: true);

    _scrollController.addListener(() {
      if (_scrollController.position.pixels ==
          _scrollController.position.maxScrollExtent) {
        if (currentPage + 1 < totalPages && !isFetchingMore) {
          fetchBooks(reset: false);
        }
      }
    });
  }

  @override
  void dispose() {
    LoanService.loanRevision.removeListener(_handleLoanRevisionChanged);
    WishlistService.wishlistRevision.removeListener(
      _handleWishlistRevisionChanged,
    );
    _scrollController.dispose();
    super.dispose();
  }

  void _handleWishlistRevisionChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  void _handleLoanRevisionChanged() {
    if (mounted) {
      fetchBooks(reset: true);
    }
  }

  Future<void> fetchDepartments() async {
    try {
      final data = await ApiService.getPublic('/books/departments');
      if (!mounted || data is! List) return;
      setState(() {
        departments = data
            .map((item) => Map<String, dynamic>.from(item))
            .toList();
      });
    } catch (error) {
      debugPrint('Error fetching departments: $error');
    }
  }

  Future<void> fetchBooks({bool reset = false}) async {
    if (!mounted) return;
    if (reset) {
      setState(() {
        isLoading = true;
        currentPage = 0;
        allBooks.clear();
      });
    } else {
      setState(() {
        isFetchingMore = true;
        currentPage++;
      });
    }

    try {
      final params = <String, String>{
        'page': '$currentPage',
        'size': '$pageSize',
      };
      if (searchQuery.trim().isNotEmpty) {
        params['query'] = searchQuery.trim();
      }
      if (selectedDepartment != null && selectedDepartment!.isNotEmpty) {
        params['department'] = selectedDepartment!;
      }
      final queryParam = '?${Uri(queryParameters: params).query}';
      final data = await ApiService.get('/books$queryParam');

      if (!mounted) return;
      setState(() {
        if (data != null && data['content'] != null) {
          allBooks.addAll(data['content']);
          totalPages = data['totalPages'] ?? 1;
        }
        isLoading = false;
        isFetchingMore = false;
        books = List<dynamic>.from(allBooks);
      });
    } catch (e) {
      debugPrint('Error fetching books: $e');
      if (!mounted) return;
      setState(() {
        isLoading = false;
        isFetchingMore = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Tra cứu tài liệu',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            tooltip: 'Làm mới danh sách',
            icon: const Icon(Icons.refresh),
            onPressed: () => fetchBooks(reset: true),
          ),
        ],
      ),
      body: Column(
        children: [
          // Search Bar
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: TextField(
              onChanged: (value) {
                searchQuery = value;
                fetchBooks(reset: true);
              },
              decoration: InputDecoration(
                hintText: 'Nhập tên sách, tác giả, ISBN...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: const Icon(Icons.tune),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
                filled: true,
                fillColor: Colors.white,
                contentPadding: const EdgeInsets.symmetric(vertical: 0),
              ),
            ),
          ),

          // Khoa / chuyên ngành
          SizedBox(
            height: 40,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                _buildChip('Tất cả', null, selectedDepartment == null),
                ...departments.map(
                  (department) => _buildChip(
                    department['displayName']?.toString() ??
                        department['key']?.toString() ??
                        'Chuyên ngành',
                    department['key']?.toString(),
                    selectedDepartment == department['key']?.toString(),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Search Results
          Expanded(
            child: isLoading
                ? const Center(child: CircularProgressIndicator())
                : books.isEmpty
                ? const Center(child: Text('Không tìm thấy kết quả.'))
                : RefreshIndicator(
                    onRefresh: () => fetchBooks(reset: true),
                    child: ListView.separated(
                      controller: _scrollController,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      itemCount: books.length + (isFetchingMore ? 1 : 0),
                      separatorBuilder: (context, index) =>
                          const Divider(height: 24),
                      itemBuilder: (context, index) {
                        if (index == books.length) {
                          return const Center(
                            child: Padding(
                              padding: EdgeInsets.all(8.0),
                              child: CircularProgressIndicator(),
                            ),
                          );
                        }
                        final book = Map<String, dynamic>.from(
                          books[index] as Map,
                        );
                        final rawBookId = book['bookId'] ?? book['id'];
                        final bookId = rawBookId is num
                            ? rawBookId.toInt()
                            : int.tryParse(rawBookId?.toString() ?? '');
                        final isSaved = WishlistService.isSavedSync(bookId);
                        final availableCopies =
                            (book['availableCopies'] as num?)?.toInt() ?? 0;
                        final totalCopies =
                            (book['copies'] as num?)?.toInt() ??
                            availableCopies;
                        final bool isAvailable = availableCopies > 0;

                        return InkWell(
                          onTap: () async {
                            await Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => DetailScreen(book: book),
                              ),
                            );
                            if (mounted) {
                              await fetchBooks(reset: true);
                            }
                          },
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              BookCover(
                                imageUrl:
                                    book['imageUrlM'] ??
                                    book['imageUrlL'] ??
                                    book['imageUrlS'] ??
                                    book['coverUrl'],
                                title: book['title'] ?? 'Unknown',
                                author: book['author'] ?? 'Unknown',
                                width: 70,
                                height: 100,
                                borderRadius: 8,
                              ),
                              const SizedBox(width: 16),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Expanded(
                                          child: Text(
                                            book['title'] ?? 'Unknown',
                                            style: const TextStyle(
                                              fontWeight: FontWeight.bold,
                                              fontSize: 16,
                                            ),
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                        if (isSaved) ...[
                                          const SizedBox(width: 6),
                                          const Icon(
                                            Icons.bookmark,
                                            color: Color(0xFF0284c7),
                                            size: 20,
                                          ),
                                        ],
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      book['author'] ?? 'Unknown',
                                      style: const TextStyle(
                                        color: Colors.grey,
                                        fontSize: 14,
                                      ),
                                    ),
                                    const SizedBox(height: 8),
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 4,
                                      ),
                                      decoration: BoxDecoration(
                                        color: isAvailable
                                            ? Colors.green.shade50
                                            : Colors.red.shade50,
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(
                                        isAvailable
                                            ? 'Có sẵn ($availableCopies/$totalCopies)'
                                            : 'Đã mượn hết (0/$totalCopies)',
                                        style: TextStyle(
                                          color: isAvailable
                                              ? Colors.green.shade700
                                              : Colors.red.shade700,
                                          fontSize: 12,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildChip(String label, String? department, bool isSelected) {
    return InkWell(
      onTap: () {
        setState(() {
          selectedDepartment = department;
        });
        fetchBooks(reset: true);
      },
      child: Container(
        margin: const EdgeInsets.only(right: 8),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFF0284c7) : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: isSelected ? null : Border.all(color: Colors.grey.shade300),
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              color: isSelected ? Colors.white : Colors.black87,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),
      ),
    );
  }
}
