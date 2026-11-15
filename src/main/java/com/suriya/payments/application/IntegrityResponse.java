package com.suriya.payments.application;

/** Ledger-integrity summary: net of all postings and how many accounts have drifted. */
public record IntegrityResponse(boolean balanced, long ledgerNet, int driftedAccounts) {}
