import 'package:flutter/material.dart';

class DetailScreen extends StatelessWidget {
  const DetailScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chi tiết sách', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.bookmark_outline),
            onPressed: () {},
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
              color: Colors.grey.shade200,
              child: const Center(
                child: Icon(Icons.menu_book, size: 100, color: Colors.grey),
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
                      const Expanded(
                        child: Text(
                          'Giáo trình Lập trình Flutter Cơ bản',
                          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900, height: 1.2),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: Colors.green.shade50,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text('Có sẵn', style: TextStyle(color: Colors.green.shade700, fontWeight: FontWeight.bold)),
                      )
                    ],
                  ),
                  const SizedBox(height: 10),
                  const Text('Tác giả: PGS. TS. Nguyễn Văn B', style: TextStyle(fontSize: 16, color: Colors.grey)),
                  const SizedBox(height: 5),
                  const Text('Năm xuất bản: 2023 | NXB Đại học Quốc gia', style: TextStyle(fontSize: 14, color: Colors.grey)),
                  
                  const SizedBox(height: 30),
                  
                  // Location Card
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.grey.shade200),
                      boxShadow: [
                        BoxShadow(color: Colors.black.withOpacity(0.02), blurRadius: 10, offset: const Offset(0, 5)),
                      ],
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.location_on, color: Color(0xFF0284c7), size: 30),
                        SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('VỊ TRÍ LƯU TRỮ', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey)),
                              SizedBox(height: 5),
                              Text('Tầng 3 - Kệ sách CNTT-02', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                              Text('Mã phân loại: 005.133', style: TextStyle(fontSize: 14)),
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
