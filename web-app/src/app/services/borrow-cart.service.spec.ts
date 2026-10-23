import { TestBed } from '@angular/core/testing';
import { BorrowCartService } from './borrow-cart.service';

describe('BorrowCartService', () => {
  let service: BorrowCartService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [BorrowCartService] });
    service = TestBed.inject(BorrowCartService);
  });

  it('stores no more than three distinct titles', () => {
    expect(service.add({ bookId: 1, title: 'Sách A' })).toBe('ADDED');
    expect(service.add({ bookId: 2, title: 'Sách B' })).toBe('ADDED');
    expect(service.add({ bookId: 3, title: 'Sách C' })).toBe('ADDED');
    expect(service.add({ bookId: 4, title: 'Sách D' })).toBe('FULL');
    expect(service.count).toBe(3);
  });

  it('rejects the same title twice and persists the cart', () => {
    expect(service.add({ bookId: 1, title: 'Sách A' })).toBe('ADDED');
    expect(service.add({ bookId: 1, title: 'Sách A' })).toBe('DUPLICATE');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [BorrowCartService] });
    const restored = TestBed.inject(BorrowCartService);
    expect(restored.allItems.map(item => item.bookId)).toEqual([1]);
  });

  it('removes an item and clears the cart', () => {
    service.add({ bookId: 1, title: 'Sách A' });
    service.add({ bookId: 2, title: 'Sách B' });
    service.remove(1);
    expect(service.allItems.map(item => item.bookId)).toEqual([2]);

    service.clear();
    expect(service.count).toBe(0);
  });
});
