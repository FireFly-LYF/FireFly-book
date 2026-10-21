package marketservice.config;

import marketservice.mq.MqConstants;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange marketExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_MARKET, true, false);
    }
}
