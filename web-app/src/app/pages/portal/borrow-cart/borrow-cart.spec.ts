import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { BorrowCartService } from '../../../services/borrow-cart.service';
import { BatchBorrowResponse, CirculationService } from '../../../services/circulation.service';
import { LibraryPolicyService } from '../../../services/library-policy.service';
import { BorrowCart } from './borrow-cart';

describe('Borrow cart request completion', () => {
  let fixture: ComponentFixture<BorrowCart>, component: BorrowCart;
  const key = 'huit_lib_pending_borrow:f02-reader';
  const item = { bookId: 7, title: 'Giáo trình F02', availableCopies: 1 };
  const result: BatchBorrowResponse = { success: true, ticketId: 19, status: 'RESERVED', ticketType: 'BORROW', message: 'Đã giữ sách',
    pickupDeadline: '2026-11-04T17:00:00', dueDate: '2026-11-14T17:00:00', bookIds: [7], bookTitles: [item.title], barcodes: ['F02-COPY'] };
  let cart: { allItems: typeof item[]; clear: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn>; setMaximumItems: ReturnType<typeof vi.fn> };
  let api: { reserveBooks: ReturnType<typeof vi.fn>; getMyBorrowEligibility: ReturnType<typeof vi.fn> };
  beforeEach(async () => {
    sessionStorage.removeItem(key);
    cart = { allItems: [item], clear: vi.fn(() => cart.allItems=[]), remove: vi.fn(), setMaximumItems: vi.fn() };
    api = { reserveBooks: vi.fn(), getMyBorrowEligibility: vi.fn(() => of({maximumItems:3,openItems:0,remainingSlots:3,eligible:true,message:'Đủ điều kiện'})) };
    await TestBed.configureTestingModule({imports:[BorrowCart],providers:[provideRouter([]),
      {provide:BorrowCartService,useValue:cart},{provide:CirculationService,useValue:api},
      {provide:AuthService,useValue:{getUserProfile:()=>({userId:'f02-reader'})}},
      {provide:LibraryPolicyService,useValue:{getPublicPolicy:()=>of({maximumOpenItems:3,homeLoanDays:10,waitlistHoldHours:48})}},
    ]}).compileComponents();
    fixture=TestBed.createComponent(BorrowCart);component=fixture.componentInstance;fixture.detectChanges();await fixture.whenStable();
  });
  afterEach(()=>{fixture.destroy();sessionStorage.removeItem(key);vi.useRealTimers();});

  it('blocks reservation while the reader is waiting for card activation', async () => {
    api.getMyBorrowEligibility.mockReturnValueOnce(of({maximumItems: 3, openItems: 0, remainingSlots: 3,
      eligible: false, cardEligible: false, message: 'Vui lòng hoàn thành lớp hướng dẫn thư viện'}));
    component.loadEligibility();
    await fixture.whenStable();

    component.submit();
    expect(api.reserveBooks).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.eligibility-note').textContent).toContain('lớp hướng dẫn');
  });

  it('shows a valid delayed response without waiting for another request, completion or user click',async()=>{
    const pending=new Subject<BatchBorrowResponse>();api.reserveBooks.mockReturnValueOnce(pending);
    component.submit();component.submit();expect(api.reserveBooks).toHaveBeenCalledTimes(1);expect(component.isSubmitting).toBe(true);
    pending.next(result);await fixture.whenStable();
    expect(component.isSubmitting).toBe(false);expect(cart.clear).toHaveBeenCalledTimes(1);expect(sessionStorage.getItem(key)).toBeNull();
    expect(fixture.nativeElement.querySelector('.success-card').textContent).toContain('Tạo phiếu #19 thành công');
    expect(api.getMyBorrowEligibility).toHaveBeenCalledTimes(1);component.submit();expect(api.reserveBooks).toHaveBeenCalledTimes(1);
  });
  it('preserves the cart and releases the button after a definite rejection',async()=>{
    api.reserveBooks.mockReturnValueOnce(throwError(()=>({status:409,error:{detail:'Đầu sách đã có trong phiếu đang hoạt động'}})));
    component.submit();await fixture.whenStable();
    expect(component.isSubmitting).toBe(false);expect(component.requiresReview).toBe(false);expect(cart.clear).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(false);expect(component.errorMessage).toContain('Đầu sách đã có');
    expect(sessionStorage.getItem(key)).toBeNull();
  });
  it.each([0,408,500,200])('requires reviewing history after an ambiguous HTTP %i outcome without retrying',async status=>{
    api.reserveBooks.mockReturnValueOnce(throwError(()=>({status,error:'Network/response error'})));
    component.submit();await fixture.whenStable();component.submit();
    expect(component.requiresReview).toBe(true);expect(component.isSubmitting).toBe(false);expect(cart.clear).not.toHaveBeenCalled();
    expect(api.reserveBooks).toHaveBeenCalledTimes(1);expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('a[href="/history"]')).toBeTruthy();expect(sessionStorage.getItem(key)).toBeTruthy();
  });
  it('does not claim success or throw a template error for a malformed HTTP 200 body',async()=>{
    api.reserveBooks.mockReturnValueOnce(of({success:true,ticketId:19,status:'RESERVED'}));component.submit();await fixture.whenStable();
    expect(component.result).toBeNull();expect(component.requiresReview).toBe(true);expect(component.isSubmitting).toBe(false);expect(cart.clear).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.success-card')).toBeNull();
  });
  it('ends a never-returning POST after 30 seconds without resending it',async()=>{
    vi.useFakeTimers();const pending=new Subject<BatchBorrowResponse>();api.reserveBooks.mockReturnValueOnce(pending);
    component.submit();await vi.advanceTimersByTimeAsync(30001);
    expect(component.isSubmitting).toBe(false);expect(component.requiresReview).toBe(true);expect(pending.observed).toBe(false);
    expect(api.reserveBooks).toHaveBeenCalledTimes(1);expect(cart.clear).not.toHaveBeenCalled();
  });
  it('locks editing while sending and cancels callbacks when navigating away, keeping the review marker',async()=>{
    const pending=new Subject<BatchBorrowResponse>();api.reserveBooks.mockReturnValueOnce(pending);component.submit();
    fixture.changeDetectorRef.markForCheck();fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#cart-note').disabled).toBe(true);expect(fixture.nativeElement.querySelector('.remove-button').disabled).toBe(true);
    component.remove(7);expect(cart.remove).not.toHaveBeenCalled();fixture.destroy();
    expect(pending.observed).toBe(false);expect(sessionStorage.getItem(key)).toBeTruthy();
    fixture=TestBed.createComponent(BorrowCart);component=fixture.componentInstance;fixture.detectChanges();await fixture.whenStable();
    expect(component.requiresReview).toBe(true);component.submit();expect(api.reserveBooks).toHaveBeenCalledTimes(1);
    component.unlockAfterReview();expect(component.requiresReview).toBe(false);expect(sessionStorage.getItem(key)).toBeNull();
  });
  it('ends a stuck eligibility check and allows a read-only retry',async()=>{
    vi.useFakeTimers();const pending=new Subject<any>();api.getMyBorrowEligibility.mockReturnValueOnce(pending);component.loadEligibility();
    await vi.advanceTimersByTimeAsync(15001);expect(component.isCheckingEligibility).toBe(false);expect(component.eligibilityError).toBeTruthy();
    vi.useRealTimers();component.loadEligibility();expect(component.eligibilityError).toBe('');expect(component.eligibility?.remainingSlots).toBe(3);
    expect(api.reserveBooks).not.toHaveBeenCalled();
  });
});
