import 'package:flutter/material.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import '../services/api_service.dart';
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
  int? selectedCategoryId;
  String? selectedDepartment;
  List<Map<String, dynamic>> categories = [];
  List<Map<String, dynamic>> departments = [];

  int currentPage = 0;
  int pageSize = 10;
  int totalPages = 1;
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    fetchCategories();
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

  Future<void> fetchCategories() async {
    try {
      final data = await ApiService.get('/categories');
      if (!mounted || data is! List) return;
      setState(() {
        categories = data
            .map((item) => Map<String, dynamic>.from(item))
            .toList();
      });
    } catch (e) {
      debugPrint('Error fetching categories: $e');
    }
  }

  Future<void> fetchBooks({bool reset = false}) async {
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
      if (selectedCategoryId != null) {
        params['categoryId'] = '$selectedCategoryId';
      }
      if (selectedDepartment != null && selectedDepartment!.isNotEmpty) {
        params['department'] = selectedDepartment!;
      }
      final queryParam = '?${Uri(queryParameters: params).query}';
      final data = await ApiService.get('/books$queryParam');

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

          // Categories Horizontal List
          SizedBox(
            height: 40,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                _buildChip('Tất cả', null, selectedCategoryId == null),
                ...categories.map(
                  (category) => _buildChip(
                    category['name']?.toString() ?? 'Khác',
                    category['categoryId'] as int?,
                    selectedCategoryId == category['categoryId'],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: DropdownButtonFormField<String?>(
              initialValue: selectedDepartment,
              isExpanded: true,
              decoration: InputDecoration(
                labelText: 'Lọc theo khoa / chuyên ngành',
                prefixIcon: const Icon(Icons.school_outlined),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
              items: [
                const DropdownMenuItem<String?>(
                  value: null,
                  child: Text('Tất cả chuyên ngành'),
                ),
                ...departments.map(
                  (department) => DropdownMenuItem<String?>(
                    value: department['key']?.toString(),
                    child: Text(
                      department['displayName']?.toString() ??
                          department['key']?.toString() ??
                          'Chuyên ngành',
                    ),
                  ),
                ),
              ],
              onChanged: (value) {
                setState(() => selectedDepartment = value);
                fetchBooks(reset: true);
              },
            ),
          ),
          const SizedBox(height: 12),

          // Search Results
          Expanded(
            child: isLoading
                ? const Center(child: CircularProgressIndicator())
                : books.isEmpty
                ? const Center(child: Text('Không tìm thấy kết quả.'))
                : ListView.separated(
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
                      final book = books[index];
                      final bool isAvailable =
                          ((book['availableCopies'] as num?)?.toInt() ?? 0) > 0;

                      return InkWell(
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => DetailScreen(book: book),
                            ),
                          );
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
                                  Text(
                                    book['title'] ?? 'Unknown',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 16,
                                    ),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
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
                                      isAvailable ? 'Có sẵn' : 'Đã mượn hết',
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
        ],
      ),
    );
  }

  Widget _buildChip(String label, int? categoryId, bool isSelected) {
    return InkWell(
      onTap: () {
        setState(() {
          selectedCategoryId = categoryId;
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
