package userservice.config;

import userservice.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    @Bean
    public DirectExchange socialExchange() {
        return new DirectExchange(MqConstants.EXCHANGE, true, false);
    }

    /** 用户资料 → search；队列由 search 声明绑定 */
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_USER, true, false);
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_NOTIFY).build();
    }

    @Bean
    public Binding likeBinding(Queue notifyQueue, DirectExchange socialExchange) {
        return BindingBuilder.bind(notifyQueue).to(socialExchange).with(MqConstants.RK_LIKE_CREATED);
    }

    @Bean
    public Binding commentBinding(Queue notifyQueue, DirectExchange socialExchange) {
        return BindingBuilder.bind(notifyQueue).to(socialExchange).with(MqConstants.RK_COMMENT_CREATED);
    }

    @Bean
    public Binding followBinding(Queue notifyQueue, DirectExchange socialExchange) {
        return BindingBuilder.bind(notifyQueue).to(socialExchange).with(MqConstants.RK_FOLLOW_CREATED);
    }
}
