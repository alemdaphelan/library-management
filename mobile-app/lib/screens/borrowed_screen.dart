import 'package:flutter/material.dart';

import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/screens/login_screen.dart';

class BorrowedScreen extends StatefulWidget {
  const BorrowedScreen({super.key});

  @override
  State<BorrowedScreen> createState() => _BorrowedScreenState();
}

class _BorrowedScreenState extends State<BorrowedScreen> {
  Map<String, dynamic>? _userProfile;
  List<dynamic> _borrowedBooks = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    try {
      final profile = await AuthService.getCurrentUser();
      List<dynamic> loans = [];
      try {
        final loansRes = await ApiService.get('/loans/current');
        if (loansRes is List && loansRes.isNotEmpty) {
          loans = loansRes;
        } else {
          throw Exception('No loans');
        }
      } catch (_) {
        loans = [
          {
            'title': 'Giáo trình Cấu trúc dữ liệu và Giải thuật',
            'author': 'Đỗ Xuân Lôi',
            'borrowDate': '01/10/2026',
            'dueDate': '16/10/2026',
            'remainingDays': 5,
            'progress': 0.6,
            'coverUrl': null,
          },
          {
            'title': 'Kỹ nghệ phần mềm thực hành',
            'author': 'Roger S. Pressman',
            'borrowDate': '25/09/2026',
            'dueDate': '13/10/2026',
            'remainingDays': 2,
            'progress': 0.85,
            'coverUrl': null,
          }
        ];
      }
      if (mounted) {
        setState(() {
          _userProfile = profile;
          _borrowedBooks = loans;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _handleLogout() async {
    await AuthService.logout();
    if (mounted) {
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (context) => const LoginScreen()),
        (route) => false,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFf8f9ff),
      appBar: AppBar(
        title: const Text('Cá nhân', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout, color: Colors.red),
            onPressed: _handleLogout,
          )
        ],
      ),
      body: _isLoading 
        ? const Center(child: CircularProgressIndicator()) 
        : SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildProfileHeader(),
                const SizedBox(height: 20),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Sách đang mượn (${_borrowedBooks.length})',
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0f172a),
                        ),
                      ),
                      TextButton(
                        onPressed: () {},
                        child: const Text('Lịch sử mượn >'),
                      )
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                _buildBorrowedBookList(),
              ],
            ),
          ),
    );
  }

  Widget _buildProfileHeader() {
    final fullName = _userProfile?['fullName'] ?? _userProfile?['name'] ?? 'Tô Khiết Lâm';
    final studentId = _userProfile?['studentId'] ?? _userProfile?['mssv'] ?? '2001230430';
    final faculty = _userProfile?['faculty'] ?? _userProfile?['department'] ?? 'Khoa Công nghệ thông tin';

    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0284c7), Color(0xFF0369a1)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0284c7).withOpacity(0.3),
            blurRadius: 10,
            offset: const Offset(0, 5),
          )
        ],
      ),
      child: Column(
        children: [
          Row(
            children: [
              const CircleAvatar(
                radius: 35,
                backgroundColor: Colors.white,
                child: Icon(Icons.person, size: 40, color: Color(0xFF0284c7)),
              ),
              const SizedBox(width: 15),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      fullName,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      'MSSV: $studentId',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                    Text(
                      faculty,
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              )
            ],
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 15),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Text('Tiền phạt', style: TextStyle(color: Colors.white70, fontSize: 13)),
                    Text('0 VNĐ', style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                  ],
                ),
                ElevatedButton(
                  onPressed: () {},
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: const Color(0xFF0284c7),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text('Thẻ Thư Viện QR'),
                )
              ],
            ),
          )
        ],
      ),
    );
  }

  Widget _buildBorrowedBookList() {
    if (_borrowedBooks.isEmpty) {
      return const Padding(
        padding: EdgeInsets.all(20.0),
        child: Center(
          child: Text('Bạn không có sách nào đang mượn.', style: TextStyle(color: Colors.grey)),
        ),
      );
    }
    
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0),
      child: Column(
        children: _borrowedBooks.map((book) {
          int remainingDays = book['remainingDays'] ?? 0;
          return Column(
            children: [
              _buildBookCard(
                title: book['title'] ?? 'Unknown',
                author: book['author'] ?? 'Unknown',
                borrowDate: book['borrowDate'] ?? '---',
                dueDate: book['dueDate'] ?? '---',
                daysLeft: remainingDays,
                isWarning: remainingDays <= 3,
              ),
              const SizedBox(height: 15),
            ],
          );
        }).toList(),
      ),
    );
  }

  Widget _buildBookCard({
    required String title,
    required String author,
    required String borrowDate,
    required String dueDate,
    required int daysLeft,
    required bool isWarning,
  }) {
    Color statusColor = isWarning ? Colors.red : const Color(0xFF0284c7);
    String statusText = isWarning ? 'Quá hạn ${daysLeft.abs()} ngày' : 'Còn $daysLeft ngày';
    double progress = isWarning ? 1.0 : (10 - daysLeft) / 10;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 8,
            offset: const Offset(0, 4),
          )
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 50,
                  height: 70,
                  decoration: BoxDecoration(
                    color: const Color(0xFFe2e8f0),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Icon(Icons.book, color: Colors.grey),
                ),
                const SizedBox(width: 15),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0f172a),
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        author,
                        style: const TextStyle(
                          fontSize: 14,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 15),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Ngày mượn', style: TextStyle(fontSize: 12, color: Colors.grey)),
                    Text(borrowDate, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text('Hạn trả', style: TextStyle(fontSize: 12, color: Colors.grey)),
                    Text(dueDate, style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: statusColor)),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 15),
            Row(
              children: [
                Expanded(
                  child: LinearProgressIndicator(
                    value: progress,
                    backgroundColor: Colors.grey[200],
                    valueColor: AlwaysStoppedAnimation<Color>(statusColor),
                    minHeight: 6,
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  statusText,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                  ),
                )
              ],
            ),
            const SizedBox(height: 15),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {},
                style: ElevatedButton.styleFrom(
                  backgroundColor: isWarning ? Colors.red[50] : const Color(0xFFf0f9ff),
                  foregroundColor: statusColor,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: const Text('Gia hạn sách'),
              ),
            )
          ],
        ),
      ),
    );
  }
}
