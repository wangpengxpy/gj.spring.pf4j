/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("gj.quartz")
public class GJQuartzProperties {

    /** Scheduler mode. */
    private Mode mode = Mode.STANDALONE;

    /** Continue executing remaining scripts when one fails. Default false (fail-fast). */
    private boolean continueOnError = false;

    public enum Mode { STANDALONE, CLUSTERED }
}
