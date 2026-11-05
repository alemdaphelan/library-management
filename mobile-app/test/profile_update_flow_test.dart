import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/services/auth_service.dart';

void main() {
  group('Cập nhật hồ sơ cá nhân', () {
    test('chuẩn hóa số điện thoại Việt Nam trước khi gửi Backend', () {
      expect(AuthService.normalizePhone('(+84) 912-345-678'), '0912345678');
      expect(AuthService.normalizePhone('0912 345 678'), '0912345678');
      expect(AuthService.normalizePhone('+84 356996264'), '0356996264');
    });

    test('chỉ chấp nhận số điện thoại hợp lệ hoặc để trống', () {
      expect(AuthService.isValidPhone('0912345678'), isTrue);
      expect(AuthService.isValidPhone('+84 912 345 678'), isTrue);
      expect(AuthService.isValidPhone(''), isTrue);
      expect(AuthService.isValidPhone('12345'), isFalse);
      expect(AuthService.isValidPhone('09123abc78'), isFalse);
    });
  });
}
