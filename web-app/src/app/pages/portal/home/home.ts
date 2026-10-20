import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  HomeService,
  RecommendationItem,
  RecommendationResponse
} from '../../../services/home.service';

export interface CarouselSlide {
  image: string;
  badge: string;
  title: string;
  description: string;
  ctaText: string;
  ctaLink: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './home.html',
  styleUrls: ['./home.css'],
})
export class Home implements OnInit, OnDestroy {
  recommendedBooks: RecommendationItem[] = [];
  recommendationMethod = '';
  recommendationDataSufficient = false;
  recommendationLoading = true;
  recommendationError = false;

  // Carousel 3 ảnh tự động chạy
  currentSlide = 0;
  carouselInterval: any;
  slides: CarouselSlide[] = [
    {
      image: '/assets/images/cover_1.jpg',
      badge: 'Cổng Tri thức Số HUIT 4.0',
      title: 'Khơi Nguồn Tri Thức - Phát Triển Sáng Tạo',
      description: 'Hơn 200.000 đầu sách in, giáo trình và tài liệu số phân quyền chuẩn quốc tế phục vụ học tập, nghiên cứu.',
      ctaText: 'Tra cứu sách ngay',
      ctaLink: '/search'
    },
    {
      image: '/assets/images/cover_2.jpg',
      badge: 'Không gian Học tập Hiện đại',
      title: 'Thư viện Thông minh & Phòng Tự học Mở',
      description: 'Hệ thống phòng đọc đa phương tiện, khu thảo luận nhóm tiện nghi và dịch vụ mượn sách trực tuyến 24/7.',
      ctaText: 'Xem hướng dẫn thư viện',
      ctaLink: '/guide'
    },
    {
      image: '/assets/images/cover_3.jpg',
      badge: 'Cơ sở Dữ liệu Quốc tế',
      title: 'Kết nối Hàng triệu Luận văn & Bài báo Khoa học',
      description: 'Truy cập không giới hạn các kho tri thức lớn: IEEE Xplore, ScienceDirect, SpringerLink và ProQuest.',
      ctaText: 'Khám phá tài liệu số',
      ctaLink: '/digital'
    }
  ];

  constructor(private homeService: HomeService) {}

  ngOnInit() {
    this.startCarouselAutoPlay();

    this.homeService.getRecommendedBooks().subscribe({
      next: (response: RecommendationResponse) => {
        this.recommendedBooks = response.items ?? [];
        this.recommendationMethod = response.method;
        this.recommendationDataSufficient = response.dataSufficient;
        this.recommendationLoading = false;
      },
      error: (err) => {
        console.error('Error fetching recommended books', err);
        this.recommendationError = true;
        this.recommendationLoading = false;
      }
    });
  }

  ngOnDestroy() {
    this.stopCarouselAutoPlay();
  }

  startCarouselAutoPlay() {
    this.stopCarouselAutoPlay();
    this.carouselInterval = setInterval(() => {
      this.nextSlide();
    }, 4500);
  }

  stopCarouselAutoPlay() {
    if (this.carouselInterval) {
      clearInterval(this.carouselInterval);
    }
  }

  nextSlide() {
    this.currentSlide = (this.currentSlide + 1) % this.slides.length;
  }

  prevSlide() {
    this.currentSlide = (this.currentSlide - 1 + this.slides.length) % this.slides.length;
  }

  goToSlide(index: number) {
    this.currentSlide = index;
    this.startCarouselAutoPlay();
  }

  get recommendationTitle(): string {
    return this.recommendationMethod === 'POPULARITY_BASELINE'
      ? 'Sách được quan tâm nhiều'
      : 'Gợi ý riêng cho bạn';
  }

  get recommendationDescription(): string {
    switch (this.recommendationMethod) {
      case 'HYBRID_PERSONALIZED':
        return 'Dựa trên lịch sử mượn, chuyên ngành và xu hướng đọc tại thư viện.';
      case 'HYBRID_COLD_START':
        return 'Khởi đầu theo chuyên ngành của bạn và những tài liệu đang được quan tâm.';
      case 'POPULARITY_BASELINE':
        return 'Các tài liệu nổi bật dựa trên lượt mượn gần đây của toàn thư viện.';
      default:
        return 'Đang phân tích dữ liệu phù hợp với nhu cầu học tập của bạn.';
    }
  }

  recommendationPercent(book: RecommendationItem): number {
    return Math.max(0, Math.min(100, Math.round((book.score ?? 0) * 100)));
  }

  onCoverError(event: Event): void {
    const image = event.target as HTMLImageElement;
    image.src = '/assets/images/cover_1.jpg';
  }
}
