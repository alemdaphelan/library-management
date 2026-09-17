import { Routes } from '@angular/router';

// Layouts
import { PortalLayout } from './layouts/portal-layout/portal-layout';
import { AdminLayout } from './layouts/admin-layout/admin-layout';

// Portal Pages
import { Home } from './pages/portal/home/home';
import { Search } from './pages/portal/search/search';
import { History } from './pages/portal/history/history';
import { Suggest } from './pages/portal/suggest/suggest';
import { Detail } from './pages/portal/detail/detail';
import { About } from './pages/portal/about/about';
import { Digital } from './pages/portal/digital/digital';
import { Guide } from './pages/portal/guide/guide';

// Admin Pages
import { Dashboard } from './pages/admin/dashboard/dashboard';
import { Accounts } from './pages/admin/accounts/accounts';
import { Books } from './pages/admin/books/books';
import { Circulation } from './pages/admin/circulation/circulation';
import { Borrows } from './pages/admin/borrows/borrows';
import { Imports } from './pages/admin/imports/imports';
import { DigitalAssets } from './pages/admin/digital-assets/digital-assets';
import { AdminSuggestions } from './pages/admin/suggestions/suggestions';
import { Notifications } from './pages/admin/notifications/notifications';
import { ShelfMap } from './pages/admin/shelf-map/shelf-map';

// Auth Pages
import { Auth } from './pages/auth/auth';
import { Profile } from './pages/portal/profile/profile';

export const routes: Routes = [
  { path: 'login', component: Auth, title: 'Đăng nhập HUIT Library' },
  { path: 'forgot-password', component: Auth, title: 'Khôi phục mật khẩu' },
  {
    path: '',
    component: PortalLayout,
    children: [
      { path: '', component: Home, title: 'Trang chủ' },
      { path: 'search', component: Search, title: 'Tra cứu sách' },
      { path: 'history', component: History, title: 'Lịch sử mượn' },
      { path: 'suggest', component: Suggest, title: 'Đề xuất sách' },
      { path: 'book/:id', component: Detail, title: 'Chi tiết sách' },
      { path: 'about', component: About, title: 'Giới thiệu Thư viện' },
      { path: 'digital', component: Digital, title: 'Tài nguyên số & CSDL' },
      { path: 'guide', component: Guide, title: 'Hướng dẫn độc giả' },
      { path: 'profile', component: Profile, title: 'Quản lý tài khoản' },
    ]
  },
  {
    path: 'admin',
    component: AdminLayout,
    children: [
      { path: '', component: Dashboard, title: 'Dashboard' },
      { path: 'accounts', component: Accounts, title: 'Quản lý tài khoản' },
      { path: 'books', component: Books, title: 'Quản lý sách' },
      { path: 'imports', component: Imports, title: 'Quản lý nhập kho' },
      { path: 'circulation', component: Circulation, title: 'Quầy mượn trả' },
      { path: 'borrows', component: Borrows, title: 'Quản lý nợ phạt' },
      { path: 'digital-assets', component: DigitalAssets, title: 'Quản trị Tài liệu số' },
      { path: 'suggestions', component: AdminSuggestions, title: 'Duyệt Đề xuất sách' },
      { path: 'notifications', component: Notifications, title: 'Thông báo & Nhắc hạn' },
      { path: 'shelf-map', component: ShelfMap, title: 'Sơ đồ Kệ Sách' }
    ]
  },
  { path: '**', redirectTo: '' }
];
