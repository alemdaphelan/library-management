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
    private final FloorRepository floorRepository;
    private final BookshelfRepository bookshelfRepository;
    private final ShelfRepository shelfRepository;
    private final BookCopyRepository bookCopyRepository;

    public LocationService(FloorRepository floorRepository, BookshelfRepository bookshelfRepository,
                           ShelfRepository shelfRepository, BookCopyRepository bookCopyRepository) {
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
        FloorEntity floor = floorRepository.findById(id).orElseThrow(() -> new RuntimeException("Floor not found"));
        floor.setName(request.getName().trim());
        return toFloor(floorRepository.save(floor));
    }

    @Transactional(readOnly = true)
    public List<LocationNodeDTO> getBookshelves(Long floorId) {
        return bookshelfRepository.findByFloor_FloorId(floorId).stream().map(this::toBookshelf).toList();
    }

    @Transactional
    public LocationNodeDTO createBookshelf(LocationRequestDTO request) {
        FloorEntity floor = floorRepository.findById(requireParent(request))
                .orElseThrow(() -> new RuntimeException("Floor not found"));
        BookshelfEntity bookshelf = new BookshelfEntity();
        bookshelf.setName(request.getName().trim());
        bookshelf.setFloor(floor);
        return toBookshelf(bookshelfRepository.save(bookshelf));
    }

    @Transactional
    public LocationNodeDTO updateBookshelf(Long id, LocationRequestDTO request) {
        BookshelfEntity bookshelf = bookshelfRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bookshelf not found"));
        bookshelf.setName(request.getName().trim());
        if (request.getParentId() != null) {
            bookshelf.setFloor(floorRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Floor not found")));
        }
        return toBookshelf(bookshelfRepository.save(bookshelf));
    }

    @Transactional(readOnly = true)
    public List<LocationNodeDTO> getShelves(Long bookshelfId) {
        return shelfRepository.findByBookshelf_BookshelfId(bookshelfId).stream().map(this::toShelf).toList();
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
        BookshelfEntity bookshelf = bookshelfRepository.findById(requireParent(request))
                .orElseThrow(() -> new RuntimeException("Bookshelf not found"));
        ShelfEntity shelf = new ShelfEntity();
        shelf.setName(request.getName().trim());
        shelf.setBookshelf(bookshelf);
        return toShelf(shelfRepository.save(shelf));
    }

    @Transactional
    public LocationNodeDTO updateShelf(Long id, LocationRequestDTO request) {
        ShelfEntity shelf = shelfRepository.findById(id).orElseThrow(() -> new RuntimeException("Shelf not found"));
        shelf.setName(request.getName().trim());
        if (request.getParentId() != null) {
            shelf.setBookshelf(bookshelfRepository.findById(request.getParentId())
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
        if (request.getParentId() == null) throw new IllegalArgumentException("parentId is required");
        return request.getParentId();
    }

    private LocationNodeDTO toFloor(FloorEntity floor) {
        return LocationNodeDTO.builder().id(floor.getFloorId()).name(floor.getName()).type("FLOOR").build();
    }

    private LocationNodeDTO toBookshelf(BookshelfEntity bookshelf) {
        FloorEntity floor = bookshelf.getFloor();
        return LocationNodeDTO.builder().id(bookshelf.getBookshelfId()).name(bookshelf.getName())
                .type("BOOKSHELF").parentId(floor == null ? null : floor.getFloorId())
                .parentName(floor == null ? null : floor.getName()).build();
    }

    private LocationNodeDTO toShelf(ShelfEntity shelf) {
        BookshelfEntity bookshelf = shelf.getBookshelf();
        return LocationNodeDTO.builder().id(shelf.getShelfId()).name(shelf.getName())
                .type("SHELF").parentId(bookshelf == null ? null : bookshelf.getBookshelfId())
                .parentName(bookshelf == null ? null : bookshelf.getName()).build();
    }
}
