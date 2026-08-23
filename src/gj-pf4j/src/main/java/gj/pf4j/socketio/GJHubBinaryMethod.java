/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.lang.annotation.*;

/**
 * Binary event method annotation. Marks a method on a GJHub subclass as the
 * handler for the given binary event (byte[] payload).
 *
 * <p>Difference to {@link GJHubMethod}:
 * <ul>
 *   <li>{@link GJHubMethod}: JSON channel, fixed event name {@code invoke},
 *       routing key is the {@code method} field inside the payload, parameter is JsonNode.</li>
 *   <li>{@link GJHubBinaryMethod}: binary channel, the annotation value IS the
 *       binary event name (the routing key), parameter is {@code byte[]}.</li>
 * </ul>
 *
 * <p>Method signature is fixed to {@code void xxx(byte[] data)}, e.g.:
 * <pre>{@code
 * @GJHubBinaryMethod("audio")
 * public void onAudio(byte[] data) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GJHubBinaryMethod {
    /** Binary event name (e.g. "audio"), lowercase */
    String value();
}
