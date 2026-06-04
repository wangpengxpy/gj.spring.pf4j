/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import org.springframework.web.server.ServerWebExchange;

public class GJPluginWebFluxSecureServerRequest extends ServerRequestWrapper {

    public GJPluginWebFluxSecureServerRequest(ServerRequest delegate) {
        super(delegate);
    }

    @Override
    @NonNull
    public ServerWebExchange exchange() {
        return new GJPluginWebFluxSecureServerWebExchange(super.exchange());
    }
}
