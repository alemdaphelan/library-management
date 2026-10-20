import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Accounts } from './accounts';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;
  let role: 'ADMIN' | 'LIBRARIAN';
  const userService = {
    getStudents: vi.fn(() => of([])),
    createStudent: vi.fn(() => of({})),
    updateStudent: vi.fn(() => of({})),
    deleteStudent: vi.fn(() => of(void 0)),
  };

  beforeEach(async () => {
    role = 'ADMIN';
    await TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [
        {
          provide: UserService,
          useValue: userService,
        },
        {
          provide: AuthService,
          useValue: { getRole: () => role },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows librarians a read-only view', () => {
    fixture.destroy();
    role = 'LIBRARIAN';
    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isReadOnly).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Chế độ chỉ xem dành cho Thủ thư');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('allows admins to create a student through the real service contract', () => {
    component.openCreate();
    component.form = {
      mssv: '20012345', fullName: 'Nguyễn Văn A', email: 'a@huit.edu.vn',
      department: 'CNTT', phone: '', cardStatus: 'ACTIVE', cardExpiryDate: null,
    };

    component.saveStudent();

    expect(userService.createStudent).toHaveBeenCalledWith(expect.objectContaining({ mssv: '20012345' }));
  });
});
