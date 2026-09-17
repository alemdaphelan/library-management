import 'package:flutter/material.dart';
import 'package:mobile_app/screens/splash_screen.dart';

void main() {
  runApp(const HuitLibraryApp());
}

class HuitLibraryApp extends StatelessWidget {
  const HuitLibraryApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HUIT Library',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0284c7), // Primary Academic Blue
          primary: const Color(0xFF0284c7),
          secondary: const Color(0xFF0f172a), // Navy Dark
          background: const Color(0xFFf8f9ff), // Light Tone
          surface: Colors.white,
        ),
        scaffoldBackgroundColor: const Color(0xFFf8f9ff),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFF0f172a),
          elevation: 0,
          centerTitle: true,
        ),
        fontFamily: 'Roboto', // Default standard font
      ),
      home: const SplashScreen(),
    );
  }
}
