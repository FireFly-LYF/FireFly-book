package socialservice.config;

import com.firefly.internalauth.FireflyHttpProperties;
import com.firefly.internalauth.FireflyRestTemplateFactory;
import com.firefly.internalauth.GatewayHmacClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpConfig {

    @Bean
    public RestTemplate restTemplate(
            FireflyHttpProperties httpProperties,
            GatewayHmacClientInterceptor hmacInterceptor) {
        return FireflyRestTemplateFactory.create(
                "social-downstream", httpProperties, hmacInterceptor);
    }
}
