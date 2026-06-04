/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import java.io.Serial;
import java.io.Serializable;

public class GJPluginStartingError implements Serializable {

    @Serial
    private static final long serialVersionUID = -153864270345999338L;

    private final String pluginId;

    private final String errorMessage;

    private final String errorDetail;

    public String getPluginId() {
        return pluginId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public GJPluginStartingError(String pluginId, String errorMessage, String errorDetail) {
        this.pluginId = pluginId;
        this.errorMessage = errorMessage;
        this.errorDetail = errorDetail;
    }
}
