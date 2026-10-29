import { ChangeDetectorRef, Component, OnInit, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ImportLineDraft, ImportsService } from '../../../services/imports.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './imports.html',
  styleUrls: ['./imports.css']
})
export class Imports implements OnInit {
  isDrawerOpen = false;
  stats: any = {};
  importReceipts: any[] = [];
  suppliers: any[] = [];
  selectedSupplierId?: number;
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  successMessage = '';
  highlightedReceiptId?: number;

  newImportLines: ImportLineDraft[] = [this.emptyLine()];

  constructor(
    private importsService: ImportsService,
    private cdr: ChangeDetectorRef,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit() {
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId');
    this.highlightedReceiptId = referenceId ? Number(referenceId) : undefined;
    this.fetchData();
  }

  fetchData() {
    this.isLoading = true;
    this.errorMessage = '';
    forkJoin({
      stats: this.importsService.getStats(),
      receipts: this.importsService.getImportReceipts(),
      suppliers: this.importsService.getSuppliers(),
    }).subscribe({
      next: ({ stats, receipts, suppliers }) => {
        this.stats = stats || {};
        this.importReceipts = receipts || [];
        this.suppliers = suppliers || [];
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching inventory data', err);
        this.isLoading = false;
        this.errorMessage = this.extractError(err, 'Không thể tải dữ liệu nhập kho. Vui lòng thử lại.');
        this.cdr.detectChanges();
      }
    });
  }

  openDrawer() {
    this.isDrawerOpen = true;
    document.body.style.overflow = 'hidden';
  }

  closeDrawer() {
    this.isDrawerOpen = false;
    document.body.style.overflow = '';
  }

  addLine() {
    this.newImportLines.push(this.emptyLine());
  }

  lookupIsbn(line: ImportLineDraft): void {
    const isbn = line.isbn.trim();
    line.bookId = undefined;
    line.title = '';
    line.lookupMessage = '';
    if (!isbn) {
      line.lookupState = 'idle';
      return;
    }
    line.lookupState = 'loading';
    this.importsService.findBookByIsbn(isbn).subscribe({
      next: book => {
        line.bookId = Number(book.bookId);
        line.title = book.title || '';
        if (!line.price && book.defaultPrice != null) line.price = Number(book.defaultPrice);
        line.lookupState = 'found';
        line.lookupMessage = 'Đã tìm thấy đầu sách trong danh mục.';
      },
      error: err => {
        line.lookupState = 'missing';
        line.lookupMessage = this.extractError(
          err,
          'ISBN chưa có trong danh mục. Hãy thêm đầu sách trước khi lập phiếu nhập.'
        );
      }
    });
  }

  removeLine(index: number) {
    if (this.newImportLines.length > 1) {
      this.newImportLines.splice(index, 1);
    }
  }

  saveImport() {
    this.errorMessage = '';
    this.successMessage = '';
    if (!this.selectedSupplierId || this.newImportLines.some(line =>
      !line.isbn.trim() || !line.bookId || line.lookupState !== 'found' || line.qty < 1 || line.price < 0)) {
      this.errorMessage = 'Hãy chọn nhà cung cấp và xác nhận mọi ISBN hợp lệ trước khi lưu phiếu.';
      return;
    }
    const importData = {
      supplierId: this.selectedSupplierId,
      lines: this.newImportLines.map(line => ({
        bookId: line.bookId,
        isbn: line.isbn.trim(),
        quantity: Number(line.qty),
        unitPrice: Number(line.price)
      }))
    };
    this.isSaving = true;
    this.importsService.saveImport(importData).subscribe({
      next: () => {
        this.isSaving = false;
        this.successMessage = 'Đã lưu phiếu nhập và tạo các bản sao trong kho.';
        this.closeDrawer();
        this.fetchData();
        this.selectedSupplierId = undefined;
        this.newImportLines = [this.emptyLine()];
      },
      error: (err) => {
        console.error('Error saving import', err);
        this.isSaving = false;
        this.errorMessage = this.extractError(err, 'Không thể lưu phiếu nhập.');
      }
    });
  }

  private emptyLine(): ImportLineDraft {
    return { isbn: '', title: '', qty: 1, price: 0, lookupState: 'idle' };
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error?.detail ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  get totalQuantity(): number {
    return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0), 0);
  }

  get totalAmount(): number {
    return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0) * (Number(line.price) || 0), 0);
  }
}
