import 'package:flutter/material.dart';

import '../services/notification_service.dart';
import '../services/push_notification_service.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen>
    with WidgetsBindingObserver {
  List<Map<String, dynamic>> _items = [];
  bool _loading = true;
  bool _markingAll = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    PushNotificationService.notificationRevision.addListener(
      _handlePushNotification,
    );
    _load();
  }

  @override
  void dispose() {
    PushNotificationService.notificationRevision.removeListener(
      _handlePushNotification,
    );
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _load(showLoading: false);
    }
  }

  void _handlePushNotification() {
    _load(showLoading: false);
  }

  int get _unreadCount => _items.where((item) => item['isRead'] != true).length;

  Future<void> _load({bool showLoading = true}) async {
    if (showLoading && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final items = await NotificationService.getMyNotifications();
      if (!mounted) return;
      setState(() {
        _items = items;
        _error = null;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted && showLoading) setState(() => _loading = false);
    }
  }

  Future<void> _handleTapItem(Map<String, dynamic> item) async {
    if (item['isRead'] != true && item['notifId'] != null) {
      try {
        await NotificationService.markAsRead(item['notifId'].toString());
        if (mounted) {
          setState(() => item['isRead'] = true);
        }
      } catch (error) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.toString().replaceFirst('Exception: ', '')),
          ),
        );
      }
    }
    if (!mounted) return;
    await NotificationService.navigateToNotificationTarget(
      context,
      item,
      fromNotificationsScreen: true,
    );
  }

  Future<void> _markAllAsRead() async {
    if (_markingAll || _unreadCount == 0) return;
    setState(() => _markingAll = true);
    try {
      await NotificationService.markAllAsRead();
      if (!mounted) return;
      setState(() {
        for (final item in _items) {
          item['isRead'] = true;
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã đánh dấu tất cả thông báo là đã đọc.'),
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
      if (mounted) {
        setState(() => _markingAll = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final unreadCount = _unreadCount;
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Thông báo',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          if (unreadCount > 0)
            TextButton.icon(
              onPressed: _markingAll ? null : _markAllAsRead,
              icon: _markingAll
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.done_all, size: 18),
              label: const Text('Đọc tất cả'),
            ),
          IconButton(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            tooltip: 'Tải lại',
          ),
        ],
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      size: 48,
                      color: Colors.redAccent,
                    ),
                    const SizedBox(height: 12),
                    Text(_error!, textAlign: TextAlign.center),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: _load,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            )
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
    final target = NotificationService.resolveNavigationTarget(item);
    final hasDeepLink =
        target.destination != NotificationDestination.notifications;
    final (icon, color) = switch (type) {
      'OVERDUE' || 'LOAN_OVERDUE' => (Icons.warning_rounded, Colors.red),
      'DUE_SOON' ||
      'LOAN_DUE_SOON' ||
      'DUE_TODAY' ||
      'LOAN_DUE_TODAY' => (Icons.access_time_filled, Colors.orange),
      'WAITLIST_AVAILABLE' => (Icons.bookmark_added, Colors.teal),
      'RESERVATION_EXPIRED' ||
      'RESERVATION_NO_SHOW' => (Icons.event_busy, Colors.deepOrange),
      'LOAN_APPROVED' ||
      'PROPOSAL_APPROVED' => (Icons.check_circle, Colors.green),
      'LOAN_REJECTED' || 'PROPOSAL_REJECTED' => (Icons.cancel, Colors.red),
      'PENALTY' ||
      'FINE' ||
      'PENALTY_CASE_CREATED' ||
      'PENALTY_CASE_DUE' ||
      'WAIVER_DECISION' => (Icons.receipt_long, Colors.deepOrange),
      'CIRCULATION_LOCKED' => (Icons.lock, Colors.red),
      'CIRCULATION_UNLOCKED' => (Icons.lock_open, Colors.green),
      _ => (Icons.notifications, Colors.blue),
    };
    final sentAt = DateTime.tryParse(item['sentAt']?.toString() ?? '');

    return InkWell(
      onTap: () => _handleTapItem(item),
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
                      if (hasDeepLink) ...[
                        const SizedBox(width: 6),
                        Icon(
                          Icons.chevron_right,
                          size: 18,
                          color: Colors.grey.shade500,
                        ),
                      ],
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
    'OVERDUE' || 'LOAN_OVERDUE' => 'Sách quá hạn',
    'DUE_TODAY' || 'LOAN_DUE_TODAY' => 'Sách đến hạn trả hôm nay',
    'DUE_SOON' || 'LOAN_DUE_SOON' => 'Sắp đến hạn trả',
    'WAITLIST_AVAILABLE' => 'Sách đặt trước đã sẵn sàng nhận',
    'RESERVATION_EXPIRED' ||
    'RESERVATION_NO_SHOW' => 'Phiếu đặt giữ đã hết hạn',
    'LOAN_APPROVED' => 'Yêu cầu mượn đã được duyệt',
    'LOAN_REJECTED' => 'Yêu cầu mượn bị từ chối',
    'PROPOSAL_APPROVED' => 'Đề xuất sách đã được duyệt',
    'PROPOSAL_REJECTED' => 'Đề xuất sách bị từ chối',
    'PENALTY' || 'FINE' => 'Phát sinh tiền phạt thư viện',
    'PENALTY_CASE_CREATED' => 'Hồ sơ phạt mới',
    'PENALTY_CASE_DUE' => 'Nhắc hạn thanh toán khoản phạt',
    'WAIVER_DECISION' => 'Kết quả xét miễn giảm phạt',
    'CIRCULATION_LOCKED' => 'Quyền mượn đã bị khóa',
    'CIRCULATION_UNLOCKED' => 'Quyền mượn đã được mở lại',
    _ => 'Thông báo thư viện',
  };
}
