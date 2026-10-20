package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagSuggestionService {
    public List<String> suggestions(List<RagRetrievedContext> contexts) {
        boolean penalty = contexts.stream().anyMatch(context ->
                context.sourceKey().contains("penalty") || context.sourceKey().contains("payment"));
        boolean catalog = contexts.stream().anyMatch(context -> "CATALOG".equals(context.type()));
        if (penalty) {
            return List.of("Phí xử lý kỹ thuật được tính thế nào?", "Khi nào thẻ được mở lại?");
        }
        if (catalog) {
            return List.of("Làm sao để đặt giữ sách?", "Tôi được gia hạn sách khi nào?");
        }
        return List.of("Quy định gia hạn sách là gì?", "Quá hạn trả sách bị xử lý thế nào?");
    }
}
