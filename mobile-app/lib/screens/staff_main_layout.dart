import 'package:flutter/material.dart';

import '../config/api_config.dart';
import '../services/auth_service.dart';
import 'change_password_screen.dart';
import 'librarian_qr_screen.dart';
import 'login_screen.dart';
import 'notifications_screen.dart';

/// A role-safe shell for librarian and administrator accounts.
///
/// It deliberately does not mount student widgets such as HomeScreen,
/// BorrowedScreen or SavedBooksScreen, so staff logins never trigger personal
/// borrowing APIs or display the student's 0/3 quota card.
class StaffMainLayout extends StatefulWidget {
  const StaffMainLayout({super.key, required this.role, required this.user});

  final String role;
  final Map<String, dynamic> user;

  @override
  State<StaffMainLayout> createState() => _StaffMainLayoutState();
}

class _StaffMainLayoutState extends State<StaffMainLayout> {
  int _currentIndex = 0;
  late final List<Widget> _screens;
  late final List<NavigationDestination> _destinations;

  bool get _isAdmin => AuthService.normalizeRoleValue(widget.role) == 'ADMIN';

  @override
  void initState() {
    super.initState();
    _screens = _isAdmin
        ? [
            _AdminStartScreen(user: widget.user),
            const LibrarianQrScreen(),
            const NotificationsScreen(),
            _StaffAccountScreen(role: widget.role, user: widget.user),
          ]
        : [
            const LibrarianQrScreen(),
            const NotificationsScreen(),
            _StaffAccountScreen(role: widget.role, user: widget.user),
          ];
    _destinations = _isAdmin
        ? const [
            NavigationDestination(
              icon: Icon(Icons.admin_panel_settings_outlined),
              selectedIcon: Icon(Icons.admin_panel_settings),
              label: 'Quản trị',
            ),
            NavigationDestination(
              icon: Icon(Icons.qr_code_scanner_outlined),
              selectedIcon: Icon(Icons.qr_code_scanner),
              label: 'Quét QR',
            ),
            NavigationDestination(
              icon: Icon(Icons.notifications_outlined),
              selectedIcon: Icon(Icons.notifications),
              label: 'Thông báo',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person),
              label: 'Tài khoản',
            ),
          ]
        : const [
            NavigationDestination(
              icon: Icon(Icons.qr_code_scanner_outlined),
              selectedIcon: Icon(Icons.qr_code_scanner),
              label: 'Quét QR',
            ),
            NavigationDestination(
              icon: Icon(Icons.notifications_outlined),
              selectedIcon: Icon(Icons.notifications),
              label: 'Thông báo',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person),
              label: 'Tài khoản',
            ),
          ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _currentIndex, children: _screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() => _currentIndex = index);
        },
        destinations: _destinations,
      ),
    );
  }
}

class _AdminStartScreen extends StatelessWidget {
  const _AdminStartScreen({required this.user});

  final Map<String, dynamic> user;

  @override
  Widget build(BuildContext context) {
    final fullName = user['fullName']?.toString().trim().isNotEmpty == true
        ? user['fullName'].toString()
        : 'Quản trị viên';
    return Scaffold(
      appBar: AppBar(title: const Text('Quản trị thư viện')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            'Xin chào, $fullName',
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Tài khoản Admin chỉ sử dụng các chức năng quản trị và nghiệp vụ '
            'tại quầy, không dùng hạn mức hoặc danh sách mượn cá nhân của sinh viên.',
          ),
          const SizedBox(height: 24),
          const _ScopeCard(
            icon: Icons.web_outlined,
            title: 'Cổng quản trị Web',
            subtitle:
                'Quản lý người dùng, kho sách, vị trí, quy định và báo cáo trên Web.',
          ),
          const SizedBox(height: 12),
          const _ScopeCard(
            icon: Icons.qr_code_scanner,
            title: 'Hỗ trợ nghiệp vụ tại quầy',
            subtitle:
                'Dùng tab Quét QR để kiểm tra phiếu mượn/trả khi cần hỗ trợ thủ thư.',
          ),
          const SizedBox(height: 24),
          SelectableText(
            'Địa chỉ Web: ${ApiConfig.webPortalUrl}',
            style: TextStyle(color: Colors.blue.shade800),
          ),
        ],
      ),
    );
  }
}

class _ScopeCard extends StatelessWidget {
  const _ScopeCard({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(child: Icon(icon)),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(subtitle),
      ),
    );
  }
}

class _StaffAccountScreen extends StatelessWidget {
  const _StaffAccountScreen({required this.role, required this.user});

  final String role;
  final Map<String, dynamic> user;

  Future<void> _logout(BuildContext context) async {
    await AuthService.logout();
    if (!context.mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final normalizedRole = AuthService.normalizeRoleValue(role);
    final roleLabel = normalizedRole == 'ADMIN' ? 'Quản trị viên' : 'Thủ thư';
    final fullName = user['fullName']?.toString().trim().isNotEmpty == true
        ? user['fullName'].toString()
        : roleLabel;
    final email = user['email']?.toString() ?? '';

    return Scaffold(
      appBar: AppBar(title: const Text('Tài khoản nhân sự')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          CircleAvatar(
            radius: 38,
            child: Icon(
              normalizedRole == 'ADMIN'
                  ? Icons.admin_panel_settings
                  : Icons.local_library,
              size: 38,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            fullName,
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
          ),
          Text(roleLabel, textAlign: TextAlign.center),
          if (email.isNotEmpty) Text(email, textAlign: TextAlign.center),
          const SizedBox(height: 24),
          ListTile(
            leading: const Icon(Icons.lock_outline),
            title: const Text('Đổi mật khẩu'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const ChangePasswordScreen(isFirstLogin: false),
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.logout, color: Colors.red),
            title: const Text('Đăng xuất', style: TextStyle(color: Colors.red)),
            onTap: () => _logout(context),
          ),
        ],
      ),
    );
  }
}
