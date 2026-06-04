/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.eventbus;

import java.util.EventListener;

public interface GJPluginLocalEventListener<T> extends EventListener {
    void HandleEvent(T event);
}
