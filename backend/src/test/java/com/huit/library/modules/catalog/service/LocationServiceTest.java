package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.LocationNodeDTO;
import com.huit.library.modules.catalog.dto.LocationRequestDTO;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.repository.BookshelfRepository;
import com.huit.library.modules.catalog.repository.FloorRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocationServiceTest {
    @Test
    void createBookshelfRequiresAndReturnsItsFloor() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        BookshelfRepository bookshelfRepository = mock(BookshelfRepository.class);
        LocationService service = new LocationService(floorRepository, bookshelfRepository,
                mock(ShelfRepository.class));
        FloorEntity floor = new FloorEntity();
        floor.setFloorId(2L);
        floor.setName("Tầng 2");
        when(floorRepository.findById(2L)).thenReturn(Optional.of(floor));
        when(bookshelfRepository.save(any())).thenAnswer(invocation -> {
            BookshelfEntity entity = invocation.getArgument(0);
            entity.setBookshelfId(5L);
            return entity;
        });
        LocationRequestDTO request = new LocationRequestDTO();
        request.setName("Kệ CNTT");
        request.setParentId(2L);

        LocationNodeDTO result = service.createBookshelf(request);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getParentId()).isEqualTo(2L);
        assertThat(result.getParentName()).isEqualTo("Tầng 2");
    }
}
