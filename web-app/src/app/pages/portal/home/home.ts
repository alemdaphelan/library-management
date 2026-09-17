import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  featuredBooks = [
    { title: 'Sách mới', count: 120, icon: '✨' },
    { title: 'Tài liệu số', count: 500, icon: '📱' },
    { title: 'Giáo trình', count: 350, icon: '📚' }
  ];

  recommendedBooks = [
    { id: 1, title: 'Clean Architecture', author: 'Robert C. Martin', cover: '/assets/images/cover_1.jpg' },
    { id: 2, title: 'Flutter for Beginners', author: 'Alessandro Biessek', cover: '/assets/images/cover_2.jpg' },
    { id: 3, title: 'Pro ASP.NET Core 6', author: 'Adam Freeman', cover: '/assets/images/cover_3.jpg' },
    { id: 1, title: 'Clean Architecture (Bản tiếng Việt)', author: 'Robert C. Martin', cover: '/assets/images/cover_1.jpg' }
  ];
}
