package com.chainlance.entity.enums;

/** Stored as text. Constants must match the CHECK constraint in V1__init.sql exactly. */
public enum SettlementStatus {
    AWAITING_PROPOSE_TX,
    ACTIVE,
    EXECUTED,
    WITHDRAWN,
    SUPERSEDED,
    CANCELLED
}
