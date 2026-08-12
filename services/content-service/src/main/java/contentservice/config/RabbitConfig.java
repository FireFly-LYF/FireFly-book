package contentservice.config;

import contentservice.mq.MqConstants;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /** 生产者侧声明交换机即可；队列由 search 消费者声明并绑定 */
    @Bean
    public DirectExchange contentExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_CONTENT, true, false);
    }
}
