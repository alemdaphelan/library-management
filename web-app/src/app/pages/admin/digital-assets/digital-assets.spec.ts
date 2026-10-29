import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { of } from 'rxjs';
import { DigitalAssets } from './digital-assets';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';
import { BookService } from '../../../services/book.service';

describe('DigitalAssets', () => {
  let fixture: ComponentFixture<DigitalAssets>;
  let component: DigitalAssets;
  const asset: DigitalAsset = {
    id: 7, name: 'Giáo trình.pdf', type: 'PDF', size: 1024,
    bookId: 2, bookTitle: 'Lập trình Java', accessLevel: 'AUTHENTICATED',
    createdAt: '2026-10-18T08:00:00'
  };
  const service = {
    getAssets: vi.fn(() => of([asset])),
    uploadAsset: vi.fn(() => of({ type: HttpEventType.Response, body: asset })),
    updateAsset: vi.fn(() => of(asset)),
    deleteAsset: vi.fn(() => of(void 0)),
    downloadAsset: vi.fn()
  };
  const bookService = {
    getBookByIsbn: vi.fn(() => of({ bookId: 2, title: 'Lập trình Java', isbn: '9786040000012' }))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [DigitalAssets],
      providers: [
        { provide: DigitalAssetsService, useValue: service },
        { provide: BookService, useValue: bookService }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(DigitalAssets);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads and renders server-backed assets', () => {
    expect(service.getAssets).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Giáo trình.pdf');
    expect(fixture.nativeElement.textContent).toContain('Lập trình Java');
  });

  it('filters by real metadata and persists edits', () => {
    component.searchTerm = 'không khớp';
    expect(component.filteredAssets).toHaveLength(0);
    component.openEdit(asset);
    component.editForm.displayName = 'Giáo trình mới';
    component.saveEdit();

    expect(service.updateAsset).toHaveBeenCalledWith(7, {
      displayName: 'Giáo trình mới', bookId: 2, accessLevel: 'AUTHENTICATED'
    });
  });

  it('resolves an edited book link by ISBN instead of asking for an internal id', () => {
    component.openEdit(asset);
    component.editForm.bookIsbn = '9786040000012';
    component.onEditIsbnChange();
    component.resolveEditBook();

    expect(bookService.getBookByIsbn).toHaveBeenCalledWith('9786040000012');
    expect(component.editForm.bookId).toBe(2);
    expect(component.editForm.bookLabel).toContain('Lập trình Java');
  });
});
