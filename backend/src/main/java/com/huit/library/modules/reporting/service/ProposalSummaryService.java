package com.huit.library.modules.reporting.service;

import com.huit.library.modules.inventory.service.Isbn;
import com.huit.library.modules.reporting.dto.ProposalSummaryReport;
import com.huit.library.modules.reporting.dto.ProposalSummaryReport.*;
import com.huit.library.modules.reporting.repository.ProposalSummaryRepository;
import com.huit.library.modules.reporting.repository.StudentObligationReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProposalSummaryService {
    private final ProposalSummaryRepository repository;
    private final StudentObligationReportRepository academic;
    public ProposalSummaryService(ProposalSummaryRepository repository, StudentObligationReportRepository academic) {
        this.repository = repository; this.academic = academic;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProposalSummaryReport preview(String facultyId, LocalDate from, LocalDate to, Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page phải không âm; size từ 1 đến 100");
        return report(filter(facultyId, from, to, status), page, size);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProposalSummaryReport exportData(String facultyId, LocalDate from, LocalDate to, Status status) {
        return report(filter(facultyId, from, to, status), null, 0);
    }
    private Filter filter(String faculty, LocalDate from, LocalDate to, Status status) {
        String id = faculty == null || faculty.isBlank() ? null : faculty.trim();
        if (id != null && !id.equals("UNCLASSIFIED") && academic.faculties().stream().noneMatch(f -> f.facultyId().equals(id)))
            throw bad("facultyId không thuộc danh mục khoa");
        if (from != null && (from.getYear() < 1 || from.getYear() > 9999)
                || to != null && (to.getYear() < 1 || to.getYear() > 9999)) throw bad("Năm từ 0001 đến 9999");
        if (from != null && to != null && from.isAfter(to)) throw bad("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        return new Filter(id, from, to, status == null ? Status.ALL : status);
    }
    private ProposalSummaryReport report(Filter filter, Integer page, int size) {
        var sources = repository.sources(filter);
        var groups = aggregate(sources);
        long people = people(sources);
        int start = page == null ? 0 : (int) Math.min((long) page * size, groups.size());
        int end = page == null ? groups.size() : (int) Math.min((long) start + size, groups.size());
        return new ProposalSummaryReport(LocalDateTime.now(ZoneId.of(StudentObligationReportService.TIMEZONE)),
                StudentObligationReportService.TIMEZONE, filter, page == null ? 0 : page,
                page == null ? groups.size() : size, groups.size(), sources.size(), people, List.copyOf(groups.subList(start, end)));
    }

    /** Conservative grouping: no fuzzy title matching, no invalid ISBN merged into another identity. */
    public static List<Group> aggregate(List<Source> sources) {
        Map<Long, String> isbns = new HashMap<>();
        Map<String, List<Source>> byIsbn = new HashMap<>();
        for (var s : sources) {
            String isbn = canonical(s.isbn()); isbns.put(s.proposalId(), isbn);
            if (isbn != null) byIsbn.computeIfAbsent(isbn, k -> new ArrayList<>()).add(s);
        }
        Set<String> conflicts = byIsbn.entrySet().stream().filter(e ->
                distinct(e.getValue(), s -> normalize(s.edition())) > 1
                || distinct(e.getValue(), s -> s.publicationYear() == null ? "" : s.publicationYear().toString()) > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<String, List<Source>> buckets = new LinkedHashMap<>();
        for (var s : sources) {
            String isbn = isbns.get(s.proposalId());
            String key;
            if (isbn != null) key = "ISBN:" + isbn + (conflicts.contains(isbn) ? ":CONFLICT:" + metadata(s) : "");
            else if (s.isbn() != null && !s.isbn().isBlank()) key = "INVALID:" + s.proposalId();
            else if (normalize(s.title()).isEmpty() || normalize(s.authorName()).isEmpty()
                    || (normalize(s.edition()).isEmpty() && s.publicationYear() == null)) key = "INCOMPLETE:" + s.proposalId();
            else key = "METADATA:" + metadata(s);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        var result = new ArrayList<Group>();
        buckets.forEach((key, items) -> {
            Source first = items.get(0); String isbn = isbns.get(first.proposalId());
            var warnings = new ArrayList<String>();
            if (key.startsWith("INVALID:")) warnings.add("ISBN không hợp lệ: giữ riêng để kiểm tra");
            if (key.startsWith("INCOMPLETE:")) warnings.add("Thiếu tác giả hoặc thông tin ấn bản: giữ riêng");
            if (isbn != null && conflicts.contains(isbn)) warnings.add("Cùng ISBN nhưng khác ấn bản/năm: tách theo metadata để kiểm tra");
            if (items.stream().anyMatch(s -> s.facultyId().equals("UNCLASSIFIED"))) warnings.add("Có người gửi chưa xác định khoa");
            var faculties = items.stream().collect(Collectors.groupingBy(Source::facultyId, TreeMap::new, Collectors.toList()))
                    .entrySet().stream().map(e -> new FacultyCount(e.getKey(), e.getValue().get(0).facultyName(), e.getValue().size(), people(e.getValue()))).toList();
            String type = key.substring(0, key.indexOf(':'));
            result.add(new Group(hash(key), type, isbn, first.title(), first.authorName(), first.publisher(), first.publicationYear(),
                    first.edition(), items.size(), people(items), faculties, List.copyOf(warnings), List.copyOf(items)));
        });
        result.sort(Comparator.comparingLong(Group::proposalCount).reversed().thenComparing(g -> normalize(g.title())).thenComparing(Group::groupKey));
        return List.copyOf(result);
    }
    private static long distinct(List<Source> sources, java.util.function.Function<Source,String> f) {
        return sources.stream().map(f).filter(s -> !s.isEmpty()).distinct().count();
    }
    private static long people(List<Source> sources) { return sources.stream().map(Source::userId).filter(Objects::nonNull).distinct().count(); }
    private static String canonical(String raw) { try { return Isbn.canonical(raw); } catch (ResponseStatusException ignored) { return null; } }
    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Z}]+", " ").trim();
    }
    private static String metadata(Source s) {
        // Length prefixes prevent delimiter collisions in user-controlled bibliographic fields.
        return java.util.stream.Stream.of(normalize(s.title()), normalize(s.authorName()), normalize(s.publisher()),
                normalize(s.edition()), Objects.toString(s.publicationYear(), "")).map(v -> v.length() + ":" + v).collect(Collectors.joining());
    }
    private static String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
