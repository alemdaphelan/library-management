import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

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

  constructor(private router: Router, private authService: AuthService) {}

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
      const validStudents = ['2001230219', '2001230430', '2001230914'];
      
      if (this.loginData.username === 'admin' || this.loginData.username === 'thuthu' || validStudents.includes(this.loginData.username)) {
        const role = this.authService.login(this.loginData.username);
        if (role === 'ADMIN' || role === 'LIBRARIAN') {
          this.router.navigate(['/admin']);
        } else {
          this.router.navigate(['/']); // Go to portal
        }
      } else {
        this.errorMessage = 'Sai tên đăng nhập hoặc mật khẩu. Vui lòng thử lại!';
        alert(this.errorMessage);
      }
    }, 1000);
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
