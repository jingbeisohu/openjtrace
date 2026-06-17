package org.openjtrace.example.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory-service")
public interface InventoryClient {

    @GetMapping("/inventory/check")
    boolean checkStock(@RequestParam("sku") String sku, @RequestParam("qty") int qty);
}
