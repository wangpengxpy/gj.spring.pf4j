/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.hotreload;

import java.io.Serial;

public class PluginHotReloadVetoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4514820369182746301L;

    public PluginHotReloadVetoException(String message) {
        super(message);
    }

    public PluginHotReloadVetoException(String message, Throwable cause) {
        super(message, cause);
    }
}
