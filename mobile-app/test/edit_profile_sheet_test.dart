import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/widgets/edit_profile_sheet.dart';

void main() {
  testWidgets('save +84 phone with keyboard open then reopen safely', (
    tester,
  ) async {
    String? savedPhone;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showModalBottomSheet<Map<String, dynamic>>(
                context: context,
                isScrollControlled: true,
                builder: (_) => EditProfileSheet(
                  user: const {'fullName': 'Bạn đọc'},
                  onSave: ({required fullName, required phone}) async {
                    savedPhone = phone;
                    return {'fullName': fullName, 'phone': phone};
                  },
                ),
              ),
              child: const Text('Edit'),
            ),
          ),
        ),
      ),
    );
    for (var i = 0; i < 2; i++) {
      await tester.tap(find.text('Edit'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('profile-phone-field')),
        '+84 356996264',
      );
      final save = find.byKey(const ValueKey('profile-save-button'));
      await tester.ensureVisible(save);
      await tester.tap(save);
      await tester.pumpAndSettle();
      expect(savedPhone, '0356996264');
      expect(find.byType(EditProfileSheet), findsNothing);
      expect(tester.takeException(), isNull);
    }
  });
}
