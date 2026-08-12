package searchservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(FireflyEsProperties.class)
public class AppConfig {

    /** 调 ES HTTP REST 用；超时保持默认即可，本地开发够用 */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
