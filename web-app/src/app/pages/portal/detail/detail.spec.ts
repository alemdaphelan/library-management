import { of, Subject, throwError } from 'rxjs';

import { Detail } from './detail';

describe('Detail review policy', () => {
  const circulationService = {
    reserveBook: vi.fn(),
    getMyBorrowEligibility: vi.fn(() =>
      of({
        maximumItems: 3,
        openItems: 3,
        remainingSlots: 0,
        eligible: false,
        message: 'Bạn đã đủ 3 tài liệu đang hoạt động.',
      }),
    ),
  };
  const borrowCartService = {
    count: 0,
    add: vi.fn(() => 'ADDED'),
    setMaximumItems: vi.fn(),
  };
  const policyService = {
    getPublicPolicy: vi.fn(() => of({ homeLoanDays: 10, waitlistHoldHours: 48 })),
  };

  const createComponent = (saveReview: ReturnType<typeof vi.fn>) =>
    new Detail(
      {} as any,
      {} as any,
      {
        saveReview,
        deleteMyReview: vi.fn(() => of(void 0)),
        getReviews: vi.fn(),
        getWishlistStatus: vi.fn(),
      } as any,
      {} as any,
      { isLoggedIn: () => true } as any,
      circulationService as any,
      borrowCartService as any,
      policyService as any,
      { detectChanges: vi.fn() } as any,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    borrowCartService.count = 0;
  });

  it('does not send a review when backend eligibility says the reader has not borrowed the book', () => {
    const saveReview = vi.fn();
    const component = createComponent(saveReview);
    component.book = { id: 7 };
    component.reviewEligible = false;
    component.reviewEligibilityMessage =
      'Bạn chỉ có thể đánh giá sau khi tài liệu đã được thủ thư giao mượn';

    component.submitReview();

    expect(saveReview).not.toHaveBeenCalled();
    expect(component.reviewErrorMessage).toContain('sau khi tài liệu đã được thủ thư giao mượn');
  });

  it('shows an explicit message instead of swallowing a second-review conflict', () => {
    const saveReview = vi.fn(() =>
      throwError(() => ({
        status: 409,
        error: { message: 'No message available' },
      })),
    );
    const component = createComponent(saveReview);
    component.book = { id: 7 };
    component.reviewEligible = true;

    component.submitReview();

    expect(component.reviewErrorMessage).toContain('đã đánh giá tài liệu này');
    expect(component.isReviewSubmitting).toBe(false);
  });

  it('explains when a book has no published PDF instead of showing a fake connection toast', () => {
    const component = createComponent(vi.fn());
    component.book = { id: 7 };
    component.digitalAssets = [];

    component.previewEbook();

    expect(component.toastMessage).toContain('chưa có bản PDF');
  });

  it('does not add another book when the backend reports all three slots are occupied', () => {
    const component = createComponent(vi.fn());
    component.book = {
      id: 7,
      title: 'Clean Code',
      author: 'Robert C. Martin',
      cover: '',
      availableCopies: 1,
    };

    component.addToBorrowCart();

    expect(circulationService.getMyBorrowEligibility).toHaveBeenCalled();
    expect(borrowCartService.add).not.toHaveBeenCalled();
    expect(component.toastMessage).toContain('đủ 3 tài liệu');
  });

  it('renders a delayed reservation result immediately and blocks duplicate submits', () => {
    const pending = new Subject<any>();
    circulationService.reserveBook.mockReturnValueOnce(pending);
    const c = createComponent(vi.fn());
    c.book = { id: 7, availableCopies: 2 };
    c.pickupDate = '2026-11-04';
    c.generateQrCode = vi.fn();
    c.submitBorrowRequest();
    c.submitBorrowRequest();
    expect(circulationService.reserveBook).toHaveBeenCalledTimes(1);
    expect(c.isSubmittingBorrow).toBe(true);
    pending.next({ success: true, status: 'RESERVED', ticketId: 15 });
    pending.complete();
    expect(c.isSubmittingBorrow).toBe(false);
    expect(c.borrowSuccessData?.ticketId).toBe(15);
    expect(c.book.availableCopies).toBe(1);
    expect(c.generateQrCode).toHaveBeenCalledWith(15);
    c.submitBorrowRequest();
    expect(circulationService.reserveBook).toHaveBeenCalledTimes(1);
  });
  it('clears the spinner after a delayed reservation failure', () => {
    const pending = new Subject<any>();
    circulationService.reserveBook.mockReturnValueOnce(pending);
    const c = createComponent(vi.fn());
    c.book = { id: 7 };
    c.submitBorrowRequest();
    pending.error({ status: 409, error: { detail: 'Thẻ bị khóa' } });
    expect(c.isSubmittingBorrow).toBe(false);
    expect(c.borrowErrorMessage).toBe('Thẻ bị khóa');
  });
});
