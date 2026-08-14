package searchservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import searchservice.mq.MqConstants;

/**
 * 业务队列挂 DLX：监听失败重试耗尽后 nack(requeue=false) → DLQ。
 * 注意：若本机已有同名队列且无 DLX 参数，需先删队列再启动（否则 PRECONDITION_FAILED）。
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public DirectExchange contentExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_CONTENT, true, false);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_USER, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue searchNoteQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_NOTE)
                .deadLetterExchange(MqConstants.EXCHANGE_DLX)
                .deadLetterRoutingKey(MqConstants.QUEUE_SEARCH_NOTE)
                .build();
    }

    @Bean
    public Queue searchUserQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_USER)
                .deadLetterExchange(MqConstants.EXCHANGE_DLX)
                .deadLetterRoutingKey(MqConstants.QUEUE_SEARCH_USER)
                .build();
    }

    @Bean
    public Queue searchNoteDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_NOTE_DLQ).build();
    }

    @Bean
    public Queue searchUserDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_USER_DLQ).build();
    }

    @Bean
    public Binding searchNoteDlqBinding(Queue searchNoteDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(searchNoteDlq).to(dlxExchange).with(MqConstants.QUEUE_SEARCH_NOTE);
    }

    @Bean
    public Binding searchUserDlqBinding(Queue searchUserDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(searchUserDlq).to(dlxExchange).with(MqConstants.QUEUE_SEARCH_USER);
    }

    @Bean
    public Binding noteCreatedBinding(Queue searchNoteQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(searchNoteQueue).to(contentExchange).with(MqConstants.RK_NOTE_CREATED);
    }

    @Bean
    public Binding noteUpdatedBinding(Queue searchNoteQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(searchNoteQueue).to(contentExchange).with(MqConstants.RK_NOTE_UPDATED);
    }

    @Bean
    public Binding noteDeletedBinding(Queue searchNoteQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(searchNoteQueue).to(contentExchange).with(MqConstants.RK_NOTE_DELETED);
    }

    @Bean
    public Binding userUpsertedBinding(Queue searchUserQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(searchUserQueue).to(userExchange).with(MqConstants.RK_USER_UPSERTED);
    }
}
