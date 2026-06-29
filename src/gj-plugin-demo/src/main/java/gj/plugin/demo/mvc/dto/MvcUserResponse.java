package gj.plugin.demo.mvc.dto;

import lombok.Data;

@Data
public class MvcUserResponse {
    private Integer id;
    private String name;
    private String email;
    private String description;
}
