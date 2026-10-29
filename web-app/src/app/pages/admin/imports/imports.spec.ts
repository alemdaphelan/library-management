import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { Imports } from './imports';
import { CatalogIdentification, ImportReceipt, ImportReceiptRequest, ImportsService } from '../../../services/imports.service';
import { LocationService, LocationNode } from '../../../services/location.service';

const identification: CatalogIdentification = { bookId: 12, title: 'Clean Code', isbn: '9780132350884', canonicalIsbn: '9780132350884',
  author: 'Robert Martin', publisher: 'Prentice Hall', publishYear: 2008, defaultPrice: 100000, categoryId: 2, categoryName: 'CNTT', ddcCallNumber: '005',
  subjects: [{ code: 'IT', name: 'Công nghệ thông tin', confidence: .9, source: 'MANUAL', evidence: 'Danh mục', primary: true }], classificationStatus: 'AVAILABLE',
  placementStatus: 'WAITING_ADMIN_RULES', suggestedShelfId: null, placementMessage: 'Chưa có quy tắc xếp sách. Chọn ngăn thủ công.' };
const receipt: ImportReceipt = { receiptId: 10, receiptCode: 'PN-10', supplierId: 7, supplierName: 'NXB kiểm thử', createdAt: '2026-10-29T16:00:00',
  quantity: 2, totalAmount: 200000, status: 'COMPLETED', lines: [{ detailId: 9, bookId: 12, title: 'Clean Code', isbn: '9780132350884', quantity: 2,
    unitPrice: 100000, shelfId: 3, locationPath: 'Tầng 1 / Kệ A / Ngăn B', barcodes: ['BC-1', 'BC-2'] }] };

