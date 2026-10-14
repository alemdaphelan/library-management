import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HomeService } from '../../../services/home.service';

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
  featuredBooks: any[] = [];
  recommendedBooks: any[] = [];

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

    this.homeService.getFeaturedBooks().subscribe({
      next: (data) => this.featuredBooks = data,
      error: (err) => console.error('Error fetching featured books', err)
    });

    this.homeService.getRecommendedBooks().subscribe({
      next: (data) => this.recommendedBooks = data,
      error: (err) => console.error('Error fetching recommended books', err)
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
}
