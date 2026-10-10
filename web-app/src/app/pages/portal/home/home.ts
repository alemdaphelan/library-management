import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HomeService } from '../../../services/home.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './home.html',
  styleUrls: ['./home.css'],
})
export class Home implements OnInit {
  featuredBooks: any[] = [];
  recommendedBooks: any[] = [];

  constructor(private homeService: HomeService) {}

  ngOnInit() {
    this.homeService.getFeaturedBooks().subscribe({
      next: (data) => this.featuredBooks = data,
      error: (err) => console.error('Error fetching featured books', err)
    });

    this.homeService.getRecommendedBooks().subscribe({
      next: (data) => this.recommendedBooks = data,
      error: (err) => console.error('Error fetching recommended books', err)
    });
  }
}
