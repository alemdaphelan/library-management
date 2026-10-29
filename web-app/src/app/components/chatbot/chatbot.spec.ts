import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';

import { Chatbot } from './chatbot';
import { ChatbotService, RagChatResponse } from '../../services/chatbot.service';

describe('Chatbot RAG component', () => {
  let fixture: ComponentFixture<Chatbot>;
  let component: Chatbot;
  let response$: Subject<RagChatResponse>;

  beforeEach(async () => {
    response$ = new Subject<RagChatResponse>();
    await TestBed.configureTestingModule({
      imports: [Chatbot],
      providers: [{
        provide: ChatbotService,
        useValue: { ask: () => response$.asObservable() }
      }]
    }).compileComponents();

    fixture = TestBed.createComponent(Chatbot);
    component = fixture.componentInstance;
    component.isOpen = true;
    fixture.detectChanges();
  });

  it('renders a grounded answer and its retrieved source', () => {
    component.sendMessage('Tôi được gia hạn mấy lần?');
    expect(component.isTyping).toBe(true);

    response$.next({
      requestId: 'request-12b',
      answer: 'Mỗi tài liệu được gia hạn tối đa một lần [1].',
      sources: [{
        sourceKey: 'circulation-policy',
        title: 'Quy định mượn, trả, gia hạn và đặt giữ',
        sourceUri: 'classpath:rag/circulation-policy.md',
        type: 'POLICY',
        snippet: 'Mỗi tài liệu được gia hạn tối đa một lần.',
        score: 0.91
      }],
      suggestions: [],
      grounded: true,
      fallbackUsed: false,
      model: 'gemini-3.6-flash'
    });
    fixture.detectChanges();

    expect(component.isTyping).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('gia hạn tối đa một lần');
    expect(fixture.nativeElement.textContent).toContain('Nguồn đã truy xuất');
    expect(fixture.nativeElement.textContent).toContain('Quy định mượn, trả, gia hạn và đặt giữ');
  });

  it('shows the rate-limit message when the backend returns 429', () => {
    component.sendMessage('Một câu hỏi');
    response$.error(new HttpErrorResponse({ status: 429 }));
    fixture.detectChanges();

    expect(component.isTyping).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('quá nhiều câu hỏi');
  });
});
