package gj.pf4j.security.servlet;

import jakarta.servlet.Filter;

public interface LastFilterExtension {
    Filter getFilter();
    default int getOrder() { return 0; }
}
