package com.easycode.api.domain.enums;

/**
 * The funnel, in order.
 *
 * <p>PITCHED means an offer went out. NEGOTIATING means they pushed back and we
 * moved down the ladder. The V2 migration renames the stored values from the
 * generic QUALIFIED/PROPOSAL, which didn't map to how EasyCode actually sells.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    PITCHED,
    NEGOTIATING,
    WON,
    LOST
}