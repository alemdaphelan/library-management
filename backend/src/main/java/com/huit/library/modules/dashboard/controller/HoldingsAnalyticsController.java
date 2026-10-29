package com.huit.library.modules.dashboard.controller;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.dashboard.dto.HoldingsAnalytics.*;
import com.huit.library.modules.dashboard.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/dashboard/analytics") @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class HoldingsAnalyticsController {
    private final HoldingsAnalyticsService service;private final MaterialClassificationService classification;
    public HoldingsAnalyticsController(HoldingsAnalyticsService service,MaterialClassificationService classification){this.service=service;this.classification=classification;}
    @GetMapping("/academic-catalog") public ResponseEntity<?> catalog(){return ok(service.catalog());}
    @GetMapping("/holdings") public ResponseEntity<?> summary(@RequestParam(required=false) String facultyId,@RequestParam(required=false) String majorId,@RequestParam(defaultValue="ALL") MaterialType type,@RequestParam(defaultValue="ALL") State state,@RequestParam(defaultValue="") String q){return ok(service.summary(facultyId,majorId,type,state,q));}
    @GetMapping("/materials") public ResponseEntity<?> materials(@RequestParam(required=false) String facultyId,@RequestParam(required=false) String majorId,@RequestParam(defaultValue="ALL") MaterialType type,@RequestParam(defaultValue="ALL") State state,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.materials(facultyId,majorId,type,state,q,page,size));}
    @GetMapping("/materials/{kind}/{id}/copies") public ResponseEntity<?> copies(@PathVariable Kind kind,@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.copies(kind,id,page,size));}
    @GetMapping("/materials/{kind}/{id}/digital-assets") public ResponseEntity<?> assets(@PathVariable Kind kind,@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.assets(kind,id,page,size));}
    @GetMapping("/materials/{kind}/{id}/classification") public ResponseEntity<?> get(@PathVariable Kind kind,@PathVariable long id){return ok(classification.get(kind,id));}
    @PutMapping("/materials/{kind}/{id}/classification") public ResponseEntity<?> put(@PathVariable Kind kind,@PathVariable long id,@Valid @RequestBody ClassificationPut request,@AuthenticationPrincipal CustomUserDetails actor){return ok(classification.put(kind,id,request,actor==null?null:actor.getUser()));}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
