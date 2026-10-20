import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ShelfMap } from './shelf-map';
import { LocationNode, LocationService } from '../../../services/location.service';

describe('ShelfMap', () => {
  let component: ShelfMap;
  let fixture: ComponentFixture<ShelfMap>;

  const floor: LocationNode = { id: 1, name: 'Tầng 1', type: 'FLOOR' };
  const bookshelf: LocationNode = { id: 2, name: 'Kệ A', type: 'BOOKSHELF', parentId: 1 };
  const shelf: LocationNode = { id: 3, name: 'Ngăn 01', type: 'SHELF', parentId: 2 };

  const locationService = {
    getFloors: vi.fn(() => of([floor])),
    getBookshelves: vi.fn(() => of([bookshelf])),
    getShelves: vi.fn(() => of([shelf])),
    createFloor: vi.fn(() => of(floor)),
    createBookshelf: vi.fn(() => of(bookshelf)),
    createShelf: vi.fn(() => of(shelf)),
    updateFloor: vi.fn(() => of(floor)),
    updateBookshelf: vi.fn(() => of(bookshelf)),
    updateShelf: vi.fn(() => of(shelf)),
    deleteFloor: vi.fn(() => of(void 0)),
    deleteBookshelf: vi.fn(() => of(void 0)),
    deleteShelf: vi.fn(() => of(void 0)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ShelfMap],
      providers: [{ provide: LocationService, useValue: locationService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ShelfMap);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads the complete location hierarchy from the backend', () => {
    expect(locationService.getFloors).toHaveBeenCalled();
    expect(locationService.getBookshelves).toHaveBeenCalledWith(1);
    expect(locationService.getShelves).toHaveBeenCalledWith(2);
    expect(component.selectedFloor).toEqual(floor);
    expect(component.selectedBookshelf).toEqual(bookshelf);
    expect(component.shelves).toEqual([shelf]);
  });

  it('creates a shelf under the selected bookshelf', () => {
    component.newShelfName = '  Ngăn 02  ';

    component.createShelf();

    expect(locationService.createShelf).toHaveBeenCalledWith('Ngăn 02', 2);
    expect(component.newShelfName).toBe('');
    expect(locationService.getShelves).toHaveBeenCalledWith(2);
  });

  it('does not create an empty location name', () => {
    component.newFloorName = '   ';

    component.createFloor();

    expect(locationService.createFloor).not.toHaveBeenCalled();
  });
});
