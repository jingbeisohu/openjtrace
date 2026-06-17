package org.openjtrace.example.dubbo;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @DubboReference
    private UserService userService;

    @GetMapping("/get")
    public int getUserInfo(@RequestParam("id") int id) {
        return userService.getUser(id);
    }
}
