import 'package:flutter/material.dart';
import 'package:mobile_app/screens/notifications_screen.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import '../services/auth_service.dart';
import '../services/loan_service.dart';
import '../widgets/book_cover.dart';

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
      final user = await AuthService.getCurrentUser();
      List<dynamic> loans = await LoanService.getLoans();
      setState(() {
        userInfo = user ?? {};
        borrowedBooks = loans;
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

    final role = (userInfo['roleId'] ?? userInfo['role'] ?? userInfo['userType'] ?? '').toString().toUpperCase();
    final email = (userInfo['email'] ?? '').toString().toLowerCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF' || email.contains('admin');
    final mssv = userInfo['mssv'] ?? userInfo['studentId'];

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
            // Thẻ Người Dùng (Phân biệt Admin vs Sinh viên)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: isAdmin
                      ? const [Color(0xFF0f172a), Color(0xFF1e293b), Color(0xFF334155)]
                      : const [Color(0xFF0284c7), Color(0xFF0369a1)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: (isAdmin ? Colors.black : const Color(0xFF0284c7)).withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 5),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Icon(
                            isAdmin ? Icons.admin_panel_settings_rounded : Icons.school_rounded,
                            color: isAdmin ? Colors.amber.shade300 : Colors.white,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            isAdmin ? 'THẺ CÁN BỘ / QUẢN TRỊ' : 'THẺ THƯ VIỆN SINH VIÊN',
                            style: TextStyle(
                              color: isAdmin ? Colors.amber.shade300 : Colors.white70,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 1.2,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                      const Icon(Icons.qr_code_2, color: Colors.white, size: 36),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    userInfo['fullName'] ?? (isAdmin ? 'Quản trị viên hệ thống' : 'Sinh viên HUIT'),
                    style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    isAdmin
                        ? 'Mã cán bộ: CB-2026-001  •  Email: ${userInfo['email'] ?? 'admin@huit.edu.vn'}'
                        : 'MSSV: ${mssv ?? '2001230430'}',
                    style: const TextStyle(color: Colors.white, fontSize: 15),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    userInfo['department'] ?? (isAdmin ? 'Phòng Quản lý & Vận hành Thư viện' : 'Khoa Công nghệ thông tin'),
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                ],
              ),
            ),

            if (isAdmin) ...[
              const SizedBox(height: 25),
              // Bảng điều hành thư viện nhanh cho Admin
              const Text('BẢNG ĐIỀU HÀNH THƯ VIỆN', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: [
                          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 6, offset: const Offset(0, 2)),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Icon(Icons.menu_book_rounded, color: Color(0xFF0284c7), size: 24),
                          SizedBox(height: 6),
                          Text('1,000+', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                          Text('Sách trong kho', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: [
                          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 6, offset: const Offset(0, 2)),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Icon(Icons.swap_horiz_rounded, color: Colors.green, size: 24),
                          SizedBox(height: 6),
                          Text('18', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                          Text('Mượn hôm nay', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: [
                          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 6, offset: const Offset(0, 2)),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Icon(Icons.warning_amber_rounded, color: Colors.red, size: 24),
                          SizedBox(height: 6),
                          Text('3', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                          Text('Quá hạn', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ],

            const SizedBox(height: 30),

            // Sách đang mượn
            Text(
              isAdmin ? 'SÁCH CÁ NHÂN ĐANG MƯỢN' : 'SÁCH ĐANG MƯỢN',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 15),

            if (borrowedBooks.isEmpty)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Text(
                    isAdmin
                        ? 'Tài khoản Quản trị viên chưa đăng ký mượn sách nào.'
                        : 'Bạn không có sách nào đang mượn.',
                    style: const TextStyle(color: Colors.grey),
                  ),
                ),
              )
            else
              ...borrowedBooks.map((book) => buildBorrowedBookCard(book)),

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
                BookCover(
                  imageUrl: book['coverUrl'] ?? book['imageUrlM'],
                  title: book['title'] ?? 'Unknown',
                  author: book['author'],
                  width: 60,
                  height: 80,
                  borderRadius: 8,
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

  Widget _buildRecommendCard(String title, String author, String tag) {
    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => DetailScreen(
              book: {
                'title': title,
                'author': author,
                'category': tag,
                'status': 'available',
                'publishYear': 2024,
                'publisher': 'Nhà xuất bản Công thương',
              },
            ),
          ),
        );
      },
      child: Container(
        width: 140,
        margin: const EdgeInsets.only(right: 15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            BookCover(
              title: title,
              author: author,
              width: 140,
              height: 140,
              borderRadius: 12,
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
      ),
    );
  }
}
