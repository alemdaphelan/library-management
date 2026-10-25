import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable, Subscription } from 'rxjs';
import * as QRCode from 'qrcode';
import { BookReview, BookReviewSummary, BookService } from '../../../services/book.service';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';
import { AuthService } from '../../../services/auth.service';
import { CirculationService, BorrowResponse } from '../../../services/circulation.service';
import { BorrowCartService } from '../../../services/borrow-cart.service';

@Component({
  selector: 'app-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './detail.html',
  styleUrls: ['./detail.css', './detail-reviews.css']
})
export class Detail implements OnInit, OnDestroy {
  isLoading = true;
  hasError = false;
  errorMessage = '';
  bookId: string | null = null;
  book: any = null;
  toastMessage: string | null = null;
  isBookSaved = false;
  isWishlistBusy = false;
  reviews: BookReview[] = [];
  reviewRating = 5;
  reviewComment = '';
  isReviewSubmitting = false;
  reviewErrorMessage = '';
  reviewEligible = false;
  reviewEligibilityMessage = '';
  areReviewsLoaded = false;
  digitalAssets: DigitalAsset[] = [];
  digitalAssetsLoading = false;
  isPreviewingEbook = false;

  // Reservation / Borrow Modal State
  showBorrowModal = false;
  showLoginPromptModal = false;
  isSubmittingBorrow = false;
  borrowType: 'BORROW' | 'ONSITE' = 'BORROW';
  pickupDate: string = '';
  minPickupDate: string = '';
  maxPickupDate: string = '';
  borrowNote: string = '';
  borrowSuccessData: BorrowResponse | null = null;
  borrowErrorMessage: string = '';
  qrCodeDataUrl: string | null = null;

  private routeSub?: Subscription;
  private toastTimer?: any;

