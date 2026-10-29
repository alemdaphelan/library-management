import { ChangeDetectorRef, Component, DestroyRef, OnDestroy, OnInit, Optional, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ImportLineDraft, ImportReceipt, ImportStats, ImportsService } from '../../../services/imports.service';
import { LocationNode, LocationService } from '../../../services/location.service';

@Component({ selector: 'app-imports', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './imports.html', styleUrls: ['./imports.css'] })
export class Imports implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  isDrawerOpen = false;
  stats: ImportStats | null = null;
  importReceipts: ImportReceipt[] = [];
  suppliers: { supplierId: number; name: string }[] = [];
  selectedSupplierId?: number;
  isLoading = false; isSaving = false; detailLoading = false;
  errorMessage = ''; successMessage = '';
  highlightedReceiptId?: number;
  selectedReceipt: ImportReceipt | null = null;
  floors: LocationNode[] = []; floorsLoading = false; locationError = '';
  newImportLines: ImportLineDraft[] = [this.emptyLine()];
  private bodyOverflow = '';
  constructor(private importsService: ImportsService, private cdr: ChangeDetectorRef, private locations: LocationService, @Optional() private route?: ActivatedRoute) {}
  ngOnInit(): void {
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId');
    this.highlightedReceiptId = referenceId ? Number(referenceId) : undefined;
    this.fetchData();
  }
  ngOnDestroy(): void { if (this.isDrawerOpen) document.body.style.overflow = this.bodyOverflow; }
  fetchData(): void {
    if (this.isLoading) return;
    this.isLoading = true; this.errorMessage = ''; this.stats = null;
    forkJoin({ stats: this.importsService.getStats(), receipts: this.importsService.getImportReceipts(), suppliers: this.importsService.getSuppliers() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: data => { this.stats = data.stats; this.importReceipts = data.receipts; this.suppliers = data.suppliers; this.isLoading = false; this.cdr.detectChanges(); },
        error: error => { this.isLoading = false; this.errorMessage = this.extractError(error, 'Không thể tải dữ liệu nhập kho.'); this.cdr.detectChanges(); },
      });
  }
  openDrawer(): void {
    if (this.isSaving || this.isDrawerOpen) return;
    this.isDrawerOpen = true; this.bodyOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden';
    this.errorMessage = ''; this.loadFloors();
  }
  closeDrawer(): void { if (!this.isSaving) { this.isDrawerOpen = false; document.body.style.overflow = this.bodyOverflow; } }
  addLine(): void { if (!this.isSaving) this.newImportLines.push(this.emptyLine()); }
  removeLine(index: number): void { if (!this.isSaving && this.newImportLines.length > 1) this.newImportLines.splice(index, 1); }
  clearIdentification(line: ImportLineDraft): void {
    line.requestVersion++; line.bookId = undefined; line.title = ''; line.identification = undefined; line.lookupState = 'idle'; line.lookupMessage = '';
  }
  lookupIsbn(line: ImportLineDraft): void {
    if (this.isSaving || line.lookupState === 'loading') return;
    const value = line.identifier.trim(), mode = line.mode;
    this.clearIdentification(line);
    if (!value) return;
    if (mode === 'bookId' && (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) <= 0)) {
      line.lookupState = 'missing'; line.lookupMessage = 'Mã đầu sách phải là số nguyên dương.'; return;
    }
    const version = line.requestVersion;
    line.lookupState = 'loading';
    this.importsService.identify(mode, value).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: book => {
        if (version !== line.requestVersion || !this.newImportLines.includes(line)) return;
        line.bookId = book.bookId; line.title = book.title; line.identification = book;
        if (!line.price && book.defaultPrice != null) line.price = Number(book.defaultPrice);
        line.lookupState = 'found'; line.lookupMessage = 'Đã đối chiếu đầu sách và ấn bản trong danh mục.'; this.cdr.detectChanges();
      }, error: error => {
        if (version !== line.requestVersion || !this.newImportLines.includes(line)) return;
        line.lookupState = 'missing'; line.lookupMessage = this.extractError(error, 'Không nhận diện được đầu sách. Kiểm tra ISBN hoặc chọn mã đầu sách rõ ràng.'); this.cdr.detectChanges();
      },
    });
  }
  loadFloors(): void {
    if (this.floorsLoading) return;
    this.floorsLoading = true; this.locationError = '';
    this.locations.getFloors().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: floors => {
        this.floors = floors; this.floorsLoading = false;
        for (const line of this.newImportLines) if (line.floorId && !floors.some(floor => floor.id === line.floorId)) { line.floorId = undefined; this.floorChanged(line); }
        this.cdr.detectChanges();
      }, error: error => { this.floorsLoading = false; this.locationError = this.extractError(error, 'Không tải được cây vị trí.'); this.cdr.detectChanges(); },
    });
  }
  floorChanged(line: ImportLineDraft): void {
    line.bookshelfId = undefined; line.shelfId = undefined; line.bookshelves = []; line.shelves = []; line.locationError = ''; line.locationLoading = false;
    const floorId = line.floorId;
    if (!floorId) return;
    line.locationLoading = true;
    this.locations.getBookshelves(floorId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: nodes => { if (line.floorId !== floorId || !this.newImportLines.includes(line)) return; line.bookshelves = nodes.filter(node => node.parentId === floorId); line.locationLoading = false; this.cdr.detectChanges(); },
      error: error => { if (line.floorId !== floorId) return; line.locationLoading = false; line.locationError = this.extractError(error, 'Không tải được kệ. Chọn lại tầng để thử lại.'); this.cdr.detectChanges(); },
    });
  }
  bookshelfChanged(line: ImportLineDraft): void {
    line.shelfId = undefined; line.shelves = []; line.locationError = ''; line.locationLoading = false;
    const bookshelfId = line.bookshelfId;
    if (!bookshelfId) return;
    line.locationLoading = true;
    this.locations.getShelves(bookshelfId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: nodes => { if (line.bookshelfId !== bookshelfId || !this.newImportLines.includes(line)) return; line.shelves = nodes.filter(node => node.parentId === bookshelfId); line.locationLoading = false; this.cdr.detectChanges(); },
      error: error => { if (line.bookshelfId !== bookshelfId) return; line.locationLoading = false; line.locationError = this.extractError(error, 'Không tải được ngăn. Chọn lại kệ để thử lại.'); this.cdr.detectChanges(); },
    });
  }
  locationPath(line: ImportLineDraft): string {
    return [this.floors.find(floor => floor.id === line.floorId)?.name, line.bookshelves.find(item => item.id === line.bookshelfId)?.name,
      line.shelves.find(item => item.id === line.shelfId)?.name].filter(Boolean).join(' / ');
  }
  saveImport(): void {
    if (this.isSaving) return;
    this.errorMessage = ''; this.successMessage = '';
    if (!this.selectedSupplierId || !this.newImportLines.length || this.newImportLines.some(line =>
      !line.bookId || line.lookupState !== 'found' || !Number.isInteger(line.qty) || line.qty < 1 || !Number.isFinite(line.price) || line.price < 0
      || line.locationLoading || !line.floorId || !line.bookshelfId || !line.shelfId || !line.shelves.some(shelf => shelf.id === line.shelfId && shelf.parentId === line.bookshelfId))) {
      this.errorMessage = 'Chọn nhà cung cấp, đối chiếu đầu sách, số lượng/giá hợp lệ và đủ tầng/kệ/ngăn cho từng dòng.'; return;
    }
    const payload = { supplierId: this.selectedSupplierId, lines: this.newImportLines.map(line => ({ bookId: line.bookId!,
      isbn: line.mode === 'isbn' ? line.identifier.trim() : undefined, quantity: Number(line.qty), unitPrice: Number(line.price),
      floorId: line.floorId, bookshelfId: line.bookshelfId, shelfId: line.shelfId! })) };
    this.isSaving = true;
    this.importsService.saveImport(payload).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: receipt => {
        this.isSaving = false; this.selectedReceipt = receipt; this.highlightedReceiptId = receipt.receiptId;
        this.closeDrawer(); this.fetchData(); this.successMessage = `Đã ghi nhận phiếu ${receipt.receiptCode} với ${receipt.quantity} bản sao và vị trí từng dòng.`;
        this.selectedSupplierId = undefined; this.newImportLines = [this.emptyLine()]; this.cdr.detectChanges();
      }, error: error => { this.isSaving = false; this.errorMessage = this.extractError(error, 'Chưa ghi nhận được phiếu nhập. Kiểm tra thông tin trước khi thử lại.'); this.cdr.detectChanges(); },
    });
  }
  viewReceipt(id: number): void {
    if (this.detailLoading) return;
    this.selectedReceipt = null; this.detailLoading = true; this.errorMessage = '';
    this.importsService.getReceipt(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: receipt => { this.selectedReceipt = receipt; this.detailLoading = false; this.cdr.detectChanges(); },
      error: error => { this.detailLoading = false; this.errorMessage = this.extractError(error, 'Không tải được chi tiết phiếu nhập.'); this.cdr.detectChanges(); },
    });
  }
  private emptyLine(): ImportLineDraft { return { mode: 'isbn', identifier: '', title: '', qty: 1, price: 0, lookupState: 'idle', requestVersion: 0, bookshelves: [], shelves: [], locationLoading: false, locationError: '' }; }
  private extractError(error: any, fallback: string): string {
    if (error?.status === 401 || error?.status === 403) return 'Tài khoản không có quyền hoặc phiên đăng nhập đã hết hạn.';
    const message = error?.error?.detail ?? error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
  get totalQuantity(): number { return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0), 0); }
  get totalAmount(): number { return this.newImportLines.reduce((sum, line) => sum + (Number(line.qty) || 0) * (Number(line.price) || 0), 0); }
}
