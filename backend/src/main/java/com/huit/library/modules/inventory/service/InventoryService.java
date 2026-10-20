package com.huit.library.modules.inventory.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final ReceiptDetailRepository receiptDetailRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;

    public InventoryService(GoodsReceiptRepository goodsReceiptRepository, SupplierRepository supplierRepository,
                            ReceiptDetailRepository receiptDetailRepository, BookRepository bookRepository,
                            BookCopyRepository bookCopyRepository) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.receiptDetailRepository = receiptDetailRepository;
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
    }

    public List<SupplierEntity> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public SupplierEntity createSupplier(SupplierEntity supplier) {
        return supplierRepository.save(supplier);
    }

    @Transactional
    public GoodsReceiptResponseDTO importBooks(ImportReceiptRequestDTO request) {
        SupplierEntity supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
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
        for (ImportReceiptRequestDTO.Line line : request.getLines()) {
            BookEntity book = resolveBook(line);
            ReceiptDetailEntity detail = new ReceiptDetailEntity();
            detail.setReceiptId(receipt.getReceiptId());
            detail.setBookId(book.getBookId());
            detail.setQuantity(line.getQuantity());
            detail.setUnitPrice(line.getUnitPrice());
            receiptDetailRepository.save(detail);

            for (int i = 0; i < line.getQuantity(); i++) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setBarcode("BC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
                copy.setBookId(book.getBookId());
                copy.setStatus("AVAILABLE");
                bookCopyRepository.save(copy);
            }
            quantity += line.getQuantity();
        }
        return toResponse(receipt, supplier, quantity);
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

    private BookEntity resolveBook(ImportReceiptRequestDTO.Line line) {
        if (line.getBookId() != null) {
            return bookRepository.findById(line.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException("Book not found: " + line.getBookId()));
        }
        if (line.getIsbn() != null && !line.getIsbn().isBlank()) {
            return bookRepository.findFirstByIsbn(line.getIsbn().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Book ISBN not found: " + line.getIsbn()));
        }
        throw new IllegalArgumentException("Each receipt line must contain bookId or ISBN");
    }

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
