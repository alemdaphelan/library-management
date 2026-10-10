import 'package:flutter/material.dart';
import '../services/api_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Map<String, dynamic> userInfo = {};
  List<dynamic> borrowedBooks = [];
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    fetchHomeData();
  }

  Future<void> fetchHomeData() async {
    try {
      final user = await ApiService.get('/user/profile');
      final loans = await ApiService.get('/loans/current');
      setState(() {
        userInfo = user ?? {};
        borrowedBooks = loans ?? [];
        isLoading = false;
      });
    } catch (e) {
      print('Error fetching home data: $e');
      setState(() {
        isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Thư viện HUIT', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Thẻ sinh viên
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
                  Text(userInfo['name'] ?? 'Đang cập nhật', style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                  Text('MSSV: ${userInfo['studentId'] ?? '---'}', style: const TextStyle(color: Colors.white, fontSize: 16)),
                  const SizedBox(height: 5),
                  Text(userInfo['department'] ?? 'Khoa Công nghệ thông tin', style: const TextStyle(color: Colors.white70, fontSize: 14)),
                ],
              ),
            ),
            const SizedBox(height: 30),

            // Sách đang mượn
            const Text('SÁCH ĐANG MƯỢN', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 15),

            if (borrowedBooks.isEmpty)
              const Center(child: Padding(
                padding: EdgeInsets.all(20.0),
                child: Text('Bạn không có sách nào đang mượn.', style: TextStyle(color: Colors.grey)),
              ))
            else
              ...borrowedBooks.map((book) => buildBorrowedBookCard(book)),
          ],
        ),
      ),
    );
  }

  Widget buildBorrowedBookCard(dynamic book) {
    // Assuming backend returns: title, author, remainingDays, progress
    double progress = book['progress']?.toDouble() ?? 0.5;
    int remainingDays = book['remainingDays'] ?? 0;
    Color statusColor = remainingDays <= 3 ? Colors.red : Colors.green;
    IconData statusIcon = remainingDays <= 3 ? Icons.warning_amber_rounded : Icons.access_time;

    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 10),
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
                    image: (book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']) != null ? DecorationImage(image: NetworkImage(book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']), fit: BoxFit.cover) : null,
                  ),
                  child: (book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl']) == null ? const Icon(Icons.book, color: Colors.grey) : null,
                ),
                const SizedBox(width: 15),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(book['title'] ?? 'Unknown', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      Text(book['author'] ?? 'Unknown', style: const TextStyle(color: Colors.grey, fontSize: 14)),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Icon(statusIcon, size: 16, color: statusColor),
                          const SizedBox(width: 5),
                          Text(remainingDays < 0 ? 'Quá hạn ${-remainingDays} ngày' : 'Còn $remainingDays ngày', 
                            style: TextStyle(color: statusColor, fontWeight: FontWeight.bold)),
                        ],
                      )
                    ],
                  ),
                )
              ],
            ),
            const SizedBox(height: 15),
            LinearProgressIndicator(value: progress, backgroundColor: Colors.grey.shade200, color: statusColor, minHeight: 6, borderRadius: BorderRadius.circular(3)),
          ],
        ),
      ),
    );
  }
}
