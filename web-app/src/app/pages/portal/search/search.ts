import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './search.html',
  styleUrls: ['./search.css']
})
export class Search {
  viewMode: 'grid' | 'list' = 'grid';

  books = [
    { id: 1, title: 'Clean Architecture', author: 'Robert C. Martin', cover: '/assets/images/cover_1.jpg', status: 'available', category: 'Lập trình' },
    { id: 2, title: 'Flutter for Beginners', author: 'Alessandro Biessek', cover: '/assets/images/cover_2.jpg', status: 'available', category: 'Di động' },
    { id: 3, title: 'Pro ASP.NET Core 6', author: 'Adam Freeman', cover: '/assets/images/cover_3.jpg', status: 'borrowed', category: 'Lập trình Web' }
  ];

  setViewMode(mode: 'grid' | 'list') {
    this.viewMode = mode;
  }
}
