/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatabaseSequence {

    private final String name;
    private int increment = 1;
    private long minValue = 1;
    private long maxValue = Long.MAX_VALUE;
    private long start = 1;
    private int cache = 1;

    public DatabaseSequence(String name) {
        this.name = name;
    }
}
