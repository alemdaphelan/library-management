import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Suggest } from './suggest';

describe('Suggest', () => {
  let component: Suggest;
  let fixture: ComponentFixture<Suggest>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Suggest],
    }).compileComponents();

    fixture = TestBed.createComponent(Suggest);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('submits the complete acquisition proposal payload', () => {
    vi.spyOn(component.authService, 'isLoggedIn').mockReturnValue(true);
    const submit = vi.spyOn((component as any).proposalService, 'submitProposal')
      .mockReturnValue(of({ proposalId: 1, title: 'Clean Architecture' }));
    component.suggestion = {
      title: 'Clean Architecture',
      authorName: 'Robert C. Martin',
      publisher: 'Prentice Hall',
      isbn: '9780132350884',
      publicationYear: 2024,
      edition: 'Tái bản lần 3',
      estimatedPrice: 150000,
      externalUrl: 'https://example.com/book',
      genre: 'Công nghệ thông tin',
      urgencyLevel: 'HIGH',
      reason: 'Phục vụ môn kiến trúc phần mềm',
    };

    component.onSubmit();

    expect(submit).toHaveBeenCalledWith(expect.objectContaining({
      isbn: '9780132350884',
      publicationYear: 2024,
      estimatedPrice: 150000,
      urgencyLevel: 'HIGH',
    }));
  });
});
