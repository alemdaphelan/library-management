import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Home } from './home';
import { provideRouter } from '@angular/router';
import { HomeService } from '../../../services/home.service';
import { of } from 'rxjs';

const recommendationResponse = {
  method: 'HYBRID_PERSONALIZED',
  dataSufficient: true,
  candidateCount: 20,
  generatedAt: '2026-10-19T10:00:00',
  items: [{
    bookId: 12,
    title: 'Lập trình Java',
    author: 'Nguyễn Văn A',
    imageUrl: null,
    score: 0.72,
    contentScore: 0.8,
    departmentScore: 0.9,
    popularityScore: 0.4,
    borrowCount: 10,
    reason: 'Phù hợp với chuyên ngành Công nghệ thông tin'
  }]
};

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        {
          provide: HomeService,
          useValue: {
            getRecommendedBooks: () => of(recommendationResponse)
          }
        }
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }, 30_000);

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders personalized recommendations returned by the backend', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(component.recommendationMethod).toBe('HYBRID_PERSONALIZED');
    expect(component.recommendedBooks[0].bookId).toBe(12);
    expect(text).toContain('Gợi ý riêng cho bạn');
    expect(text).toContain('Lập trình Java');
    expect(text).not.toContain('72% phù hợp');
  });

  it('does not render the removed mock room, seat, news or book sections', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).not.toContain('Đặt Phòng Nhóm');
    expect(text).not.toContain('482 / 1.200');
    expect(text).not.toContain('Tin Tức & Hoạt Động Học Thuật');
    expect(text).not.toContain('Còn 22 cuốn');
  });
});
