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

  beforeEach(async () => {
    getBooks.mockReset();
    getBooks.mockReturnValue(of({ content: [], totalPages: 0, totalElements: 0 }));
    await TestBed.configureTestingModule({
      imports: [Search],
      providers: [
        provideRouter([]),
        {
          provide: BookService,
          useValue: {
            getBooks,
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
    expect(getBooks).toHaveBeenCalledWith('', 0, 12, undefined, 'AVAILABLE');
  });
});
