package com.huit.library.modules.dashboard.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.dashboard.dto.HoldingsAnalytics.*;
import com.huit.library.modules.dashboard.repository.HoldingsAnalyticsRepository;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class MaterialClassificationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final HoldingsAnalyticsRepository holdings;
    private final ObjectMapper json;
    private final Validator validator;
    public MaterialClassificationService(NamedParameterJdbcTemplate jdbc,HoldingsAnalyticsRepository holdings,ObjectMapper json,Validator validator){this.jdbc=jdbc;this.holdings=holdings;this.json=json;this.validator=validator;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Classification get(Kind kind,long id){holdings.requireMaterial(kind,id,false);return classification(kind,id);}
    @Transactional
    public Classification put(Kind kind,long id,ClassificationPut request,UserEntity actor){
        if(actor==null||actor.getUserId()==null)throw error(HttpStatus.UNAUTHORIZED,"Không xác định được người gán ngành");
        if(!validator.validate(request).isEmpty())throw error(HttpStatus.BAD_REQUEST,"version, danh sách ngành và nguồn là bắt buộc; tối đa 100 ngành/1000 ký tự nguồn");
        var ids=request.majorIds().stream().map(String::trim).sorted().toList();
        if(ids.stream().distinct().count()!=ids.size())throw error(HttpStatus.BAD_REQUEST,"Danh sách ngành bị trùng");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",new MapSqlParameterSource("key","ACADEMIC:"+kind+":"+id),(org.springframework.jdbc.core.RowCallbackHandler)r->{});
        holdings.requireMaterial(kind,id,true);
        var catalog=holdings.catalog().majors();var selected=catalog.stream().filter(m->ids.contains(m.majorId())).sorted(Comparator.comparing(Major::majorId)).toList();
        if(selected.size()!=ids.size())throw error(HttpStatus.BAD_REQUEST,"Có ngành không thuộc danh mục chính thức");
        var previous=classification(kind,id);var before=previous.current();String source=request.sourceReference().trim();
        boolean same=before.majors().stream().map(Major::majorId).sorted().toList().equals(ids)&&Objects.equals(source,before.sourceReference());
        if(before.version()!=request.version()){
            if(before.version()==request.version()+1&&same)return previous;
            throw error(HttpStatus.CONFLICT,"Ánh xạ đã thay đổi; tải lại version mới");
        }
        if(same)return previous;
        var p=profile(kind,id);UUID profile=p==null?UUID.randomUUID():p.id();long next=before.version()+1;
        LocalDateTime at=LocalDateTime.now(ZoneId.of(CirculationAnalyticsService.TIMEZONE));String name=actor.getFullName()==null?actor.getUserId().toString():actor.getFullName();
        var params=new MapSqlParameterSource("profile",profile).addValue("kind",kind.name()).addValue("id",id).addValue("version",next).addValue("source",source).addValue("actor",actor.getUserId()).addValue("name",name).addValue("at",at);
        if(p==null)jdbc.update("INSERT INTO material_classifications(profile_id,material_kind,material_id,version,source_reference,updated_by,updated_by_name,updated_at) VALUES (:profile,:kind,:id,:version,:source,:actor,:name,:at)",params);
        else jdbc.update("UPDATE material_classifications SET version=:version,source_reference=:source,updated_by=:actor,updated_by_name=:name,updated_at=:at WHERE profile_id=:profile",params);
        jdbc.update("DELETE FROM material_major_assignments WHERE profile_id=:profile",params);
        for(String major:ids)jdbc.update("INSERT INTO material_major_assignments(assignment_id,profile_id,major_id) VALUES (:assignment,:profile,:major)",new MapSqlParameterSource("assignment",UUID.randomUUID()).addValue("profile",profile).addValue("major",major));
        var after=new ClassificationState(next,source,selected,actor.getUserId(),name,at);
        jdbc.update("INSERT INTO material_classification_history(event_id,profile_id,version,actor_id,actor_name,recorded_at,before_snapshot,after_snapshot) VALUES (:event,:profile,:version,:actor,:name,:at,:before,:after)",params.addValue("event",UUID.randomUUID()).addValue("before",write(before)).addValue("after",write(after)));
        return classification(kind,id);
    }
    private Classification classification(Kind kind,long id){
        var p=profile(kind,id);
        if(p==null)return new Classification(kind,id,new ClassificationState(0,null,List.of(),null,null,null),List.of());
        var majors=jdbc.query("SELECT "+HoldingsAnalyticsRepository.MAJOR_COLUMNS+" FROM material_major_assignments x JOIN academic_majors a ON a.major_id=x.major_id JOIN academic_faculties f ON f.faculty_id=a.faculty_id WHERE x.profile_id=:profile ORDER BY a.major_id",new MapSqlParameterSource("profile",p.id()),HoldingsAnalyticsRepository.MAJOR_MAPPER);
        var state=new ClassificationState(p.version(),p.source(),majors,p.actor(),p.name(),p.at());
        var events=jdbc.query("SELECT * FROM material_classification_history WHERE profile_id=:profile ORDER BY version",new MapSqlParameterSource("profile",p.id()),(r,n)->new ClassificationEvent(r.getObject("event_id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("actor_name"),r.getTimestamp("recorded_at").toLocalDateTime(),r.getString("before_snapshot")==null?null:read(r.getString("before_snapshot")),read(r.getString("after_snapshot"))));
        return new Classification(kind,id,state,events);
    }
    private Profile profile(Kind kind,long id){return jdbc.query("SELECT * FROM material_classifications WHERE material_kind=:kind AND material_id=:id",new MapSqlParameterSource("kind",kind.name()).addValue("id",id),(r,n)->new Profile(r.getObject("profile_id",UUID.class),r.getLong("version"),r.getString("source_reference"),r.getObject("updated_by",UUID.class),r.getString("updated_by_name"),r.getTimestamp("updated_at").toLocalDateTime())).stream().findFirst().orElse(null);}
    private record Profile(UUID id,long version,String source,UUID actor,String name,LocalDateTime at){}
    private String write(ClassificationState value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private ClassificationState read(String value){try{return json.readValue(value,ClassificationState.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
