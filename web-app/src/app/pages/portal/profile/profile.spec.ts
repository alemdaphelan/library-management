import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { NotificationService } from '../../../services/notification.service';
import { Profile } from './profile';

describe('Profile', () => {
  let fixture: ComponentFixture<Profile>;
  let component: Profile;
  const getPreferences = vi.fn();

  beforeEach(async () => {
    getPreferences.mockReset();
    getPreferences.mockReturnValue(of({ emailEnabled: true, pushEnabled: false }));
    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
        {
          provide: AuthService,
          useValue: {
            fetchMyProfile: () => of({
              fullName: 'Nguyễn Văn A', mssv: '20010001', email: 'a@huit.edu.vn',
              department: 'Công nghệ thông tin', cardStatus: null,
            }),
            getRole: () => 'STUDENT',
            getUserProfile: () => null,
            logout: vi.fn(),
          },
        },
        {
          provide: NotificationService,
          useValue: {
            getPreferences,
            updatePreferences: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Profile);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the profile without requesting notification settings on the info tab', () => {
    expect(component.userInfo.fullName).toBe('Nguyễn Văn A');
    expect(component.isProfileLoading).toBe(false);
    expect(component.accountStatusLabel()).toBe('Chưa được cấp thẻ');
    expect(getPreferences).not.toHaveBeenCalled();
  });

  it('loads notification settings only when the settings tab is opened', () => {
    component.setActiveTab('settings');

    expect(getPreferences).toHaveBeenCalledTimes(1);
    expect(component.preferencesLoaded).toBe(true);
    expect(component.settings).toEqual({ emailAlerts: true, pushAlerts: false });
  });
});
