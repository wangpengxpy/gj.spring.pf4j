/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.anonymous;

/**
 * Write-only interface for registering and unregistering anonymous path entries.
 * Injected into Handler Mappings (MVC and WebFlux) — not exposed to plugins.
 */
public interface PluginAnonymousPathRegistrar {

    void register(String pluginId, AnonymousPathEntry entry);

    void unregisterByPlugin(String pluginId);
}
