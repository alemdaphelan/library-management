import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PortalLayout } from './portal-layout';
import { provideRouter } from '@angular/router';

describe('PortalLayout', () => {
  let component: PortalLayout;
  let fixture: ComponentFixture<PortalLayout>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PortalLayout],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PortalLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
