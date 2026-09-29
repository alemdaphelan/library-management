import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from '../../../services/user.service';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './accounts.html',
  styleUrls: ['./accounts.css']
})
export class Accounts implements OnInit {
  isImportModalOpen = false;
  students: any[] = [];

  constructor(private userService: UserService) {}

  ngOnInit() {
    this.loadStudents();
  }

  loadStudents() {
    this.userService.getStudents().subscribe({
      next: (data) => {
        this.students = data;
      },
      error: (err) => console.error('Failed to load students', err)
    });
  }

  openImportModal() {
    this.isImportModalOpen = true;
  }

  closeImportModal() {
    this.isImportModalOpen = false;
  }
}
