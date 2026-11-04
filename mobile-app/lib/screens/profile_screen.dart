import 'package:flutter/material.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/notification_service.dart';
import 'package:mobile_app/services/push_notification_service.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/screens/borrowed_screen.dart';
import 'package:mobile_app/screens/change_password_screen.dart';
import 'package:mobile_app/screens/notifications_screen.dart';
import 'package:mobile_app/screens/penalties_screen.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/screens/saved_books_screen.dart';
import 'package:mobile_app/services/librarian_qr_service.dart';
import 'package:mobile_app/services/wishlist_service.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile_app/config/api_config.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _user;
  int _unreadNotificationCount = 0;
  bool _isLoading = true;
  bool _isIssuingLibraryCardQr = false;

  @override
  void initState() {
    super.initState();
    PushNotificationService.notificationRevision.addListener(
      _refreshNotificationCount,
    );
    WishlistService.wishlistRevision.addListener(
      _handleWishlistRevisionChanged,
    );
    AuthService.profileRevision.addListener(_handleProfileRevisionChanged);
    _loadUser();
  }

  @override
  void dispose() {
    PushNotificationService.notificationRevision.removeListener(
      _refreshNotificationCount,
    );
    WishlistService.wishlistRevision.removeListener(
      _handleWishlistRevisionChanged,
    );
    AuthService.profileRevision.removeListener(_handleProfileRevisionChanged);
    super.dispose();
  }

  Future<void> _handleProfileRevisionChanged() async {
    final user = await AuthService.getCachedUser();
    if (!mounted || user == null) return;
    setState(() => _user = user);
  }

  void _handleWishlistRevisionChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _refreshNotificationCount() async {
    try {
      final count = await NotificationService.getUnreadCount();
      if (!mounted) return;
      setState(() {
        _unreadNotificationCount = count;
      });
    } catch (_) {}
  }

  Future<void> _loadUser() async {
    final user = await AuthService.getCurrentUser(forceRefresh: true);
    var unreadCount = 0;
    try {
      unreadCount = await NotificationService.getUnreadCount();
    } catch (_) {}
    try {
      await WishlistService.getMine();
    } catch (_) {}
    if (mounted) {
      setState(() {
        _user = user;
        _unreadNotificationCount = unreadCount;
        _isLoading = false;
      });
    }
  }

  Future<void> _openNotifications() async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const NotificationsScreen()),
    );
    if (mounted) {
      await _refreshNotificationCount();
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
    if (_isIssuingLibraryCardQr) return;
    setState(() => _isIssuingLibraryCardQr = true);
    try {
      final response = await LibrarianQrService.issueMyCard();
      if (!mounted) return;
      final payload = response['payload']?.toString() ?? '';
      if (payload.isEmpty) {
        throw Exception('Backend không trả về dữ liệu QR thẻ thư viện.');
      }
      final expiresAt = DateTime.tryParse(
        response['expiresAt']?.toString() ?? '',
      );
      await showDialog<void>(
        context: context,
        barrierColor: Colors.transparent,
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    } finally {
      if (mounted) setState(() => _isIssuingLibraryCardQr = false);
    }
  }

  Future<void> _showEditProfileSheet() async {
    final currentUser = _user;
    if (currentUser == null) return;

    final formKey = GlobalKey<FormState>();
    final nameController = TextEditingController(
      text: currentUser['fullName']?.toString() ?? '',
    );
    final phoneController = TextEditingController(
      text: currentUser['phone']?.toString() ?? '',
    );
    var isSaving = false;

    try {
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (sheetContext) => StatefulBuilder(
          builder: (context, setSheetState) => Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              20,
              20,
              MediaQuery.viewInsetsOf(context).bottom + 20,
            ),
            child: SingleChildScrollView(
              child: Form(
                key: formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        const Expanded(
                          child: Text(
                            'Cập nhật thông tin cá nhân',
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        IconButton(
                          onPressed: isSaving
                              ? null
                              : () => Navigator.pop(sheetContext),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Bạn chỉ có thể thay đổi họ tên và số điện thoại. '
                      'MSSV, email và vai trò do nhà trường quản lý.',
                      style: TextStyle(color: Colors.black54),
                    ),
                    const SizedBox(height: 20),
                    TextFormField(
                      key: const ValueKey('profile-full-name-field'),
                      controller: nameController,
                      enabled: !isSaving,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Họ và tên',
                        prefixIcon: Icon(Icons.person_outline),
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        final name = value?.trim() ?? '';
                        if (name.isEmpty) return 'Vui lòng nhập họ và tên';
                        if (name.length > 150) {
                          return 'Họ tên không được vượt quá 150 ký tự';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 14),
                    TextFormField(
                      key: const ValueKey('profile-phone-field'),
                      controller: phoneController,
                      enabled: !isSaving,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Số điện thoại',
                        hintText: 'Ví dụ: 0912345678',
                        prefixIcon: Icon(Icons.phone_outlined),
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) =>
                          AuthService.isValidPhone(value ?? '')
                          ? null
                          : 'Nhập 10 chữ số bắt đầu bằng 0 hoặc mã +84',
                    ),
                    const SizedBox(height: 14),
                    _ReadOnlyProfileField(
                      label: 'Mã số sinh viên',
                      value:
                          (currentUser['mssv'] ??
                                  currentUser['studentId'] ??
                                  'Chưa cập nhật')
                              .toString(),
                      icon: Icons.badge_outlined,
                    ),
                    const SizedBox(height: 10),
                    _ReadOnlyProfileField(
                      label: 'Email',
                      value: (currentUser['email'] ?? 'Chưa cập nhật')
                          .toString(),
                      icon: Icons.email_outlined,
                    ),
                    const SizedBox(height: 10),
                    _ReadOnlyProfileField(
                      label: 'Vai trò',
                      value:
                          (currentUser['roleId'] ??
                                  currentUser['role'] ??
                                  'STUDENT')
                              .toString(),
                      icon: Icons.verified_user_outlined,
                    ),
                    const SizedBox(height: 20),
                    FilledButton.icon(
                      key: const ValueKey('profile-save-button'),
                      onPressed: isSaving
                          ? null
                          : () async {
                              if (!(formKey.currentState?.validate() ??
                                  false)) {
                                return;
                              }
                              setSheetState(() => isSaving = true);
                              try {
                                final updated = await AuthService.updateProfile(
                                  fullName: nameController.text,
                                  phone: phoneController.text,
                                );
                                if (!mounted) return;
                                setState(() => _user = updated);
                                if (sheetContext.mounted) {
                                  Navigator.pop(sheetContext);
                                }
                                ScaffoldMessenger.of(this.context)
                                  ..hideCurrentSnackBar()
                                  ..showSnackBar(
                                    const SnackBar(
                                      content: Text(
                                        'Cập nhật thông tin cá nhân thành công.',
                                      ),
                                    ),
                                  );
                              } catch (error) {
                                if (!sheetContext.mounted) return;
                                setSheetState(() => isSaving = false);
                                ScaffoldMessenger.of(this.context)
                                  ..hideCurrentSnackBar()
                                  ..showSnackBar(
                                    SnackBar(
                                      content: Text(
                                        error.toString().replaceFirst(
                                          'Exception: ',
                                          '',
                                        ),
                                      ),
                                    ),
                                  );
                              }
                            },
                      icon: isSaving
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.save_outlined),
                      label: Text(isSaving ? 'Đang lưu...' : 'Lưu thay đổi'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    } finally {
      nameController.dispose();
      phoneController.dispose();
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
        actions: [
          Stack(
            alignment: Alignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_outlined),
                onPressed: _openNotifications,
              ),
              if (_unreadNotificationCount > 0)
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
                      _unreadNotificationCount > 9
                          ? '9+'
                          : '$_unreadNotificationCount',
                      style: const TextStyle(color: Colors.white, fontSize: 10),
                    ),
                  ),
                ),
            ],
          ),
        ],
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
                      title: const Text('Mã số sinh viên'),
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
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            if (!isAdmin) ...[
              ListTile(
                key: const ValueKey('profile-edit-tile'),
                leading: const Icon(
                  Icons.edit_outlined,
                  color: Color(0xFF0284c7),
                ),
                title: const Text('Cập nhật thông tin cá nhân'),
                subtitle: const Text('Thay đổi họ tên và số điện thoại'),
                trailing: const Icon(Icons.chevron_right),
                onTap: _showEditProfileSheet,
              ),
              const Divider(height: 1),
            ],

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
                  style: const TextStyle(fontSize: 12),
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
              leading: const Icon(
                Icons.notifications_active_outlined,
                color: Color(0xFF0284c7),
              ),
              title: const Text('Thông báo'),
              subtitle: Text(
                _unreadNotificationCount > 0
                    ? 'Bạn có $_unreadNotificationCount thông báo chưa đọc'
                    : 'Xem lịch sử thông báo thư viện',
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_unreadNotificationCount > 0)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        '$_unreadNotificationCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  const SizedBox(width: 4),
                  const Icon(Icons.chevron_right),
                ],
              ),
              onTap: _openNotifications,
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.qr_code_2, color: Colors.teal),
              title: const Text('Thẻ thư viện QR'),
              subtitle: const Text(
                'Mã bảo mật có thời hạn để thủ thư xác thực',
              ),
              trailing: _isIssuingLibraryCardQr
                  ? const SizedBox.square(
                      dimension: 22,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.chevron_right),
              onTap: _isIssuingLibraryCardQr ? null : _showLibraryCardQr,
            ),
            const Divider(height: 1),
            ListTile(
              key: const ValueKey('profile-saved-books-tile'),
              leading: const Icon(Icons.bookmark, color: Color(0xFF0284c7)),
              title: const Text('Sách đã lưu'),
              subtitle: Text(
                WishlistService.savedCount > 0
                    ? 'Bạn đã lưu ${WishlistService.savedCount} cuốn sách'
                    : 'Danh sách quan tâm của bạn',
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (WishlistService.savedCount > 0)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0284c7),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        '${WishlistService.savedCount}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  const SizedBox(width: 4),
                  const Icon(Icons.chevron_right),
                ],
              ),
              onTap: () async {
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const SavedBooksScreen(),
                  ),
                );
                if (mounted) {
                  setState(() {});
                }
              },
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
              leading: const Icon(
                Icons.receipt_long_outlined,
                color: Colors.deepOrange,
              ),
              title: const Text('Khoản phạt & Công nợ'),
              subtitle: const Text(
                'Xem tiền thế chân, phạt quá hạn, mất/hỏng và lịch sử thu',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const PenaltiesScreen(),
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
                        const ChangePasswordScreen(isFirstLogin: false),
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

class _ReadOnlyProfileField extends StatelessWidget {
  const _ReadOnlyProfileField({
    required this.label,
    required this.value,
    required this.icon,
  });

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return InputDecorator(
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        suffixIcon: const Icon(Icons.lock_outline, size: 18),
        border: const OutlineInputBorder(),
        filled: true,
        fillColor: Colors.grey.shade100,
      ),
      child: Text(value),
    );
  }
}
