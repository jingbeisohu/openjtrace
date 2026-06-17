package org.openjtrace.example.feign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private InventoryClient inventoryClient;

    @PostMapping("/create")
    public String createOrder(@RequestParam("sku") String sku, @RequestParam("qty") int qty) {
        boolean hasStock = inventoryClient.checkStock(sku, qty);
        if (hasStock) {
            return "SUCCESS";
        }
        return "NO_STOCK";
    }
}
