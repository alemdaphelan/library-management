import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ChatbotService, RagSource } from '../../services/chatbot.service';

interface ChatMessage {
  sender: 'user' | 'bot';
  text: string;
  time: string;
  suggests: string[];
  sources: RagSource[];
  fallbackUsed?: boolean;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrls: ['./chatbot.css']
})
export class Chatbot {
  private chatbotService = inject(ChatbotService);
  private cdr = inject(ChangeDetectorRef);

  isOpen = false;
  userInput = '';

  messages: ChatMessage[] = [
    {
      sender: 'bot',
      text: 'Xin chào! Tôi là Trợ lý RAG của HUIT Library. Câu trả lời của tôi sẽ kèm nguồn từ kho tri thức thư viện.',
      time: this.getCurrentTime(),
      suggests: ['Quy định gia hạn sách?', 'Quá hạn trả sách bị xử lý thế nào?'],
      sources: []
    }
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

    this.messages.push({ sender: 'user', text: text.trim(), time: this.getCurrentTime(), suggests: [], sources: [] });
    this.userInput = '';
    this.isTyping = true;

    this.callRagApi(text.trim());
  }

  callRagApi(query: string) {
    this.chatbotService.ask(query).subscribe({
      next: (response) => {
        this.isTyping = false;
        this.messages.push({
          sender: 'bot',
          text: response.answer,
          time: this.getCurrentTime(),
          suggests: response.suggestions ?? [],
          sources: response.sources ?? [],
          fallbackUsed: response.fallbackUsed
        });
        this.cdr.detectChanges();
      },
      error: (error: HttpErrorResponse) => {
        this.isTyping = false;
        const message = error.status === 429
          ? 'Bạn đã gửi quá nhiều câu hỏi. Vui lòng thử lại sau một phút.'
          : 'Xin lỗi, kho tri thức hiện chưa phản hồi. Bạn có thể thử lại hoặc liên hệ thủ thư.';
        this.messages.push({
          sender: 'bot',
          text: message,
          time: this.getCurrentTime(),
          suggests: ['Thử lại', 'Quy định mượn sách?'],
          sources: []
        });
        this.cdr.detectChanges();
      }
    });
  }

  handleSuggestClick(suggestText: string) {
    this.sendMessage(suggestText);
  }

  getCurrentTime(): string {
    const now = new Date();
    return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
  }
}
