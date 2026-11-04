import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/services/auth_service.dart';

void main() {
  group('role routing helpers', () {
    test('normalizes backend role variants', () {
      expect(AuthService.normalizeRoleValue('ROLE_STUDENT'), 'STUDENT');
      expect(AuthService.normalizeRoleValue('librarian'), 'LIBRARIAN');
      expect(AuthService.normalizeRoleValue('STAFF'), 'LIBRARIAN');
      expect(AuthService.normalizeRoleValue('ROLE_ADMIN'), 'ADMIN');
    });

    test('reads roles from login and auth-me payloads', () {
      expect(AuthService.roleFromProfile({'role': 'STUDENT'}), 'STUDENT');
      expect(AuthService.roleFromProfile({'roleId': 'LIBRARIAN'}), 'LIBRARIAN');
      expect(AuthService.roleFromProfile({'userType': 'ADMIN'}), 'ADMIN');
      expect(
        AuthService.roleFromProfile({
          'authorities': [
            {'authority': 'ROLE_STUDENT'},
          ],
        }),
        'STUDENT',
      );
    });

    test('distinguishes student and staff scopes', () {
      expect(AuthService.isStudentRole('ROLE_STUDENT'), isTrue);
      expect(AuthService.isStaffRole('LIBRARIAN'), isTrue);
      expect(AuthService.isStaffRole('ROLE_ADMIN'), isTrue);
      expect(AuthService.isStaffRole('STUDENT'), isFalse);
      expect(AuthService.isStudentRole('ADMIN'), isFalse);
    });
  });
}
