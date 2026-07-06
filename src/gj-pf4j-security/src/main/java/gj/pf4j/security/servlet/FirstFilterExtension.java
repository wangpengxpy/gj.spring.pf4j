package gj.pf4j.security.servlet;

import jakarta.servlet.Filter;

public interface FirstFilterExtension {
    Filter getFilter();
    default int getOrder() { return 0; }
}
