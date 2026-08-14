package mediaservice.config;

import mediaservice.media.MediaUrlSigner;
import mediaservice.media.SignedFileFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaSecurityConfig {

    @Bean
    public MediaUrlSigner mediaUrlSigner(MediaProperties props) {
        return new MediaUrlSigner(props.getSignSecret(), props.getSignTtlSeconds());
    }

    @Bean
    public FilterRegistrationBean<SignedFileFilter> signedFileFilter(MediaUrlSigner signer) {
        FilterRegistrationBean<SignedFileFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SignedFileFilter(signer));
        bean.addUrlPatterns("/files/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
