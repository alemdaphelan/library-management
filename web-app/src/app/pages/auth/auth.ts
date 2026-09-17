import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.html',
  styleUrls: ['./auth.css']
})
export class Auth implements OnInit {
  mode: 'login' | 'forgot' = 'login';
  
  loginData = { username: '', password: '' };
  forgotData = { email: '' };

  isSubmitting = false;
  successMessage = '';
  errorMessage = '';

  constructor(private router: Router) {}

  ngOnInit() {
    // Check if url contains 'forgot-password'
    if (window.location.pathname.includes('forgot-password')) {
      this.mode = 'forgot';
    }
  }

  toggleMode(newMode: 'login' | 'forgot', event: Event) {
    event.preventDefault();
    this.mode = newMode;
    this.successMessage = '';
    this.errorMessage = '';
    
    // Update URL without reloading
    const newUrl = newMode === 'login' ? '/login' : '/forgot-password';
    window.history.pushState({}, '', newUrl);
  }

  onLogin() {
    this.isSubmitting = true;
    this.errorMessage = '';
    
    setTimeout(() => {
      this.isSubmitting = false;
      if (this.loginData.username === 'admin') {
        this.router.navigate(['/admin']);
      } else {
        this.router.navigate(['/']); // Go to portal
      }
    }, 1500);
  }

  onLoginSSO() {
    this.isSubmitting = true;
    setTimeout(() => {
      this.isSubmitting = false;
      this.router.navigate(['/']);
    }, 1500);
  }

  onForgotPassword() {
    this.isSubmitting = true;
    this.errorMessage = '';
    
    setTimeout(() => {
      this.isSubmitting = false;
      this.successMessage = 'Một liên kết đặt lại mật khẩu đã được gửi đến email của bạn.';
      this.forgotData.email = '';
    }, 1500);
  }
}
