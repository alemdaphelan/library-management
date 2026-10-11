import 'package:flutter/material.dart';

class DetailScreen extends StatelessWidget {
  final Map<String, dynamic>? book;

  const DetailScreen({super.key, this.book});

  @override
  Widget build(BuildContext context) {
    final title = book?['title'] ?? 'Giáo trình Lập trình Flutter Cơ bản';
    final author = book?['author'] ?? 'PGS. TS. Nguyễn Văn B';
    final publisher = book?['publisher'] ?? 'NXB Đại học Quốc gia TP.HCM';
    final year = book?['publishYear']?.toString() ?? '2023';
    final category = book?['category'] ?? 'Công nghệ thông tin';
    final isAvailable = (book?['status'] ?? 'available') == 'available';
    final imageUrl = book?['imageUrlL'] ?? book?['imageUrlM'] ?? book?['imageUrlS'] ?? book?['coverUrl'];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Chi tiết sách', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.bookmark_outline),
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Đã lưu vào danh sách quan tâm!')),
              );
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Cover Image Header
            Container(
              width: double.infinity,
              height: 250,
              decoration: BoxDecoration(
                color: Colors.grey.shade200,
                image: imageUrl != null
                    ? DecorationImage(image: NetworkImage(imageUrl), fit: BoxFit.contain)
                    : null,
              ),
              child: imageUrl == null
                  ? const Center(child: Icon(Icons.menu_book, size: 100, color: Colors.grey))
                  : null,
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
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900, height: 1.2),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: isAvailable ? Colors.green.shade50 : Colors.red.shade50,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          isAvailable ? 'Có sẵn' : 'Đã mượn hết',
                          style: TextStyle(
                            color: isAvailable ? Colors.green.shade700 : Colors.red.shade700,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      )
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text('Tác giả: $author', style: const TextStyle(fontSize: 16, color: Colors.grey)),
                  const SizedBox(height: 5),
                  Text('Năm xuất bản: $year | $publisher', style: const TextStyle(fontSize: 14, color: Colors.grey)),
                  
                  const SizedBox(height: 25),
                  
                  // Location Card
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.grey.shade200),
                      boxShadow: [
                        BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 10, offset: const Offset(0, 5)),
                      ],
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.location_on, color: Color(0xFF0284c7), size: 32),
                        const SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('VỊ TRÍ LƯU TRỮ TRONG THƯ VIỆN', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey)),
                              const SizedBox(height: 5),
                              Text('Tầng 3 - Kệ sách $category', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                              Text('Mã định danh: ISBN ${book?['isbn'] ?? '978-604-0-1234'}', style: const TextStyle(fontSize: 14)),
                            ],
                          ),
                        )
                      ],
                    ),
                  ),

                  const SizedBox(height: 30),
                  const Text('TÓM TẮT', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  const Text(
                    'Cuốn sách cung cấp kiến thức nền tảng về framework Flutter, giúp người học làm quen với ngôn ngữ Dart và cách xây dựng giao diện đa nền tảng một cách nhanh chóng và hiệu quả. Bao gồm nhiều ví dụ thực tế và bài tập thực hành.',
                    style: TextStyle(fontSize: 15, height: 1.6, color: Colors.black87),
                  ),
                ],
              ),
            )
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: SizedBox(
            height: 55,
            child: ElevatedButton(
              onPressed: () {},
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0284c7),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text('ĐĂNG KÝ MƯỢN SÁCH', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            ),
          ),
        ),
      ),
    );
  }
}
