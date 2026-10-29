package com.huit.library.modules.dashboard.repository;
import com.huit.library.modules.dashboard.dto.HoldingsAnalytics.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class HoldingsAnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public HoldingsAnalyticsRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String[] COPY_FIELDS={"total_copies","available_copies","borrowed_copies","reserved_copies","lost_copies","damaged_copies","maintenance_copies","retired_copies","other_copies"};
    public static final String MAJOR_COLUMNS="a.major_id,a.major_name,a.major_code,a.faculty_id,f.faculty_name";
    public static final RowMapper<Major> MAJOR_MAPPER=(rs,n)->new Major(rs.getString("major_id"),rs.getString("major_name"),rs.getString("major_code"),rs.getString("faculty_id"),rs.getString("faculty_name"));
    public Catalog catalog(){return new Catalog(jdbc.query("SELECT faculty_id,faculty_name FROM academic_faculties ORDER BY faculty_name,faculty_id",(rs,n)->new Faculty(rs.getString(1),rs.getString(2))),jdbc.query("SELECT "+MAJOR_COLUMNS+" FROM academic_majors a JOIN academic_faculties f ON f.faculty_id=a.faculty_id ORDER BY f.faculty_name,a.major_name,a.major_id",MAJOR_MAPPER));}
    private String cte(Filter filter){
        String counters=String.join(",",Arrays.stream(COPY_FIELDS).map(f->"coalesce(c."+f+",0) "+f).toList());
        String zeros=String.join(",",Arrays.stream(COPY_FIELDS).map(f->"0::bigint "+f).toList());
        return """
                WITH copy_states AS (SELECT c.*, CASE
                  WHEN upper(c.status) IN ('BORROWING','BORROWED','OVERDUE') THEN 'BORROWING'
                  WHEN upper(c.status)='RESERVED' OR (upper(c.status)='AVAILABLE' AND (c.locked_until>:snapshot OR (c.locked_by_user IS NOT NULL AND c.locked_until IS NULL))) THEN 'RESERVED'
                  WHEN upper(c.status)='AVAILABLE' THEN 'AVAILABLE'
                  WHEN upper(c.status)='LOST' THEN 'LOST' WHEN upper(c.status)='DAMAGED' THEN 'DAMAGED'
                  WHEN upper(c.status)='MAINTENANCE' THEN 'MAINTENANCE'
                  WHEN upper(c.status) IN ('RETIRED','WITHDRAWN','DISPOSED','LIQUIDATED','DESTROYED') THEN 'RETIRED' ELSE 'OTHER' END bucket FROM book_copies c),
                copy_counts AS (SELECT book_id,count(*) total_copies,
                  count(*) FILTER(WHERE bucket='AVAILABLE') available_copies,count(*) FILTER(WHERE bucket='BORROWING') borrowed_copies,
                  count(*) FILTER(WHERE bucket='RESERVED') reserved_copies,count(*) FILTER(WHERE bucket='LOST') lost_copies,
                  count(*) FILTER(WHERE bucket='DAMAGED') damaged_copies,count(*) FILTER(WHERE bucket='MAINTENANCE') maintenance_copies,
                  count(*) FILTER(WHERE bucket='RETIRED') retired_copies,count(*) FILTER(WHERE bucket='OTHER') other_copies FROM copy_states GROUP BY book_id),
                files AS (SELECT book_id,count(*) digital_files FROM digital_documents WHERE book_id IS NOT NULL GROUP BY book_id),
                raw_materials AS (SELECT 'BOOK' kind,b.book_id id,b.title,b.author,
                  (NOT coalesce(b.is_digital,false) OR coalesce(c.total_copies,0)>0) has_physical,
                  (coalesce(b.is_digital,false) OR coalesce(f.digital_files,0)>0) has_digital,
                  %s,coalesce(f.digital_files,0) digital_files FROM books b LEFT JOIN copy_counts c ON c.book_id=b.book_id LEFT JOIN files f ON f.book_id=b.book_id
                UNION ALL SELECT 'DIGITAL',d.document_id,coalesce(nullif(d.display_name,''),d.original_filename,'Tài liệu số #'||d.document_id),NULL,false,true,%s,1 FROM digital_documents d WHERE d.book_id IS NULL),
                materials AS (SELECT m.*,CASE WHEN has_physical AND has_digital THEN 'HYBRID' WHEN has_digital THEN 'DIGITAL' ELSE 'PHYSICAL' END type FROM raw_materials m),
                profiled AS (SELECT m.*,p.profile_id,EXISTS(SELECT 1 FROM material_major_assignments x WHERE x.profile_id=p.profile_id) mapped FROM materials m
                  LEFT JOIN material_classifications p ON p.material_kind=m.kind AND p.material_id=m.id),
                selected AS (SELECT m.* FROM profiled m WHERE %s)
                """.formatted(counters,zeros,where(filter));
    }
    private String where(Filter f){
        var clauses=new ArrayList<String>();
        if("UNCLASSIFIED".equals(f.facultyId())||"UNCLASSIFIED".equals(f.majorId()))clauses.add("NOT m.mapped");
        else if(f.facultyId()!=null||f.majorId()!=null)clauses.add("EXISTS(SELECT 1 FROM material_major_assignments x JOIN academic_majors a ON a.major_id=x.major_id WHERE x.profile_id=m.profile_id"
                +(f.facultyId()!=null?" AND a.faculty_id=:faculty":"")+(f.majorId()!=null?" AND a.major_id=:major":"")+")");
        if(f.type()!=MaterialType.ALL)clauses.add("m.type=:type");
        if(!f.query().isEmpty())clauses.add("(strpos(lower(coalesce(m.title,'')),lower(:query))>0 OR strpos(lower(coalesce(m.author,'')),lower(:query))>0 OR m.id::text=:query)");
        clauses.add(switch(f.state()){
            case ALL -> "true";case AVAILABLE -> "m.available_copies>0";case BORROWING -> "m.borrowed_copies>0";case RESERVED -> "m.reserved_copies>0";
            case LOST -> "m.lost_copies>0";case DAMAGED -> "m.damaged_copies>0";case MAINTENANCE -> "m.maintenance_copies>0";case RETIRED -> "m.retired_copies>0";
            case NO_COPIES -> "m.has_physical AND m.total_copies=0";case NO_DIGITAL_FILE -> "m.has_digital AND m.digital_files=0";
        });return String.join(" AND ",clauses);
    }
    private MapSqlParameterSource params(Filter f,LocalDateTime at){return new MapSqlParameterSource("snapshot",at).addValue("faculty",f.facultyId()).addValue("major",f.majorId()).addValue("type",f.type().name()).addValue("query",f.query());}
    private String aggregates(){return "count(*) materials,count(*) FILTER(WHERE kind='BOOK') catalog_titles,count(*) FILTER(WHERE has_physical) physical_titles,count(*) FILTER(WHERE has_digital) digital_titles,count(*) FILTER(WHERE kind='DIGITAL') standalone_digital_titles,"
            +String.join(",",Arrays.stream(COPY_FIELDS).map(f->"coalesce(sum("+f+"),0) "+f).toList())+",coalesce(sum(digital_files),0) digital_files,count(*) FILTER(WHERE NOT mapped) unmapped_materials";}
    private Counts counts(ResultSet r)throws SQLException{return new Counts(r.getLong("materials"),r.getLong("catalog_titles"),r.getLong("physical_titles"),r.getLong("digital_titles"),r.getLong("standalone_digital_titles"),r.getLong("total_copies"),r.getLong("available_copies"),r.getLong("borrowed_copies"),r.getLong("reserved_copies"),r.getLong("lost_copies"),r.getLong("damaged_copies"),r.getLong("maintenance_copies"),r.getLong("retired_copies"),r.getLong("other_copies"),r.getLong("digital_files"),r.getLong("unmapped_materials"));}
    public Counts totals(Filter f,LocalDateTime at){return jdbc.queryForObject(cte(f)+"SELECT "+aggregates()+" FROM selected",params(f,at),(r,n)->counts(r));}
    public List<Group> groups(Filter f,LocalDateTime at,boolean byMajor){
        String fields=byMajor?",a.major_id,a.major_name":"";
        String sql=cte(f)+",relation AS (SELECT DISTINCT s.*,a.faculty_id,f.faculty_name"+fields+" FROM selected s LEFT JOIN material_major_assignments x ON x.profile_id=s.profile_id LEFT JOIN academic_majors a ON a.major_id=x.major_id LEFT JOIN academic_faculties f ON f.faculty_id=a.faculty_id) "
                +"SELECT faculty_id,faculty_name"+(byMajor?",major_id,major_name":"")+","+aggregates()+" FROM relation"
                +(f.facultyId()!=null&&!f.facultyId().equals("UNCLASSIFIED")?" WHERE faculty_id=:faculty":"")
                +(f.majorId()!=null&&!f.majorId().equals("UNCLASSIFIED")?(f.facultyId()!=null?" AND ":" WHERE ")+"major_id=:major":"")
                +" GROUP BY faculty_id,faculty_name"+(byMajor?",major_id,major_name":"")+" ORDER BY faculty_name NULLS LAST"+(byMajor?",major_name NULLS LAST":"");
        // A major filter must not hide its faculty; filter relation by major only for major groups.
        if(!byMajor&&f.majorId()!=null&&!f.majorId().equals("UNCLASSIFIED")) {
            sql=cte(f)+",relation AS (SELECT DISTINCT s.*,a.faculty_id,f.faculty_name FROM selected s JOIN material_major_assignments x ON x.profile_id=s.profile_id JOIN academic_majors a ON a.major_id=x.major_id JOIN academic_faculties f ON f.faculty_id=a.faculty_id WHERE a.major_id=:major) SELECT faculty_id,faculty_name,"+aggregates()+" FROM relation GROUP BY faculty_id,faculty_name ORDER BY faculty_name";
        }
        return jdbc.query(sql,params(f,at),(r,n)->new Group(Objects.toString(r.getString("faculty_id"),"UNCLASSIFIED"),Objects.toString(r.getString("faculty_name"),"Chưa phân loại"),byMajor?Objects.toString(r.getString("major_id"),"UNCLASSIFIED"):null,byMajor?Objects.toString(r.getString("major_name"),"Chưa phân loại"):null,counts(r)));
    }
    public long count(Filter f,LocalDateTime at){return jdbc.queryForObject(cte(f)+"SELECT count(*) FROM selected",params(f,at),Long.class);}
    public Coverage coverage(){return jdbc.queryForObject("SELECT (SELECT count(*) FROM book_copies) copies,(SELECT count(*) FROM book_copies c LEFT JOIN books b ON b.book_id=c.book_id WHERE b.book_id IS NULL) missing_copies,(SELECT count(*) FROM digital_documents d LEFT JOIN books b ON b.book_id=d.book_id WHERE d.book_id IS NOT NULL AND b.book_id IS NULL) missing_files",new MapSqlParameterSource(),(r,n)->new Coverage(r.getLong("copies"),r.getLong("missing_copies"),r.getLong("missing_files")));}
    public List<Material> materials(Filter f,LocalDateTime at,int page,int size){
        var rows=jdbc.query(cte(f)+"SELECT * FROM selected ORDER BY lower(title) NULLS LAST,kind,id LIMIT :limit OFFSET :offset",params(f,at).addValue("limit",size).addValue("offset",(long)page*size),(r,n)->new MaterialRow(Kind.valueOf(r.getString("kind")),r.getLong("id"),r.getString("title"),r.getString("author"),MaterialType.valueOf(r.getString("type")),r.getBoolean("mapped"),r.getObject("profile_id",UUID.class),new Counts(1,r.getString("kind").equals("BOOK")?1:0,r.getBoolean("has_physical")?1:0,r.getBoolean("has_digital")?1:0,r.getString("kind").equals("DIGITAL")?1:0,r.getLong("total_copies"),r.getLong("available_copies"),r.getLong("borrowed_copies"),r.getLong("reserved_copies"),r.getLong("lost_copies"),r.getLong("damaged_copies"),r.getLong("maintenance_copies"),r.getLong("retired_copies"),r.getLong("other_copies"),r.getLong("digital_files"),r.getBoolean("mapped")?0:1)));
        var profiles=rows.stream().map(MaterialRow::profileId).filter(Objects::nonNull).distinct().toList();var majors=new HashMap<UUID,List<Major>>();
        if(!profiles.isEmpty())jdbc.query("SELECT x.profile_id,"+MAJOR_COLUMNS+" FROM material_major_assignments x JOIN academic_majors a ON a.major_id=x.major_id JOIN academic_faculties f ON f.faculty_id=a.faculty_id WHERE x.profile_id IN (:ids) ORDER BY a.major_id",new MapSqlParameterSource("ids",profiles),(org.springframework.jdbc.core.RowCallbackHandler)r->majors.computeIfAbsent(r.getObject("profile_id",UUID.class),k->new ArrayList<>()).add(MAJOR_MAPPER.mapRow(r,0)));
        return rows.stream().map(r->new Material(r.kind(),r.id(),r.title(),r.author(),r.type(),r.mapped()?"MAPPED":"UNMAPPED",r.counts(),List.copyOf(majors.getOrDefault(r.profileId(),List.of())),r.kind()==Kind.BOOK?"/book/"+r.id():null,"/dashboard/analytics/materials/"+r.kind()+"/"+r.id()+"/copies","/dashboard/analytics/materials/"+r.kind()+"/"+r.id()+"/digital-assets")).toList();
    }
    private record MaterialRow(Kind kind,long id,String title,String author,MaterialType type,boolean mapped,UUID profileId,Counts counts){}
    public void requireMaterial(Kind kind,long id,boolean lock){
        if(id<1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"ID tài liệu phải dương");
        String sql=kind==Kind.BOOK?"SELECT book_id link FROM books WHERE book_id=:id":"SELECT book_id link FROM digital_documents WHERE document_id=:id";
        var rows=jdbc.query(sql+(lock?" FOR UPDATE":""),new MapSqlParameterSource("id",id),(r,n)->r.getObject("link",Long.class));
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy tài liệu");
        if(kind==Kind.DIGITAL&&rows.get(0)!=null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Tệp thuộc đầu sách BOOK/"+rows.get(0)+"; dùng đầu sách để thống kê/gán ngành");
    }
    public long copyCount(Kind kind,long id){return kind==Kind.DIGITAL?0:jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE book_id=:id",new MapSqlParameterSource("id",id),Long.class);}
    public List<Copy> copies(Kind kind,long id,int page,int size){if(kind==Kind.DIGITAL)return List.of();return jdbc.query("SELECT c.barcode,c.status,c.condition_note,c.shelf_id,concat_ws(' / ',f.name,b.name,s.name) location FROM book_copies c LEFT JOIN shelves s ON s.shelf_id=c.shelf_id LEFT JOIN bookshelves b ON b.bookshelf_id=s.bookshelf_id LEFT JOIN floors f ON f.floor_id=b.floor_id WHERE c.book_id=:id ORDER BY c.barcode LIMIT :limit OFFSET :offset",new MapSqlParameterSource("id",id).addValue("limit",size).addValue("offset",(long)page*size),(r,n)->new Copy(r.getString("barcode"),r.getString("status"),r.getString("condition_note"),r.getObject("shelf_id",Long.class),r.getString("location"),"/book-copies/"+java.net.URLEncoder.encode(r.getString("barcode"),java.nio.charset.StandardCharsets.UTF_8)));}
    private String assetWhere(Kind kind){return kind==Kind.BOOK?"book_id=:id":"document_id=:id AND book_id IS NULL";}
    public long assetCount(Kind kind,long id){return jdbc.queryForObject("SELECT count(*) FROM digital_documents WHERE "+assetWhere(kind),new MapSqlParameterSource("id",id),Long.class);}
    public List<DigitalAsset> assets(Kind kind,long id,int page,int size){return jdbc.query("SELECT document_id,coalesce(display_name,original_filename) name,format,file_size,access_level FROM digital_documents WHERE "+assetWhere(kind)+" ORDER BY document_id LIMIT :limit OFFSET :offset",new MapSqlParameterSource("id",id).addValue("limit",size).addValue("offset",(long)page*size),(r,n)->new DigitalAsset(r.getLong("document_id"),r.getString("name"),r.getString("format"),r.getObject("file_size",Long.class),r.getString("access_level"),"/digital-assets/"+r.getLong("document_id")+"/download"));}
}
