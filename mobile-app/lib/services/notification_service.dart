import 'package:flutter/material.dart';

import '../screens/borrowed_screen.dart';
import '../screens/notifications_screen.dart';
import '../screens/penalties_screen.dart';
import '../screens/suggest_book_screen.dart';
import 'api_service.dart';
import 'auth_service.dart';
import 'push_notification_service.dart';

enum NotificationDestination { borrowed, suggestBook, penalties, notifications }

class NotificationNavigationTarget {
  final NotificationDestination destination;
  final String type;
  final String? referenceType;
  final String? referenceId;
  final String? ticketId;
  final String? detailId;
  final String? waitlistId;
  final String? proposalId;
  final String? penaltyCaseId;

  const NotificationNavigationTarget({
    required this.destination,
    required this.type,
    this.referenceType,
    this.referenceId,
    this.ticketId,
    this.detailId,
    this.waitlistId,
    this.proposalId,
    this.penaltyCaseId,
  });
}

class NotificationService {
  static Future<List<Map<String, dynamic>>> getMyNotifications() async {
    final data = await ApiService.get('/api/v1/notifications');
    return (data as List<dynamic>)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  static Future<int> getUnreadCount() async {
    final items = await getMyNotifications();
    return items.where((item) => item['isRead'] != true).length;
  }

  static Future<void> markAsRead(String notificationId) async {
    await ApiService.patch('/api/v1/notifications/$notificationId/read');
    PushNotificationService.notificationRevision.value++;
  }

  static Future<Map<String, dynamic>> markAllAsRead() async {
    final response = await ApiService.patch('/api/v1/notifications/read-all');
    PushNotificationService.notificationRevision.value++;
    if (response is Map) {
      return Map<String, dynamic>.from(response);
    }
    return const <String, dynamic>{};
  }

  static NotificationNavigationTarget resolveNavigationTarget(
    Map<String, dynamic> item,
  ) {
    final type = (item['type'] ?? '').toString().trim().toUpperCase();
    final rawRefType = (item['referenceType'] ?? '')
        .toString()
        .trim()
        .toUpperCase();
    final refType = rawRefType.isEmpty || rawRefType == 'NULL'
        ? null
        : rawRefType;
    final rawRefId = item['referenceId']?.toString().trim();
    final refId =
        (rawRefId == null ||
            rawRefId.isEmpty ||
            rawRefId.toLowerCase() == 'null')
        ? null
        : rawRefId;

    const loanAndReservationTypes = <String>{
      'LOAN_APPROVED',
      'LOAN_REJECTED',
      'RESERVATION_EXPIRED',
      'RESERVATION_NO_SHOW',
      'WAITLIST_AVAILABLE',
      'DUE_SOON',
      'LOAN_DUE_SOON',
      'DUE_TODAY',
      'LOAN_DUE_TODAY',
      'OVERDUE',
      'LOAN_OVERDUE',
    };

    if (loanAndReservationTypes.contains(type) ||
        refType == 'LOAN_TICKET' ||
        refType == 'LOAN_DETAIL' ||
        refType == 'WAITLIST') {
      String? ticketId;
      String? detailId;
      String? waitlistId;
      if (refType == 'LOAN_TICKET') {
        ticketId = refId;
      } else if (refType == 'LOAN_DETAIL') {
        detailId = refId;
      } else if (refType == 'WAITLIST' || type == 'WAITLIST_AVAILABLE') {
        waitlistId = refId;
      } else if (type == 'LOAN_APPROVED' ||
          type == 'LOAN_REJECTED' ||
          type == 'RESERVATION_EXPIRED' ||
          type == 'RESERVATION_NO_SHOW') {
        ticketId = refId;
      } else {
        detailId = refId;
      }
      return NotificationNavigationTarget(
        destination: NotificationDestination.borrowed,
        type: type,
        referenceType: refType,
        referenceId: refId,
        ticketId: ticketId,
        detailId: detailId,
        waitlistId: waitlistId,
      );
    }

    if (type == 'PROPOSAL_APPROVED' ||
        type == 'PROPOSAL_REJECTED' ||
        refType == 'BOOK_PROPOSAL') {
      return NotificationNavigationTarget(
        destination: NotificationDestination.suggestBook,
        type: type,
        referenceType: refType,
        referenceId: refId,
        proposalId: refId,
      );
    }

    const penaltyTypes = <String>{
      'PENALTY',
      'FINE',
      'PENALTY_CASE_CREATED',
      'PENALTY_CASE_DUE',
      'WAIVER_DECISION',
    };
    if (penaltyTypes.contains(type) ||
        refType == 'PENALTY_CASE' ||
        refType == 'TRANSACTION') {
      return NotificationNavigationTarget(
        destination: NotificationDestination.penalties,
        type: type,
        referenceType: refType,
        referenceId: refId,
        penaltyCaseId: refType == 'TRANSACTION' ? null : refId,
      );
    }

    return NotificationNavigationTarget(
      destination: NotificationDestination.notifications,
      type: type,
      referenceType: refType,
      referenceId: refId,
    );
  }

  static Future<void> navigateToNotificationTarget(
    BuildContext context,
    Map<String, dynamic> item, {
    bool fromNotificationsScreen = false,
  }) async {
    final target = resolveNavigationTarget(item);
    final role = await AuthService.getCurrentRole();
    if (!AuthService.isStudentRole(role) &&
        target.destination != NotificationDestination.notifications) {
      if (!context.mounted || fromNotificationsScreen) return;
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const NotificationsScreen()),
      );
      return;
    }
    if (!context.mounted) return;
    switch (target.destination) {
      case NotificationDestination.borrowed:
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => BorrowedScreen(
              initialTicketId: target.ticketId,
              initialDetailId: target.detailId,
              initialWaitlistId: target.waitlistId,
            ),
          ),
        );
        return;
      case NotificationDestination.suggestBook:
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) =>
                SuggestBookScreen(initialProposalId: target.proposalId),
          ),
        );
        return;
      case NotificationDestination.penalties:
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) =>
                PenaltiesScreen(initialCaseId: target.penaltyCaseId),
          ),
        );
        return;
      case NotificationDestination.notifications:
        if (!fromNotificationsScreen) {
          await Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const NotificationsScreen()),
          );
        }
        return;
    }
  }
}
