import { of, throwError } from 'rxjs';

import { Detail } from './detail';

describe('Detail review policy', () => {
  const circulationService = {
    getMyBorrowEligibility: vi.fn(() => of({
      maximumItems: 3,
      openItems: 3,
      remainingSlots: 0,
      eligible: false,
      message: 'Bạn đã đủ 3 tài liệu đang hoạt động.',
    })),
  };
  const borrowCartService = {
    count: 0,
    add: vi.fn(() => 'ADDED'),
  };

  const createComponent = (saveReview: ReturnType<typeof vi.fn>) => new Detail(
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
    component.reviewEligibilityMessage = 'Bạn chỉ có thể đánh giá sau khi tài liệu đã được thủ thư giao mượn';

    component.submitReview();

    expect(saveReview).not.toHaveBeenCalled();
    expect(component.reviewErrorMessage).toContain('sau khi tài liệu đã được thủ thư giao mượn');
  });

  it('shows an explicit message instead of swallowing a second-review conflict', () => {
    const saveReview = vi.fn(() => throwError(() => ({
      status: 409,
      error: { message: 'No message available' },
    })));
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
});
