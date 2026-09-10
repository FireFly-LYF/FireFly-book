package com.firefly.javagateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JavaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaGatewayApplication.class, args);
    }
}
