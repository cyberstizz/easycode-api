package com.easycode.api.domain.enums;

import java.util.List;

/** The six stages, in the order a job actually runs. Baked into the tracker rail. */
public enum StageKey {
    DISCOVERY, DESIGN, DEVELOPMENT, REVIEW, LAUNCH, MAINTENANCE;

    public static final List<StageKey> ORDER = List.of(values());

    public int position() {
        return ORDER.indexOf(this);
    }

    public StageKey next() {
        int i = position();
        return (i < 0 || i >= ORDER.size() - 1) ? this : ORDER.get(i + 1);
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
