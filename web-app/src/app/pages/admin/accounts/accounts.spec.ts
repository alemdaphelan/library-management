import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Accounts } from './accounts';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';
import { provideRouter } from '@angular/router';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;
  let role: 'ADMIN' | 'LIBRARIAN';
  const userService = {
    getStudents: vi.fn(() => of([])),
    createStudent: vi.fn(() => of({})),
    updateStudent: vi.fn(() => of({})),
    deleteStudent: vi.fn(() => of(void 0)),
    getReaderProfile: vi.fn(() => of({
      student: { userId: 'user-1', fullName: 'Nguyễn Văn A', mssv: '20012345', email: 'a@huit.edu.vn' },
      card: { issued: true, cardNumber: '20012345', ownerName: 'Nguyễn Văn A', cardStatus: 'ACTIVE', depositBalance: 50000, totalDebt: 0 },
      loans: [], ledger: [], penaltyCases: [],
    })),
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
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    fixture.detectChanges();
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
    expect(fixture.nativeElement.textContent).not.toContain('Thêm sinh viên');
    expect(fixture.nativeElement.textContent).not.toContain('Sửa');
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

  it('loads a complete operational profile for both admin and librarian review', () => {
    const student = { userId: 'user-1', fullName: 'Nguyễn Văn A', mssv: '20012345', email: 'a@huit.edu.vn' };

    component.openProfile(student);
    fixture.detectChanges();

    expect(userService.getReaderProfile).toHaveBeenCalledWith('20012345');
    expect(component.selectedProfile?.card.depositBalance).toBe(50000);
    expect(fixture.nativeElement.textContent).toContain('Hồ sơ nghiệp vụ bạn đọc');
    expect(fixture.nativeElement.textContent).toContain('Tiền thế chân');
  });
});
