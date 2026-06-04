package gj.plugin.demo.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.plugin.demo.examples.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/plugin")
public class PluginTestController {
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    @GetMapping("getAll")
    public String getAll() throws JsonProcessingException {
        Name name = new Name();
        name.setFirstName("jeffcky");
        name.setLastName("wang");
        Customer customer = new Customer();
        customer.setName(name);

        Address address = new Address();
        address.setCity("sz");
        address.setStreet("north station");

        Order order = new Order();
        order.setCustomer(customer);
        order.setBillingAddress(address);

        ModelMapper modelMapper = applicationContext.getBean(ModelMapper.class);

        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);

        return objectMapper.writeValueAsString(orderDTO);
    }
}