package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ShelfPlacementService {
    public record Placement(Long shelfId,Long bookshelfId,Long floorId,String shelfName,String bookshelfName,String floorName,String subjectName) {}
    private final ShelfSubjectRuleRepository rules;
    private final BookSubjectAssignmentRepository assignments;
    public ShelfPlacementService(ShelfSubjectRuleRepository rules,BookSubjectAssignmentRepository assignments){this.rules=rules;this.assignments=assignments;}
    @Transactional(readOnly=true)
    public List<Placement> candidates(Long bookId){
        Map<Long,Placement> result=new LinkedHashMap<>();
        for(var rule:rules.findMatchingPlacementRules(bookId)){
            var shelf=rule.getShelf();var bookshelf=shelf.getBookshelf();var floor=bookshelf.getFloor();
            result.putIfAbsent(shelf.getShelfId(),new Placement(shelf.getShelfId(),bookshelf.getBookshelfId(),floor.getFloorId(),shelf.getName(),bookshelf.getName(),floor.getName(),rule.getSubject().getName()));
        }
        return List.copyOf(result.values());
    }
    @Transactional(readOnly=true)
    public void requireAllowed(Long bookId,Long shelfId){
        var allowed=rules.findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(shelfId).stream().map(r->r.getSubject().getSubjectId()).toList();
        if(!allowed.isEmpty()&&!assignments.existsByBook_BookIdAndSubject_SubjectIdIn(bookId,allowed))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Ngăn đã chọn không cho phép chủ đề của đầu sách theo quy định Admin. Tra cứu lại vị trí.");
    }
}
