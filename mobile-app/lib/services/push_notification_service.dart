import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/firebase_options.dart';
import 'api_service.dart';
import 'auth_service.dart';

const String _channelId = 'huit_library_notifications';
const String _channelName = 'Thông báo Thư viện HUIT';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  if (Firebase.apps.isEmpty && DefaultFirebaseOptions.isConfigured) {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
  }
}

class PushNotificationService {
  static const String pushTokenKey = 'huit_lib_push_token';
  static const String deviceIdKey = 'huit_lib_device_id';
  static final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();
  static bool _initialized = false;

  static Future<bool> initialize() async {
    if (_initialized) return true;
    if (!DefaultFirebaseOptions.isConfigured) {
      debugPrint(
        'Firebase chưa có client config; bỏ qua FCM nhưng ứng dụng vẫn hoạt động.',
      );
      return false;
    }

    try {
      await Firebase.initializeApp(
        options: DefaultFirebaseOptions.currentPlatform,
      );
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
      await _configureLocalNotifications();
      await FirebaseMessaging.instance.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      FirebaseMessaging.onMessage.listen(_showForegroundMessage);
      FirebaseMessaging.instance.onRegistered.listen(_registerToken);
      await FirebaseMessaging.instance.register();
      _initialized = true;
      return true;
    } catch (error) {
      debugPrint('Không thể khởi tạo Firebase Messaging: $error');
      return false;
    }
  }

  static Future<void> syncToken() async {
    if (!_initialized && !await initialize()) return;
    if (await AuthService.getToken() == null) return;
    try {
      final messaging = FirebaseMessaging.instance;
      final registration = messaging.onRegistered.first.timeout(
        const Duration(seconds: 10),
      );
      await messaging.register();
      final token = await registration;
      if (token.isNotEmpty) {
        await _registerToken(token);
      }
    } catch (error) {
      debugPrint('Không thể đồng bộ FCM token: $error');
    }
  }

  static Future<void> _registerToken(String token) async {
    if (await AuthService.getToken() == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      var deviceId = prefs.getString(deviceIdKey);
      if (deviceId == null) {
        deviceId = 'mobile-${DateTime.now().microsecondsSinceEpoch}';
        await prefs.setString(deviceIdKey, deviceId);
      }
      await ApiService.post('/api/v1/notifications/devices', {
        'token': token,
        'platform': _platformName(),
        'deviceId': deviceId,
      });
      await prefs.setString(pushTokenKey, token);
    } catch (error) {
      debugPrint('Không thể đăng ký thiết bị nhận push: $error');
    }
  }

  static Future<void> _configureLocalNotifications() async {
    const initializationSettings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(),
    );
    await _localNotifications.initialize(settings: initializationSettings);

    const channel = AndroidNotificationChannel(
      _channelId,
      _channelName,
      description: 'Nhắc hạn, quá hạn, đặt giữ và trạng thái tài khoản',
      importance: Importance.high,
    );
    await _localNotifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(channel);
  }

  static Future<void> _showForegroundMessage(RemoteMessage message) async {
    final notification = message.notification;
    if (notification == null) return;
    await _localNotifications.show(
      id: message.messageId.hashCode,
      title: notification.title ?? 'Thông báo Thư viện HUIT',
      body: notification.body,
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          _channelName,
          importance: Importance.high,
          priority: Priority.high,
        ),
        iOS: DarwinNotificationDetails(),
      ),
    );
  }

  static String _platformName() {
    if (kIsWeb) return 'WEB';
    if (Platform.isIOS) return 'IOS';
    return 'ANDROID';
  }
}
