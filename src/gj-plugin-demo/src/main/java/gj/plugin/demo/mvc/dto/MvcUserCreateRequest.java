package gj.plugin.demo.mvc.dto;

import lombok.Data;

@Data
public class MvcUserCreateRequest {
    private String name;
    private String email;
    private String description;
}
