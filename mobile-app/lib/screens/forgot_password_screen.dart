import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/auth_service.dart';

class ForgotPasswordScreen extends StatefulWidget {
  final int initialStep;

  const ForgotPasswordScreen({super.key, this.initialStep = 0})
    : assert(initialStep >= 0 && initialStep <= 2);

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _identifierController = TextEditingController();
  final _otpController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  late int _step;
  bool _isLoading = false;
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    _step = widget.initialStep;
  }

  @override
  void dispose() {
    _identifierController.dispose();
    _otpController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<http.Response> _post(String endpoint, Map<String, dynamic> body) {
    return http.post(
      Uri.parse('${AuthService.baseUrl}/$endpoint'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
  }

  Future<void> _sendOtp() async {
    final identifier = _identifierController.text.trim();
    if (identifier.isEmpty) {
      _showMessage('Vui lòng nhập email hoặc mã số sinh viên.');
      return;
    }
    await _runRequest(() async {
      final response = await _post('forgot-password', {'email': identifier});
      _ensureSuccess(response);
      if (!mounted) return;
      setState(() => _step = 1);
      _showMessage('Mã OTP đã được gửi đến email đã đăng ký.');
    });
  }

  Future<void> _verifyOtp() async {
    final otp = _otpController.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(otp)) {
      _showMessage('OTP phải gồm đúng 6 chữ số.');
      return;
    }
    await _runRequest(() async {
      final response = await _post('verify-otp', {
        'email': _identifierController.text.trim(),
        'otp': otp,
      });
      _ensureSuccess(response);
      if (!mounted) return;
      setState(() => _step = 2);
    });
  }

  Future<void> _resetPassword() async {
    final password = _newPasswordController.text;
    if (!RegExp(
      r'^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9\s]).{8,128}$',
    ).hasMatch(password)) {
      _showMessage(
        'Mật khẩu mới phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.',
      );
      return;
    }
    if (password != _confirmPasswordController.text) {
      _showMessage('Mật khẩu xác nhận không khớp.');
      return;
    }
    await _runRequest(() async {
      final response = await _post('reset-password', {
        'email': _identifierController.text.trim(),
        'otp': _otpController.text.trim(),
        'newPassword': password,
      });
      _ensureSuccess(response);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Khôi phục mật khẩu thành công'),
          content: const Text(
            'Tất cả phiên đăng nhập cũ đã bị khóa. Bạn có thể đăng nhập bằng mật khẩu mới.',
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Về đăng nhập'),
            ),
          ],
        ),
      );
      if (mounted) Navigator.pop(context);
    });
  }

  Future<void> _runRequest(Future<void> Function() request) async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      await request();
    } on ApiException catch (error) {
      _showMessage(error.message);
    } catch (_) {
      _showMessage('Không thể kết nối máy chủ. Vui lòng thử lại.');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _ensureSuccess(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        response.statusCode,
        ApiService.extractErrorMessage(response),
      );
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
      appBar: AppBar(title: const Text('Quên mật khẩu')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Icon(Icons.lock_reset, size: 72, color: Color(0xFF0284C7)),
              const SizedBox(height: 16),
              Text(
                switch (_step) {
                  0 => 'Nhận mã xác nhận',
                  1 => 'Xác thực OTP',
                  _ => 'Tạo mật khẩu mới',
                },
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                switch (_step) {
                  0 =>
                    'Nhập email hoặc mã số sinh viên. OTP sẽ được gửi đến email đã đăng ký.',
                  1 =>
                    'Nhập mã OTP gồm 6 chữ số trong email. Mã có hiệu lực trong 10 phút.',
                  _ =>
                    'Mật khẩu mới phải có chữ hoa, chữ thường và ký tự đặc biệt.',
                },
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey),
              ),
              const SizedBox(height: 28),
              if (_step == 0) ...[
                TextField(
                  controller: _identifierController,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.username],
                  decoration: const InputDecoration(
                    labelText: 'Email hoặc mã số sinh viên',
                    prefixIcon: Icon(Icons.person_outline),
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 20),
                _actionButton('GỬI MÃ OTP', _sendOtp),
              ] else if (_step == 1) ...[
                TextField(
                  key: const Key('forgot-password-otp-field'),
                  controller: _otpController,
                  keyboardType: TextInputType.number,
                  textAlign: TextAlign.center,
                  maxLength: 6,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(6),
                  ],
                  autofillHints: const [AutofillHints.oneTimeCode],
                  decoration: const InputDecoration(
                    labelText: 'Mã OTP',
                    hintText: 'Nhập 6 chữ số',
                    counterText: '',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 20),
                _actionButton('XÁC THỰC OTP', _verifyOtp),
                TextButton(
                  onPressed: _isLoading ? null : _sendOtp,
                  child: const Text('Gửi lại mã OTP'),
                ),
                TextButton(
                  onPressed: _isLoading
                      ? null
                      : () => setState(() => _step = 0),
                  child: const Text('Đổi email/MSSV'),
                ),
              ] else ...[
                TextField(
                  controller: _newPasswordController,
                  obscureText: _obscurePassword,
                  decoration: InputDecoration(
                    labelText: 'Mật khẩu mới',
                    prefixIcon: const Icon(Icons.lock_outline),
                    suffixIcon: IconButton(
                      onPressed: () =>
                          setState(() => _obscurePassword = !_obscurePassword),
                      icon: Icon(
                        _obscurePassword
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                    ),
                    border: const OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _confirmPasswordController,
                  obscureText: _obscurePassword,
                  decoration: const InputDecoration(
                    labelText: 'Xác nhận mật khẩu mới',
                    prefixIcon: Icon(Icons.lock_outline),
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 20),
                _actionButton('ĐẶT LẠI MẬT KHẨU', _resetPassword),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _actionButton(String label, Future<void> Function() action) {
    return SizedBox(
      height: 50,
      child: FilledButton(
        onPressed: _isLoading ? null : action,
        child: _isLoading
            ? const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Text(label),
      ),
    );
  }
}
