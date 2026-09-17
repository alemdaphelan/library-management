import 'package:flutter/material.dart';
import 'package:mobile_app/screens/login_screen.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _navigateToLogin();
  }

  void _navigateToLogin() async {
    // Chờ 2 giây
    await Future.delayed(const Duration(seconds: 2));
    if (!mounted) return;
    
    // Chuyển sang màn hình Đăng nhập (thay thế hoàn toàn Splash)
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => const LoginScreen()),
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
            // Logo (Sử dụng Icon lớn hoặc Asset image mô phỏng logo trường)
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFFf8f9ff),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFF0284c7), width: 2),
              ),
              child: const Icon(
                Icons.school, // Mock logo HUIT
                size: 80,
                color: Color(0xFF0284c7),
              ),
            ),
            const SizedBox(height: 24),
            
            // Tên ứng dụng
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
            
            // Loading indicator (tùy chọn, để màn hình không bị tĩnh)
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
