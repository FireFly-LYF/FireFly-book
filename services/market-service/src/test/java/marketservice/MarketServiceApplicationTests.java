package marketservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "firefly.internal-auth.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class MarketServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
