import 'package:flutter/material.dart';
import '../widgets/book_cover.dart';
import '../services/api_service.dart';
import '../services/loan_service.dart';
import '../services/wishlist_service.dart';
import '../services/library_policy_service.dart';
import '../widgets/ticket_qr_dialog.dart';
import 'borrowed_screen.dart';

class DetailScreen extends StatefulWidget {
  final Map<String, dynamic>? book;

  const DetailScreen({super.key, this.book});

  @override
  State<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends State<DetailScreen> {
  late Map<String, dynamic> _bookData;
  bool _isSaved = false;
  bool _saving = false;
  bool _refreshingBook = false;
  LibraryPolicy? _policy;
  int _maxOpenItems = 3;
  int _openItemsCount = 0;
  int _remainingSlots = 3;
  String? _eligibilityMessage;
  num _securityDeposit = 0;
  num _currentBorrowedValue = 0;
  num _requestedBookValue = 0;
  num _maximumBorrowingValue = 0;
  num _remainingBorrowingValue = 0;
  bool _priceKnown = true;
  bool _monetaryEligible = true;
  bool _eligibilityLoaded = false;
  bool _hasActiveLoanForBook = false;
  bool _hasActiveWaitlistForBook = false;
  bool _hasNotifiedWaitlistHold = false;

  int? get _bookId {
    final raw = _bookData['bookId'] ?? _bookData['id'];
    return raw is num ? raw.toInt() : int.tryParse(raw?.toString() ?? '');
  }

  @override
  void initState() {
    super.initState();
    _bookData = Map<String, dynamic>.from(widget.book ?? const {});
    _isSaved = WishlistService.isSavedSync(_bookId);
    LoanService.loanRevision.addListener(_handleLoanRevisionChanged);
    WishlistService.wishlistRevision.addListener(
      _handleWishlistRevisionChanged,
    );
    _loadSavedStatus();
    _loadPolicy();
    _refreshAllBookAndLoanState();
  }

  @override
  void didUpdateWidget(covariant DetailScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.book != oldWidget.book) {
      _bookData = Map<String, dynamic>.from(widget.book ?? const {});
      _isSaved = WishlistService.isSavedSync(_bookId);
      _loadSavedStatus();
      _refreshAllBookAndLoanState();
    }
  }

  @override
  void dispose() {
    LoanService.loanRevision.removeListener(_handleLoanRevisionChanged);
    WishlistService.wishlistRevision.removeListener(
      _handleWishlistRevisionChanged,
    );
    super.dispose();
  }

  void _handleWishlistRevisionChanged() {
    if (!mounted || _saving) return;
    final synced = WishlistService.isSavedSync(_bookId);
    if (_isSaved != synced) {
      setState(() => _isSaved = synced);
    }
  }

  void _handleLoanRevisionChanged() {
    if (mounted) {
      _refreshAllBookAndLoanState();
    }
  }

  Future<void> _refreshAllBookAndLoanState() async {
    await Future.wait([_loadBookDetail(), _loadBorrowState()]);
  }

  Future<void> _loadBookDetail() async {
    final id = _bookId;
    if (id == null) return;
    if (mounted) {
      setState(() => _refreshingBook = true);
    }
    try {
      final response = await ApiService.get('/books/$id');
      if (!mounted || response is! Map) return;
      setState(() {
        _bookData = {..._bookData, ...Map<String, dynamic>.from(response)};
      });
    } catch (_) {
      // Giữ thông tin hiện có nếu không thể tải lại chi tiết.
    } finally {
      if (mounted) {
        setState(() => _refreshingBook = false);
      }
    }
  }

