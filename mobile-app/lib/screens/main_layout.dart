import 'package:flutter/material.dart';
import 'package:mobile_app/screens/home_screen.dart';
import 'package:mobile_app/screens/search_screen.dart';
import 'package:mobile_app/screens/profile_screen.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/screens/staff_main_layout.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/push_notification_service.dart';

class MainLayout extends StatefulWidget {
  const MainLayout({super.key});

  @override
  State<MainLayout> createState() => _MainLayoutState();
}

class _MainLayoutState extends State<MainLayout> {
  int _currentIndex = 0;
  bool _loadingRole = true;
  String _role = '';
  Map<String, dynamic> _user = const {};

  final List<Widget> _studentScreens = [
    const HomeScreen(),
    const SearchScreen(),
    const ProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    _resolveRole();
  }

  Future<void> _resolveRole() async {
    final user =
        await AuthService.getCurrentUser(forceRefresh: true) ??
        await AuthService.getCachedUser();
    final role = AuthService.roleFromProfile(user);
    if (!mounted) return;
    setState(() {
      _user = user ?? const {};
      _role = role;
      _loadingRole = false;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      PushNotificationService.consumePendingNavigation(context);
    });
  }

  Future<void> _returnToLogin() async {
    await AuthService.clearLocalSession();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loadingRole) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (AuthService.isStaffRole(_role)) {
      return StaffMainLayout(role: _role, user: _user);
    }

    if (!AuthService.isStudentRole(_role)) {
      return Scaffold(
        appBar: AppBar(title: const Text('Không xác định quyền truy cập')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.gpp_bad_outlined, size: 56, color: Colors.red),
                const SizedBox(height: 16),
                const Text(
                  'Tài khoản chưa có vai trò hợp lệ để sử dụng ứng dụng.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _returnToLogin,
                  child: const Text('Về trang đăng nhập'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      body: IndexedStack(index: _currentIndex, children: _studentScreens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Trang chủ',
          ),
          NavigationDestination(
            icon: Icon(Icons.search_outlined),
            selectedIcon: Icon(Icons.search),
            label: 'Tra cứu',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Cá nhân',
          ),
        ],
      ),
    );
  }
}
