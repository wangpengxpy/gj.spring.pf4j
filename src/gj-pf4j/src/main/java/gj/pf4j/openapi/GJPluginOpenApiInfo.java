/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.openapi;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class GJPluginOpenApiInfo {
    public String GroupName;
    public List<String> ControllerPackages;
}
