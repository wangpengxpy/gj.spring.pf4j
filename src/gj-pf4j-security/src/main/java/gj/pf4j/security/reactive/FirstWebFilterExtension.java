package gj.pf4j.security.reactive;

import org.springframework.web.server.WebFilter;

public interface FirstWebFilterExtension {
    WebFilter getWebFilter();
    default int getOrder() { return 0; }
}
