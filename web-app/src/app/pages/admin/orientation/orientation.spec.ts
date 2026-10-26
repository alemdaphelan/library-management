import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { OrientationClass, OrientationService } from '../../../services/orientation.service';
import { Orientation } from './orientation';

describe('Orientation', () => {
  let fixture: ComponentFixture<Orientation>;
  let component: Orientation;
  const orientationClass: OrientationClass = {
    classId: 1,
    classCode: 'HD-TEST',
    title: 'Hướng dẫn sử dụng thư viện',
    location: 'Phòng A1',
    startAt: '2026-10-27T08:00:00',
    endAt: '2026-10-27T10:00:00',
    capacity: 30,
    status: 'SCHEDULED',
    enrolledCount: 0,
    enrollments: []
  };
  const service = {
    list: vi.fn(() => of([orientationClass])),
    create: vi.fn(() => of(orientationClass)),
    update: vi.fn(() => of(orientationClass)),
    delete: vi.fn(() => of(void 0)),
    availableStudents: vi.fn(() => of([])),
    facilitators: vi.fn(() => of([])),
    assign: vi.fn(() => of(orientationClass)),
    attendance: vi.fn(() => of(orientationClass)),
    template: vi.fn(),
    importAttendance: vi.fn()
  };
  const auth = { getRole: vi.fn(() => 'ADMIN') };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [Orientation],
      providers: [
        { provide: OrientationService, useValue: service },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Orientation);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the weekly class calendar from server data', () => {
    expect(service.list).toHaveBeenCalled();
    expect(component.classes).toEqual([orientationClass]);
    expect(fixture.nativeElement.textContent).toContain('Lớp hướng dẫn sử dụng thư viện');
  });

  it('lets an admin update a scheduled class', () => {
    component.openEdit(orientationClass);
    component.form.location = 'Phòng A2';
    component.save();

    expect(service.update).toHaveBeenCalledWith(1, expect.objectContaining({ location: 'Phòng A2' }));
    expect(component.success).toContain('cập nhật');
  });
});
