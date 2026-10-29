package com.huit.library.modules.dashboard.dto;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class HoldingsAnalytics {
    private HoldingsAnalytics() {}
    public enum Kind { BOOK, DIGITAL }
    public enum MaterialType { ALL, PHYSICAL, DIGITAL, HYBRID }
    public enum State { ALL, AVAILABLE, BORROWING, RESERVED, LOST, DAMAGED, MAINTENANCE, RETIRED, NO_COPIES, NO_DIGITAL_FILE }
    public record Filter(String facultyId, String majorId, MaterialType type, State state, String query) {
        public Filter(String facultyId,String majorId,MaterialType type,State state){this(facultyId,majorId,type,state,"");}
    }
    public record Major(String majorId, String majorName, String majorCode, String facultyId, String facultyName) {}
    public record Faculty(String facultyId, String facultyName) {}
    public record Catalog(List<Faculty> faculties, List<Major> majors) {}
    public record Counts(long materials, long catalogTitles, long physicalTitles, long digitalTitles, long standaloneDigitalTitles,
            long totalCopies, long availableCopies, long borrowedCopies, long reservedCopies, long lostCopies, long damagedCopies,
            long maintenanceCopies, long retiredCopies, long otherCopies, long digitalFiles, long unmappedMaterials) {}
    public record Group(String facultyId, String facultyName, String majorId, String majorName, Counts counts) {}
    public record Coverage(long totalPhysicalCopies, long copiesWithoutCurrentCatalog, long linkedDigitalFilesWithoutCurrentCatalog) {}
    public record Summary(LocalDateTime snapshotAt, String timezone, Filter filter, Counts totals, List<Group> faculties, List<Group> majors, Coverage coverage) {}
    public record Material(Kind kind, long id, String title, String author, MaterialType type, String mappingStatus,
            Counts counts, List<Major> majors, String bookUrl, String copiesApi, String digitalAssetsApi) {}
    public record MaterialPage(LocalDateTime snapshotAt, String timezone, Filter filter, int page, int size, long totalMaterials, List<Material> materials) {}
    public record Copy(String barcode, String status, String conditionNote, Long shelfId, String location, String copyApi) {}
    public record DigitalAsset(long id, String name, String format, Long size, String accessLevel, String downloadUrl) {}
    public record DetailPage<T>(LocalDateTime snapshotAt, String timezone, Kind kind, long id, int page, int size, long total, List<T> items) {}
    public record ClassificationPut(@NotNull @PositiveOrZero Long version, @NotNull @Size(max=100) List<@NotBlank @Size(max=128) String> majorIds,
            @NotBlank @Size(max=1000) String sourceReference) {}
    public record ClassificationState(long version, String sourceReference, List<Major> majors, UUID updatedBy, String updatedByName, LocalDateTime updatedAt) {}
    public record ClassificationEvent(UUID eventId, UUID actorId, String actorName, LocalDateTime at, ClassificationState before, ClassificationState after) {}
    public record Classification(Kind kind, long id, ClassificationState current, List<ClassificationEvent> history) {}
}
