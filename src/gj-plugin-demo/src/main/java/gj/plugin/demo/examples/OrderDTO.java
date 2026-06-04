package gj.plugin.demo.examples;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderDTO {
    public String customerFirstName;
    public String customerLastName;
    public String billingStreet;
    public String billingCity;
}
