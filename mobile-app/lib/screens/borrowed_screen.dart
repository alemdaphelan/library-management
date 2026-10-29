import 'package:flutter/material.dart';

import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/loan_service.dart';
import 'package:mobile_app/widgets/book_cover.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/services/librarian_qr_service.dart';
import 'package:mobile_app/services/library_policy_service.dart';
import 'package:qr_flutter/qr_flutter.dart';

class BorrowedScreen extends StatefulWidget {
  const BorrowedScreen({super.key});

  @override
  State<BorrowedScreen> createState() => _BorrowedScreenState();
}

class _BorrowedScreenState extends State<BorrowedScreen> {
  Map<String, dynamic>? _userProfile;
  List<dynamic> _borrowedBooks = [];
  bool _isLoading = true;
  LibraryPolicy? _policy;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    try {
      final profile = await AuthService.getCurrentUser();
      final loans = await LoanService.getLoans();
      LibraryPolicy? policy;
      try {
        policy = await LibraryPolicyService.getPolicy();
      } catch (_) {
        // Vẫn hiển thị danh sách; thao tác gia hạn bị khóa đến khi tải được quy định.
      }
      if (mounted) {
        setState(() {
          _userProfile = profile;
          _borrowedBooks = loans;
          _policy = policy;
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

  Future<void> _showTicketQr(int ticketId) async {
    try {
      final response = await LibrarianQrService.issueTicket(ticketId);
      if (!mounted) return;
      showDialog(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text('QR phiếu #$ticketId'),
          content: QrImageView(
            data: response['payload']?.toString() ?? '',
            size: 230,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Đóng'),
            ),
          ],
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFf8f9ff),
      appBar: AppBar(
        title: const Text(
          'Cá nhân',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout, color: Colors.red),
            onPressed: _handleLogout,
          ),
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
                          'Lịch sử mượn (${_borrowedBooks.length})',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF0f172a),
                          ),
                        ),
                        IconButton(
                          onPressed: _fetchData,
                          icon: const Icon(Icons.refresh),
                          tooltip: 'Tải lại',
                        ),
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
    final role =
        (_userProfile?['roleId'] ??
                _userProfile?['role'] ??
                _userProfile?['userType'] ??
                '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';

    final fullName =
        _userProfile?['fullName'] ?? _userProfile?['name'] ?? 'Chưa cập nhật';
    final studentId = _userProfile?['studentId'] ?? _userProfile?['mssv'];
    final faculty =
        _userProfile?['faculty'] ??
        _userProfile?['department'] ??
        'Chưa cập nhật';

    return Container(
      margin: const EdgeInsets.all(16),
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
            color: (isAdmin ? Colors.black : const Color(0xFF0284c7))
                .withValues(alpha: 0.3),
            blurRadius: 10,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: Column(
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 35,
                backgroundColor: isAdmin
                    ? Colors.amber.shade400.withValues(alpha: 0.2)
                    : Colors.white,
                child: Icon(
                  isAdmin ? Icons.admin_panel_settings_rounded : Icons.person,
                  size: 40,
                  color: isAdmin
                      ? Colors.amber.shade300
                      : const Color(0xFF0284c7),
                ),
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
                      isAdmin
                          ? 'Email: ${_userProfile?['email'] ?? 'Chưa cập nhật'}'
                          : 'MSSV: ${studentId ?? 'Chưa cập nhật'}',
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
              ),
            ],
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 15),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isAdmin ? 'Quyền hạn' : 'Sách trong lịch sử',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 13,
                      ),
                    ),
                    Text(
                      isAdmin
                          ? 'Quản trị viên'
                          : '${_borrowedBooks.length} cuốn',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                Chip(
                  avatar: const Icon(Icons.verified_user, size: 18),
                  label: Text(
                    _userProfile?['cardStatus']?.toString() ?? 'ACTIVE',
                  ),
                  backgroundColor: Colors.white,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBorrowedBookList() {
    final role =
        (_userProfile?['roleId'] ??
                _userProfile?['role'] ??
                _userProfile?['userType'] ??
                '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';

    if (_borrowedBooks.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(20.0),
        child: Center(
          child: Text(
            isAdmin
                ? 'Tài khoản Quản trị viên chưa có sách cá nhân đang mượn.'
                : 'Bạn không có sách nào đang mượn.',
            style: const TextStyle(color: Colors.grey),
          ),
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
                dueDate: book['dueDateDisplay'] ?? '---',
                daysLeft: remainingDays,
                isWarning: remainingDays <= 3,
                status: book['status']?.toString() ?? 'PENDING',
                ticketId: (book['ticketId'] as num?)?.toInt(),
                detailId: (book['detailId'] as num?)?.toInt(),
                renewalCount: (book['renewalCount'] as num?)?.toInt() ?? 0,
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
    required String status,
    required int? ticketId,
    required int? detailId,
    required int renewalCount,
  }) {
    final canRenew =
        _policy != null &&
        status == 'BORROWING' &&
        daysLeft >= 0 &&
        ticketId != null &&
        detailId != null &&
        renewalCount < _policy!.maximumRenewalCount;
    final canCancel =
        (status == 'PENDING' || status == 'RESERVED') && ticketId != null;
    Color statusColor = status == 'OVERDUE' || daysLeft < 0
        ? Colors.red
        : const Color(0xFF0284c7);
    String statusText = switch (status) {
      'PENDING' => 'Chờ duyệt',
      'RESERVED' => 'Đang giữ tại quầy',
      'RETURNED' => 'Đã trả',
      'CANCELLED' => 'Đã hủy',
      'REJECTED' => 'Bị từ chối',
      'OVERDUE' => 'Quá hạn ${daysLeft.abs()} ngày',
      _ => 'Còn $daysLeft ngày',
    };
    final loanDays = _policy?.homeLoanDays;
    double progress = loanDays == null || loanDays <= 0
        ? 0
        : ((loanDays - daysLeft) / loanDays).clamp(0.0, 1.0);

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
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
                BookCover(
                  title: title,
                  author: author,
                  width: 65,
                  height: 90,
                  borderRadius: 6,
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
                    const Text(
                      'Ngày mượn',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    Text(
                      borrowDate,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Hạn trả',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    Text(
                      dueDate,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: statusColor,
                      ),
                    ),
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
                ),
              ],
            ),
            if (ticketId != null) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () => _showTicketQr(ticketId),
                  icon: const Icon(Icons.qr_code_2),
                  label: const Text('Hiển thị QR phiếu mượn'),
                ),
              ),
            ],
            if (canRenew || canCancel) ...[
              const SizedBox(height: 15),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () async {
                    try {
                      if (canRenew) {
                        await LoanService.renewLoan(ticketId, detailId);
                      } else {
                        await LoanService.cancelReservation(ticketId);
                      }
                      await _fetchData();
                      if (!mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            canRenew ? 'Gia hạn thành công.' : 'Đã hủy phiếu.',
                          ),
                        ),
                      );
                    } catch (error) {
                      if (!mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            error.toString().replaceFirst('Exception: ', ''),
                          ),
                        ),
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isWarning
                        ? Colors.red[50]
                        : const Color(0xFFf0f9ff),
                    foregroundColor: statusColor,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    canRenew
                        ? 'Gia hạn thêm ${_policy!.renewalDays} ngày (${renewalCount + 1}/${_policy!.maximumRenewalCount})'
                        : 'Hủy phiếu đặt giữ',
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
