import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const uiBook = <String, dynamic>{
  'bookId': 1,
  'title': 'Kiến trúc phần mềm và các hệ thống thông tin hiện đại',
  'author': 'Tác giả kiểm thử',
  'category': {'name': 'Tin học'},
  'department': 'Công nghệ thông tin',
  'copies': 3,
  'availableCopies': 2,
  'status': 'available',
  'location': 'Tầng 2 / Kệ Công nghệ thông tin / Ngăn chuyên ngành',
  'defaultPrice': 55000,
  'priceSource': 'DEMO_GENERATED',
  'description':
      'Dữ liệu giả lập dùng kiểm tra giao diện. Không phải giao dịch thật.',
};

const uiUser = <String, dynamic>{
  'userId': '00000000-0000-0000-0000-000000000001',
  'fullName': 'Bạn đọc kiểm thử',
  'mssv': 'TEST-001',
  'roleId': 'STUDENT',
  'role': 'STUDENT',
  'email': 'reader@example.test',
  'phone': '0356996264',
  'cardStatus': 'ACTIVE',
};

http.Client mobileUiClient() => MockClient((request) async {
  final path = request.url.path;
  dynamic data;
  if (path.endsWith('/auth/me')) {
    data = {
      ...uiUser,
      if (request.method == 'PUT')
        ...jsonDecode(request.body) as Map<String, dynamic>,
    };
  } else if (path.endsWith('/library-policy')) {
    data = {
      'maximumOpenItems': 3,
      'homeLoanDays': 10,
      'renewalDays': 10,
      'maximumRenewalCount': 1,
      'waitlistHoldHours': 48,
    };
  } else if (path.endsWith('/books/1')) {
    data = uiBook;
  } else if (path.endsWith('/my-eligibility')) {
    data = {
      'maximumItems': 3,
      'openItems': 0,
      'remainingSlots': 3,
      'securityDeposit': 450000,
      'currentBorrowedValue': 0,
      'requestedBookValue': 55000,
      'maximumBorrowingValue': 500000,
      'remainingBorrowingValue': 445000,
      'priceKnown': true,
      'monetaryEligible': true,
    };
  } else if (path.endsWith('/library-card')) {
    data = {
      'issued': true,
      'cardStatus': 'ACTIVE',
      'depositBalance': 450000,
      'totalDebt': 25000,
    };
  } else if (path.endsWith('/penalty-cases/my')) {
    data = [
      {
        'caseId': 99123,
        'barcode': 'BC-TEST-VERY-LONG-BARCODE-001',
        'status': 'PAID',
        'description': 'Phí sửa chữa tài liệu đã xử lý. Dữ liệu kiểm thử.',
        'totalAmount': 50000,
        'paidAmount': 50000,
      },
    ];
  } else if (path.endsWith('/my-ledger')) {
    data = [
      {
        'transactionId': 1,
        'reason': 'Phiếu thu sửa chữa tài liệu',
        'type': 'FINE',
        'status': 'PAID',
        'amount': 50000,
      },
    ];
  } else if (path.endsWith('/proposals/majors')) {
    data = [
      {
        'majorId': 'TEST-IT',
        'majorName': 'Công nghệ thông tin và hệ thống thông tin quản lý',
        'sourceReference': 'Danh mục kiểm thử',
      },
      {
        'majorId': 'TEST-ACC',
        'majorName': 'Kế toán',
        'sourceReference': 'Danh mục kiểm thử',
      },
    ];
  } else if (path.endsWith('/proposals/my')) {
    data = [
      {
        'proposalId': 1,
        'title': 'Kiến trúc phần mềm',
        'status': 'PENDING',
        'majorId': 'TEST-IT',
        'majorName': 'Công nghệ thông tin',
        'majorSourceReference': 'Danh mục kiểm thử',
        'reason': 'Phục vụ học tập chuyên ngành',
      },
    ];
  } else if (path.endsWith('/proposals') && request.method == 'POST') {
    data = {'proposalId': 2, 'status': 'PENDING'};
  } else if (path.endsWith('/qr/resolve')) {
    data = {
      'type': 'LOAN_TICKET',
      'ticketId': 123,
      'readerName': 'Bạn đọc kiểm thử',
      'status': 'BORROWING',
      'allowedActions': ['RETURN'],
      'items': [
        {
          'detailId': 101,
          'bookId': 1,
          'bookTitle': uiBook['title'],
          'barcode': 'BC-TEST-001',
          'status': 'BORROWING',
          'itemStatus': 'BORROWING',
          'returnable': true,
          'priceKnown': true,
          'bookValue': 55000,
          'bookValueCurrency': 'VND',
          'bookValueSource': 'DEMO_GENERATED',
        },
      ],
    };
  } else if (path.endsWith('/notifications/unread-count')) {
    data = {'count': 0};
  } else if (path.endsWith('/reserve')) {
    data = {
      'success': true,
      'ticketId': 123,
      'message': 'Đã đăng ký (dữ liệu kiểm thử)',
    };
  } else {
    data = [];
  }
  return http.Response(
    jsonEncode(data),
    200,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );
});
