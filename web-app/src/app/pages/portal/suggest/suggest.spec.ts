import { ComponentFixture, TestBed } from '@angular/core/testing';

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
});