  Future<void> _loadBorrowState() async {
    final id = _bookId;
    try {
      final results = await Future.wait<dynamic>([
        LoanService.getEligibility(
          bookId: id,
        ).catchError((_) => <String, dynamic>{}),
        LoanService.getLoans(
          activeOnly: true,
        ).catchError((_) => <Map<String, dynamic>>[]),
        LoanService.getMyWaitlist().catchError((_) => <Map<String, dynamic>>[]),
      ]);
      if (!mounted) return;
      final eligibility = results[0] as Map<String, dynamic>;
      final activeLoans = results[1] as List<Map<String, dynamic>>;
      final waitlist = results[2] as List<Map<String, dynamic>>;

      final maxItems =
          (eligibility['maximumItems'] as num?)?.toInt() ??
          _policy?.maximumOpenItems ??
          3;
      final openItems =
          (eligibility['openItems'] as num?)?.toInt() ?? activeLoans.length;
      final remaining =
          (eligibility['remainingSlots'] as num?)?.toInt() ??
          (maxItems - openItems).clamp(0, maxItems);

      final hasLoanForBook =
          id != null &&
          activeLoans.any((loan) {
            final loanBookId = loan['bookId'] is num
                ? (loan['bookId'] as num).toInt()
                : int.tryParse(loan['bookId']?.toString() ?? '');
            return loanBookId == id && LoanService.isOpenLoanItem(loan);
          });

      final hasWaitingForBook =
          id != null &&
          waitlist.any((entry) {
            final entryBookId = entry['bookId'] is num
                ? (entry['bookId'] as num).toInt()
                : int.tryParse(entry['bookId']?.toString() ?? '');
            final status = (entry['status'] ?? '').toString().toUpperCase();
            return entryBookId == id && status == 'WAITING';
          });

      final hasNotifiedHold =
          id != null &&
          waitlist.any((entry) {
            final entryBookId = entry['bookId'] is num
                ? (entry['bookId'] as num).toInt()
                : int.tryParse(entry['bookId']?.toString() ?? '');
            final status = (entry['status'] ?? '').toString().toUpperCase();
            return entryBookId == id && status == 'NOTIFIED';
          });

      setState(() {
        _maxOpenItems = maxItems;
        _openItemsCount = openItems;
        _remainingSlots = remaining;
        _eligibilityMessage = eligibility['message']?.toString();
        _securityDeposit = _asMoney(eligibility['securityDeposit']);
        _currentBorrowedValue = _asMoney(eligibility['currentBorrowedValue']);
        _requestedBookValue = _asMoney(eligibility['requestedBookValue']);
        _maximumBorrowingValue = _asMoney(eligibility['maximumBorrowingValue']);
        _remainingBorrowingValue = _asMoney(
          eligibility['remainingBorrowingValue'],
        );
        _priceKnown = eligibility['priceKnown'] != false;
        _monetaryEligible = eligibility['monetaryEligible'] != false;
        _eligibilityLoaded = eligibility.isNotEmpty;
        _hasActiveLoanForBook = hasLoanForBook;
        _hasActiveWaitlistForBook = hasWaitingForBook;
        _hasNotifiedWaitlistHold = hasNotifiedHold;
      });
    } catch (_) {}
  }

  num _asMoney(dynamic value) {
    if (value is num) return value;
    return num.tryParse(value?.toString() ?? '') ?? 0;
  }

  String _formatVnd(num value) {
    final digits = value.round().abs().toString();
    final buffer = StringBuffer();
    for (var index = 0; index < digits.length; index++) {
      if (index > 0 && (digits.length - index) % 3 == 0) {
        buffer.write('.');
      }
      buffer.write(digits[index]);
    }
    return '${value < 0 ? '-' : ''}${buffer.toString()} đ';
  }

  Future<void> _loadPolicy() async {
    try {
      final policy = await LibraryPolicyService.getPolicy();
      if (mounted) {
        setState(() {
          _policy = policy;
          _maxOpenItems = policy.maximumOpenItems;
        });
      }
    } catch (_) {
      // Backend vẫn kiểm tra quy định khi sinh viên gửi yêu cầu mượn.
    }
  }

  Future<void> _loadSavedStatus() async {
    final id = _bookId;
    if (id == null) return;
    try {
      final saved = await WishlistService.isSaved(id);
      if (mounted && !_saving) setState(() => _isSaved = saved);
    } catch (_) {
      // The detail page remains usable when wishlist status cannot be loaded.
    }
  }

