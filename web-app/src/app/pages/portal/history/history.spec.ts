import { ComponentFixture, TestBed } from '@angular/core/testing';

import { History } from './history';
import { LibraryPolicyService } from '../../../services/library-policy.service';
import { of } from 'rxjs';

describe('History', () => {
  let component: History;
  let fixture: ComponentFixture<History>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [History],
      providers: [{
        provide: LibraryPolicyService,
        useValue: { getPublicPolicy: () => of({ maximumRenewalCount: 1, renewalDays: 10 }) },
      }],
    }).compileComponents();

    fixture = TestBed.createComponent(History);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('separates returned and cancelled tickets and keeps reserved tickets pending', () => {
    (component as any).processLoans([
      { ticketId: 1, status: 'RETURNED', bookTitle: 'A' },
      { ticketId: 2, status: 'CANCELLED', bookTitle: 'B' },
      { ticketId: 3, status: 'REJECTED', bookTitle: 'C', decisionNote: 'Thẻ đã hết hạn' },
      { ticketId: 4, status: 'RESERVED', bookTitle: 'D', pickupDeadline: '2026-10-25T17:00:00' },
      { ticketId: 5, status: 'OVERDUE', bookTitle: 'E' },
    ]);

    expect(component.pastList.map(item => item.ticketId)).toEqual([1]);
    expect(component.cancelledList.map(item => item.ticketId)).toEqual([2, 3]);
    expect(component.cancelledList[1].decisionNote).toBe('Thẻ đã hết hạn');
    expect(component.pendingList[0].pickupDeadline).toBe('2026-10-25T17:00:00');
    expect(component.borrowingList.map(item => item.ticketId)).toContain(5);
  });
});
