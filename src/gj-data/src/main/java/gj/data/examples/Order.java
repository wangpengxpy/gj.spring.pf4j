package gj.data.examples;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {
    public Customer customer;
    public Address billingAddress;
}
