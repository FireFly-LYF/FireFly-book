package com.firefly.helloservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "hello firefly";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
