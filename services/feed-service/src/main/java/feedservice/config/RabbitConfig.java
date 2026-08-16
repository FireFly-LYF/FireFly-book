package feedservice.config;

import feedservice.mq.MqConstants;
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
    public DirectExchange socialExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_SOCIAL, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue feedNoteQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FEED_NOTE)
                .deadLetterExchange(MqConstants.EXCHANGE_DLX)
                .deadLetterRoutingKey(MqConstants.QUEUE_FEED_NOTE)
                .build();
    }

    @Bean
    public Queue feedFollowQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FEED_FOLLOW)
                .deadLetterExchange(MqConstants.EXCHANGE_DLX)
                .deadLetterRoutingKey(MqConstants.QUEUE_FEED_FOLLOW)
                .build();
    }

    @Bean
    public Queue feedNoteDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FEED_NOTE_DLQ).build();
    }

    @Bean
    public Queue feedFollowDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FEED_FOLLOW_DLQ).build();
    }

    @Bean
    public Binding feedNoteDlqBinding(Queue feedNoteDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(feedNoteDlq).to(dlxExchange).with(MqConstants.QUEUE_FEED_NOTE);
    }

    @Bean
    public Binding feedFollowDlqBinding(Queue feedFollowDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(feedFollowDlq).to(dlxExchange).with(MqConstants.QUEUE_FEED_FOLLOW);
    }

    @Bean
    public Binding noteCreatedBinding(Queue feedNoteQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(feedNoteQueue).to(contentExchange).with(MqConstants.RK_NOTE_CREATED);
    }

    @Bean
    public Binding noteDeletedBinding(Queue feedNoteQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(feedNoteQueue).to(contentExchange).with(MqConstants.RK_NOTE_DELETED);
    }

    @Bean
    public Binding followCreatedBinding(Queue feedFollowQueue, DirectExchange socialExchange) {
        return BindingBuilder.bind(feedFollowQueue).to(socialExchange).with(MqConstants.RK_FOLLOW_CREATED);
    }

    @Bean
    public Binding followDeletedBinding(Queue feedFollowQueue, DirectExchange socialExchange) {
        return BindingBuilder.bind(feedFollowQueue).to(socialExchange).with(MqConstants.RK_FOLLOW_DELETED);
    }
}
