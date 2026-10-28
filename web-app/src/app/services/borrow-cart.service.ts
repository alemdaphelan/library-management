import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface BorrowCartItem {
  bookId: number;
  title: string;
  author?: string;
  cover?: string;
  availableCopies?: number;
}

export type AddToBorrowCartResult = 'ADDED' | 'DUPLICATE' | 'FULL';

@Injectable({ providedIn: 'root' })
export class BorrowCartService {
  private readonly storageKey = 'huit_library_borrow_cart';
  private items: BorrowCartItem[] = this.readItems();
  private maximumItems = Number.MAX_SAFE_INTEGER;
  private readonly countSubject = new BehaviorSubject<number>(this.items.length);
  readonly count$ = this.countSubject.asObservable();

  get allItems(): BorrowCartItem[] {
    return [...this.items];
  }

  get count(): number {
    return this.items.length;
  }

  setMaximumItems(value: number): void {
    if (Number.isFinite(value) && value > 0) {
      this.maximumItems = Math.floor(value);
    }
  }

  add(item: BorrowCartItem): AddToBorrowCartResult {
    if (this.items.some(existing => existing.bookId === item.bookId)) {
      return 'DUPLICATE';
    }
    if (this.items.length >= this.maximumItems) {
      return 'FULL';
    }
    this.items = [...this.items, item];
    this.persist();
    return 'ADDED';
  }

  remove(bookId: number): void {
    this.items = this.items.filter(item => item.bookId !== bookId);
    this.persist();
  }

  clear(): void {
    this.items = [];
    this.persist();
  }

  private readItems(): BorrowCartItem[] {
    try {
      const raw = localStorage.getItem(this.storageKey);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.items));
    } catch {
      // Trình duyệt có thể chặn storage; giỏ vẫn hoạt động trong phiên hiện tại.
    }
    this.countSubject.next(this.items.length);
  }
}
