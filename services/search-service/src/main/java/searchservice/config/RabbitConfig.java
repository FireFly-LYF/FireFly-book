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

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        // 与 content/user 生产者一致：JSON 序列化，便于跨服务传 POJO
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // 关键：生产者 __TypeId__ 是 contentservice.xxx，本服务类在 searchservice.xxx
        // 用监听方法参数类型反序列化，避免 ClassNotFound
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
    public Queue searchNoteQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_NOTE).build();
    }

    @Bean
    public Queue searchUserQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SEARCH_USER).build();
    }

    // 同一队列绑定多个 routing key：创建/更新/删除都进 search.note.events
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
