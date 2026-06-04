/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GJHubMethod {
    String value();
}