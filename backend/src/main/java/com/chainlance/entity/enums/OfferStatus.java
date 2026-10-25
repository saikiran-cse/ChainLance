package com.chainlance.entity.enums;

/** Stored as text. Constants must match the CHECK constraint in V2__marketplace_core.sql exactly. */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    COUNTERED,
    WITHDRAWN,
    CLOSED
}
