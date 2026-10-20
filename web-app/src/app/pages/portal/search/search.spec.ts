import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Search } from './search';
import { BookService } from '../../../services/book.service';
import { CategoryService } from '../../../services/category.service';

describe('Search', () => {
  let component: Search;
  let fixture: ComponentFixture<Search>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Search],
      providers: [
        provideRouter([]),
        {
          provide: BookService,
          useValue: {
            getBooks: () => of({ content: [], totalPages: 0, totalElements: 0 }),
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
});
