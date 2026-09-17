import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Borrows } from './borrows';

describe('Borrows', () => {
  let component: Borrows;
  let fixture: ComponentFixture<Borrows>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Borrows],
    }).compileComponents();

    fixture = TestBed.createComponent(Borrows);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
