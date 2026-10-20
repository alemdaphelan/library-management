import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { of } from 'rxjs';
import { DigitalAssets } from './digital-assets';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';

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

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [DigitalAssets],
      providers: [{ provide: DigitalAssetsService, useValue: service }]
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
});
