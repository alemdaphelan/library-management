import 'package:flutter/material.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/change_password_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _user;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final user = await AuthService.getCurrentUser();
    if (mounted) {
      setState(() {
        _user = user;
        _isLoading = false;
      });
    }
  }

  void _handleLogout() async {
    await AuthService.logout();
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => const LoginScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (_user == null) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('Không thể tải thông tin cá nhân'),
              TextButton(
                onPressed: _handleLogout,
                child: const Text('Đăng xuất'),
              )
            ],
          ),
        ),
      );
    }

    final role = (_user!['roleId'] ?? _user!['role'] ?? _user!['userType'] ?? '').toString().toUpperCase();
    final email = (_user!['email'] ?? '').toString().toLowerCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF' || email.contains('admin');

    return Scaffold(
      appBar: AppBar(
        title: Text(isAdmin ? 'Tài khoản Quản trị' : 'Tài khoản cá nhân'),
        backgroundColor: const Color(0xFF0f172a),
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // Header Profile
            Container(
              padding: const EdgeInsets.all(24),
              color: const Color(0xFF0f172a),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 40,
                    backgroundColor: isAdmin ? Colors.amber.withOpacity(0.2) : Colors.white24,
                    child: Icon(
                      isAdmin ? Icons.admin_panel_settings_rounded : Icons.person,
                      size: 48,
                      color: isAdmin ? Colors.amber.shade300 : Colors.white,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _user!['fullName'] ?? (isAdmin ? 'Quản trị viên hệ thống' : 'Chưa cập nhật'),
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _user!['email'] ?? '',
                          style: const TextStyle(color: Colors.white70),
                        ),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: isAdmin
                                ? const Color(0xFFb45309)
                                : (_user!['cardStatus'] == 'ACTIVE' ? Colors.green : Colors.red),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            isAdmin
                                ? 'Quyền Quản trị viên (ADMIN)'
                                : (_user!['cardStatus'] == 'ACTIVE' ? 'Thẻ đang hoạt động' : 'Thẻ chưa kích hoạt'),
                            style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
                          ),
                        )
                      ],
                    ),
                  )
                ],
              ),
            ),
            const SizedBox(height: 16),
            
            // Menu Items
            if (isAdmin) ...[
              ListTile(
                leading: const Icon(Icons.dashboard_customize_rounded, color: Colors.indigo),
                title: const Text('Cổng Web Admin (Quản trị hệ thống)'),
                subtitle: const Text('Quản lý sách, kho và lưu thông tại http://localhost:4200', style: TextStyle(fontSize: 12)),
                trailing: const Icon(Icons.open_in_new, size: 18),
                onTap: () {
                  showDialog(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('Phân hệ Web Admin'),
                      content: const Text('Toàn bộ các nghiệp vụ Quản trị hệ thống, Nhập sách, Quản lý sinh viên, Duyệt mượn trả và Thống kê báo cáo được triển khai trên nền tảng Web tại địa chỉ:\n\nhttp://localhost:4200\n\nBạn có thể mở trình duyệt trên máy tính để đăng nhập với cùng tài khoản Admin này.'),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Đã hiểu')),
                      ],
                    ),
                  );
                },
              ),
              const Divider(height: 1),
            ],
            ListTile(
              leading: const Icon(Icons.lightbulb_outline, color: Colors.amber),
              title: const Text('Đề xuất sách mới'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const SuggestBookScreen()),
                );
              },
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.book, color: Colors.blue),
              title: const Text('Sách đang mượn'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const BorrowedScreen()),
                );
              },
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.lock_outline, color: Colors.orange),
              title: const Text('Đổi mật khẩu'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ChangePasswordScreen(
                      isFirstLogin: false,
                      email: _user!['email'] ?? '',
                    ),
                  ),
                );
              },
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.logout, color: Colors.red),
              title: const Text('Đăng xuất', style: TextStyle(color: Colors.red)),
              onTap: _handleLogout,
            ),
          ],
        ),
      ),
    );
  }
}
