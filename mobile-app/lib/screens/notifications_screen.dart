import 'package:flutter/material.dart';

import '../services/notification_service.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  List<Map<String, dynamic>> _items = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await NotificationService.getMyNotifications();
      if (!mounted) return;
      setState(() => _items = items);
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _markRead(Map<String, dynamic> item) async {
    if (item['isRead'] == true || item['notifId'] == null) return;
    try {
      await NotificationService.markAsRead(item['notifId'].toString());
      if (!mounted) return;
      setState(() => item['isRead'] = true);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Thông báo',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, textAlign: TextAlign.center))
          : _items.isEmpty
          ? const Center(child: Text('Bạn chưa có thông báo nào.'))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: _items.length,
                separatorBuilder: (_, index) => const SizedBox(height: 12),
                itemBuilder: (_, index) => _buildItem(_items[index]),
              ),
            ),
    );
  }

  Widget _buildItem(Map<String, dynamic> item) {
    final unread = item['isRead'] != true;
    final type = item['type']?.toString().toUpperCase() ?? '';
    final (icon, color) = switch (type) {
      'OVERDUE' => (Icons.warning_rounded, Colors.red),
      'DUE_SOON' => (Icons.access_time_filled, Colors.orange),
      'RESERVATION_EXPIRED' => (Icons.event_busy, Colors.deepOrange),
      _ => (Icons.notifications, Colors.blue),
    };
    final sentAt = DateTime.tryParse(item['sentAt']?.toString() ?? '');

    return InkWell(
      onTap: () => _markRead(item),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: unread ? Colors.white : Colors.grey.shade50,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: unread ? Colors.blue.shade100 : Colors.grey.shade200,
            width: unread ? 1.5 : 1,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              backgroundColor: color.withValues(alpha: 0.1),
              child: Icon(icon, color: color),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _titleFor(type),
                          style: TextStyle(
                            fontWeight: unread
                                ? FontWeight.bold
                                : FontWeight.w600,
                          ),
                        ),
                      ),
                      if (unread)
                        const Icon(Icons.circle, color: Colors.red, size: 9),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(item['content']?.toString() ?? ''),
                  if (sentAt != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      '${sentAt.day.toString().padLeft(2, '0')}/'
                      '${sentAt.month.toString().padLeft(2, '0')}/${sentAt.year} '
                      '${sentAt.hour.toString().padLeft(2, '0')}:'
                      '${sentAt.minute.toString().padLeft(2, '0')}',
                      style: TextStyle(
                        color: Colors.grey.shade600,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _titleFor(String type) => switch (type) {
    'OVERDUE' => 'Sách quá hạn',
    'DUE_SOON' => 'Sắp đến hạn trả',
    'RESERVATION_EXPIRED' => 'Phiếu đặt giữ đã hết hạn',
    _ => 'Thông báo thư viện',
  };
}
