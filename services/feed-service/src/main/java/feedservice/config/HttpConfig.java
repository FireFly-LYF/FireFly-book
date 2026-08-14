package feedservice.config;

import com.firefly.internalauth.GatewayHmacClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class HttpConfig {

    @Bean
    public RestTemplate restTemplate(GatewayHmacClientInterceptor hmacInterceptor) {
        RestTemplate rt = new RestTemplate();
        rt.setInterceptors(List.of(hmacInterceptor));
        return rt;
    }
}
