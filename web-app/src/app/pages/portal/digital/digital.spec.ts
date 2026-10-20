import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Digital } from './digital';
import { DigitalAssetsService } from '../../../services/digital-assets.service';
import { AuthService } from '../../../services/auth.service';

describe('Digital portal', () => {
  let fixture: ComponentFixture<Digital>;
  const assets = [{ id: 1, name: 'Tài liệu.pdf', type: 'PDF', size: 2048, accessLevel: 'AUTHENTICATED' }];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Digital],
      providers: [
        provideRouter([]),
        { provide: DigitalAssetsService, useValue: { getAssets: () => of(assets) } },
        { provide: AuthService, useValue: { isLoggedIn: () => true } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Digital);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('shows authenticated digital documents from the API', () => {
    expect(fixture.nativeElement.textContent).toContain('Tài liệu.pdf');
    expect(fixture.nativeElement.textContent).toContain('2.0 KB');
  });
});
