package com.huit.library.modules.circulation.dto;

import java.util.UUID;

/** Read-only discovery projection; mutable entities are loaded only after ticket locks. */
public record ReturnTarget(Long detailId, Long ticketId, String barcode, UUID userId) {
}
