package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShelfPlacementService {
    public record Placement(
            Long shelfId,
            Long bookshelfId,
            Long floorId,
            String shelfName,
            String bookshelfName,
            String floorName,
            String subjectName) {}

    private final ShelfSubjectRuleRepository rules;

    public ShelfPlacementService(ShelfSubjectRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<Placement> candidates(Long bookId) {
        Map<Long, Placement> result = new LinkedHashMap<>();
        for (var rule : rules.findMatchingPlacementRules(bookId)) {
            var shelf = rule.getShelf();
            var bookshelf = shelf.getBookshelf();
            var floor = bookshelf.getFloor();
            result.putIfAbsent(
                    shelf.getShelfId(),
                    new Placement(
                            shelf.getShelfId(),
                            bookshelf.getBookshelfId(),
                            floor.getFloorId(),
                            shelf.getName(),
                            bookshelf.getName(),
                            floor.getName(),
                            rule.getSubject().getName()));
        }
        return List.copyOf(result.values());
    }

    @Transactional(readOnly = true)
    public void requireAllowed(Long bookId, Long shelfId) {
        if (rules.allowsPlacement(shelfId, bookId)) {
            return;
        }
        if (!rules.hasActiveRules(shelfId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Admin cần cấu hình chủ đề cho ngăn trước khi xếp sách");
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Đầu sách không thuộc chủ đề được phép xếp tại ngăn này");
    }
}
