package com.easycode.api.domain.enums;

/**
 * Call dispositions.
 *
 * <p>BAD_NUMBER is deliberately separate from NO_ANSWER — a dead number is a
 * list-quality problem, not a rejection, and merging the two makes a cold list
 * look worse than it is.
 */
public enum ActivityOutcome {
    CONNECTED,
    VOICEMAIL,
    NO_ANSWER,
    BAD_NUMBER,
    CALLBACK,
    NOT_INTERESTED
}
