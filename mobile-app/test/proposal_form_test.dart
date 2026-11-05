import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile_app/screens/suggest_book_screen.dart';
import 'package:mobile_app/services/api_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late List<Map<String, dynamic>> submittedBodies;

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'huit_lib_token': 'test-jwt-student-2001230219',
    });
    submittedBodies = [];
    ApiService.testClient = MockClient((request) async {
      if (request.method == 'GET' && request.url.path.endsWith('/proposals/majors')) {
        return http.Response(
          jsonEncode([
            {'majorId': 'CNTT', 'majorName': 'Công nghệ thông tin'},
            {'majorId': 'KT', 'majorName': 'Kế toán'},
          ]),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }
      if (request.method == 'GET' &&
          request.url.path.endsWith('/proposals/my')) {
        return http.Response('[]', 200);
      }
      if (request.method == 'POST' && request.url.path.endsWith('/proposals')) {
        submittedBodies.add(
          Map<String, dynamic>.from(jsonDecode(request.body) as Map),
        );
        return http.Response(
          jsonEncode({'proposalId': 'proposal-1', 'status': 'PENDING'}),
          201,
        );
      }
      return http.Response('Not found', 404);
    });
  });

  tearDown(() {
    ApiService.testClient = null;
  });

  Future<void> pumpForm(WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: SuggestBookScreen()));
    // RefreshIndicator keeps an animation registered in widget tests, so wait
    // only for the two mocked requests instead of waiting for every animation.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
  }

  testWidgets('official majors replace genres and urgency', (tester) async {
    await pumpForm(tester);
    expect(find.text('Mức độ cần thiết'), findsNothing);
    final dropdown = find.byType(DropdownButtonFormField<String>);
    await tester.ensureVisible(dropdown);
    await tester.tap(dropdown);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Công nghệ thông tin'), findsOneWidget);
    expect(find.text('Kế toán'), findsOneWidget);
    expect(find.text('Khác'), findsNothing);
    await tester.tap(find.text('Công nghệ thông tin').last);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  });

  testWidgets('thông tin ít dùng được thu gọn', (
    tester,
  ) async {
    await pumpForm(tester);

    expect(find.text('Tên sách *'), findsOneWidget);
    expect(find.text('Lý do đề xuất *'), findsOneWidget);
    expect(find.text('Thông tin bổ sung'), findsOneWidget);
    expect(find.text('Nhà xuất bản'), findsNothing);

    final additionalInformation = find.text('Thông tin bổ sung');
    await tester.ensureVisible(additionalInformation);
    await tester.pump();
    await tester.tap(additionalInformation);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Nhà xuất bản'), findsOneWidget);
    expect(find.text('ISBN'), findsOneWidget);
    expect(find.text('Năm xuất bản'), findsOneWidget);
    expect(find.text('Giá dự kiến (VNĐ)'), findsOneWidget);
    expect(find.text('Liên kết tham khảo'), findsOneWidget);
  });

  testWidgets('gửi mã ngành chính thức, bỏ thể loại và ưu tiên', (tester) async {
    await pumpForm(tester);

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Tên sách *'),
      'Kiến trúc phần mềm',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Lý do đề xuất *'),
      'Phục vụ môn học chuyên ngành',
    );
    tester.testTextInput.hide();
    await tester.pump();

    final genreDropdown = find.byType(DropdownButtonFormField<String>).first;
    await tester.ensureVisible(genreDropdown);
    await tester.pump();
    await tester.tap(genreDropdown);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Công nghệ thông tin').last);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final submitButton = find.widgetWithText(ElevatedButton, 'GỬI ĐỀ XUẤT');
    await tester.ensureVisible(submitButton);
    await tester.pump();
    tester.widget<ElevatedButton>(submitButton).onPressed!.call();
    await tester.pump(const Duration(milliseconds: 500));

    expect(submittedBodies, hasLength(1));
    expect(submittedBodies.single['title'], 'Kiến trúc phần mềm');
    expect(submittedBodies.single['reason'], 'Phục vụ môn học chuyên ngành');
    expect(submittedBodies.single['majorId'], 'CNTT');
    expect(submittedBodies.single.containsKey('genre'), isFalse);
    expect(submittedBodies.single.containsKey('urgencyLevel'), isFalse);
    expect(submittedBodies.single.containsKey('publisher'), isFalse);
  });
}
