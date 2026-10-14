import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:mobile_app/services/auth_service.dart';

class LoanService {
  static const String _loansPrefix = 'huit_borrowed_loans_';

  static final List<Map<String, dynamic>> studentDefaultLoans = [
    {
      'title': 'Giáo trình Cấu trúc dữ liệu và Giải thuật',
      'author': 'Đỗ Xuân Lôi',
      'borrowDate': '01/10/2026',
      'dueDate': '16/10/2026',
      'remainingDays': 5,
      'progress': 0.6,
      'coverUrl': null,
      'ticketCode': 'PM-2026-1042',
    },
    {
      'title': 'Kỹ nghệ phần mềm thực hành',
      'author': 'Roger S. Pressman',
      'borrowDate': '25/09/2026',
      'dueDate': '13/10/2026',
      'remainingDays': 2,
      'progress': 0.85,
      'coverUrl': null,
      'ticketCode': 'PM-2026-0925',
    }
  ];

  static Future<String> _getStorageKey() async {
    final user = await AuthService.getCurrentUser();
    final id = user?['email'] ?? user?['mssv'] ?? 'guest';
    return '$_loansPrefix$id';
  }

  static Future<List<Map<String, dynamic>>> getLoans() async {
    final user = await AuthService.getCurrentUser();
    final role = (user?['roleId'] ?? user?['role'] ?? user?['userType'] ?? '').toString().toUpperCase();
    final email = (user?['email'] ?? '').toString().toLowerCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF' || email.contains('admin');

    final key = await _getStorageKey();
    final prefs = await SharedPreferences.getInstance();
    final savedJson = prefs.getString(key);
    List<Map<String, dynamic>> userLoans = [];
    if (savedJson != null) {
      try {
        final decoded = jsonDecode(savedJson) as List;
        userLoans = decoded.map((e) => Map<String, dynamic>.from(e)).toList();
      } catch (_) {}
    }

    // Only student accounts have curriculum study book defaults
    if (!isAdmin) {
      return [...userLoans, ...studentDefaultLoans];
    }
    // Admin has their own separate loan record (no fake student loans)
    return userLoans;
  }

  static Future<Map<String, dynamic>> addLoan(Map<String, dynamic> book) async {
    final key = await _getStorageKey();
    final prefs = await SharedPreferences.getInstance();
    final now = DateTime.now();
    final due = now.add(const Duration(days: 14));
    final ticketCode = 'PM-${now.year}-${(1000 + (book['title'].toString().hashCode.abs() % 9000))}';
    
    final newLoan = {
      'title': book['title'] ?? 'Sách tham khảo',
      'author': book['author'] ?? 'Nhiều tác giả',
      'borrowDate': '${now.day.toString().padLeft(2, '0')}/${now.month.toString().padLeft(2, '0')}/${now.year}',
      'dueDate': '${due.day.toString().padLeft(2, '0')}/${due.month.toString().padLeft(2, '0')}/${due.year}',
      'remainingDays': 14,
      'progress': 0.05,
      'coverUrl': book['imageUrlM'] ?? book['imageUrlL'] ?? book['imageUrlS'] ?? book['coverUrl'],
      'status': 'borrowed',
      'ticketCode': ticketCode,
    };

    // Save into SharedPreferences per user
    final savedJson = prefs.getString(key);
    List<Map<String, dynamic>> userLoans = [];
    if (savedJson != null) {
      try {
        final decoded = jsonDecode(savedJson) as List;
        userLoans = decoded.map((e) => Map<String, dynamic>.from(e)).toList();
      } catch (_) {}
    }
    userLoans.insert(0, newLoan);
    await prefs.setString(key, jsonEncode(userLoans));

    // Try backend call
    try {
      await ApiService.post('/loans/checkout', {
        'bookTitle': book['title'],
        'isbn': book['isbn'],
      });
    } catch (_) {}

    return newLoan;
  }
}
