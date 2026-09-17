import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-digital',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './digital.html',
  styleUrls: ['./digital.css']
})
export class Digital {
  databases = [
    { name: 'IEEE Xplore', desc: 'Thư viện số chuyên ngành Kỹ thuật Điện, Điện tử, Viễn thông, Công nghệ thông tin.', img: 'https://upload.wikimedia.org/wikipedia/commons/2/21/IEEE_logo.svg', link: '#' },
    { name: 'ProQuest', desc: 'Cơ sở dữ liệu đa ngành với hàng triệu luận văn, bài báo khoa học chất lượng cao.', img: 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/ProQuest_logo.svg/512px-ProQuest_logo.svg.png', link: '#' },
    { name: 'SpringerLink', desc: 'Kho tàng tài liệu khổng lồ về khoa học, công nghệ, y học và toán học.', img: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c9/Springer.svg/512px-Springer.svg.png', link: '#' },
    { name: 'ScienceDirect', desc: 'Cơ sở dữ liệu khoa học uy tín hàng đầu thế giới của nhà xuất bản Elsevier.', img: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/ScienceDirect_logo.svg/512px-ScienceDirect_logo.svg.png', link: '#' }
  ];
}
