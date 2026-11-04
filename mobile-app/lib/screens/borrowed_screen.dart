import 'package:flutter/material.dart';

import 'package:mobile_app/services/auth_service.dart';
import 'package:mobile_app/services/loan_service.dart';
import 'package:mobile_app/widgets/book_cover.dart';
import 'package:mobile_app/widgets/ticket_qr_dialog.dart';
import 'package:mobile_app/screens/login_screen.dart';
import 'package:mobile_app/services/library_policy_service.dart';

class BorrowedScreenAppBarTitle extends StatelessWidget {
  const BorrowedScreenAppBarTitle({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: double.infinity,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: Text(
          'Quản lý mượn & Đặt giữ',
          maxLines: 1,
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
      ),
    );
  }
}

class BorrowingLimitSummary extends StatelessWidget {
  final int openItems;
  final int maximumItems;
  final int remainingSlots;
  final String cardStatus;

  const BorrowingLimitSummary({
    super.key,
    required this.openItems,
    required this.maximumItems,
    required this.remainingSlots,
    required this.cardStatus,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            flex: 3,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Hạn mức mượn / đặt giữ',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.white70, fontSize: 12),
                ),
                Text(
                  'Đang chiếm chỗ: $openItems/$maximumItems '
                  '(Còn $remainingSlots lượt)',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Flexible(
            flex: 2,
            child: Tooltip(
              message: cardStatus,
              child: Chip(
                avatar: const Icon(Icons.verified_user, size: 18),
                label: Text(
                  cardStatus,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                backgroundColor: Colors.white,
                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                visualDensity: VisualDensity.compact,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class BorrowedScreen extends StatefulWidget {
  final String? initialTicketId;
  final String? initialDetailId;
  final String? initialWaitlistId;

  const BorrowedScreen({
    super.key,
    this.initialTicketId,
    this.initialDetailId,
    this.initialWaitlistId,
  });

  @override
  State<BorrowedScreen> createState() => _BorrowedScreenState();
}

class _BorrowedScreenState extends State<BorrowedScreen> {
  Map<String, dynamic>? _userProfile;
  List<Map<String, dynamic>> _borrowedBooks = [];
  List<Map<String, dynamic>> _waitlistEntries = [];
  Map<String, dynamic> _eligibility = {};
  bool _isLoading = true;
  bool _accessDenied = false;
  bool _checkedInitialReference = false;
  LibraryPolicy? _policy;
  final Set<int> _processingTickets = <int>{};

  @override
  void initState() {
    super.initState();
    LoanService.loanRevision.addListener(_handleLoanRevisionChanged);
    AuthService.profileRevision.addListener(_handleProfileRevisionChanged);
    _fetchData();
  }

  @override
  void dispose() {
    LoanService.loanRevision.removeListener(_handleLoanRevisionChanged);
    AuthService.profileRevision.removeListener(_handleProfileRevisionChanged);
    super.dispose();
  }

  Future<void> _handleProfileRevisionChanged() async {
    final user = await AuthService.getCachedUser();
    if (!mounted || user == null) return;
    setState(() => _userProfile = user);
  }

  void _handleLoanRevisionChanged() {
    if (mounted) {
      _fetchData();
    }
  }

  Future<void> _fetchData() async {
    try {
      final user = await AuthService.getCurrentUser();
      final role = AuthService.roleFromProfile(user);
      if (!AuthService.isStudentRole(role)) {
        if (mounted) {
          setState(() {
            _userProfile = user;
            _borrowedBooks = [];
            _waitlistEntries = [];
            _eligibility = {};
            _accessDenied = true;
            _isLoading = false;
          });
        }
        return;
      }

      LibraryPolicy? policy;
      try {
        policy = await LibraryPolicyService.getPolicy();
      } catch (_) {
        // Vẫn hiển thị danh sách; thao tác gia hạn bị khóa đến khi tải được quy định.
      }
      final results = await Future.wait<dynamic>([
        LoanService.getLoans(policyLoanDays: policy?.homeLoanDays),
        LoanService.getMyWaitlist().catchError((_) => <Map<String, dynamic>>[]),
        LoanService.getEligibility().catchError((_) => <String, dynamic>{}),
      ]);
      if (mounted) {
        setState(() {
          _userProfile = user;
          _borrowedBooks = List<Map<String, dynamic>>.from(
            results[0] as List<dynamic>,
          );
          _waitlistEntries = List<Map<String, dynamic>>.from(
            results[1] as List<dynamic>,
          );
          _eligibility = Map<String, dynamic>.from(
            results[2] as Map<String, dynamic>,
          );
          _policy = policy;
          _accessDenied = false;
          _isLoading = false;
        });
        _verifyReferencedLoanIfNeeded();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  void _verifyReferencedLoanIfNeeded() {
    if (_checkedInitialReference) return;
    _checkedInitialReference = true;

    final ticketId = widget.initialTicketId?.trim();
    final detailId = widget.initialDetailId?.trim();
    final waitlistId = widget.initialWaitlistId?.trim();
    if ((ticketId == null || ticketId.isEmpty) &&
        (detailId == null || detailId.isEmpty) &&
        (waitlistId == null || waitlistId.isEmpty)) {
      return;
    }

    var found = false;
    if (ticketId != null && ticketId.isNotEmpty) {
      found = _borrowedBooks.any(
        (item) =>
            item['ticketId']?.toString() == ticketId ||
            item['id']?.toString() == ticketId,
      );
    } else if (detailId != null && detailId.isNotEmpty) {
      found = _borrowedBooks.any(
        (item) =>
            item['detailId']?.toString() == detailId ||
            item['id']?.toString() == detailId,
      );
    } else if (waitlistId != null && waitlistId.isNotEmpty) {
      found = _waitlistEntries.any(
        (entry) =>
            entry['waitlistId']?.toString() == waitlistId ||
            entry['id']?.toString() == waitlistId,
      );
    }

    if (!found) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ScaffoldMessenger.maybeOf(context)?.showSnackBar(
          const SnackBar(
            content: Text(
              'Phiếu mượn hoặc yêu cầu đặt giữ được tham chiếu không còn tồn tại hoặc bạn không có quyền truy cập.',
            ),
          ),
        );
      });
    }
  }

  Future<void> _handleLogout() async {
    await AuthService.logout();
    if (mounted) {
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (context) => const LoginScreen()),
        (route) => false,
      );
    }
  }

  Future<void> _showTicketQr(int ticketId, TicketQrPurpose purpose) async {
    await showTicketQrDialog(
      context: context,
      ticketId: ticketId,
      purpose: purpose,
    );
  }

  List<Map<String, dynamic>> get _waitingPickupBooks =>
      _borrowedBooks.where((item) => item['isWaitingPickup'] == true).toList();

  List<Map<String, dynamic>> get _activeBorrowingBooks =>
      _borrowedBooks.where((item) => item['isBorrowing'] == true).toList();

  List<Map<String, dynamic>> get _historyBooks =>
      _borrowedBooks.where((item) => item['isHistory'] == true).toList();

  List<Map<String, dynamic>> get _activeWaitlistEntries =>
      _waitlistEntries.where((entry) {
        final status = (entry['status'] ?? '').toString().toUpperCase();
        return status == 'WAITING' || status == 'NOTIFIED';
      }).toList();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFf8f9ff),
      appBar: AppBar(
        title: const BorrowedScreenAppBarTitle(),
        titleSpacing: 0,
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            onPressed: _fetchData,
            icon: const Icon(Icons.refresh),
            tooltip: 'Tải lại',
          ),
          IconButton(
            icon: const Icon(Icons.logout, color: Colors.red),
            onPressed: _handleLogout,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _accessDenied
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.admin_panel_settings_outlined,
                      size: 56,
                      color: Color(0xFF0284C7),
                    ),
                    SizedBox(height: 16),
                    Text(
                      'Danh sách mượn và hạn mức đặt giữ chỉ dành cho tài khoản sinh viên.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 16),
                    ),
                  ],
                ),
              ),
            )
          : RefreshIndicator(
              onRefresh: _fetchData,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildProfileHeader(),
                    const SizedBox(height: 12),
                    _buildBorrowedSections(),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
    );
  }

  Widget _buildProfileHeader() {
    final role =
        (_userProfile?['roleId'] ??
                _userProfile?['role'] ??
                _userProfile?['userType'] ??
                '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';

    final fullName =
        _userProfile?['fullName'] ?? _userProfile?['name'] ?? 'Chưa cập nhật';
    final studentId = _userProfile?['studentId'] ?? _userProfile?['mssv'];
    final faculty =
        _userProfile?['faculty'] ??
        _userProfile?['department'] ??
        'Chưa cập nhật';

    final maxItems =
        (_eligibility['maximumItems'] as num?)?.toInt() ??
        _policy?.maximumOpenItems ??
        3;
    final openItems =
        (_eligibility['openItems'] as num?)?.toInt() ??
        (_waitingPickupBooks.length + _activeBorrowingBooks.length);
    final remainingSlots =
        (_eligibility['remainingSlots'] as num?)?.toInt() ??
        (maxItems - openItems).clamp(0, maxItems);

    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isAdmin
              ? const [Color(0xFF0f172a), Color(0xFF1e293b), Color(0xFF334155)]
              : const [Color(0xFF0284c7), Color(0xFF0369a1)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: (isAdmin ? Colors.black : const Color(0xFF0284c7))
                .withValues(alpha: 0.3),
            blurRadius: 10,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: Column(
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 35,
                backgroundColor: isAdmin
                    ? Colors.amber.shade400.withValues(alpha: 0.2)
                    : Colors.white,
                child: Icon(
                  isAdmin ? Icons.admin_panel_settings_rounded : Icons.person,
                  size: 40,
                  color: isAdmin
                      ? Colors.amber.shade300
                      : const Color(0xFF0284c7),
                ),
              ),
              const SizedBox(width: 15),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      fullName.toString(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      isAdmin
                          ? 'Email: ${_userProfile?['email'] ?? 'Chưa cập nhật'}'
                          : 'MSSV: ${studentId ?? 'Chưa cập nhật'}',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                    Text(
                      faculty.toString(),
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          BorrowingLimitSummary(
            openItems: openItems,
            maximumItems: maxItems,
            remainingSlots: remainingSlots,
            cardStatus: _userProfile?['cardStatus']?.toString() ?? 'ACTIVE',
          ),
        ],
      ),
    );
  }

  Widget _buildBorrowedSections() {
    final role =
        (_userProfile?['roleId'] ??
                _userProfile?['role'] ??
                _userProfile?['userType'] ??
                '')
            .toString()
            .toUpperCase();
    final isAdmin = role == 'ADMIN' || role == 'LIBRARIAN' || role == 'STAFF';

    final waitingPickup = _waitingPickupBooks;
    final activeBorrowing = _activeBorrowingBooks;
    final activeWaitlist = _activeWaitlistEntries;
    final history = _historyBooks;

    if (waitingPickup.isEmpty &&
        activeBorrowing.isEmpty &&
        activeWaitlist.isEmpty &&
        history.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(24.0),
        child: Center(
          child: Text(
            isAdmin
                ? 'Tài khoản Quản trị viên chưa có sách cá nhân đang mượn.'
                : 'Bạn không có sách nào đang mượn hoặc đặt giữ.',
            style: const TextStyle(color: Colors.grey),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSectionTitle(
            'Chờ nhận tại quầy (${waitingPickup.length})',
            Icons.storefront_outlined,
            const Color(0xFF0284c7),
          ),
          const SizedBox(height: 8),
          if (waitingPickup.isEmpty)
            _buildEmptySectionCard('Không có phiếu nào đang chờ nhận tại quầy.')
          else
            ...waitingPickup.map(
              (book) => Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: _buildBookCard(book),
              ),
            ),
          const SizedBox(height: 16),

          _buildSectionTitle(
            'Đang mượn (${activeBorrowing.length})',
            Icons.menu_book_rounded,
            Colors.green.shade700,
          ),
          const SizedBox(height: 8),
          if (activeBorrowing.isEmpty)
            _buildEmptySectionCard('Không có tài liệu nào đang trong kỳ mượn.')
          else
            ...activeBorrowing.map(
              (book) => Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: _buildBookCard(book),
              ),
            ),

          if (activeWaitlist.isNotEmpty) ...[
            const SizedBox(height: 16),
            _buildSectionTitle(
              'Hàng đợi đặt trước (${activeWaitlist.length})',
              Icons.hourglass_top_rounded,
              Colors.orange.shade800,
            ),
            const SizedBox(height: 8),
            ...activeWaitlist.map(
              (entry) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _buildWaitlistCard(entry),
              ),
            ),
          ],

          const SizedBox(height: 16),
          _buildSectionTitle(
            'Lịch sử mượn & Đã hủy (${history.length})',
            Icons.history_rounded,
            Colors.blueGrey.shade700,
          ),
          const SizedBox(height: 8),
          if (history.isEmpty)
            _buildEmptySectionCard('Chưa có lịch sử trả sách hoặc hủy phiếu.')
          else
            ...history.map(
              (book) => Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: _buildBookCard(book),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title, IconData icon, Color color) {
    return Row(
      children: [
        Icon(icon, size: 20, color: color),
        const SizedBox(width: 8),
        Text(
          title,
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _buildEmptySectionCard(String message) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Text(
        message,
        style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
      ),
    );
  }

  Widget _buildWaitlistCard(Map<String, dynamic> entry) {
    final status = (entry['status'] ?? 'WAITING').toString().toUpperCase();
    final isNotified = status == 'NOTIFIED';
    final bookId = entry['bookId'] ?? '—';
    final expiresDisplay = LoanService.formatDateTime(entry['expiresAt']);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isNotified ? Colors.green.shade300 : Colors.orange.shade200,
        ),
      ),
      child: Row(
        children: [
          Icon(
            isNotified
                ? Icons.notifications_active_rounded
                : Icons.hourglass_bottom_rounded,
            color: isNotified ? Colors.green.shade700 : Colors.orange.shade800,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry['bookTitle']?.toString() ?? 'Đầu sách #$bookId',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isNotified
                      ? 'Đã có bản sao giữ chỗ cho bạn • Hạn xác nhận: $expiresDisplay'
                      : 'Đang xếp hàng chờ bản sao được trả về quầy',
                  style: TextStyle(
                    fontSize: 12,
                    color: isNotified
                        ? Colors.green.shade800
                        : Colors.orange.shade900,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _handleTicketAction({
    required int ticketId,
    required int? detailId,
    required bool isRenew,
  }) async {
    final messenger = ScaffoldMessenger.maybeOf(context);
    setState(() => _processingTickets.add(ticketId));
    try {
      if (isRenew && detailId != null) {
        await LoanService.renewLoan(ticketId, detailId);
      } else {
        await LoanService.cancelReservation(ticketId);
      }
      await _fetchData();
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            isRenew
                ? 'Gia hạn thời gian mượn thành công.'
                : 'Đã hủy phiếu đặt giữ và hoàn trả bản sao về kho.',
          ),
        ),
      );
    } catch (error) {
      await _fetchData();
      if (!mounted) return;
      // Nếu là thao tác hủy phiếu và sau khi đồng bộ lại thấy phiếu đã chuyển sang CANCELLED,
      // hiển thị thông báo thành công thay vì báo lỗi giả.
      if (!isRenew) {
        final matching = _borrowedBooks
            .where((item) => (item['ticketId'] as num?)?.toInt() == ticketId)
            .toList();
        if (matching.isNotEmpty &&
            matching.every(
              (item) =>
                  item['status'] == 'CANCELLED' ||
                  item['itemStatus'] == 'CANCELLED',
            )) {
          messenger?.hideCurrentSnackBar();
          messenger?.showSnackBar(
            const SnackBar(
              content: Text(
                'Đã hủy phiếu đặt giữ và đồng bộ lại trạng thái thành công.',
              ),
            ),
          );
          return;
        }
      }
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _processingTickets.remove(ticketId));
      }
    }
  }

  Widget _buildBookCard(Map<String, dynamic> book) {
    final title = (book['title'] ?? 'Tài liệu thư viện').toString();
    final author = (book['author'] ?? 'Chưa rõ tác giả').toString();
    final borrowDate = (book['borrowDate'] ?? '—').toString();
    final pickupDeadlineDisplay = (book['pickupDeadlineDisplay'] ?? '—')
        .toString();
    final dueDateDisplay = (book['dueDateDisplay'] ?? '—').toString();
    final returnDateDisplay = (book['returnDateDisplay'] ?? '—').toString();
    final daysLeft = (book['remainingDays'] as num?)?.toInt() ?? 0;
    final status = (book['status'] ?? 'PENDING').toString().toUpperCase();
    final itemStatus = (book['itemStatus'] ?? status).toString().toUpperCase();
    final ticketId = (book['ticketId'] as num?)?.toInt();
    final detailId = (book['detailId'] as num?)?.toInt();
    final renewalCount = (book['renewalCount'] as num?)?.toInt() ?? 0;
    final barcode = book['barcode']?.toString();

    final isWaitingPickup = book['isWaitingPickup'] == true;
    final isBorrowing = book['isBorrowing'] == true;
    final isHistory = book['isHistory'] == true;
    final isOverdue = book['isOverdue'] == true || status == 'OVERDUE';
    final isWarning = isBorrowing && (isOverdue || daysLeft <= 3);

    final canRenew =
        _policy != null &&
        isBorrowing &&
        status == 'BORROWING' &&
        !isOverdue &&
        daysLeft >= 0 &&
        ticketId != null &&
        detailId != null &&
        renewalCount < _policy!.maximumRenewalCount;

    final canCancel =
        isWaitingPickup &&
        (status == 'PENDING' || status == 'RESERVED') &&
        ticketId != null;
    final canShowPickupQr =
        ticketId != null && LoanService.canShowPickupQr(book);
    final canShowReturnQr =
        ticketId != null && LoanService.canShowReturnQr(book);

    Color statusColor;
    if (isOverdue) {
      statusColor = Colors.red.shade700;
    } else if (isWaitingPickup) {
      statusColor = const Color(0xFF0284c7);
    } else if (isBorrowing) {
      statusColor = daysLeft <= 3
          ? Colors.orange.shade800
          : Colors.green.shade700;
    } else {
      statusColor = Colors.blueGrey.shade600;
    }

    String statusText = switch (status) {
      'PENDING' => 'Chờ duyệt tại quầy',
      'RESERVED' => 'Đang giữ tại quầy',
      'RETURNED' => 'Đã trả sách',
      'CANCELLED' => 'Đã hủy yêu cầu',
      'REJECTED' => 'Bị từ chối',
      'OVERDUE' => 'Quá hạn ${daysLeft.abs() == 0 ? 1 : daysLeft.abs()} ngày',
      _ =>
        itemStatus == 'CANCELLED'
            ? 'Đã hủy yêu cầu'
            : (itemStatus == 'RETURNED'
                  ? 'Đã trả sách'
                  : (daysLeft < 0
                        ? 'Quá hạn ${daysLeft.abs()} ngày'
                        : 'Còn $daysLeft ngày')),
    };

    final loanDays = _policy?.homeLoanDays;
    final double progress = isHistory
        ? 1.0
        : (isWaitingPickup
              ? 0.25
              : ((book['progress'] as num?)?.toDouble() ??
                    (loanDays == null || loanDays <= 0
                        ? 0.0
                        : ((loanDays - daysLeft) / loanDays).clamp(0.0, 1.0))));

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isOverdue
              ? Colors.red.shade200
              : (isWaitingPickup
                    ? const Color(0xFF0284c7).withValues(alpha: 0.25)
                    : Colors.grey.shade200),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                BookCover(
                  imageUrl: book['coverUrl']?.toString(),
                  title: title,
                  author: author,
                  width: 65,
                  height: 90,
                  borderRadius: 6,
                ),
                const SizedBox(width: 15),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          if (ticketId != null)
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: statusColor.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                'Phiếu #$ticketId',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                  color: statusColor,
                                ),
                              ),
                            ),
                          if (barcode != null && barcode.isNotEmpty) ...[
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text(
                                '• $barcode',
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: Colors.grey,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        title,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0f172a),
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        author,
                        style: const TextStyle(
                          fontSize: 14,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            if (isWaitingPickup)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Hạn đến quầy nhận sách',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        pickupDeadlineDisplay,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: statusColor,
                        ),
                      ),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      const Text(
                        'Hạn trả dự kiến',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        dueDateDisplay,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ],
              )
            else if (isBorrowing)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Ngày mượn',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        borrowDate,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      const Text(
                        'Hạn trả sau khi nhận sách',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        dueDateDisplay,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: statusColor,
                        ),
                      ),
                    ],
                  ),
                ],
              )
            else
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Ngày đăng ký',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        borrowDate,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        status == 'RETURNED' || itemStatus == 'RETURNED'
                            ? 'Ngày trả'
                            : 'Hạn trả ghi nhận',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.grey,
                        ),
                      ),
                      Text(
                        (status == 'RETURNED' || itemStatus == 'RETURNED') &&
                                returnDateDisplay != '—'
                            ? returnDateDisplay
                            : dueDateDisplay,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: statusColor,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: LinearProgressIndicator(
                    value: progress,
                    backgroundColor: Colors.grey[200],
                    valueColor: AlwaysStoppedAnimation<Color>(statusColor),
                    minHeight: 6,
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  statusText,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                  ),
                ),
              ],
            ),
            if (canShowPickupQr || canShowReturnQr) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () => _showTicketQr(
                    ticketId,
                    canShowReturnQr
                        ? TicketQrPurpose.returnBook
                        : TicketQrPurpose.pickup,
                  ),
                  icon: const Icon(Icons.qr_code_2),
                  label: Text(
                    canShowReturnQr
                        ? 'Hiển thị QR trả sách'
                        : 'Hiển thị QR nhận sách',
                  ),
                ),
              ),
            ],
            if (canRenew || canCancel) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _processingTickets.contains(ticketId)
                      ? null
                      : () => _handleTicketAction(
                          ticketId: ticketId,
                          detailId: detailId,
                          isRenew: canRenew,
                        ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isWarning
                        ? Colors.red[50]
                        : const Color(0xFFf0f9ff),
                    foregroundColor: statusColor,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: _processingTickets.contains(ticketId)
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(
                          canRenew
                              ? 'Gia hạn thêm ${_policy!.renewalDays} ngày (${renewalCount + 1}/${_policy!.maximumRenewalCount})'
                              : 'Hủy phiếu đặt giữ',
                        ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
