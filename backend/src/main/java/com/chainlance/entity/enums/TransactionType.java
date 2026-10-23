package com.chainlance.entity.enums;

/** Stored as text. Constants must match the CHECK constraint in V1__init.sql exactly. */
public enum TransactionType {
    CREATE_ESCROW,
    FUND,
    APPROVE,
    SETTLE_PROPOSE,
    SETTLE_WITHDRAW,
    SETTLE,
    DISPUTE,
    RESOLVE
}
