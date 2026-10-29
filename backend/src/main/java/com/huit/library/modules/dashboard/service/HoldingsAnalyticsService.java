package com.huit.library.modules.dashboard.service;
import com.huit.library.modules.dashboard.dto.HoldingsAnalytics.*;
import com.huit.library.modules.dashboard.repository.HoldingsAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class HoldingsAnalyticsService {
    private final HoldingsAnalyticsRepository repository;
    public HoldingsAnalyticsService(HoldingsAnalyticsRepository repository){this.repository=repository;}
    public Catalog catalog(){return repository.catalog();}
    public Summary summary(String faculty,String major,MaterialType type,State state,String query){var f=withQuery(filter(faculty,major,type,state),query);var at=now();return new Summary(at,CirculationAnalyticsService.TIMEZONE,f,repository.totals(f,at),repository.groups(f,at,false),repository.groups(f,at,true),repository.coverage());}
    public MaterialPage materials(String faculty,String major,MaterialType type,State state,String query,int page,int size){page(page,size);var f=withQuery(filter(faculty,major,type,state),query);var at=now();return new MaterialPage(at,CirculationAnalyticsService.TIMEZONE,f,page,size,repository.count(f,at),repository.materials(f,at,page,size));}
    private Filter withQuery(Filter f,String query){String q=query==null?"":query.trim();if(q.length()>200)throw bad("Tìm kiếm tối đa 200 ký tự");return new Filter(f.facultyId(),f.majorId(),f.type(),f.state(),q);}
    public Summary summary(String faculty,String major,MaterialType type,State state){var f=filter(faculty,major,type,state);var at=now();return new Summary(at,CirculationAnalyticsService.TIMEZONE,f,repository.totals(f,at),repository.groups(f,at,false),repository.groups(f,at,true),repository.coverage());}
    public MaterialPage materials(String faculty,String major,MaterialType type,State state,int page,int size){page(page,size);var f=filter(faculty,major,type,state);var at=now();return new MaterialPage(at,CirculationAnalyticsService.TIMEZONE,f,page,size,repository.count(f,at),repository.materials(f,at,page,size));}
    public DetailPage<Copy> copies(Kind kind,long id,int page,int size){page(page,size);repository.requireMaterial(kind,id,false);return new DetailPage<>(now(),CirculationAnalyticsService.TIMEZONE,kind,id,page,size,repository.copyCount(kind,id),repository.copies(kind,id,page,size));}
    public DetailPage<DigitalAsset> assets(Kind kind,long id,int page,int size){page(page,size);repository.requireMaterial(kind,id,false);return new DetailPage<>(now(),CirculationAnalyticsService.TIMEZONE,kind,id,page,size,repository.assetCount(kind,id),repository.assets(kind,id,page,size));}
    private Filter filter(String faculty,String major,MaterialType type,State state){
        faculty=clean(faculty);major=clean(major);var catalog=repository.catalog();
        if(faculty!=null&&!faculty.equals("UNCLASSIFIED")){String id=faculty;if(catalog.faculties().stream().noneMatch(f->f.facultyId().equals(id)))throw bad("Khoa không thuộc danh mục");}
        if(major!=null&&!major.equals("UNCLASSIFIED")){String id=major;var m=catalog.majors().stream().filter(a->a.majorId().equals(id)).findFirst().orElseThrow(()->bad("Ngành không thuộc danh mục"));if(faculty!=null&&!faculty.equals(m.facultyId()))throw bad("Ngành không thuộc khoa đã chọn");}
        if("UNCLASSIFIED".equals(major)&&faculty!=null&&!faculty.equals("UNCLASSIFIED"))throw bad("Chưa phân loại không có khoa xác định");
        return new Filter(faculty,major,type==null?MaterialType.ALL:type,state==null?State.ALL:state);
    }
    private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
    private void page(int page,int size){if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw bad("page không âm, size 1–100");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of(CirculationAnalyticsService.TIMEZONE));}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
