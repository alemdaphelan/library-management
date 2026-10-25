import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PortalLayout } from './portal-layout';
import { provideRouter } from '@angular/router';

describe('PortalLayout', () => {
  let component: PortalLayout;
  let fixture: ComponentFixture<PortalLayout>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [PortalLayout],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PortalLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the admin switch only for staff roles', () => {
    const isLoggedIn = vi.spyOn(component.authService, 'isLoggedIn').mockReturnValue(true);
    const getRole = vi.spyOn(component.authService, 'getRole').mockReturnValue('ADMIN');
    expect(component.canAccessAdmin).toBe(true);

    getRole.mockReturnValue('LIBRARIAN');
    expect(component.canAccessAdmin).toBe(true);

    getRole.mockReturnValue('STUDENT');
    expect(component.canAccessAdmin).toBe(false);

    getRole.mockReturnValue('ADMIN');
    isLoggedIn.mockReturnValue(false);
    expect(component.canAccessAdmin).toBe(false);
  });

  it('labels a missing card instead of showing an unknown status', () => {
    expect(component.cardStatusLabel(undefined)).toBe('Chưa được cấp thẻ');
  });
});
