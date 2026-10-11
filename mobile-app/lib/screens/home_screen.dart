import 'package:flutter/material.dart';
import 'package:mobile_app/screens/notifications_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thư viện HUIT', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          Stack(
            alignment: Alignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_outlined),
                onPressed: () {
                  Navigator.push(context, MaterialPageRoute(builder: (context) => const NotificationsScreen()));
                },
              ),
              Positioned(
                right: 12,
                top: 12,
                child: Container(
                  padding: const EdgeInsets.all(2),
                  decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                  constraints: const BoxConstraints(minWidth: 10, minHeight: 10),
                ),
              )
            ],
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Thẻ sinh viên ảo
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF0284c7), Color(0xFF0369a1)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(color: const Color(0xFF0284c7).withOpacity(0.3), blurRadius: 10, offset: const Offset(0, 5)),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'THẺ THƯ VIỆN',
                        style: TextStyle(color: Colors.white70, fontWeight: FontWeight.bold, letterSpacing: 1.5),
                      ),
                      const Icon(Icons.qr_code_2, color: Colors.white, size: 40),
                    ],
                  ),
                  const SizedBox(height: 20),
                  const Text('Nguyễn Văn A', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                  const Text('MSSV: 2001190001', style: TextStyle(color: Colors.white, fontSize: 16)),
                  const SizedBox(height: 5),
                  const Text('Khoa Công nghệ thông tin', style: TextStyle(color: Colors.white70, fontSize: 14)),
                ],
              ),
            ),
            const SizedBox(height: 30),

            // Tiến độ đọc sách
            const Text('SÁCH ĐANG MƯỢN', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 15),

            // Card Sách đang mượn 1 (Sắp hết hạn)
            Card(
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: Colors.grey.shade200)),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 60, height: 80,
                          decoration: BoxDecoration(
                            color: Colors.grey.shade200,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(Icons.book, color: Colors.grey),
                        ),
                        const SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('Clean Architecture', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                              const Text('Robert C. Martin', style: TextStyle(color: Colors.grey, fontSize: 14)),
                              const SizedBox(height: 10),
                              Row(
                                children: [
                                  const Icon(Icons.warning_amber_rounded, size: 16, color: Colors.red),
                                  const SizedBox(width: 5),
                                  const Text('Còn 2 ngày', style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
                                ],
                              )
                            ],
                          ),
                        )
                      ],
                    ),
                    const SizedBox(height: 15),
                    LinearProgressIndicator(value: 0.8, backgroundColor: Colors.grey.shade200, color: Colors.red, minHeight: 6, borderRadius: BorderRadius.circular(3)),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 10),

            // Card Sách đang mượn 2 (Bình thường)
            Card(
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: Colors.grey.shade200)),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 60, height: 80,
                          decoration: BoxDecoration(
                            color: Colors.grey.shade200,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(Icons.book, color: Colors.grey),
                        ),
                        const SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('Flutter in Action', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                              const Text('Eric Windmill', style: TextStyle(color: Colors.grey, fontSize: 14)),
                              const SizedBox(height: 10),
                              Row(
                                children: [
                                  const Icon(Icons.access_time, size: 16, color: Colors.green),
                                  const SizedBox(width: 5),
                                  const Text('Còn 12 ngày', style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold)),
                                ],
                              )
                            ],
                          ),
                        )
                      ],
                    ),
                    const SizedBox(height: 15),
                    LinearProgressIndicator(value: 0.2, backgroundColor: Colors.grey.shade200, color: Colors.green, minHeight: 6, borderRadius: BorderRadius.circular(3)),
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 30),
            
            // Gợi ý cá nhân hóa
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('GỢI Ý CHO BẠN', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                TextButton(
                  onPressed: () {},
                  child: const Text('Xem tất cả >', style: TextStyle(color: Color(0xFF0284c7))),
                )
              ],
            ),
            const SizedBox(height: 10),
            SizedBox(
              height: 200,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  _buildRecommendCard('Domain-Driven Design', 'Eric Evans', 'CNTT'),
                  _buildRecommendCard('Head First Design Patterns', 'Eric Freeman', 'CNTT'),
                  _buildRecommendCard('Clean Code', 'Robert C. Martin', 'CNTT'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRecommendCard(String title, String author, String tag) {
    return Container(
      width: 140,
      margin: const EdgeInsets.only(right: 15),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            height: 140,
            decoration: BoxDecoration(
              color: Colors.grey.shade200,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Center(child: Icon(Icons.book, size: 50, color: Colors.grey)),
          ),
          const SizedBox(height: 8),
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          Text(
            author,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Colors.grey, fontSize: 12),
          ),
        ],
      ),
    );
  }
}

