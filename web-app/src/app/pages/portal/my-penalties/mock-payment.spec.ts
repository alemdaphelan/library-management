import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';
import { PenaltyService } from '../../../services/penalty.service';
import { MockPayment } from './mock-payment';

describe('MockPayment', () => {
  let fixture: ComponentFixture<MockPayment>;
  let component: MockPayment;
  const reference = 'MOCK-' + 'a'.repeat(64);
  const session: any = {
    reference,
    provider: 'MOCK',
    amount: 10000,
    purpose: 'DEBT',
    status: 'PENDING',
    expiresAt: '2026-11-05T10:00:00',
  };
  const api = {
    getPaymentCapabilities: vi.fn(),
    createMockPayment: vi.fn(),
    getMockPayment: vi.fn(),
    finishMockPayment: vi.fn(),
  };
  const dialog = { show: vi.fn() };
  beforeEach(async () => {
    vi.clearAllMocks();
    sessionStorage.clear();
    api.getPaymentCapabilities.mockReturnValue(of({ mockEnabled: true }));
    api.createMockPayment.mockReturnValue(of(session));
    api.getMockPayment.mockReturnValue(of(session));
    api.finishMockPayment.mockReturnValue(of({ ...session, status: 'CANCELLED' }));
    await TestBed.configureTestingModule({
      imports: [MockPayment],
      providers: [
        { provide: PenaltyService, useValue: api },
        { provide: AuthService, useValue: { getUserProfile: () => ({ userId: 'student-f05' }) } },
        { provide: DialogService, useValue: dialog },
      ],
    }).compileComponents();
  });
  afterEach(() => fixture?.destroy());
  function mount() {
    fixture = TestBed.createComponent(MockPayment);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('debt', 30000);
    fixture.detectChanges();
  }

  it('hides all controls when the server disables the feature or capability loading fails', () => {
    api.getPaymentCapabilities.mockReturnValueOnce(of({ mockEnabled: false }));
    mount();
    expect(fixture.nativeElement.querySelector('.mock-panel')).toBeNull();
    component.create();
    expect(api.createMockPayment).not.toHaveBeenCalled();
    fixture.destroy();
    api.getPaymentCapabilities.mockReturnValueOnce(throwError(() => ({ status: 404 })));
    mount();
    expect(fixture.nativeElement.querySelector('.mock-panel')).toBeNull();
  });

  it('labels test money clearly, validates whole amounts and keeps deposit distinct from debt', () => {
    mount();
    expect(fixture.nativeElement.textContent).toContain('GIẢ LẬP');
    component.amount = 1000.5;
    component.create();
    component.amount = 40000;
    component.create();
    expect(api.createMockPayment).not.toHaveBeenCalled();
    component.purpose = 'DEPOSIT';
    component.selectPurpose();
    expect(component.amount).toBe(50000);
    api.createMockPayment.mockReturnValueOnce(
      of({ ...session, purpose: 'DEPOSIT', amount: 50000 }),
    );
    component.create();
    expect(api.createMockPayment).toHaveBeenCalledWith(
      expect.objectContaining({ purpose: 'DEPOSIT', amount: 50000 }),
    );
    expect(component.session?.purpose).toBe('DEPOSIT');
  });

  it('locks create/finish while pending and emits a refresh only after confirmed success', () => {
    mount();
    component.amount = 10000;
    const pending = new Subject<any>();
    api.createMockPayment.mockReturnValueOnce(pending);
    const completed = vi.fn();
    component.completed.subscribe(completed);
    component.create();
    component.create();
    expect(api.createMockPayment).toHaveBeenCalledTimes(1);
    expect(component.busy).toBe(true);
    pending.next(session);
    pending.complete();
    expect(component.busy).toBe(false);
    expect(completed).not.toHaveBeenCalled();
    const finish = new Subject<any>();
    api.finishMockPayment.mockReturnValueOnce(finish);
    component.finish('SUCCESS');
    component.finish('SUCCESS');
    expect(api.finishMockPayment).toHaveBeenCalledTimes(1);
    finish.next({ ...session, status: 'COMPLETED', paymentTransactionId: 101 });
    finish.complete();
    expect(completed).toHaveBeenCalledTimes(1);
    expect(dialog.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Giả lập thanh toán thành công' }),
    );
    expect(sessionStorage.getItem('huit_lib_mock_payment:student-f05')).toBeNull();
    component.finish('SUCCESS');
    expect(api.finishMockPayment).toHaveBeenCalledTimes(1);
  });

  it('uses the terminal result from the server even if success was requested', () => {
    mount();
    component.amount = 10000;
    component.create();
    component.finish('SUCCESS');
    expect(component.session?.status).toBe('CANCELLED');
    expect(dialog.show).not.toHaveBeenCalled();
    expect(component.draft).toBeNull();
    component.reset();
    expect(component.session).toBeNull();
  });

  it('restores an ambiguous create with its original key without posting automatically on reload', () => {
    mount();
    component.amount = 10000;
    api.createMockPayment.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.create();
    const payload = api.createMockPayment.mock.calls[0][0];
    expect(component.draft).not.toBeNull();
    fixture.destroy();
    mount();
    expect(api.createMockPayment).toHaveBeenCalledTimes(1);
    component.recover();
    expect(api.createMockPayment).toHaveBeenLastCalledWith(payload);
    expect(component.session?.reference).toBe(reference);
  });

  it('checks a known session after reload and never creates a second one', () => {
    mount();
    component.amount = 10000;
    component.create();
    fixture.destroy();
    mount();
    expect(api.createMockPayment).toHaveBeenCalledTimes(1);
    component.recover();
    expect(api.getMockPayment).toHaveBeenCalledWith(reference);
    expect(api.createMockPayment).toHaveBeenCalledTimes(1);
  });

  it('keeps a recoverable session on timeout or a malformed completion and never reports false success', () => {
    mount();
    component.amount = 10000;
    component.create();
    api.finishMockPayment.mockReturnValueOnce(new Subject<any>());
    vi.useFakeTimers();
    try {
      component.finish('SUCCESS');
      vi.advanceTimersByTime(30000);
      expect(component.busy).toBe(false);
      expect(component.draft?.reference).toBe(reference);
    } finally {
      vi.useRealTimers();
    }
    api.getMockPayment.mockReturnValueOnce(of({ ...session, status: 'COMPLETED' }));
    component.recover();
    expect(component.error).toContain('Chưa xác nhận');
    expect(component.draft).not.toBeNull();
    expect(dialog.show).not.toHaveBeenCalled();
  });
});
