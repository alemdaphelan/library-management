import 'dart:convert';
import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/firebase_options.dart';
import 'api_service.dart';
import 'auth_service.dart';
import 'notification_service.dart';

const String _channelId = 'huit_library_notifications';
const String _channelName = 'Thông báo Thư viện HUIT';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  if (Firebase.apps.isEmpty && DefaultFirebaseOptions.canInitialize) {
    await _initializeFirebaseApp();
  }
}

Future<void> _initializeFirebaseApp() async {
  if (DefaultFirebaseOptions.isConfigured) {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
    return;
  }
  // On Android the Google Services Gradle plugin exposes the values from
  // google-services.json to Firebase.initializeApp().
  await Firebase.initializeApp();
}

class PushNotificationService {
  static const String pushTokenKey = 'huit_lib_push_token';
  static const String deviceIdKey = 'huit_lib_device_id';
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();
  static final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();
  static bool _initialized = false;
  static bool _listenersAttached = false;
  static Future<bool>? _initialization;
  static const String pushIdentifierTypeKey = 'huit_lib_push_identifier_type';
  static String? _lastKnownToken;
  static Map<String, dynamic>? _pendingNavigationPayload;
  static final ValueNotifier<int> notificationRevision = ValueNotifier<int>(0);

  static String? get lastKnownToken => _lastKnownToken;

  static Future<bool> initialize() {
    if (_initialized) return Future.value(true);
    return _initialization ??= _initialize().whenComplete(() { _initialization = null; });
  }

  static Future<bool> _initialize() async {
    if (_initialized) return true;
    if (!DefaultFirebaseOptions.canInitialize) {
      debugPrint(
        'Firebase chưa có client config; bỏ qua FCM nhưng ứng dụng vẫn hoạt động.',
      );
      return false;
    }

    try {
      await _initializeFirebaseApp();
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
      await _configureLocalNotifications();
      await FirebaseMessaging.instance.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      if (!_listenersAttached) {
        FirebaseMessaging.onMessage.listen(_showForegroundMessage);
        FirebaseMessaging.onMessageOpenedApp.listen(_handleRemoteMessageOpenedApp);
        FirebaseMessaging.instance.onRegistered.listen(_onTokenReceived);
        _listenersAttached = true;
      }

      final initialMessage = await FirebaseMessaging.instance
          .getInitialMessage();
      if (initialMessage != null) {
        _pendingNavigationPayload = _extractNavigationPayload(initialMessage);
      }

      await FirebaseMessaging.instance.register().timeout(const Duration(seconds: 10));
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
      final prefs = await SharedPreferences.getInstance();
      final cachedToken = _lastKnownToken ?? prefs.getString(pushTokenKey);
      if (prefs.getString(pushIdentifierTypeKey) == 'FID' &&
          cachedToken != null && cachedToken.trim().isNotEmpty) {
        await _registerTokenWithBackend(cachedToken.trim());
      }
      final messaging = FirebaseMessaging.instance;
      final registration = messaging.onRegistered.first.timeout(
        const Duration(seconds: 6),
      );
      final results = await Future.wait<dynamic>([
        messaging.register().timeout(const Duration(seconds: 6)),
        registration,
      ]);
      final token = results[1] as String;
      if (token.trim().isNotEmpty) {
        await _onTokenReceived(token.trim());
      }
    } catch (error) {
      debugPrint('Không thể đồng bộ FCM token: $error');
    }
  }

