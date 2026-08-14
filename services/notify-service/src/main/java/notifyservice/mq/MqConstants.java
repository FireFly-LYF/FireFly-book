package notifyservice.mq;

public final class MqConstants {

    public static final String EXCHANGE = "firefly.social";
    public static final String EXCHANGE_DLX = "firefly.dlx";

    public static final String QUEUE_NOTIFY = "notify.events";
    public static final String QUEUE_NOTIFY_DLQ = "notify.events.dlq";

    public static final String RK_LIKE_CREATED = "like.created";
    public static final String RK_COMMENT_CREATED = "comment.created";
    public static final String RK_FOLLOW_CREATED = "follow.created";

    private MqConstants() {
    }
}
