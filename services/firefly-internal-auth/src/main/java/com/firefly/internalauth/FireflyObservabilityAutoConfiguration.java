package com.firefly.internalauth;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Actuator 存在时注册 /health 别名（与 ActuatorHealthAliasController 同包）。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HealthEndpoint.class)
public class FireflyObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActuatorHealthAliasController.class)
    public ActuatorHealthAliasController actuatorHealthAliasController(HealthEndpoint healthEndpoint) {
        return new ActuatorHealthAliasController(healthEndpoint);
    }
}
