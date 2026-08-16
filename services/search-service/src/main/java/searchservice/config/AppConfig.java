package searchservice.config;

import com.firefly.internalauth.FireflyHttpProperties;
import com.firefly.internalauth.FireflyRestTemplateFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(FireflyEsProperties.class)
public class AppConfig {

    /** 调 ES HTTP：连接池 + 超时 + 熔断（ES 读超时可在 yml 调大） */
    @Bean
    public RestTemplate restTemplate(FireflyHttpProperties httpProperties) {
        return FireflyRestTemplateFactory.create("search-es", httpProperties);
    }
}
