package mediaservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MediaProperties mediaProperties;

    public WebConfig(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Windows 必须用 file:///C:/... 形式，手写 file:C:/ 可能导致 404（前端表现为黑块图）
        Path mediaRoot = Paths.get(mediaProperties.getStorageDir()).toAbsolutePath().normalize();
        String location = mediaRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location);
    }
}
