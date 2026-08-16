package com.firefly.internalauth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({InternalAuthProperties.class, FireflyHttpProperties.class})
public class InternalAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GatewayHmacFilter gatewayHmacFilter(InternalAuthProperties props) {
        return new GatewayHmacFilter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayHmacClientInterceptor gatewayHmacClientInterceptor(InternalAuthProperties props) {
        return new GatewayHmacClientInterceptor(props);
    }
}
