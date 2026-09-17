import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-suggest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './suggest.html',
  styleUrls: ['./suggest.css']
})
export class Suggest {
  suggestion = {
    title: '',
    author: '',
    publisher: '',
    link: '',
    reason: ''
  };

  isSubmitted = false;

  onSubmit() {
    this.isSubmitted = true;
    setTimeout(() => {
      this.isSubmitted = false;
      this.suggestion = { title: '', author: '', publisher: '', link: '', reason: '' };
    }, 3000);
  }
}
