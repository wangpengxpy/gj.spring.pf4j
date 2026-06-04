
package gj.pf4j.webflux;

import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

public class GJPluginWebFluxSecureServerWebExchange extends ServerWebExchangeDecorator {

    public GJPluginWebFluxSecureServerWebExchange(ServerWebExchange delegate) {
        super(delegate);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return null;
    }
}
