/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import java.util.Map;

import org.pf4j.PluginRuntimeException;

public interface ConfigurationRepository {

    /**
     * Get a particular plugin configuration properties from this repository.
     *
     * @param id the id of the plugin
     * @return the plugin configuration properties
     */
    Map<String, Object> get(String id);
    
    /**
     * Save a plugin configuration properties in this repository.
     * 
     * @param id the id of the plugin
     * @param properties the configuration properties of the plugin
     */
    void save(String id, Map<String, Object> properties);
    
    /**
     * Removes a plugin configuration properties from the repository.
     *
     * @param id the id of the plugin
     * @return true if deleted
     * @throws PluginRuntimeException if something goes wrong
     */
    boolean delete(String id);
}
