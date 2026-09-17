import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-borrows',
  imports: [CommonModule],
  templateUrl: './borrows.html',
  styleUrl: './borrows.css',
})
export class Borrows {
  showPasswordModal: boolean = false;
}
