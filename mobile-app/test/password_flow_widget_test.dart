import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/screens/change_password_screen.dart';
import 'package:mobile_app/screens/forgot_password_screen.dart';
import 'package:mobile_app/screens/login_screen.dart';

void main() {
  testWidgets(
    'personal password change requires the current password, not OTP, and validates strength and confirmation',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: ChangePasswordScreen(isFirstLogin: false)),
      );

      expect(find.text('Mật khẩu hiện tại'), findsOneWidget);
      expect(find.text('Mật khẩu mới'), findsOneWidget);
      expect(find.text('Xác nhận mật khẩu mới'), findsOneWidget);
      expect(find.textContaining('OTP'), findsNothing);

      // Missing current password
      await tester.tap(find.text('XÁC NHẬN ĐỔI MẬT KHẨU'));
      await tester.pump();
      expect(find.text('Vui lòng nhập mật khẩu hiện tại.'), findsOneWidget);

      // Weak new password
      final fields = find.byType(TextField);
      await tester.enterText(fields.at(0), 'Old-Pass!1');
      await tester.enterText(fields.at(1), 'weakpass');
      await tester.enterText(fields.at(2), 'weakpass');
      await tester.tap(find.text('XÁC NHẬN ĐỔI MẬT KHẨU'));
      await tester.pump();
      expect(
        find.textContaining(
          'Mật khẩu mới phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'login screen exposes forgot password button and navigates to OTP recovery flow',
    (tester) async {
      await tester.pumpWidget(const MaterialApp(home: LoginScreen()));

      expect(find.text('Quên mật khẩu?'), findsOneWidget);
      await tester.tap(find.text('Quên mật khẩu?'));
      await tester.pumpAndSettle();

      expect(find.text('Quên mật khẩu'), findsOneWidget);
      expect(find.text('Email hoặc mã số sinh viên'), findsOneWidget);
      expect(find.text('GỬI MÃ OTP'), findsOneWidget);
    },
  );

  testWidgets(
    'forgot password starts with the OTP recovery flow and validates empty input',
    (tester) async {
      await tester.pumpWidget(const MaterialApp(home: ForgotPasswordScreen()));

      expect(find.text('Quên mật khẩu'), findsOneWidget);
      expect(find.text('Email hoặc mã số sinh viên'), findsOneWidget);
      expect(find.text('GỬI MÃ OTP'), findsOneWidget);

      await tester.tap(find.text('GỬI MÃ OTP'));
      await tester.pump();
      expect(
        find.text('Vui lòng nhập email hoặc mã số sinh viên.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'OTP uses one visible numeric field limited to exactly six digits',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: ForgotPasswordScreen(initialStep: 1)),
      );

      final otpFinder = find.byKey(const Key('forgot-password-otp-field'));
      expect(otpFinder, findsOneWidget);

      final otpField = tester.widget<TextField>(otpFinder);
      expect(otpField.obscureText, isFalse);
      expect(otpField.keyboardType, TextInputType.number);
      expect(find.byIcon(Icons.password), findsNothing);

      await tester.enterText(otpFinder, '12a34-5678');
      expect(find.text('123456'), findsOneWidget);
    },
  );
}
