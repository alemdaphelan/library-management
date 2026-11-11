package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.LocationNodeDTO;
import com.huit.library.modules.catalog.dto.LocationRequestDTO;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookshelfRepository;
import com.huit.library.modules.catalog.repository.FloorRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.dto.ShelfBookDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {
    public record CopiesPage(List<ShelfBookDTO> items, long total, int page, int size) {}

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CopiesPage getCopiesPage(Long shelfId, int page, int size) {
        return getCopiesPage(shelfId, null, page, size);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CopiesPage getCopiesPage(Long shelfId, Long targetShelf, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Trang phải từ 0; số dòng từ 1 đến 100; vị trí phân trang không vượt giới hạn hỗ trợ");
        }
        Long selectedShelf = shelfId == null ? targetShelf : shelfId;
        if (selectedShelf != null && !shelfRepository.existsById(selectedShelf)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy ngăn kệ");
        }
        var result =
                bookCopyRepository.findLocationBooks(
                        shelfId,
                        targetShelf,
                        org.springframework.data.domain.PageRequest.of(page, size));
        return new CopiesPage(result.getContent(), result.getTotalElements(), page, size);
    }

    private final FloorRepository floorRepository;
    private final BookshelfRepository bookshelfRepository;
    private final ShelfRepository shelfRepository;
    private final BookCopyRepository bookCopyRepository;

    public LocationService(
            FloorRepository floorRepository,
            BookshelfRepository bookshelfRepository,
            ShelfRepository shelfRepository,
            BookCopyRepository bookCopyRepository) {
        this.floorRepository = floorRepository;
        this.bookshelfRepository = bookshelfRepository;
        this.shelfRepository = shelfRepository;
        this.bookCopyRepository = bookCopyRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationNodeDTO> getFloors() {
        return floorRepository.findAll().stream().map(this::toFloor).toList();
    }

    @Transactional
    public LocationNodeDTO createFloor(LocationRequestDTO request) {
        FloorEntity floor = new FloorEntity();
        floor.setName(request.getName().trim());
        return toFloor(floorRepository.save(floor));
    }

    @Transactional
    public LocationNodeDTO updateFloor(Long id, LocationRequestDTO request) {
        FloorEntity floor =
                floorRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Floor not found"));
        floor.setName(request.getName().trim());
        return toFloor(floorRepository.save(floor));
    }

    @Transactional(readOnly = true)
    public List<LocationNodeDTO> getBookshelves(Long floorId) {
        return bookshelfRepository.findByFloor_FloorId(floorId).stream()
                .map(this::toBookshelf)
                .toList();
    }

    @Transactional
    public LocationNodeDTO createBookshelf(LocationRequestDTO request) {
        FloorEntity floor =
                floorRepository
                        .findById(requireParent(request))
                        .orElseThrow(() -> new RuntimeException("Floor not found"));
        BookshelfEntity bookshelf = new BookshelfEntity();
        bookshelf.setName(request.getName().trim());
        bookshelf.setFloor(floor);
        return toBookshelf(bookshelfRepository.save(bookshelf));
    }

    @Transactional
    public LocationNodeDTO updateBookshelf(Long id, LocationRequestDTO request) {
        BookshelfEntity bookshelf =
                bookshelfRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Bookshelf not found"));
        bookshelf.setName(request.getName().trim());
        if (request.getParentId() != null) {
            bookshelf.setFloor(
                    floorRepository
                            .findById(request.getParentId())
                            .orElseThrow(() -> new RuntimeException("Floor not found")));
        }
        return toBookshelf(bookshelfRepository.save(bookshelf));
    }

    @Transactional(readOnly = true)
    public List<LocationNodeDTO> getShelves(Long bookshelfId) {
        return shelfRepository.findByBookshelf_BookshelfId(bookshelfId).stream()
                .map(this::toShelf)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShelfBookDTO> getShelfBooks(Long shelfId) {
        if (!shelfRepository.existsById(shelfId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy ngăn kệ");
        }
        return bookCopyRepository.findShelfBooks(shelfId);
    }

    @Transactional
    public LocationNodeDTO createShelf(LocationRequestDTO request) {
        BookshelfEntity bookshelf =
                bookshelfRepository
                        .findById(requireParent(request))
                        .orElseThrow(() -> new RuntimeException("Bookshelf not found"));
        ShelfEntity shelf = new ShelfEntity();
        shelf.setName(request.getName().trim());
        shelf.setBookshelf(bookshelf);
        if (request.getCapacity() != null && request.getCapacity() > 0)
            shelf.setCapacity(request.getCapacity());
        return toShelf(shelfRepository.save(shelf));
    }

    @Transactional
    public LocationNodeDTO updateShelf(Long id, LocationRequestDTO request) {
        ShelfEntity shelf =
                shelfRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new RuntimeException("Shelf not found"));
        Integer capacity = request.getCapacity();
        if (capacity != null) {
            if (capacity > 0 && bookCopyRepository.countByShelf_ShelfId(id) > capacity) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Sức chứa không được nhỏ hơn số bản sao đang xếp trong ngăn");
            }
            shelf.setCapacity(capacity == 0 ? null : capacity);
        }
        shelf.setName(request.getName().trim());
        if (request.getParentId() != null) {
            shelf.setBookshelf(
                    bookshelfRepository
                            .findById(request.getParentId())
                            .orElseThrow(() -> new RuntimeException("Bookshelf not found")));
        }
        return toShelf(shelfRepository.save(shelf));
    }

    @Transactional
    public void deleteFloor(Long id) {
        floorRepository.deleteById(id);
    }

    @Transactional
    public void deleteBookshelf(Long id) {
        bookshelfRepository.deleteById(id);
    }

    @Transactional
    public void deleteShelf(Long id) {
        shelfRepository.deleteById(id);
    }

    private Long requireParent(LocationRequestDTO request) {
        if (request.getParentId() == null)
            throw new IllegalArgumentException("parentId is required");
        return request.getParentId();
    }

    private LocationNodeDTO toFloor(FloorEntity floor) {
        return LocationNodeDTO.builder()
                .id(floor.getFloorId())
                .name(floor.getName())
                .type("FLOOR")
                .build();
    }

    private LocationNodeDTO toBookshelf(BookshelfEntity bookshelf) {
        FloorEntity floor = bookshelf.getFloor();
        return LocationNodeDTO.builder()
                .id(bookshelf.getBookshelfId())
                .name(bookshelf.getName())
                .type("BOOKSHELF")
                .parentId(floor == null ? null : floor.getFloorId())
                .parentName(floor == null ? null : floor.getName())
                .build();
    }

    private LocationNodeDTO toShelf(ShelfEntity shelf) {
        BookshelfEntity bookshelf = shelf.getBookshelf();
        return LocationNodeDTO.builder()
                .id(shelf.getShelfId())
                .name(shelf.getName())
                .capacity(shelf.getCapacity())
                .type("SHELF")
                .parentId(bookshelf == null ? null : bookshelf.getBookshelfId())
                .parentName(bookshelf == null ? null : bookshelf.getName())
                .build();
    }
}
