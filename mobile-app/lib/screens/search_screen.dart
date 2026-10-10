import 'package:flutter/material.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import '../services/api_service.dart';

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
  String selectedCategory = 'Tất cả';
  
  int currentPage = 0;
  int pageSize = 10;
  int totalPages = 1;
  ScrollController _scrollController = ScrollController();

  final List<String> categories = ['Tất cả', 'Khoa học máy tính', 'Hệ thống thông tin', 'Đồ họa', 'Kinh tế', 'Ngoại ngữ'];

  @override
  void initState() {
    super.initState();
    fetchBooks(reset: true);
    
    _scrollController.addListener(() {
      if (_scrollController.position.pixels == _scrollController.position.maxScrollExtent) {
        if (currentPage + 1 < totalPages && !isFetchingMore) {
          fetchBooks(reset: false);
        }
      }
    });
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
      final queryParam = searchQuery.isNotEmpty ? '?query=$searchQuery&page=$currentPage&size=$pageSize' : '?page=$currentPage&size=$pageSize';
      final data = await ApiService.get('/catalog/books$queryParam');
      
      setState(() {
        if (data != null && data['content'] != null) {
          allBooks.addAll(data['content']);
          totalPages = data['totalPages'] ?? 1;
        }
        isLoading = false;
        isFetchingMore = false;
        applyFilters();
      });
    } catch (e) {
      print('Error fetching books: $e');
      setState(() {
        isLoading = false;
        isFetchingMore = false;
      });
    }
  }

  void applyFilters() {
    setState(() {
      books = allBooks.where((book) {
        bool matchesCategory = true;
        if (selectedCategory != 'Tất cả') {
          matchesCategory = book['category'] == selectedCategory;
        }
        return matchesCategory;
      }).toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tra cứu tài liệu', style: TextStyle(fontWeight: FontWeight.bold)),
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
              children: categories.map((cat) => _buildChip(cat, selectedCategory == cat)).toList(),
            ),
          ),
          const SizedBox(height: 16),

          // Search Results
          Expanded(
            child: isLoading 
              ? const Center(child: CircularProgressIndicator())
              : books.isEmpty
                ? const Center(child: Text('Không tìm thấy kết quả.'))
                : ListView.separated(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    itemCount: books.length + (isFetchingMore ? 1 : 0),
                    separatorBuilder: (context, index) => const Divider(height: 24),
                    itemBuilder: (context, index) {
                      if (index == books.length) {
                        return const Center(child: Padding(padding: EdgeInsets.all(8.0), child: CircularProgressIndicator()));
                      }
                      final book = books[index];
                      final bool isAvailable = (book['status'] ?? 'available') == 'available';
                      
                      return InkWell(
                        onTap: () {
                          // In a real app, pass book ID to detail screen
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (context) => const DetailScreen()),
                          );
                        },
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              width: 70, height: 100,
                              decoration: BoxDecoration(
                                color: Colors.grey.shade200,
                                borderRadius: BorderRadius.circular(8),
                                image: (book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']) != null ? DecorationImage(image: NetworkImage(book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']), fit: BoxFit.cover) : null,
                              ),
                              child: (book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']) == null ? const Icon(Icons.image, color: Colors.grey) : null,
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    book['title'] ?? 'Unknown',
                                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                    maxLines: 2, overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(book['author'] ?? 'Unknown', style: const TextStyle(color: Colors.grey, fontSize: 14)),
                                  const SizedBox(height: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: isAvailable ? Colors.green.shade50 : Colors.red.shade50,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: Text(
                                      isAvailable ? 'Có sẵn' : 'Đã mượn hết',
                                      style: TextStyle(
                                        color: isAvailable ? Colors.green.shade700 : Colors.red.shade700,
                                        fontSize: 12, fontWeight: FontWeight.bold
                                      ),
                                    ),
                                  )
                                ],
                              ),
                            )
                          ],
                        ),
                      );
                    },
                  ),
          )
        ],
      ),
    );
  }

  Widget _buildChip(String label, bool isSelected) {
    return InkWell(
      onTap: () {
        setState(() {
          selectedCategory = label;
          applyFilters();
        });
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
