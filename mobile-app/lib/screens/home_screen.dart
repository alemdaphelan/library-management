import 'package:flutter/material.dart';
import 'package:mobile_app/screens/notifications_screen.dart';
import 'package:mobile_app/screens/detail_screen.dart';
import 'package:mobile_app/screens/librarian_qr_screen.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../services/loan_service.dart';
import '../services/notification_service.dart';
import '../services/recommendation_service.dart';
import '../widgets/book_cover.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Map<String, dynamic> userInfo = {};
  List<dynamic> borrowedBooks = [];
  List<RecommendationItem> recommendations = [];
  String recommendationMethod = '';
  bool recommendationDataSufficient = false;
  bool recommendationLoadFailed = false;
  int unreadNotificationCount = 0;
  int catalogTotalElements = 0;
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    fetchHomeData();
  }

  Future<void> fetchHomeData() async {
    try {
      final user = await AuthService.getCurrentUser();
      final results = await Future.wait([
        LoanService.getLoans(activeOnly: true),
        ApiService.get('/books?page=0&size=3'),
        NotificationService.getMyNotifications(),
        _loadRecommendationsSafely(),
      ]);
      final loans = results[0] as List<Map<String, dynamic>>;
      final catalog = results[1] as Map<String, dynamic>;
      final notifications = results[2] as List<Map<String, dynamic>>;
      final recommendationResult = results[3] as RecommendationResult?;
      setState(() {
        userInfo = user ?? {};
        borrowedBooks = loans;
        recommendations = recommendationResult?.items ?? [];
        recommendationMethod = recommendationResult?.method ?? '';
        recommendationDataSufficient =
            recommendationResult?.dataSufficient ?? false;
        recommendationLoadFailed = recommendationResult == null;
        catalogTotalElements = (catalog['totalElements'] as num?)?.toInt() ?? 0;
        unreadNotificationCount = notifications
            .where((item) => item['isRead'] != true)
            .length;
        isLoading = false;
      });
    } catch (e) {
      debugPrint('Error fetching home data: $e');
      setState(() {
        isLoading = false;
      });
    }
  }

  Future<RecommendationResult?> _loadRecommendationsSafely() async {
    try {
      return await RecommendationService.getRecommendations(limit: 10);
    } catch (error) {
      debugPrint('Error fetching recommendations: $error');
      return null;
    }
  }

  String get _recommendationTitle {
    return recommendationMethod == 'POPULARITY_BASELINE'
        ? 'SÁCH ĐƯỢC QUAN TÂM NHIỀU'
        : 'GỢI Ý RIÊNG CHO BẠN';
  }

  String get _recommendationDescription {
    switch (recommendationMethod) {
      case 'HYBRID_PERSONALIZED':
        return 'Theo lịch sử mượn, chuyên ngành và xu hướng đọc';
      case 'HYBRID_COLD_START':
        return 'Khởi đầu theo chuyên ngành và tài liệu nổi bật';
      case 'POPULARITY_BASELINE':
        return 'Dựa trên lượt mượn gần đây tại thư viện';
      default:
        return recommendationDataSufficient
            ? 'Được cá nhân hóa từ dữ liệu thư viện'
            : 'Dữ liệu gợi ý đang được cập nhật';
    }
  }

  Future<void> _openRecommendation(RecommendationItem item) async {
    var book = item.toBookMap();
    try {
      final response = await ApiService.get('/books/${item.bookId}');
      book = Map<String, dynamic>.from(response as Map)
        ..['recommendationReason'] = item.reason
        ..['recommendationScore'] = item.score;
    } catch (error) {
      debugPrint('Error fetching recommended book detail: $error');
    }

    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => DetailScreen(book: book)),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final role =
        (userInfo['roleId'] ?? userInfo['role'] ?? userInfo['userType'] ?? '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';
    final canUseLibrarianQr = role == 'ADMIN' || role == 'LIBRARIAN';
    final mssv = userInfo['mssv'] ?? userInfo['studentId'];

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Thư viện HUIT',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          Stack(
            alignment: Alignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_outlined),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const NotificationsScreen(),
                    ),
                  );
                },
              ),
              if (unreadNotificationCount > 0)
                Positioned(
                  right: 8,
                  top: 8,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 5,
                      vertical: 2,
                    ),
                    decoration: const BoxDecoration(
                      color: Colors.red,
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      unreadNotificationCount > 9
                          ? '9+'
                          : '$unreadNotificationCount',
                      style: const TextStyle(color: Colors.white, fontSize: 10),
                    ),
                  ),
                ),
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
                      ? const [
                          Color(0xFF0f172a),
                          Color(0xFF1e293b),
                          Color(0xFF334155),
                        ]
                      : const [Color(0xFF0284c7), Color(0xFF0369a1)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: (isAdmin ? Colors.black : const Color(0xFF0284c7))
                        .withValues(alpha: 0.3),
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
                            isAdmin
                                ? Icons.admin_panel_settings_rounded
                                : Icons.school_rounded,
                            color: isAdmin
                                ? Colors.amber.shade300
                                : Colors.white,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            isAdmin
                                ? 'THẺ CÁN BỘ / QUẢN TRỊ'
                                : 'THẺ THƯ VIỆN SINH VIÊN',
                            style: TextStyle(
                              color: isAdmin
                                  ? Colors.amber.shade300
                                  : Colors.white70,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 1.2,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                      const Icon(
                        Icons.qr_code_2,
                        color: Colors.white,
                        size: 36,
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    userInfo['fullName'] ?? 'Chưa cập nhật',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    isAdmin
                        ? 'Email: ${userInfo['email'] ?? 'Chưa cập nhật'}'
                        : 'MSSV: ${mssv ?? 'Chưa cập nhật'}',
                    style: const TextStyle(color: Colors.white, fontSize: 15),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    userInfo['department'] ?? 'Chưa cập nhật',
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                ],
              ),
            ),

            if (isAdmin) ...[
              const SizedBox(height: 25),
              // Bảng điều hành thư viện nhanh cho Admin
              const Text(
                'BẢNG ĐIỀU HÀNH THƯ VIỆN',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
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
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.04),
                            blurRadius: 6,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.menu_book_rounded,
                            color: Color(0xFF0284c7),
                            size: 24,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '$catalogTotalElements',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const Text(
                            'Sách trong kho',
                            style: TextStyle(fontSize: 11, color: Colors.grey),
                          ),
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
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.04),
                            blurRadius: 6,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.swap_horiz_rounded,
                            color: Colors.green,
                            size: 24,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '${borrowedBooks.where((book) => book['status'] == 'BORROWING').length}',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const Text(
                            'Đang mượn cá nhân',
                            style: TextStyle(fontSize: 11, color: Colors.grey),
                          ),
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
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.04),
                            blurRadius: 6,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.warning_amber_rounded,
                            color: Colors.red,
                            size: 24,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '${borrowedBooks.where((book) => book['status'] == 'OVERDUE').length}',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const Text(
                            'Quá hạn',
                            style: TextStyle(fontSize: 11, color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              if (canUseLibrarianQr) ...[
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const LibrarianQrScreen(),
                        ),
                      );
                    },
                    icon: const Icon(Icons.qr_code_scanner),
                    label: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 12),
                      child: Text('QUÉT QR MƯỢN / TRẢ TẠI QUẦY'),
                    ),
                  ),
                ),
              ],
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

            Text(
              _recommendationTitle,
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 4),
            Text(
              _recommendationDescription,
              style: TextStyle(color: Colors.blueGrey.shade600, fontSize: 12),
            ),
            const SizedBox(height: 10),
            SizedBox(
              height: 255,
              child: recommendations.isEmpty
                  ? Center(
                      child: Text(
                        recommendationLoadFailed
                            ? 'Chưa thể tải gợi ý lúc này.'
                            : 'Chưa có đủ tài liệu phù hợp để gợi ý.',
                        style: const TextStyle(color: Colors.grey),
                      ),
                    )
                  : ListView(
                      scrollDirection: Axis.horizontal,
                      children: recommendations
                          .map((item) => _buildRecommendCard(item))
                          .toList(),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget buildBorrowedBookCard(dynamic book) {
    // LoanService derives these values from the authenticated /loans/my-loans response.
    double progress = book['progress']?.toDouble() ?? 0.0;
    int remainingDays = book['remainingDays'] ?? 0;
    Color statusColor = remainingDays <= 3 ? Colors.red : Colors.green;
    IconData statusIcon = remainingDays <= 3
        ? Icons.warning_amber_rounded
        : Icons.access_time;

    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade200),
      ),
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
                      Text(
                        book['title'] ?? 'Unknown',
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      Text(
                        book['author'] ?? 'Unknown',
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Icon(statusIcon, size: 16, color: statusColor),
                          const SizedBox(width: 5),
                          Text(
                            remainingDays < 0
                                ? 'Quá hạn ${-remainingDays} ngày'
                                : 'Còn $remainingDays ngày',
                            style: TextStyle(
                              color: statusColor,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 15),
            LinearProgressIndicator(
              value: progress,
              backgroundColor: Colors.grey.shade200,
              color: statusColor,
              minHeight: 6,
              borderRadius: BorderRadius.circular(3),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRecommendCard(RecommendationItem item) {
    final percent = (item.score * 100).round().clamp(0, 100);
    return InkWell(
      onTap: () => _openRecommendation(item),
      child: Container(
        width: 140,
        margin: const EdgeInsets.only(right: 15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            BookCover(
              imageUrl: item.imageUrl,
              title: item.title,
              author: item.author,
              width: 140,
              height: 140,
              borderRadius: 12,
            ),
            const SizedBox(height: 8),
            Text(
              item.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            Text(
              item.author,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.grey, fontSize: 12),
            ),
            const SizedBox(height: 5),
            Row(
              children: [
                const Icon(
                  Icons.auto_awesome_rounded,
                  size: 13,
                  color: Color(0xFF0284c7),
                ),
                const SizedBox(width: 4),
                Text(
                  '$percent% phù hợp',
                  style: const TextStyle(
                    color: Color(0xFF0284c7),
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 3),
            Text(
              item.reason,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: Colors.blueGrey.shade600, fontSize: 10),
            ),
          ],
        ),
      ),
    );
  }
}
