import 'package:flutter/material.dart';
import 'dart:async';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/screens/main_layout.dart';
import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/push_notification_service.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _openInitialScreen();
  }

  void _openInitialScreen() async {
    await Future.delayed(const Duration(milliseconds: 900));
    if (!mounted) return;
    final token = await AuthService.getToken();
    if (token != null && token.isNotEmpty) {
      unawaited(PushNotificationService.syncToken());
    }
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (context) => token == null || token.isEmpty
            ? const LoginScreen()
            : const MainLayout(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFFf8f9ff),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFF0284c7), width: 2),
              ),
              child: const Icon(
                Icons.school,
                size: 80,
                color: Color(0xFF0284c7),
              ),
            ),
            const SizedBox(height: 24),

            const Text(
              'HUIT LIBRARY',
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w900,
                color: Color(0xFF0f172a),
                letterSpacing: 2.0,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Tri thức kiến tạo tương lai',
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey,
                letterSpacing: 0.5,
              ),
            ),
            const SizedBox(height: 50),

            const SizedBox(
              width: 30,
              height: 30,
              child: CircularProgressIndicator(
                strokeWidth: 3,
                color: Color(0xFF0284c7),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
