package com.huit.library.modules.inventory.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.inventory.dto.ReceiptLineResponseDTO;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.inventory.dto.GoodsReceiptResponseDTO;
import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import com.huit.library.modules.inventory.entity.ReceiptDetailEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.repository.GoodsReceiptRepository;
import com.huit.library.modules.inventory.repository.ReceiptDetailRepository;
import com.huit.library.modules.inventory.repository.SupplierRepository;
import com.huit.library.modules.notification.service.StaffNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final ReceiptDetailRepository receiptDetailRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final StaffNotificationService staffNotificationService;
    private final ShelfRepository shelfRepository;

    public InventoryService(GoodsReceiptRepository goodsReceiptRepository, SupplierRepository supplierRepository,
                            ReceiptDetailRepository receiptDetailRepository, BookRepository bookRepository,
                            BookCopyRepository bookCopyRepository,
                            StaffNotificationService staffNotificationService, ShelfRepository shelfRepository) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.receiptDetailRepository = receiptDetailRepository;
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.staffNotificationService = staffNotificationService;
        this.shelfRepository = shelfRepository;
    }

    public List<SupplierEntity> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public SupplierEntity createSupplier(SupplierEntity supplier) {
        return supplierRepository.save(supplier);
    }

    @Transactional
    public GoodsReceiptResponseDTO importBooks(ImportReceiptRequestDTO request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) throw invalid("Phiếu nhập cần ít nhất một dòng");
        for (var line : request.getLines()) {
            if (line == null || line.getShelfId() == null || line.getShelfId() <= 0 || line.getQuantity() == null || line.getQuantity() < 1
                    || line.getUnitPrice() == null || line.getUnitPrice().signum() < 0) throw invalid("Mỗi dòng cần số lượng dương, đơn giá không âm và ngăn hợp lệ");
        }
        SupplierEntity supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"));
        var shelfIds = request.getLines().stream().map(ImportReceiptRequestDTO.Line::getShelfId).distinct().toList();
        // Shared hierarchy locks block moves/deletes while allowing concurrent receipts to use the same shelf.
        var locations = shelfRepository.findImportLocationsForUpdate(shelfIds).stream()
                .collect(Collectors.toMap(ShelfEntity::getShelfId, Function.identity()));
        var prepared = new ArrayList<PreparedLine>();
        for (var line : request.getLines()) {
            ShelfEntity shelf = locations.get(line.getShelfId());
            if (shelf == null || shelf.getBookshelf() == null || shelf.getBookshelf().getFloor() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ngăn không tồn tại hoặc thiếu cây tầng/kệ. Hãy tải lại vị trí");
            }
            var bookshelf = shelf.getBookshelf();
            if ((line.getBookshelfId() != null && !line.getBookshelfId().equals(bookshelf.getBookshelfId()))
                    || (line.getFloorId() != null && !line.getFloorId().equals(bookshelf.getFloor().getFloorId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cây vị trí đã thay đổi hoặc ngăn không thuộc tầng/kệ đã chọn; cần tải lại");
            }
            if (blank(shelf.getName()) || blank(bookshelf.getName()) || blank(bookshelf.getFloor().getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Vị trí thiếu tên tầng/kệ/ngăn, cần sửa trước khi nhập");
            }
            String path = bookshelf.getFloor().getName() + " / " + bookshelf.getName() + " / " + shelf.getName();
            prepared.add(new PreparedLine(line, resolveBook(line), shelf, path));
        }
        BigDecimal total = request.getLines().stream()
                .map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        GoodsReceiptEntity receipt = new GoodsReceiptEntity();
        receipt.setSupplierId(supplier.getSupplierId());
        receipt.setReceiptCode("PN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        receipt.setTotalAmount(total);
        receipt.setStatus("COMPLETED");
        receipt = goodsReceiptRepository.save(receipt);

        int quantity = 0;
        var responseLines = new ArrayList<ReceiptLineResponseDTO>();
        for (PreparedLine preparedLine : prepared) {
            var line = preparedLine.line();
            BookEntity book = preparedLine.book();
            ReceiptDetailEntity detail = new ReceiptDetailEntity();
            detail.setReceiptId(receipt.getReceiptId());
            detail.setBookId(book.getBookId());
            detail.setQuantity(line.getQuantity());
            detail.setUnitPrice(line.getUnitPrice());
            detail.setShelfId(preparedLine.shelf().getShelfId());
            detail.setLocationPath(preparedLine.path());
            detail = receiptDetailRepository.save(detail);

            var barcodes = new ArrayList<String>();
            for (int i = 0; i < line.getQuantity(); i++) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setBarcode("BC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
                copy.setBookId(book.getBookId());
                copy.setStatus("AVAILABLE");
                copy.setShelf(preparedLine.shelf());
                copy.setReceiptDetailId(detail.getDetailId());
                bookCopyRepository.save(copy);
                barcodes.add(copy.getBarcode());
            }
            responseLines.add(new ReceiptLineResponseDTO(detail.getDetailId(), book.getBookId(), book.getTitle(), book.getIsbn(),
                    line.getQuantity(), line.getUnitPrice(), detail.getShelfId(), detail.getLocationPath(), barcodes));
            quantity = Math.addExact(quantity, line.getQuantity());
        }
        receipt.setCompletedAt(java.time.LocalDateTime.now());
        goodsReceiptRepository.save(receipt);
        GoodsReceiptResponseDTO response = toResponse(receipt, supplier, quantity);
        response.setLines(responseLines);
        staffNotificationService.notifyAll(
                "IMPORT_COMPLETED",
                "Phiếu " + receipt.getReceiptCode() + " đã nhập " + quantity
                        + " bản sao từ " + supplier.getName() + ".",
                "GOODS_RECEIPT", String.valueOf(receipt.getReceiptId()),
                "IMPORT_COMPLETED:" + receipt.getReceiptId(), java.time.LocalDateTime.now());
        return response;
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponseDTO getReceipt(Long id) {
        var receipt = goodsReceiptRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu nhập"));
        var details = receiptDetailRepository.findByReceiptId(id);
        var copies = details.isEmpty() ? List.<BookCopyEntity>of() : bookCopyRepository.findByReceiptDetailIdIn(details.stream().map(ReceiptDetailEntity::getDetailId).toList());
        var barcodes = copies.stream().collect(Collectors.groupingBy(BookCopyEntity::getReceiptDetailId,
                Collectors.mapping(BookCopyEntity::getBarcode, Collectors.toList())));
        var books = bookRepository.findAllById(details.stream().map(ReceiptDetailEntity::getBookId).distinct().toList()).stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));
        var result = toResponse(receipt, supplierRepository.findById(receipt.getSupplierId()).orElse(null), details.stream().mapToInt(ReceiptDetailEntity::getQuantity).sum());
        result.setLines(details.stream().map(line -> {
            var book = books.get(line.getBookId());
            return new ReceiptLineResponseDTO(line.getDetailId(), line.getBookId(), book == null ? null : book.getTitle(), book == null ? null : book.getIsbn(),
                    line.getQuantity(), line.getUnitPrice(), line.getShelfId(), line.getLocationPath(), barcodes.getOrDefault(line.getDetailId(), List.of()).stream().sorted().toList());
        }).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponseDTO> getAllReceipts() {
        return goodsReceiptRepository.findAll().stream().map(receipt -> {
            SupplierEntity supplier = supplierRepository.findById(receipt.getSupplierId()).orElse(null);
            int quantity = receiptDetailRepository.findByReceiptId(receipt.getReceiptId()).stream()
                    .mapToInt(ReceiptDetailEntity::getQuantity).sum();
            return toResponse(receipt, supplier, quantity);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        List<GoodsReceiptResponseDTO> receipts = getAllReceipts();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalImports", receipts.size());
        stats.put("booksReceived", receipts.stream().mapToInt(GoodsReceiptResponseDTO::getQuantity).sum());
        stats.put("totalSpent", receipts.stream().map(GoodsReceiptResponseDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("pending", receipts.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count());
        return stats;
    }

    BookEntity resolveBook(ImportReceiptRequestDTO.Line line) {
        BookEntity book;
        if (line.getBookId() != null) {
            book = bookRepository.findById(line.getBookId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầu sách " + line.getBookId()));
            if (line.getIsbn() != null && !line.getIsbn().isBlank() && !Isbn.canonical(line.getIsbn()).equals(Isbn.canonical(book.getIsbn()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN không khớp đầu sách đã chọn; cần tra cứu lại");
            }
        } else if (line.getIsbn() != null && !line.getIsbn().isBlank()) {
            String isbn = Isbn.canonical(line.getIsbn());
            var candidates = bookRepository.findInventoryIsbnCandidates(isbn, Isbn.legacy(isbn));
            if (candidates.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ISBN chưa có trong danh mục; cần tạo đầu sách trước");
            if (candidates.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN khớp nhiều đầu sách; kiểm tra ấn bản và chọn bookId rõ ràng");
            book = candidates.get(0);
        } else {
            throw invalid("Mỗi dòng cần bookId hoặc ISBN");
        }
        if (Boolean.TRUE.equals(book.getIsDigital())) throw invalid("Không thể tạo bản sao vật lý cho tài liệu số");
        return book;
    }

    private record PreparedLine(ImportReceiptRequestDTO.Line line, BookEntity book, ShelfEntity shelf, String path) {}
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private GoodsReceiptResponseDTO toResponse(GoodsReceiptEntity receipt, SupplierEntity supplier, int quantity) {
        return GoodsReceiptResponseDTO.builder()
                .receiptId(receipt.getReceiptId())
                .receiptCode(receipt.getReceiptCode())
                .createdAt(receipt.getCreatedAt())
                .supplierId(receipt.getSupplierId())
                .supplierName(supplier == null ? "Unknown" : supplier.getName())
                .quantity(quantity)
                .totalAmount(receipt.getTotalAmount())
                .status(receipt.getStatus())
                .build();
    }
}
