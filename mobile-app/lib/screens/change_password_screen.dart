import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/screens/login_screen.dart';

class ChangePasswordScreen extends StatefulWidget {
  final bool isFirstLogin;

  const ChangePasswordScreen({super.key, required this.isFirstLogin});

  @override
  State<ChangePasswordScreen> createState() => _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends State<ChangePasswordScreen> {
  final _oldPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _isLoading = false;

  Future<void> _handleChangePassword() async {
    final currentPassword = _oldPasswordController.text;
    final newPassword = _newPasswordController.text;
    if (currentPassword.isEmpty) {
      _showMessage('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (!RegExp(
      r'^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9\s]).{8,128}$',
    ).hasMatch(newPassword)) {
      _showMessage(
        'Mật khẩu mới phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.',
      );
      return;
    }
    if (newPassword == currentPassword) {
      _showMessage('Mật khẩu mới phải khác mật khẩu hiện tại.');
      return;
    }
    if (_newPasswordController.text != _confirmPasswordController.text) {
      _showMessage('Mật khẩu xác nhận không khớp!');
      return;
    }

    setState(() => _isLoading = true);

    try {
      final token = await AuthService.getToken();
      final response = await http.post(
        Uri.parse('${AuthService.baseUrl}/change-password'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'currentPassword': currentPassword,
          'newPassword': newPassword,
        }),
      );

      if (response.statusCode == 200) {
        if (!mounted) return;
        await AuthService.clearLocalSession();
        if (!mounted) return;
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (dialogContext) => AlertDialog(
            title: const Text('Đổi mật khẩu thành công'),
            content: const Text(
              'Tất cả phiên đăng nhập cũ đã bị khóa. Vui lòng đăng nhập lại bằng mật khẩu mới.',
            ),
            actions: [
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('Đăng nhập lại'),
              ),
            ],
          ),
        );
        if (!mounted) return;
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (context) => const LoginScreen()),
          (route) => false,
        );
      } else {
        if (!mounted) return;
        _showMessage(ApiService.extractErrorMessage(response));
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Lỗi kết nối máy chủ!')));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Đổi mật khẩu'),
        // Chặn back nếu là first login
        automaticallyImplyLeading: !widget.isFirstLogin,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (widget.isFirstLogin) ...[
                const Icon(Icons.security, size: 64, color: Colors.orange),
                const SizedBox(height: 16),
                const Text(
                  'Bắt buộc đổi mật khẩu',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                const Text(
                  'Đây là lần đăng nhập đầu tiên của bạn. Vui lòng đổi mật khẩu để tiếp tục sử dụng dịch vụ.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.grey),
                ),
                const SizedBox(height: 32),
              ],
              TextField(
                controller: _oldPasswordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Mật khẩu hiện tại',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _newPasswordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Mật khẩu mới',
                  helperText:
                      'Ít nhất 8 ký tự: chữ hoa, chữ thường và ký tự đặc biệt',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _confirmPasswordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Xác nhận mật khẩu mới',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                height: 50,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _handleChangePassword,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0f172a),
                    foregroundColor: Colors.white,
                  ),
                  child: _isLoading
                      ? const CircularProgressIndicator(color: Colors.white)
                      : const Text('XÁC NHẬN ĐỔI MẬT KHẨU'),
                ),
              ),
              if (widget.isFirstLogin) ...[
                const SizedBox(height: 16),
                TextButton(
                  onPressed: () {
                    AuthService.logout();
                    Navigator.pushReplacement(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const LoginScreen(),
                      ),
                    );
                  },
                  child: const Text('Đăng xuất'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