  Future<void> _toggleSaved() async {
    final id = _bookId;
    if (id == null || _saving || WishlistService.isInFlight(id)) return;
    final messenger = ScaffoldMessenger.maybeOf(context);
    final previousSaved = _isSaved;
    final targetSaved = !previousSaved;
    setState(() {
      _saving = true;
      _isSaved = targetSaved;
    });
    try {
      if (targetSaved) {
        final savedBook = await WishlistService.save(id);
        if (mounted && savedBook != null) {
          setState(() {
            _bookData = {..._bookData, ...savedBook};
          });
        }
      } else {
        await WishlistService.remove(id);
      }
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            targetSaved
                ? 'Đã lưu vào danh sách sách đã lưu.'
                : 'Đã bỏ khỏi danh sách sách đã lưu.',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        setState(() => _isSaved = previousSaved);
      }
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(error.toString()),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String _resolveCategory(Map<String, dynamic> book) {
    final categoryObj = book['category'];
    if (categoryObj is Map) {
      final name = categoryObj['name']?.toString().trim();
      if (name != null && name.isNotEmpty) return name;
    } else if (categoryObj != null) {
      final text = categoryObj.toString().trim();
      if (text.isNotEmpty) return text;
    }
    return 'Tài liệu chung';
  }

  String _resolveDepartment(
    Map<String, dynamic> book,
    String categoryFallback,
  ) {
    for (final key in const ['department', 'major', 'majorName', 'faculty']) {
      final value = book[key]?.toString().trim();
      if (value != null && value.isNotEmpty && value != 'null') {
        return value;
      }
    }
    return categoryFallback;
  }

  Future<void> _showBorrowModal({
    required String title,
    required String author,
    required String category,
    required String department,
    required String shelfLocation,
    required bool isJoiningWaitlist,
  }) async {
    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0284c7).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      isJoiningWaitlist
                          ? Icons.hourglass_top_rounded
                          : Icons.bookmark_add_rounded,
                      color: const Color(0xFF0284c7),
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Text(
                      isJoiningWaitlist
                          ? 'Xác nhận đăng ký hàng đợi'
                          : 'Xác nhận đăng ký mượn',
                      style: const TextStyle(
                        fontSize: 19,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.grey.shade50,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  children: [
                    _buildInfoRow('Tên sách:', title, isBold: true),
                    const Divider(height: 18),
                    _buildInfoRow('Tác giả:', author),
                    const Divider(height: 18),
                    _buildInfoRow('Thể loại:', category),
                    const Divider(height: 18),
                    _buildInfoRow('Chuyên ngành:', department),
                    const Divider(height: 18),
                    _buildInfoRow('Vị trí:', shelfLocation),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Nơi nhận:',
                      'Quầy thủ thư - Tầng 2 (Thư viện HUIT)',
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Hạn giữ sách:',
                      isJoiningWaitlist
                          ? (_policy == null
                                ? 'Giữ chỗ theo quy định thư viện khi có bản sao trả về'
                                : 'Giữ tối đa ${_policy!.waitlistHoldHours} giờ kể từ khi có thông báo sách về')
                          : 'Nhận trong ngày làm việc hiện tại (trước giờ đóng cửa quầy)',
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Thời hạn mượn:',
                      _policy == null
                          ? 'Theo quy định lưu thông hiện hành của thư viện'
                          : '${_policy!.homeLoanDays} ngày theo quy định thư viện',
                      highlightColor: Colors.green.shade700,
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Hạn mức hiện tại:',
                      'Đang có $_openItemsCount/$_maxOpenItems tài liệu (còn $_remainingSlots lượt)',
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Tiền thế chân:',
                      _formatVnd(_securityDeposit),
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Đang giữ/mượn:',
                      _formatVnd(_currentBorrowedValue),
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Giá cuốn này:',
                      _priceKnown
                          ? _formatVnd(_requestedBookValue)
                          : 'Chưa xác định giá',
                      highlightColor: _priceKnown
                          ? Colors.black87
                          : Colors.red.shade700,
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Tối đa được giữ:',
                      _formatVnd(_maximumBorrowingValue),
                    ),
                    const Divider(height: 18),
                    _buildInfoRow(
                      'Hạn mức còn lại:',
                      _formatVnd(_remainingBorrowingValue),
                      highlightColor: _monetaryEligible
                          ? Colors.green.shade700
                          : Colors.red.shade700,
                    ),
                  ],
                ),
              ),
              if (!_priceKnown || !_monetaryEligible) ...[
                const SizedBox(height: 12),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.red.shade50,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Colors.red.shade200),
                  ),
                  child: Text(
                    _eligibilityMessage ??
                        'Tài khoản chưa đáp ứng hạn mức giá trị để đăng ký tài liệu này.',
                    style: TextStyle(
                      color: Colors.red.shade900,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text(
                        'Hủy bỏ',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: _priceKnown && _monetaryEligible
                          ? () => Navigator.pop(ctx, true)
                          : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0284c7),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: Text(
                        isJoiningWaitlist
                            ? 'XÁC NHẬN VÀO HÀNG ĐỢI'
                            : 'XÁC NHẬN MƯỢN',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
            ],
          ),
        );
      },
    );
    if (confirmed == true && mounted) {
      await _processBorrow(title, author);
    }
  }

  Widget _buildInfoRow(
    String label,
    String value, {
    bool isBold = false,
    Color? highlightColor,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 108,
          child: Text(
            label,
            style: const TextStyle(color: Colors.grey, fontSize: 13),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: highlightColor ?? Colors.black87,
            ),
          ),
        ),
      ],
    );
  }

  String _buildReadableErrorMessage(Object error) {
    final raw = error.toString().replaceFirst('Exception: ', '').trim();
    if (error is ApiException && error.statusCode == 409) {
      if (raw.contains('trạng thái dữ liệu đã thay đổi') ||
          raw.toLowerCase() == 'conflict') {
        return 'Trạng thái bản sao hoặc phiếu mượn vừa thay đổi. '
            'Hệ thống đã tự động tải lại số lượng bản sao mới nhất trên màn hình, '
            'vui lòng kiểm tra lại và thực hiện tiếp.';
      }
    }
    return raw;
  }

  Future<void> _processBorrow(String title, String author) async {
    final messenger = ScaffoldMessenger.maybeOf(context);
    final rootNavigator = Navigator.of(context, rootNavigator: true);
    var loadingVisible = true;
    showDialog<void>(
      context: context,
      useRootNavigator: true,
      barrierDismissible: false,
      builder: (ctx) =>
          const Center(child: CircularProgressIndicator(color: Colors.white)),
    );

    Map<String, dynamic> newLoan;
    try {
      final bookId = _bookId;
      if (bookId == null) {
        throw Exception('Không xác định được mã sách.');
      }
      newLoan = await LoanService.reserveBook(bookId);
    } catch (error) {
      if (loadingVisible && mounted) {
        rootNavigator.pop();
        loadingVisible = false;
      }
      await _refreshAllBookAndLoanState();
      if (!mounted) return;
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(content: Text(_buildReadableErrorMessage(error))),
      );
      return;
    }

    if (loadingVisible && mounted) {
      rootNavigator.pop();
      loadingVisible = false;
    }
    await _refreshAllBookAndLoanState();
    if (!mounted) return;

    final status = (newLoan['status'] ?? 'RESERVED').toString().toUpperCase();
    final isWaitlist = status == 'WAITLIST';
    final pickupDisplay = LoanService.formatDateTime(
      newLoan['pickupDate'] ?? newLoan['pickupDeadline'],
    );
    final dueDisplay = LoanService.formatDate(newLoan['dueDate']);
    final backendMessage = newLoan['message']?.toString().trim();
    final ticketId =
        (newLoan['ticketId'] as num?)?.toInt() ??
        int.tryParse(newLoan['ticketId']?.toString() ?? '');

    await showDialog<void>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        contentPadding: const EdgeInsets.all(24),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: isWaitlist ? Colors.amber.shade50 : Colors.green.shade50,
                shape: BoxShape.circle,
              ),
              child: Icon(
                isWaitlist
                    ? Icons.hourglass_top_rounded
                    : Icons.check_circle_rounded,
                color: isWaitlist ? Colors.amber.shade800 : Colors.green,
                size: 54,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              isWaitlist ? 'Đã vào hàng đợi!' : 'Đăng ký thành công!',
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              isWaitlist
                  ? 'Trạng thái: Hàng đợi đặt trước (WAITLIST)'
                  : 'Phiếu mượn: #${newLoan['ticketId'] ?? '—'}',
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                color: Color(0xFF0284c7),
                fontSize: 15,
              ),
            ),
            if (!isWaitlist) ...[
              const SizedBox(height: 8),
              Text(
                'Hạn đến quầy nhận sách: $pickupDisplay\nHạn trả dự kiến: $dueDisplay',
                style: const TextStyle(fontSize: 13, color: Colors.black87),
                textAlign: TextAlign.center,
              ),
            ],
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.amber.shade50,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.amber.shade200),
              ),
              child: Text(
                (backendMessage != null && backendMessage.isNotEmpty)
                    ? backendMessage
                    : (isWaitlist
                          ? 'Hệ thống đã ghi nhận yêu cầu vào hàng đợi đặt trước và sẽ thông báo ngay khi có bản sao được hoàn trả.'
                          : 'Vui lòng mang thẻ sinh viên đến Quầy thủ thư Tầng 2 trước $pickupDisplay để nhận sách.'),
                style: const TextStyle(fontSize: 12, color: Colors.black87),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 20),
            if (!isWaitlist && ticketId != null) ...[
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  key: const Key('show-ticket-qr-after-reservation'),
                  onPressed: () async {
                    Navigator.pop(dialogCtx);
                    await showTicketQrDialog(
                      context: context,
                      ticketId: ticketId,
                    );
                    if (mounted) {
                      await _refreshAllBookAndLoanState();
                    }
                  },
                  icon: const Icon(Icons.qr_code_2),
                  label: const Text('Hiển thị QR nhận sách'),
                ),
              ),
              const SizedBox(height: 10),
            ],
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(dialogCtx),
                    style: OutlinedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text('Đóng'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () async {
                      Navigator.pop(dialogCtx);
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const BorrowedScreen(),
                        ),
                      );
                      if (mounted) {
                        await _refreshAllBookAndLoanState();
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0284c7),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text('Xem sách mượn'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final book = _bookData;
    final title = (book['title'] ?? 'Tài liệu thư viện').toString();
    final author = (book['author'] ?? 'Chưa rõ tác giả').toString();
    final publisher = (book['publisher'] ?? 'Chưa cập nhật nhà xuất bản')
        .toString();
    final year = book['publishYear']?.toString() ?? 'Chưa cập nhật';
    final category = _resolveCategory(book);
    final department = _resolveDepartment(book, category);
    final availableCopies = (book['availableCopies'] as num?)?.toInt() ?? 0;
    final totalCopies =
        (book['copies'] as num?)?.toInt() ??
        (availableCopies > 0 ? availableCopies : 0);
    final isDigital = book['isDigital'] == true;
    final isAvailable = availableCopies > 0;
    final supportsWaitlist = !isDigital && totalCopies > 0;
    final imageUrl =
        book['imageUrlL'] ??
        book['imageUrlM'] ??
        book['imageUrlS'] ??
        book['coverUrl'];
    final shelfLocation =
        (book['location']?.toString().trim().isNotEmpty ?? false)
        ? book['location'].toString().trim()
        : 'Chưa xếp vị trí';

    final reachedThreeDocLimit = _remainingSlots <= 0;
    final monetaryBlocked =
        _eligibilityLoaded && (!_priceKnown || !_monetaryEligible);
    final canRegisterBorrow =
        !_hasActiveLoanForBook &&
        !_hasActiveWaitlistForBook &&
        !reachedThreeDocLimit &&
        !monetaryBlocked &&
        (isAvailable || _hasNotifiedWaitlistHold);
    final canJoinWaitlist =
        !_hasActiveLoanForBook &&
        !_hasActiveWaitlistForBook &&
        !reachedThreeDocLimit &&
        !monetaryBlocked &&
        !isAvailable &&
        !_hasNotifiedWaitlistHold &&
        supportsWaitlist;
    final canSubmitAction = canRegisterBorrow || canJoinWaitlist;

    String buttonLabel;
    String? helperBannerText;
    Color helperBannerColor = Colors.blue.shade50;
    Color helperTextColor = Colors.blue.shade900;

    if (_hasActiveLoanForBook) {
      buttonLabel = 'ĐÃ ĐĂNG KÝ / ĐANG MƯỢN ĐẦU SÁCH NÀY';
      helperBannerText =
          'Bạn đã có đầu sách này trong danh sách đang mượn hoặc chờ nhận tại quầy. Không thể gửi nhiều yêu cầu cho cùng một đầu sách.';
      helperBannerColor = Colors.amber.shade50;
      helperTextColor = Colors.amber.shade900;
    } else if (_hasActiveWaitlistForBook) {
      buttonLabel = 'ĐÃ TRONG HÀNG ĐỢI ĐẶT TRƯỚC';
      helperBannerText =
          'Bạn đã có tên trong hàng đợi đặt trước của đầu sách này. Thư viện sẽ thông báo ngay khi có bản sao được hoàn trả.';
      helperBannerColor = Colors.amber.shade50;
      helperTextColor = Colors.amber.shade900;
    } else if (reachedThreeDocLimit) {
      buttonLabel = 'ĐÃ ĐẠT GIỚI HẠN $_maxOpenItems TÀI LIỆU';
      helperBannerText =
          _eligibilityMessage ??
          'Bạn đã đạt giới hạn tối đa $_maxOpenItems tài liệu đang mượn/đặt giữ ($_openItemsCount/$_maxOpenItems). Vui lòng trả sách hoặc hủy một phiếu đặt giữ trước khi đăng ký thêm.';
      helperBannerColor = Colors.red.shade50;
      helperTextColor = Colors.red.shade900;
    } else if (monetaryBlocked) {
      buttonLabel = !_priceKnown
          ? 'TÀI LIỆU CHƯA ĐƯỢC XÁC ĐỊNH GIÁ'
          : 'KHÔNG ĐỦ HẠN MỨC TIỀN THẾ CHÂN';
      helperBannerText =
          _eligibilityMessage ??
          'Tài khoản chưa đáp ứng hạn mức giá trị để đăng ký tài liệu này.';
      helperBannerColor = Colors.red.shade50;
      helperTextColor = Colors.red.shade900;
    } else if (!isAvailable && !_hasNotifiedWaitlistHold && !supportsWaitlist) {
      buttonLabel = 'KHÔNG CÒN BẢN SAO ĐỂ MƯỢN';
      helperBannerText = isDigital
          ? 'Tài liệu số không hỗ trợ đăng ký mượn bản in hoặc hàng đợi đặt trước.'
          : 'Đầu sách hiện không còn bản sao vật lý lưu thông và không hỗ trợ hàng đợi.';
      helperBannerColor = Colors.red.shade50;
      helperTextColor = Colors.red.shade900;
    } else if (canJoinWaitlist) {
      buttonLabel = 'ĐĂNG KÝ HÀNG ĐỢI (WAITLIST)';
      helperBannerText =
          'Cả $totalCopies bản sao hiện đang được mượn hoặc giữ chỗ. Bạn có thể đăng ký vào Hàng đợi đặt trước để được ưu tiên khi có người trả sách.';
      helperBannerColor = Colors.orange.shade50;
      helperTextColor = Colors.orange.shade900;
    } else {
      buttonLabel = 'ĐĂNG KÝ MƯỢN SÁCH';
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Chi tiết sách',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            tooltip: 'Làm mới dữ liệu sách',
            icon: _refreshingBook
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh),
            onPressed: _refreshingBook ? null : _refreshAllBookAndLoanState,
          ),
          IconButton(
            key: const ValueKey('detail-save-book-button'),
            tooltip: _isSaved ? 'Bỏ lưu' : 'Lưu sách',
            icon: Icon(
              _isSaved ? Icons.bookmark : Icons.bookmark_outline,
              color: _isSaved ? const Color(0xFF0284c7) : null,
            ),
            onPressed: _bookId == null ? null : _toggleSaved,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refreshAllBookAndLoanState,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Cover Image Header with BookCover fallback
              Container(
                width: double.infinity,
                height: 250,
                color: const Color(0xFF0f172a).withValues(alpha: 0.04),
                child: Center(
                  child: Hero(
                    tag: 'book_cover_${book['isbn'] ?? title}',
                    child: BookCover(
                      imageUrl: imageUrl?.toString(),
                      title: title,
                      author: author,
                      width: 165,
                      height: 220,
                      borderRadius: 12,
                    ),
                  ),
                ),
              ),

              // Book Info
              Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            title,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w900,
                              height: 1.2,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: isAvailable
                                ? Colors.green.shade50
                                : Colors.red.shade50,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isAvailable
                                  ? Colors.green.shade200
                                  : Colors.red.shade200,
                            ),
                          ),
                          child: Text(
                            isAvailable ? 'Có sẵn' : 'Đã mượn hết',
                            style: TextStyle(
                              color: isAvailable
                                  ? Colors.green.shade700
                                  : Colors.red.shade700,
                              fontWeight: FontWeight.bold,
                              fontSize: 12,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(
                      'Tác giả: $author',
                      style: const TextStyle(fontSize: 16, color: Colors.grey),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      'Năm xuất bản: $year | $publisher',
                      style: const TextStyle(fontSize: 14, color: Colors.grey),
                    ),
                    const SizedBox(height: 12),

                    // Thể loại & Chuyên ngành chips
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(
                              0xFF0284c7,
                            ).withValues(alpha: 0.08),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            'Thể loại: $category',
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF0284c7),
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.teal.shade50,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            'Chuyên ngành: $department',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Colors.teal.shade800,
                            ),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 20),

                    // Location & Copies Card
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.grey.shade200),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.04),
                            blurRadius: 10,
                            offset: const Offset(0, 5),
                          ),
                        ],
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.location_on,
                            color: Color(0xFF0284c7),
                            size: 32,
                          ),
                          const SizedBox(width: 15),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'VỊ TRÍ LƯU TRỮ (TẦNG / KỆ / NGĂN)',
                                  style: TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.grey,
                                  ),
                                ),
                                const SizedBox(height: 5),
                                Text(
                                  shelfLocation,
                                  style: const TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Mã định danh: ISBN ${book['isbn'] ?? 'Chưa cập nhật'}',
                                  style: const TextStyle(fontSize: 14),
                                ),
                                Text(
                                  'Bản sao sẵn sàng: $availableCopies/$totalCopies',
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),

                    if (helperBannerText != null) ...[
                      const SizedBox(height: 16),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: helperBannerColor,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              Icons.info_outline_rounded,
                              size: 18,
                              color: helperTextColor,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                helperBannerText,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: helperTextColor,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],

                    const SizedBox(height: 24),
                    const Text(
                      'TÓM TẮT',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      (book['description'] ??
                              book['summary'] ??
                              'Tài liệu học tập và nghiên cứu được lưu trữ tại Thư viện Trường Đại học Công Thương TP. Hồ Chí Minh (HUIT).')
                          .toString(),
                      style: const TextStyle(
                        fontSize: 15,
                        height: 1.6,
                        color: Colors.black87,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: SizedBox(
            height: 55,
            child: ElevatedButton(
              onPressed: canSubmitAction
                  ? () {
                      _showBorrowModal(
                        title: title,
                        author: author,
                        category: category,
                        department: department,
                        shelfLocation: shelfLocation,
                        isJoiningWaitlist: canJoinWaitlist,
                      );
                    }
                  : null,
              style: ElevatedButton.styleFrom(
                backgroundColor: canJoinWaitlist
                    ? Colors.orange.shade700
                    : const Color(0xFF0284c7),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: Text(
                buttonLabel,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
