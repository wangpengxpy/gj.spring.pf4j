package gj.plugin.demo.webflux.dto;

import lombok.Data;

@Data
public class WebFluxUserCreateRequest {
    private String name;
    private String email;
    private String description;
}