describe('Imports with per-line physical location', () => {
  let fixture: ComponentFixture<Imports>, component: Imports;
  let service: ReturnType<typeof importsStub>, locations: ReturnType<typeof locationsStub>;
  function importsStub() { return { getStats: vi.fn(() => of({ totalImports: 1, booksReceived: 2, totalSpent: 200000, pending: 0 })),
    getImportReceipts: vi.fn(() => of([receipt])), getSuppliers: vi.fn(() => of([{ supplierId: 7, name: 'NXB kiểm thử' }])),
    identify: vi.fn(() => of(identification)), saveImport: vi.fn((_payload: ImportReceiptRequest) => of(receipt)), getReceipt: vi.fn(() => of(receipt)) }; }
  function locationsStub() { return { getFloors: vi.fn(() => of<LocationNode[]>([{ id: 1, name: 'Tầng 1', type: 'FLOOR' }, { id: 11, name: 'Tầng 2', type: 'FLOOR' }])),
    getBookshelves: vi.fn(() => of<LocationNode[]>([{ id: 2, name: 'Kệ A', type: 'BOOKSHELF', parentId: 1 }])),
    getShelves: vi.fn(() => of<LocationNode[]>([{ id: 3, name: 'Ngăn B', type: 'SHELF', parentId: 2 }])) }; }
  beforeEach(async () => {
    service = importsStub(); locations = locationsStub();
    await TestBed.configureTestingModule({ imports: [Imports], providers: [provideRouter([]), { provide: ImportsService, useValue: service }, { provide: LocationService, useValue: locations }] }).compileComponents();
    fixture = TestBed.createComponent(Imports); component = fixture.componentInstance;
    fixture.detectChanges(); await fixture.whenStable();
  });
  afterEach(() => { fixture.destroy(); document.body.style.overflow = ''; });
  function readyLine() {
    component.openDrawer(); const line = component.newImportLines[0]; line.identifier = '9780132350884'; component.lookupIsbn(line);
    line.floorId = 1; component.floorChanged(line); line.bookshelfId = 2; component.bookshelfChanged(line); line.shelfId = 3; line.qty = 2;
    component.selectedSupplierId = 7; return line;
  }
  it('loads real receipt stats and reopens persisted location/barcodes after page load', () => {
    component.viewReceipt(10); fixture.detectChanges();
    expect(service.getReceipt).toHaveBeenCalledWith(10);
    expect(fixture.nativeElement.textContent).toContain('Tầng 1 / Kệ A / Ngăn B');
    expect(fixture.nativeElement.textContent).toContain('BC-1, BC-2');
  });
  it('requires complete location, validated identity and integer quantities before any mutation', () => {
    component.openDrawer(); component.selectedSupplierId = 7; component.saveImport(); expect(service.saveImport).not.toHaveBeenCalled();
    const line = readyLine(); line.shelfId = undefined; component.saveImport(); expect(service.saveImport).not.toHaveBeenCalled();
    line.shelfId = 3; line.qty = 1.5; component.saveImport(); expect(service.saveImport).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('đủ tầng/kệ/ngăn');
  });
  it('sends the shelf and its displayed parents and persists location for every generated copy', () => {
    readyLine(); component.saveImport(); fixture.detectChanges();
    expect(service.saveImport).toHaveBeenCalledWith({ supplierId: 7, lines: [{ bookId: 12, isbn: '9780132350884', quantity: 2, unitPrice: 100000, floorId: 1, bookshelfId: 2, shelfId: 3 }] });
    expect(component.selectedReceipt?.lines?.[0].barcodes).toEqual(['BC-1', 'BC-2']); expect(component.isDrawerOpen).toBe(false);
  });
  it('keeps independent shelf choices for different lines and clears descendants on parent change', () => {
    const first = readyLine(); component.addLine(); const second = component.newImportLines[1];
    expect(second.shelfId).toBeUndefined();
    first.floorId = 11; component.floorChanged(first);
    expect(first.bookshelfId).toBeUndefined(); expect(first.shelfId).toBeUndefined(); expect(first.shelves).toEqual([]);
  });
  it('ignores stale ISBN responses when a staff member changes the entered identifier', () => {
    const pending = new Subject<CatalogIdentification>(); service.identify.mockReturnValueOnce(pending);
    const line = component.newImportLines[0]; line.identifier = '9780132350884'; component.lookupIsbn(line);
    line.identifier = '9780134494166'; component.clearIdentification(line); pending.next(identification); pending.complete();
    expect(line.bookId).toBeUndefined(); expect(line.title).toBe(''); expect(line.lookupState).toBe('idle');
  });
  it('ignores an old hierarchy response after switching floors', () => {
    const pending = new Subject<LocationNode[]>(); locations.getBookshelves.mockReturnValueOnce(pending);
    const line = component.newImportLines[0]; line.floorId = 1; component.floorChanged(line); line.floorId = 11; component.floorChanged(line);
    pending.next([{ id: 2, name: 'Kệ cũ', type: 'BOOKSHELF', parentId: 1 }]); pending.complete();
    expect(line.bookshelves).toEqual([]); expect(line.shelfId).toBeUndefined();
  });
  it('displays catalog classification provenance but does not claim an automatic shelf recommendation', () => {
    const line = readyLine(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Công nghệ thông tin'); expect(fixture.nativeElement.textContent).toContain('MANUAL');
    expect(fixture.nativeElement.textContent).toContain('Chưa có quy tắc xếp sách');
    expect(line.identification?.suggestedShelfId).toBeNull();
  });
  it('accepts explicit bookId for legacy or ambiguous ISBN without sending a stale ISBN', () => {
    const line = readyLine(); line.mode = 'bookId'; line.identifier = '12'; component.clearIdentification(line); component.lookupIsbn(line);
    component.saveImport(); expect(service.identify).toHaveBeenLastCalledWith('bookId', '12');
    expect(service.saveImport.mock.calls[0][0].lines[0].isbn).toBeUndefined();
  });
  it('locks form, closes and duplicate sends until the original save completes', () => {
    const pending = new Subject<ImportReceipt>(); service.saveImport.mockReturnValueOnce(pending); readyLine();
    component.saveImport(); component.saveImport(); component.closeDrawer(); component.addLine(); fixture.detectChanges();
    expect(service.saveImport).toHaveBeenCalledOnce(); expect(component.isDrawerOpen).toBe(true); expect(component.newImportLines).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    pending.next(receipt); pending.complete(); expect(component.isSaving).toBe(false); expect(component.isDrawerOpen).toBe(false);
  });
  it('shows server conflict without discarding the draft or claiming stock was created', () => {
    service.saveImport.mockReturnValueOnce(throwError(() => ({ status: 409, error: { detail: 'Cây vị trí đã thay đổi' } })));
    readyLine(); component.saveImport(); fixture.detectChanges();
    expect(component.errorMessage).toContain('Cây vị trí đã thay đổi'); expect(component.newImportLines).toHaveLength(1); expect(component.successMessage).toBe('');
  });
  it('has explicit errors for denied metadata and missing location data without fabricated fallbacks', () => {
    service.identify.mockReturnValueOnce(throwError(() => ({ status: 403 })));
    locations.getFloors.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.openDrawer(); const line = component.newImportLines[0]; line.identifier = '9780132350884'; component.lookupIsbn(line);
    expect(line.lookupState).toBe('missing'); expect(line.lookupMessage).toContain('không có quyền'); expect(component.locationError).toContain('Không tải');
    component.loadFloors(); expect(component.floors).toHaveLength(2);
  });
  it('canceling an unsent form has no mutation and restores page scrolling', () => {
    readyLine(); component.closeDrawer(); expect(service.saveImport).not.toHaveBeenCalled(); expect(document.body.style.overflow).toBe('');
  });
});
