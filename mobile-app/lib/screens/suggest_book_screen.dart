import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:mobile_app/services/auth_service.dart';

class SuggestBookScreen extends StatefulWidget {
  const SuggestBookScreen({super.key});

  @override
  State<SuggestBookScreen> createState() => _SuggestBookScreenState();
}

class _SuggestBookScreenState extends State<SuggestBookScreen> {
  final _titleController = TextEditingController();
  final _authorController = TextEditingController();
  final _reasonController = TextEditingController();
  bool _isSubmitting = false;

  void _submit() async {
    final title = _titleController.text.trim();
    final reason = _reasonController.text.trim();
    final author = _authorController.text.trim();

    if (title.isEmpty || reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập tên sách và lý do!')),
      );
      return;
    }

    setState(() => _isSubmitting = true);
    
    try {
      final token = await AuthService.getToken();
      final user = await AuthService.getCurrentUser();
      
      final headers = {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

      final body = jsonEncode({
        'title': title,
        'authorName': author.isNotEmpty ? author : 'Chưa rõ tác giả',
        'reason': reason,
        'userId': user?['userId'] ?? user?['id'],
      });

      final response = await http.post(
        Uri.parse('http://10.0.2.2:8080/feedback/proposals'),
        headers: headers,
        body: body,
      );

      if (!mounted) return;

      if (response.statusCode == 200 || response.statusCode == 201) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã gửi đề xuất thành công! Dữ liệu đã lưu vào hệ thống.')),
        );
        Navigator.pop(context);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể gửi đề xuất (${response.statusCode})')),
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lỗi kết nối máy chủ: $e')),
      );
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Đề xuất sách mới', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black,
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Góp phần làm phong phú Thư viện',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              'Nếu bạn không tìm thấy sách mình cần, hãy gửi yêu cầu để Thư viện bổ sung vào đợt nhập sách tới.',
              style: TextStyle(color: Colors.grey, height: 1.5),
            ),
            const SizedBox(height: 30),
            TextField(
              controller: _titleController,
              decoration: InputDecoration(
                labelText: 'Tên sách (Bắt buộc)',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _authorController,
              decoration: InputDecoration(
                labelText: 'Tác giả',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _reasonController,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: 'Lý do đề xuất (Phục vụ môn học, nghiên cứu...)',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            const SizedBox(height: 30),
            SizedBox(
              height: 55,
              child: ElevatedButton(
                onPressed: _isSubmitting ? null : _submit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0284c7),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: _isSubmitting 
                    ? const CircularProgressIndicator(color: Colors.white)
                    : const Text('GỬI ĐỀ XUẤT', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ),
            )
          ],
        ),
      ),
    );
  }
}
