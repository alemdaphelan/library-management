import 'api_service.dart';

class NotificationService {
  static Future<List<Map<String, dynamic>>> getMyNotifications() async {
    final data = await ApiService.get('/api/v1/notifications');
    return (data as List<dynamic>)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  static Future<void> markAsRead(String notificationId) async {
    await ApiService.patch('/api/v1/notifications/$notificationId/read');
  }
}
