import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibraryPolicy } from './library-policy';
import { LibraryPolicyService } from '../../../services/library-policy.service';

describe('LibraryPolicy', () => {
  let fixture: ComponentFixture<LibraryPolicy>;
  let component: LibraryPolicy;
  const service = {
    getPolicy: vi.fn(),
    getHistory: vi.fn(),
    updatePolicy: vi.fn(),
  };
  const policy = {
    maximumOpenItems: 3, homeLoanDays: 10, renewalDays: 10, maximumRenewalCount: 1, waitlistHoldHours: 48,
    homeLateFinePerDay: 1000, onsiteLateFinePerDay: 5000,
    technicalProcessingFee: 50000, compensationSettlementDays: 30,
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    service.getPolicy.mockReturnValue(of(policy));
    service.getHistory.mockReturnValue(of([{
      ...policy,
      historyId: 1,
      changedByName: 'Hệ thống',
      changedAt: '2026-10-29T08:00:00',
    }]));
    service.updatePolicy.mockReturnValue(of(policy));
    await TestBed.configureTestingModule({
      imports: [LibraryPolicy],
      providers: [{ provide: LibraryPolicyService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(LibraryPolicy);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and renders real policy values', () => {
    expect(service.getPolicy).toHaveBeenCalled();
    expect(component.policy?.waitlistHoldHours).toBe(48);
    expect(fixture.nativeElement.textContent).toContain('Quy định mượn');
    expect(service.getHistory).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Lịch sử thay đổi quy định');
  });

  it('saves a valid policy', () => {
    component.save();
    expect(service.updatePolicy).toHaveBeenCalledWith(policy);
    expect(component.success).toContain('Đã cập nhật');
    expect(service.getHistory).toHaveBeenCalledTimes(2);
  });

  it('shows backend failure', () => {
    service.updatePolicy.mockReturnValue(throwError(() => ({ error: { message: 'Không hợp lệ' } })));
    component.save();
    expect(component.error).toBe('Không hợp lệ');
  });

  it('shows differences between policy versions', () => {
    component.history = [
      { ...policy, maximumOpenItems: 5, historyId: 2, changedByName: 'Admin', changedAt: '2026-10-29T09:00:00' },
      { ...policy, historyId: 1, changedByName: 'Hệ thống', changedAt: '2026-10-29T08:00:00' },
    ];

    expect(component.changesFor(0)).toEqual([{
      label: 'Số tài liệu tối đa',
      before: '3 tài liệu',
      after: '5 tài liệu',
    }]);
  });
});
