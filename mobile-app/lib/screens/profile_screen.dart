import 'package:flutter/material.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/change_password_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/services/librarian_qr_service.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile_app/config/api_config.dart';

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
    final user = await AuthService.getCurrentUser(forceRefresh: true);
    if (mounted) {
      setState(() {
        _user = user;
        _isLoading = false;
      });
    }
  }

  Future<void> _editProfile() async {
    final nameController = TextEditingController(
      text: _user?['fullName']?.toString() ?? '',
    );
    final phoneController = TextEditingController(
      text: _user?['phone']?.toString() ?? '',
    );
    final formKey = GlobalKey<FormState>();
    final shouldSave = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Cập nhật hồ sơ'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Họ và tên',
                  prefixIcon: Icon(Icons.badge_outlined),
                ),
                validator: (value) => value == null || value.trim().isEmpty
                    ? 'Vui lòng nhập họ và tên'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: phoneController,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: 'Số điện thoại',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
                validator: (value) {
                  final phone = value?.trim() ?? '';
                  if (phone.isEmpty) return null;
                  if (!RegExp(r'^[0-9+() -]{8,20}$').hasMatch(phone)) {
                    return 'Số điện thoại không hợp lệ';
                  }
                  return null;
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () {
              if (formKey.currentState!.validate()) {
                Navigator.pop(dialogContext, true);
              }
            },
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
    if (shouldSave != true || !mounted) {
      nameController.dispose();
      phoneController.dispose();
      return;
    }

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );
    try {
      final updated = await AuthService.updateProfile(
        fullName: nameController.text,
        phone: phoneController.text,
      );
      if (!mounted) return;
      Navigator.pop(context);
      setState(() => _user = updated);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Đã cập nhật hồ sơ.')));
    } catch (error) {
      if (!mounted) return;
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    } finally {
      nameController.dispose();
      phoneController.dispose();
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

  Future<void> _showLibraryCardQr() async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );
    try {
      final response = await LibrarianQrService.issueMyCard();
      if (!mounted) return;
      Navigator.pop(context);
      final payload = response['payload']?.toString() ?? '';
      final expiresAt = DateTime.tryParse(
        response['expiresAt']?.toString() ?? '',
      );
      showDialog(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Thẻ thư viện QR'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              QrImageView(data: payload, size: 230),
              const SizedBox(height: 8),
              const Text('Đưa mã này cho thủ thư quét tại quầy.'),
              if (expiresAt != null)
                Text(
                  'Hết hạn lúc ${expiresAt.hour.toString().padLeft(2, '0')}:'
                  '${expiresAt.minute.toString().padLeft(2, '0')}',
                  style: const TextStyle(color: Colors.grey),
                ),
            ],
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
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
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
              ),
            ],
          ),
        ),
      );
    }

    final role =
        (_user!['roleId'] ?? _user!['role'] ?? _user!['userType'] ?? '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';

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
                    backgroundColor: isAdmin
                        ? Colors.amber.withValues(alpha: 0.2)
                        : Colors.white24,
                    child: Icon(
                      isAdmin
                          ? Icons.admin_panel_settings_rounded
                          : Icons.person,
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
                          _user!['fullName'] ??
                              (isAdmin
                                  ? 'Quản trị viên hệ thống'
                                  : 'Chưa cập nhật'),
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
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: isAdmin
                                ? const Color(0xFFb45309)
                                : (_user!['cardStatus'] == 'ACTIVE'
                                      ? Colors.green
                                      : Colors.red),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            isAdmin
                                ? 'Quyền Quản trị viên (ADMIN)'
                                : (_user!['cardStatus'] == 'ACTIVE'
                                      ? 'Thẻ đang hoạt động'
                                      : 'Thẻ chưa kích hoạt'),
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Card(
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.badge_outlined),
                      title: const Text('Mã số'),
                      subtitle: Text(
                        (_user!['mssv'] ??
                                _user!['studentId'] ??
                                'Chưa cập nhật')
                            .toString(),
                      ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      leading: const Icon(Icons.school_outlined),
                      title: const Text('Khoa / chuyên ngành'),
                      subtitle: Text(
                        (_user!['department'] ?? 'Chưa cập nhật').toString(),
                      ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      leading: const Icon(Icons.phone_outlined),
                      title: const Text('Số điện thoại'),
                      subtitle: Text(
                        (_user!['phone'] ?? 'Chưa cập nhật').toString(),
                      ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      leading: const Icon(
                        Icons.edit_outlined,
                        color: Color(0xFF0284c7),
                      ),
                      title: const Text('Cập nhật họ tên và số điện thoại'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: _editProfile,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Menu Items
            if (isAdmin) ...[
              ListTile(
                leading: const Icon(
                  Icons.dashboard_customize_rounded,
                  color: Colors.indigo,
                ),
                title: const Text('Cổng Web Admin (Quản trị hệ thống)'),
                subtitle: Text(
                  'Quản lý sách, kho và lưu thông tại ${ApiConfig.webPortalUrl}',
                  style: TextStyle(fontSize: 12),
                ),
                trailing: const Icon(Icons.open_in_new, size: 18),
                onTap: () {
                  showDialog(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('Phân hệ Web Admin'),
                      content: Text(
                        'Toàn bộ nghiệp vụ quản trị, kho, sinh viên, lưu thông và báo cáo được triển khai trên Web tại:\n\n${ApiConfig.webPortalUrl}\n\nBạn có thể đăng nhập bằng cùng tài khoản.',
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(ctx),
                          child: const Text('Đã hiểu'),
                        ),
                      ],
                    ),
                  );
                },
              ),
              const Divider(height: 1),
            ],
            ListTile(
              leading: const Icon(Icons.qr_code_2, color: Colors.teal),
              title: const Text('Thẻ thư viện QR'),
              subtitle: const Text(
                'Mã bảo mật có thời hạn để thủ thư xác thực',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: _showLibraryCardQr,
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.lightbulb_outline, color: Colors.amber),
              title: const Text('Đề xuất sách mới'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const SuggestBookScreen(),
                  ),
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
                  MaterialPageRoute(
                    builder: (context) => const BorrowedScreen(),
                  ),
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
                    builder: (context) =>
                        ChangePasswordScreen(isFirstLogin: false),
                  ),
                );
              },
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.logout, color: Colors.red),
              title: const Text(
                'Đăng xuất',
                style: TextStyle(color: Colors.red),
              ),
              onTap: _handleLogout,
            ),
          ],
        ),
      ),
    );
  }
}
