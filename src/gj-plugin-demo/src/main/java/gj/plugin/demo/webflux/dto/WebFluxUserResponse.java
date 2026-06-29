package gj.plugin.demo.webflux.dto;

import lombok.Data;

@Data
public class WebFluxUserResponse {
    private Long id;
    private String name;
    private String email;
    private String description;
}
