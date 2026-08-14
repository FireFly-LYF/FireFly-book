package socialservice.config;

import com.firefly.internalauth.GatewayHmacClientInterceptor;
import socialservice.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RestTemplate restTemplate(GatewayHmacClientInterceptor hmacInterceptor) {
        RestTemplate rt = new RestTemplate();
        rt.setInterceptors(List.of(hmacInterceptor));
        return rt;
    }

    @Bean
    public DirectExchange socialExchange() {
        return new DirectExchange(MqConstants.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_DLX, true, false);
    }

    /** 与 notify-service 参数一致（含 DLX） */
    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_NOTIFY)
                .deadLetterExchange(MqConstants.EXCHANGE_DLX)
                .deadLetterRoutingKey(MqConstants.QUEUE_NOTIFY)
                .build();
    }

    @Bean
    public Queue notifyDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_NOTIFY_DLQ).build();
    }

    @Bean
    public Binding notifyDlqBinding(Queue notifyDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(notifyDlq).to(dlxExchange).with(MqConstants.QUEUE_NOTIFY);
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