  static Future<void> _onTokenReceived(String token) async {
    final trimmed = token.trim();
    if (trimmed.isEmpty) return;
    _lastKnownToken = trimmed;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(pushTokenKey, trimmed);
      await prefs.setString(pushIdentifierTypeKey, 'FID');
    } catch (_) {}
    await _registerTokenWithBackend(trimmed);
  }

  static Future<void> _registerTokenWithBackend(String token) async {
    if (await AuthService.getToken() == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      var deviceId = prefs.getString(deviceIdKey);
      if (deviceId == null || deviceId.trim().isEmpty) {
        deviceId = 'mobile-${DateTime.now().microsecondsSinceEpoch}';
        await prefs.setString(deviceIdKey, deviceId);
      }
      await ApiService.post('/api/v1/notifications/devices', {
        'token': token,
        'identifierType': 'FID',
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
    await _localNotifications.initialize(
      settings: initializationSettings,
      onDidReceiveNotificationResponse: _onDidReceiveNotificationResponse,
    );

    final launchDetails = await _localNotifications
        .getNotificationAppLaunchDetails();
    if (launchDetails?.didNotificationLaunchApp == true) {
      final payload = launchDetails?.notificationResponse?.payload;
      if (payload != null && payload.isNotEmpty) {
        _pendingNavigationPayload = _decodePayload(payload);
      }
    }

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

  static Map<String, dynamic> _extractNavigationPayload(RemoteMessage message) {
    final data = message.data;
    return <String, dynamic>{
      'notifId': data['notificationId'] ?? data['notifId'],
      'type': data['type'] ?? '',
      'referenceType': data['referenceType'] ?? '',
      'referenceId': data['referenceId'] ?? '',
      'title': message.notification?.title ?? '',
      'content': message.notification?.body ?? '',
    };
  }

  static Map<String, dynamic>? _decodePayload(String? rawPayload) {
    if (rawPayload == null || rawPayload.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(rawPayload);
      if (decoded is Map) {
        return Map<String, dynamic>.from(decoded);
      }
    } catch (_) {}
    return null;
  }

  static Future<void> _showForegroundMessage(RemoteMessage message) async {
    // Báo cho các màn hình đang mở tải lại dữ liệu thông báo thật từ backend.
    notificationRevision.value++;
    final notification = message.notification;
    if (notification == null) return;
    final payload = jsonEncode(_extractNavigationPayload(message));
    await _localNotifications.show(
      id: message.messageId?.hashCode ?? DateTime.now().millisecondsSinceEpoch,
      title: notification.title ?? 'Thông báo Thư viện HUIT',
      body: notification.body,
      payload: payload,
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

  static void _onDidReceiveNotificationResponse(
    NotificationResponse response,
  ) {
    notificationRevision.value++;
    final payloadMap = _decodePayload(response.payload);
    if (payloadMap != null) {
      handleNotificationTapPayload(payloadMap);
    }
  }

  static void _handleRemoteMessageOpenedApp(RemoteMessage message) {
    notificationRevision.value++;
    final payloadMap = _extractNavigationPayload(message);
    handleNotificationTapPayload(payloadMap);
  }

  static Future<void> handleNotificationTapPayload(
    Map<String, dynamic> payload,
  ) async {
    final token = await AuthService.getToken();
    final navContext = navigatorKey.currentContext;
    if (token == null || navContext == null || !navContext.mounted) {
      _pendingNavigationPayload = payload;
      return;
    }

    final notifId = payload['notifId']?.toString().trim();
    if (notifId != null && notifId.isNotEmpty && notifId != 'null') {
      try {
        await NotificationService.markAsRead(notifId);
      } catch (_) {}
    }

    if (!navContext.mounted) return;
    await NotificationService.navigateToNotificationTarget(
      navContext,
      payload,
    );
  }

  static Future<void> consumePendingNavigation(BuildContext context) async {
    final pending = _pendingNavigationPayload;
    if (pending == null) return;
    _pendingNavigationPayload = null;
    if (!context.mounted) return;

    final notifId = pending['notifId']?.toString().trim();
    if (notifId != null && notifId.isNotEmpty && notifId != 'null') {
      try {
        await NotificationService.markAsRead(notifId);
      } catch (_) {}
    }

    if (!context.mounted) return;
    await NotificationService.navigateToNotificationTarget(context, pending);
  }

  static String _platformName() {
    if (kIsWeb) return 'WEB';
    if (Platform.isIOS) return 'IOS';
    return 'ANDROID';
  }
}
