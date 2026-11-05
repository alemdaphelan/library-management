import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';
import { ShelfMap } from './shelf-map';
import { LocationNode, LocationService, ShelfBooksPage } from '../../../services/location.service';
import { AuthService } from '../../../services/auth.service';

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
    getShelfBooksPage:vi.fn<LocationService['getShelfBooksPage']>(()=>of({items:[],total:0,page:0,size:50})),
    getUnplacedCopies:vi.fn<LocationService['getUnplacedCopies']>(()=>of({items:[],total:0,page:0,size:50})),
    placeCopy:vi.fn(()=>of({})),
    getShelfBooks: vi.fn(() => of([])),
    getAvailableSubjects: vi.fn(() => of([
      { subjectId: 5, code: 'IT', name: 'Công nghệ thông tin' },
      { subjectId: 8, code: 'FOOD', name: 'Công nghệ thực phẩm' },
    ])),
    getShelfSubjects: vi.fn(() => of([
      { subjectId: 5, code: 'IT', name: 'Công nghệ thông tin' },
    ])),
    updateShelfSubjects: vi.fn(() => of([
      { subjectId: 5, code: 'IT', name: 'Công nghệ thông tin' },
    ])),
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

  const authService = { getRole: vi.fn(() => 'ADMIN') };

  beforeEach(async () => {
    vi.clearAllMocks();
    locationService.getShelfBooksPage.mockReset().mockReturnValue(of({items:[],total:0,page:0,size:50}));
    locationService.getUnplacedCopies.mockReset().mockReturnValue(of({items:[],total:0,page:0,size:50}));
    await TestBed.configureTestingModule({
      imports: [ShelfMap],
      providers: [provideRouter([]),
        { provide: LocationService, useValue: locationService },
        { provide: AuthService, useValue: authService },
      ],
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

  it('loads and saves the selected shelf subject rules', () => {
    component.selectShelf(shelf);

    expect(locationService.getShelfSubjects).toHaveBeenCalledWith(3);
    expect(component.selectedSubjectIds).toEqual([5]);

    component.toggleSubject(8, true);
    component.saveSubjectRules();

    expect(locationService.updateShelfSubjects).toHaveBeenCalledWith(3, [5, 8]);
  });

  it('ignores a delayed book page from a previously selected shelf',()=>{
    const pending=new Subject<any>();locationService.getShelfBooksPage.mockReturnValueOnce(pending);
    component.selectShelf(shelf);component.selectShelf({...shelf,id:4,name:'Ngăn 02'});
    pending.next({items:[{barcode:'OLD'}],total:1});pending.complete();
    expect(component.shelfBooks).toEqual([]);expect(component.selectedShelf?.id).toBe(4);
  });
  it('places an unlocated copy only into the explicitly selected shelf and reloads both lists',()=>{
    const book:any={barcode:'BC-X',bookId:9,title:'A',status:'AVAILABLE'};
    component.placeUnlocated(book);expect(locationService.placeCopy).not.toHaveBeenCalled();
    component.selectShelf(shelf);component.placeUnlocated(book);
    expect(locationService.placeCopy).toHaveBeenCalledWith('BC-X',3);expect(component.placingBarcode).toBe('');
    expect(locationService.getUnplacedCopies).toHaveBeenLastCalledWith(0);
  });

  function render(){fixture.changeDetectorRef.markForCheck();fixture.detectChanges();}
  it('shows a local retry error rather than an empty shelf or stale total, then clears it on success',()=>{
    locationService.getShelfBooksPage.mockReturnValueOnce(throwError(()=>new Error('offline')));
    component.selectShelf(shelf);render();
    expect(component.shelfBooksError).toBeTruthy();expect(fixture.nativeElement.textContent).not.toContain('Ngăn kệ này chưa có tài liệu.');
    expect(fixture.nativeElement.querySelector('.shelf-books .count')).toBeNull();
    expect(component.errorMessage).toBe('');
    locationService.getShelfBooksPage.mockReturnValueOnce(of({items:[{barcode:'ORPHAN',bookId:999,title:'Chưa đối chiếu đầu sách',catalogMatched:false,status:'AVAILABLE'}],total:1,page:0,size:50}));
    component.loadShelfBooks();render();expect(component.shelfBooksError).toBe('');expect(fixture.nativeElement.textContent).toContain('ORPHAN');
    expect(fixture.nativeElement.querySelector('.shelf-books table a')).toBeNull();
  });
  it('renders a successful empty shelf only after a confirmed response',()=>{
    const pending=new Subject<ShelfBooksPage>();locationService.getShelfBooksPage.mockReturnValueOnce(pending);
    component.selectShelf(shelf);render();expect(fixture.nativeElement.textContent).not.toContain('Ngăn kệ này chưa có tài liệu.');
    pending.next({items:[],total:0,page:0,size:50});pending.complete();render();expect(fixture.nativeElement.textContent).toContain('Ngăn kệ này chưa có tài liệu.');
  });
  it('never builds a catalog URL for a copy with null bookId',()=>{
    locationService.getShelfBooksPage.mockReturnValueOnce(of({items:[{barcode:'NO-ID',bookId:null,title:'Chưa đối chiếu đầu sách',catalogMatched:false,status:'LOST'}],total:1,page:0,size:50}));
    component.selectShelf(shelf);render();expect(fixture.nativeElement.querySelector('.shelf-books table a')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('(mã )');expect(fixture.nativeElement.textContent).toContain('NO-ID');
  });
  it('clamps a vanished last page and retains the corrected server page',()=>{
    component.selectShelf(shelf);
    locationService.getShelfBooksPage.mockReturnValueOnce(of({items:[],total:50,page:1,size:50})).mockReturnValueOnce(of({items:[{barcode:'LAST',bookId:1,title:'Sách',status:'AVAILABLE'}],total:50,page:0,size:50}));
    component.loadShelfBooks(1);expect(locationService.getShelfBooksPage).toHaveBeenLastCalledWith(3,0);expect(component.shelfPage).toBe(0);expect(component.shelfBooks[0].barcode).toBe('LAST');
  });
  it('hides stale unplaced copies on failure and recovers independently',()=>{
    locationService.getUnplacedCopies.mockReturnValueOnce(throwError(()=>new Error('offline')));component.loadUnplaced();render();
    expect(component.unplacedError).toBeTruthy();expect(fixture.nativeElement.textContent).not.toContain('Không có bản sao chưa xếp ngăn.');
    component.loadUnplaced();render();expect(component.unplacedError).toBe('');expect(fixture.nativeElement.textContent).toContain('Không có bản sao chưa xếp ngăn.');
  });
  it('cancels page requests on retry/destroy and never fetches a negative page',()=>{
    const pending=new Subject<ShelfBooksPage>();locationService.getShelfBooksPage.mockReturnValueOnce(pending);
    component.selectShelf(shelf);expect(pending.observed).toBe(true);component.loadShelfBooks();expect(pending.observed).toBe(false);
    const count=locationService.getShelfBooksPage.mock.calls.length;component.loadShelfBooks(-1);expect(locationService.getShelfBooksPage).toHaveBeenCalledTimes(count);
    const again=new Subject<ShelfBooksPage>();locationService.getShelfBooksPage.mockReturnValueOnce(again);component.loadShelfBooks();fixture.destroy();expect(again.observed).toBe(false);
  });
  it('releases loading and offers retry when a page times out',()=>{
    vi.useFakeTimers();
    try {locationService.getShelfBooksPage.mockReturnValueOnce(new Subject<ShelfBooksPage>());component.selectShelf(shelf);
      vi.advanceTimersByTime(15000);expect(component.isLoadingShelfBooks).toBe(false);expect(component.shelfBooksError).toContain('Thử lại');
    }finally{vi.useRealTimers();}
  });

});
