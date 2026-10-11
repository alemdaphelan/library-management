import 'package:flutter/material.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thông báo', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
      ),
      backgroundColor: const Color(0xFFf8f9ff),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildNotificationItem(
            icon: Icons.warning_rounded,
            iconColor: Colors.red,
            title: 'Sách quá hạn!',
            message: 'Cuốn "Clean Architecture" đã quá hạn 5 ngày. Vui lòng trả sách hoặc đóng phạt tại quầy.',
            time: '2 giờ trước',
            isUnread: true,
          ),
          const SizedBox(height: 12),
          _buildNotificationItem(
            icon: Icons.access_time_filled,
            iconColor: Colors.orange,
            title: 'Sắp đến hạn trả sách',
            message: 'Cuốn "Flutter in Action" còn 2 ngày nữa là đến hạn trả. Đừng quên nhé!',
            time: '1 ngày trước',
            isUnread: false,
          ),
          const SizedBox(height: 12),
          _buildNotificationItem(
            icon: Icons.check_circle,
            iconColor: Colors.green,
            title: 'Đề xuất sách được duyệt',
            message: 'Đề xuất sách "Domain-Driven Design" của bạn đã được duyệt và đang trong quá trình đặt mua.',
            time: '3 ngày trước',
            isUnread: false,
          ),
        ],
      ),
    );
  }

  Widget _buildNotificationItem({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String message,
    required String time,
    required bool isUnread,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isUnread ? Colors.white : Colors.grey.shade50,
        borderRadius: BorderRadius.circular(12),
        border: isUnread ? Border.all(color: Colors.blue.shade100, width: 1.5) : Border.all(color: Colors.grey.shade200),
        boxShadow: [
          if (isUnread)
            BoxShadow(color: Colors.blue.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, 5))
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            backgroundColor: iconColor.withOpacity(0.1),
            child: Icon(icon, color: iconColor),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        title,
                        style: TextStyle(
                          fontWeight: isUnread ? FontWeight.bold : FontWeight.w600,
                          fontSize: 16,
                          color: Colors.black87,
                        ),
                      ),
                    ),
                    if (isUnread)
                      Container(
                        width: 8, height: 8,
                        decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                      )
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  message,
                  style: TextStyle(
                    color: isUnread ? Colors.black87 : Colors.grey.shade600,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  time,
                  style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
                ),
              ],
            ),
          )
        ],
      ),
    );
  }
}
