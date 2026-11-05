import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-finance-pager',
  standalone: true,
  imports: [CommonModule],
  template: `<nav *ngIf="total > size" [attr.aria-label]="'Phân trang ' + label">
    <button type="button" (click)="pageChange.emit(page - 1)" [disabled]="page === 0">Trước</button>
    <span>Trang {{page + 1}} / {{pages}} · {{total}} {{label}}</span>
    <button type="button" (click)="pageChange.emit(page + 1)" [disabled]="page + 1 >= pages">Sau</button>
  </nav>`,
  styles: [`nav{display:flex;align-items:center;justify-content:center;gap:12px;padding:16px;flex-wrap:wrap;font-size:.85rem}button{border:1px solid #cbd5e1;border-radius:8px;background:white;padding:8px 14px;color:#075985;cursor:pointer}button:disabled{opacity:.45;cursor:default}`],
})
export class FinancePager {
  @Input() label = 'dòng';
  @Input() page = 0;
  @Input() size = 10;
  @Input() total = 0;
  @Output() pageChange = new EventEmitter<number>();
  get pages(): number { return Math.ceil(this.total / this.size); }
}
