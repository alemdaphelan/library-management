package com.huit.library.modules.inventory.service;

import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.repository.GoodsReceiptRepository;
import com.huit.library.modules.inventory.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;

    public InventoryService(GoodsReceiptRepository goodsReceiptRepository, SupplierRepository supplierRepository) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
    }

    public List<SupplierEntity> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public SupplierEntity createSupplier(SupplierEntity supplier) {
        return supplierRepository.save(supplier);
    }

    public GoodsReceiptEntity importBooks(GoodsReceiptEntity receipt) {
        receipt.setStatus("COMPLETED");
        // Basic calculation of total amount or status can be added here
        return goodsReceiptRepository.save(receipt);
    }

    public List<GoodsReceiptEntity> getAllReceipts() {
        return goodsReceiptRepository.findAll();
    }
}