  get hasMyReview(): boolean {
    return this.reviews.some(review => review.mine);
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private bookService: BookService,
    private digitalAssetsService: DigitalAssetsService,
    private authService: AuthService,
    private circulationService: CirculationService,
    private borrowCartService: BorrowCartService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    window.scrollTo(0, 0);

    this.routeSub = this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      this.bookId = id;
      if (id) {
        this.fetchBookDetail(id);
      } else {
        this.hasError = true;
        this.errorMessage = 'Mã sách không hợp lệ.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy() {
    if (this.routeSub) {
      this.routeSub.unsubscribe();
    }
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
  }

  fetchBookDetail(id: string) {
    this.isLoading = true;
    this.hasError = false;
    this.errorMessage = '';
    this.book = null;

    this.bookService.getBookById(id).subscribe({
      next: (data) => {
        if (!data) {
          this.hasError = true;
          this.errorMessage = 'Không tìm thấy thông tin cuốn sách này.';
          this.isLoading = false;
          this.cdr.detectChanges();
          return;
        }

        this.book = this.transformBookData(data);
        this.isLoading = false;
        this.loadReviews(Number(this.book.id));
        if (this.authService.isLoggedIn()) {
          this.loadWishlistStatus(Number(this.book.id));
          this.loadDigitalAssets(Number(this.book.id));
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching book detail:', err);
        this.hasError = true;
        this.errorMessage = 'Không thể tải thông tin cuốn sách. Vui lòng kiểm tra lại đường dẫn hoặc mã định danh sách.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private transformBookData(data: any): any {
    const rawId = Number(data.bookId || data.id) || 1;
    const catName = data.category?.name || 'Tài liệu chung';
    const catId = data.category?.categoryId;
    
    // Choose cover image with fallbacks
    const coverUrl = data.imageUrlL || data.imageUrlM || data.imageUrlS || '/assets/images/cover_1.jpg';

    // Format publisher and year
    let publisherStr = data.publisher || 'Nhà xuất bản Tổng hợp';
    if (data.publishYear) {
      publisherStr += ` (${data.publishYear})`;
    }

    // Call number & real shelf location returned by the catalog API
    const callNo = data.ddcCallNumber || (data.isbn ? `DDC ${data.isbn.slice(0, 3)}` : 'DDC 004 HUIT');
    const shelf = data.location || 'Chưa xếp vị trí';

    const availableCopies = Number(data.availableCopies || 0);

    const pages = data.pages || null;
    const description = data.description || 'Nội dung tài liệu đang được thư viện cập nhật.';

    return {
      id: rawId,
      title: data.title,
      subtitle: data.subtitle || catName,
      author: data.author || 'Đang cập nhật',
      coAuthor: data.coAuthor || null,
      category: catName,
      categoryId: catId,
      publisher: publisherStr,
      rawPublisher: data.publisher,
      publishYear: data.publishYear,
      isbn: data.isbn || 'Đang cập nhật',
      pages: pages,
      cover: coverUrl,
      imgError: false,
      description: description,
      callNo: callNo,
      shelf: shelf,
      availableCopies: availableCopies,
      totalCopies: Number(data.copies || 0),
      isDigital: !!data.isDigital,
      rating: Number(data.rating || 0),
      reviewsCount: Number(data.reviewsCount || 0),
      borrowCount: Number(data.borrowCount || 0),
      viewCount: Number(data.viewCount || 0),
      language: data.language || 'Tiếng Việt'
    };
  }

  onImageError() {
    if (this.book) {
      this.book.imgError = true;
    }
  }

  showToast(message: string) {
    this.toastMessage = message;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.toastMessage = null;
      this.cdr.detectChanges();
    }, 3500);
  }

  // --- BORROW / RESERVATION MODAL HANDLING ---

  addToBorrowCart() {
    if (!this.authService.isLoggedIn()) {
      this.showLoginPromptModal = true;
      return;
    }
    if (!this.book || this.book.availableCopies <= 0) {
      this.showToast('Tài liệu hiện hết bản sẵn sàng. Hãy dùng Mượn ngay để đăng ký hàng đợi.');
      return;
    }
    const result = this.borrowCartService.add({
      bookId: Number(this.book.id),
      title: this.book.title,
      author: this.book.author,
      cover: this.book.cover,
      availableCopies: this.book.availableCopies,
    });
    if (result === 'FULL') {
      this.showToast('Giỏ mượn đã đủ 3 tài liệu. Vui lòng hoàn tất phiếu hiện tại.');
    } else if (result === 'DUPLICATE') {
      this.showToast('Đầu sách này đã có trong giỏ mượn.');
    } else {
      this.showToast('Đã thêm vào giỏ mượn. Bạn có thể chọn thêm tối đa 3 tài liệu.');
    }
  }

  openBorrowModal() {
    // 1. Kiểm tra trạng thái đăng nhập
    if (!this.authService.isLoggedIn()) {
      this.showLoginPromptModal = true;
      return;
    }

    // 2. Tính toán ngày nhận sách hợp lệ
    const today = new Date();
    const minStr = this.formatLocalDate(today);

    this.minPickupDate = minStr;
    this.maxPickupDate = minStr;
    this.pickupDate = minStr;

    // Reset form state
    this.borrowType = 'BORROW';
    this.borrowNote = '';
    this.borrowSuccessData = null;
    this.borrowErrorMessage = '';
    this.qrCodeDataUrl = null;
    this.showBorrowModal = true;
  }

  private formatLocalDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  closeBorrowModal() {
    this.showBorrowModal = false;
    this.showLoginPromptModal = false;
    this.borrowSuccessData = null;
    this.borrowErrorMessage = '';
    this.qrCodeDataUrl = null;
  }

  redirectToLogin() {
    this.closeBorrowModal();
    this.router.navigate(['/login'], {
      queryParams: { returnUrl: `/book/${this.book?.id}` }
    });
  }

  submitBorrowRequest() {
    if (!this.book || !this.book.id) return;

    this.isSubmittingBorrow = true;
    this.borrowErrorMessage = '';
    this.qrCodeDataUrl = null;

    const payload = {
      bookId: Number(this.book.id),
      ticketType: this.borrowType,
      pickupDate: this.pickupDate,
      note: this.borrowNote.trim()
    };

    this.circulationService.reserveBook(payload).subscribe({
      next: (res) => {
        this.isSubmittingBorrow = false;
        this.borrowSuccessData = res;

        // Cập nhật số bản sách sẵn có trên giao diện nếu mượn thành công
        if (res.success && res.status !== 'WAITLIST' && this.book.availableCopies > 0) {
          this.book.availableCopies = Math.max(0, this.book.availableCopies - 1);
        }

        // Tự động tạo mã QR cho phiếu mượn
        if (res.ticketId) {
          this.generateQrCode(res.ticketId);
        }

        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isSubmittingBorrow = false;
        console.error('Error submitting borrow reservation:', err);

        this.borrowErrorMessage = this.extractBorrowError(err);
        this.cdr.detectChanges();
      }
    });
  }

  private extractBorrowError(err: any): string {
    if (err?.status === 401 || err?.status === 403) {
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại tài khoản sinh viên/bạn đọc.';
    }
    const body = err?.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    const message = body?.detail ?? body?.message ?? body?.error;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    if (err?.status === 409) {
      return 'Yêu cầu mượn không đáp ứng quy định hiện tại. Vui lòng kiểm tra số sách đang mượn, công nợ và trạng thái thẻ.';
    }
    return 'Không thể gửi yêu cầu đặt sách. Vui lòng thử lại!';
  }

  generateQrCode(ticketId: number | string) {
    this.circulationService.getTicketQrPayload(Number(ticketId)).subscribe({
      next: ({ payload }) => {
        QRCode.toDataURL(payload, {
          width: 220,
          margin: 1.5,
          color: {
            dark: '#0f172a',
            light: '#ffffff'
          },
          errorCorrectionLevel: 'M'
        }).then((url: string) => {
          this.qrCodeDataUrl = url;
          this.cdr.detectChanges();
        }).catch((err: any) => this.handleQrError(err));
      },
      error: (err) => this.handleQrError(err),
    });
  }

  private handleQrError(err: unknown) {
    console.error('Lỗi tạo mã QR phiếu mượn:', err);
    this.borrowErrorMessage = 'Đã tạo phiếu nhưng chưa thể tạo QR bảo mật. Bạn có thể mở lại QR trong Lịch sử mượn.';
    this.cdr.detectChanges();
  }

  downloadQrCode() {
    if (!this.qrCodeDataUrl || !this.borrowSuccessData?.ticketId) return;
    const link = document.createElement('a');
    link.href = this.qrCodeDataUrl;
    link.download = `HUIT-PhieuMuon-${this.borrowSuccessData.ticketId}.png`;
    link.click();
    this.showToast('Đã tải hình ảnh mã QR phiếu mượn về máy!');
  }

  bookmarkBook() {
    if (!this.authService.isLoggedIn()) {
      this.showLoginPromptModal = true;
      return;
    }
    if (!this.book || this.isWishlistBusy) return;
    this.isWishlistBusy = true;
    const request: Observable<unknown> = this.isBookSaved
      ? this.bookService.removeFromWishlist(Number(this.book.id))
      : this.bookService.addToWishlist(Number(this.book.id));
    request.subscribe({
      next: () => {
        this.isBookSaved = !this.isBookSaved;
        this.isWishlistBusy = false;
        this.showToast(this.isBookSaved
          ? 'Đã thêm cuốn sách vào Tủ sách cá nhân.'
          : 'Đã bỏ cuốn sách khỏi Tủ sách cá nhân.');
        this.cdr.detectChanges();
      },
      error: () => {
        this.isWishlistBusy = false;
        this.showToast('Không thể cập nhật Tủ sách cá nhân. Vui lòng thử lại.');
        this.cdr.detectChanges();
      },
    });
  }

  submitReview() {
    if (!this.authService.isLoggedIn()) {
      this.showLoginPromptModal = true;
      return;
    }
    if (!this.book || this.isReviewSubmitting) return;
    if (!this.reviewEligible || this.hasMyReview) {
      this.reviewErrorMessage = this.reviewEligibilityMessage || 'Bạn không đủ điều kiện gửi đánh giá này.';
      return;
    }
    this.isReviewSubmitting = true;
    this.reviewErrorMessage = '';
    this.bookService.saveReview(Number(this.book.id), this.reviewRating, this.reviewComment.trim()).subscribe({
      next: summary => {
        this.applyReviewSummary(summary);
        this.isReviewSubmitting = false;
        this.showToast('Đã ghi nhận đánh giá của bạn.');
        this.cdr.detectChanges();
      },
      error: error => {
        this.isReviewSubmitting = false;
        this.reviewErrorMessage = this.resolveApiError(error, 'Không thể lưu đánh giá.');
        this.cdr.detectChanges();
      },
    });
  }

  deleteMyReview() {
    if (!this.book || this.isReviewSubmitting) return;
    this.isReviewSubmitting = true;
    this.bookService.deleteMyReview(Number(this.book.id)).subscribe({
      next: () => {
        this.reviewRating = 5;
        this.reviewComment = '';
        this.isReviewSubmitting = false;
        this.loadReviews(Number(this.book.id));
        this.showToast('Đã xóa đánh giá của bạn.');
      },
      error: () => {
        this.isReviewSubmitting = false;
        this.reviewErrorMessage = 'Không thể xóa đánh giá.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadReviews(bookId: number) {
    this.areReviewsLoaded = false;
    this.bookService.getReviews(bookId).subscribe({
      next: summary => {
        this.applyReviewSummary(summary);
        this.cdr.detectChanges();
      },
      error: () => {
        this.reviews = [];
        this.reviewErrorMessage = 'Chưa thể tải danh sách đánh giá.';
        this.reviewEligibilityMessage = this.reviewErrorMessage;
        this.areReviewsLoaded = true;
        this.cdr.detectChanges();
      },
    });
  }

  private applyReviewSummary(summary: BookReviewSummary) {
    this.reviews = summary.reviews || [];
    this.reviewEligible = !!summary.reviewEligible;
    this.reviewEligibilityMessage = summary.reviewEligibilityMessage || '';
    this.reviewErrorMessage = '';
    this.areReviewsLoaded = true;
    if (this.book) {
      this.book.rating = Number(summary.averageRating || 0);
      this.book.reviewsCount = Number(summary.reviewCount || 0);
    }
    if (summary.myReview) {
      this.reviewRating = summary.myReview.rating;
      this.reviewComment = summary.myReview.comment || '';
    }
  }

  private resolveApiError(error: any, fallback: string): string {
    if (typeof error?.error === 'string' && error.error.trim()) {
      return error.error;
    }
    const apiMessage = error?.error?.detail || error?.error?.message;
    if (apiMessage && apiMessage !== 'No message available') {
      return apiMessage;
    }
    if (error?.status === 409) {
      return 'Bạn đã đánh giá tài liệu này. Mỗi bạn đọc chỉ được gửi một đánh giá.';
    }
    if (error?.status === 403) {
      return 'Bạn chỉ có thể đánh giá sau khi tài liệu đã được thủ thư giao mượn.';
    }
    return fallback;
  }

  private loadWishlistStatus(bookId: number) {
    this.bookService.getWishlistStatus(bookId).subscribe({
      next: result => {
        this.isBookSaved = !!result.saved;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isBookSaved = false;
      },
    });
  }

  copyLink() {
    const url = window.location.href;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url).then(() => {
        this.showToast('Đã sao chép liên kết cuốn sách vào bộ nhớ tạm!');
      }).catch(() => {
        this.showToast('Đã sao chép liên kết cuốn sách!');
      });
    } else {
      this.showToast('Đã sao chép liên kết cuốn sách!');
    }
  }

  previewEbook() {
    if (!this.authService.isLoggedIn()) {
      this.showLoginPromptModal = true;
      return;
    }
    if (this.digitalAssetsLoading || this.isPreviewingEbook) return;
    const asset = this.digitalAssets.find(item => item.type?.toUpperCase() === 'PDF'
      || item.contentType === 'application/pdf');
    if (!asset) {
      this.showToast('Đầu sách này chưa có bản PDF được thư viện công bố.');
      return;
    }
    const previewWindow = window.open('', '_blank');
    if (!previewWindow) {
      this.showToast('Trình duyệt đang chặn cửa sổ đọc thử. Vui lòng cho phép mở tab mới.');
      return;
    }
    previewWindow.document.title = `Đang mở ${asset.name}`;
    this.isPreviewingEbook = true;
    this.digitalAssetsService.previewAsset(asset.id).subscribe({
      next: response => {
        this.isPreviewingEbook = false;
        if (!response.body) {
          previewWindow.close();
          this.showToast('Tệp E-book không có dữ liệu để hiển thị.');
          return;
        }
        const url = URL.createObjectURL(response.body);
        previewWindow.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000);
        this.cdr.detectChanges();
      },
      error: () => {
        this.isPreviewingEbook = false;
        previewWindow.close();
        this.showToast('Không thể mở E-book. Vui lòng kiểm tra quyền truy cập hoặc thử lại.');
        this.cdr.detectChanges();
      },
    });
  }

  private loadDigitalAssets(bookId: number) {
    this.digitalAssetsLoading = true;
    this.digitalAssets = [];
    this.digitalAssetsService.getAssetsByBook(bookId).subscribe({
      next: assets => {
        this.digitalAssets = assets || [];
        this.digitalAssetsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.digitalAssets = [];
        this.digitalAssetsLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  citeBook() {
    const citation = `${this.book?.author || 'Tác giả'}. (${this.book?.publishYear || 'n.d.'}). ${this.book?.title}. ${this.book?.rawPublisher || 'HUIT'}.`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(citation).then(() => {
        this.showToast('Đã sao chép trích dẫn APA vào bộ nhớ tạm!');
      }).catch(() => {
        this.showToast('Đã sao chép trích dẫn APA!');
      });
    } else {
      this.showToast('Đã sao chép trích dẫn APA!');
    }
  }
}
