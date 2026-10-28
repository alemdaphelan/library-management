import 'api_service.dart';

class LibraryPolicy {
  final int maximumOpenItems;
  final int homeLoanDays;
  final int renewalDays;
  final int maximumRenewalCount;
  final int waitlistHoldHours;

  const LibraryPolicy({
    required this.maximumOpenItems,
    required this.homeLoanDays,
    required this.renewalDays,
    required this.maximumRenewalCount,
    required this.waitlistHoldHours,
  });

  factory LibraryPolicy.fromJson(Map<String, dynamic> json) {
    int number(String key) => (json[key] as num).toInt();
    return LibraryPolicy(
      maximumOpenItems: number('maximumOpenItems'),
      homeLoanDays: number('homeLoanDays'),
      renewalDays: number('renewalDays'),
      maximumRenewalCount: number('maximumRenewalCount'),
      waitlistHoldHours: number('waitlistHoldHours'),
    );
  }
}

class LibraryPolicyService {
  static Future<LibraryPolicy> getPolicy() async {
    final response = await ApiService.getPublic('/library-policy');
    return LibraryPolicy.fromJson(Map<String, dynamic>.from(response as Map));
  }
}
