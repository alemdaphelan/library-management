import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Search } from './search';
import { BookService } from '../../../services/book.service';
import { CategoryService } from '../../../services/category.service';

describe('Search', () => {
  let component: Search;
  let fixture: ComponentFixture<Search>;
  const getBooks = vi.fn();
  const getDepartments = vi.fn();

  beforeEach(async () => {
    getBooks.mockReset();
    getDepartments.mockReset();
    getBooks.mockReturnValue(of({ content: [], totalPages: 0, totalElements: 0 }));
    getDepartments.mockReturnValue(of([
      { key: 'cong nghe thong tin', displayName: 'Công nghệ thông tin' },
    ]));
    await TestBed.configureTestingModule({
      imports: [Search],
      providers: [
        provideRouter([]),
        {
          provide: BookService,
          useValue: {
            getBooks,
            getDepartments,
          },
        },
        {
          provide: CategoryService,
          useValue: { getCategories: () => of([]) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Search);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('requests availability from the backend before pagination', () => {
    getBooks.mockClear();
    component.filterStatus = 'available';

    component.onStatusChange();

    expect(component.currentPage).toBe(0);
    expect(getBooks).toHaveBeenCalledWith('', 0, 12, undefined, 'AVAILABLE', undefined);
  });

  it('loads HUIT departments and sends the selected department to the backend', () => {
    expect(component.departments).toEqual([
      { key: 'cong nghe thong tin', displayName: 'Công nghệ thông tin' },
    ]);
    getBooks.mockClear();
    component.selectedDepartment = 'cong nghe thong tin';

    component.onDepartmentChange();

    expect(component.currentPage).toBe(0);
    expect(getBooks).toHaveBeenCalledWith(
      '', 0, 12, undefined, undefined, 'cong nghe thong tin'
    );
  });
});
