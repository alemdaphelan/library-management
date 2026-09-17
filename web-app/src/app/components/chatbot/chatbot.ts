import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrls: ['./chatbot.css']
})
export class Chatbot {
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  private apiKey = environment.cohereApiKey;

  isOpen = false;
  userInput = '';

  messages: { sender: string, text: string, time: string, suggests: string[] }[] = [
    { sender: 'bot', text: 'Xin chào! Tôi là Trợ lý AI của HUIT Library. Tôi có thể giúp gì cho bạn?', time: '20:34', suggests: ['Quy định mượn sách?', 'Giờ mở cửa thư viện?'] }
  ];

  isTyping = false;

  toggleChat() {
    this.isOpen = !this.isOpen;
  }

  sendMessage(text: string = this.userInput) {
    if (!text.trim()) return;

    if (this.messages.length > 0) {
      this.messages[this.messages.length - 1].suggests = [];
    }

    this.messages.push({ sender: 'user', text: text.trim(), time: this.getCurrentTime(), suggests: [] });
    this.userInput = '';
    this.isTyping = true;

    this.callGeminiAPI(text.trim());
  }

  callGeminiAPI(query: string) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=${this.apiKey}`;

    // RAG System Prompt
    const payload = {
      contents: [{
        parts: [{
          text: `Bạn là trợ lý AI của Thư viện Đại học Công Thương TP.HCM (HUIT). 
Hãy đóng vai lịch sự, thân thiện. 
Dựa vào Dữ liệu (Context) sau đây để trả lời câu hỏi của sinh viên:
- Trễ hạn: Phạt 1,000đ/ngày.
- Làm mất sách: Đền sách mới cùng năm xuất bản hoặc phạt gấp 5 lần giá bìa.
- Mượn tối đa 3 tài liệu trong 10 ngày.
- Giờ mở cửa: T2-T7 từ 07:30 đến 20:30.
- Sinh viên có thể dùng Thẻ sinh viên điện tử (Mã QR) trên Mobile App hoặc mượn bằng HUIT SSO.

Yêu cầu BẮT BUỘC: Ở cuối câu trả lời, hãy gợi ý chính xác 2 câu hỏi liên quan tiếp theo mà sinh viên có thể muốn hỏi. 
Mỗi câu hỏi phải được đặt sau chữ [SUGGEST]. Tuyệt đối KHÔNG gạch đầu dòng, KHÔNG xuống dòng ở phần gợi ý.
Ví dụ định dạng trả lời: Trả lời ABC... [SUGGEST]Làm sao để đăng nhập?[SUGGEST]Vị trí kệ sách?

Câu hỏi của sinh viên: ${query}`
        }]
      }]
    };

    this.http.post(url, payload).subscribe({
      next: (res: any) => {
        this.isTyping = false;
        const rawResponse = res?.candidates?.[0]?.content?.parts?.[0]?.text || 'Xin lỗi, tôi không thể lấy câu trả lời.';
        this.parseAndAddMessage(rawResponse);
        this.cdr.detectChanges(); // Ép UI cập nhật ngay lập tức
      },
      error: (err) => {
        console.error('API Error:', err);
        this.isTyping = false;
        this.parseAndAddMessage('Xin lỗi, đã xảy ra lỗi kết nối với máy chủ AI (API Key không hợp lệ hoặc đường truyền bị đứt).[SUGGEST]Thử lại[SUGGEST]Trở về trang chủ');
        this.cdr.detectChanges(); // Ép UI cập nhật ngay lập tức
      }
    });
  }

  parseAndAddMessage(rawResponse: string) {
    const parts = rawResponse.split('[SUGGEST]');
    const mainText = parts[0].trim();
    // Lọc bỏ những phần tử rỗng nếu có
    const suggests = parts.slice(1).map(s => s.trim()).filter(s => s.length > 0);

    this.messages.push({ sender: 'bot', text: mainText, time: this.getCurrentTime(), suggests: suggests });
  }

  handleSuggestClick(suggestText: string) {
    this.sendMessage(suggestText);
  }

  getCurrentTime(): string {
    const now = new Date();
    return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
  }
}
