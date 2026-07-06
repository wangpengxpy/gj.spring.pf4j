package gj.pf4j.security.servlet;

import jakarta.servlet.Filter;

public interface SessionRestoreFilterExtension {
    Filter getFilter();
    default int getOrder() { return 0; }
}
