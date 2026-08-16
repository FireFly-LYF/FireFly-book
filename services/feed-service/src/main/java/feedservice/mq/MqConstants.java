package feedservice.mq;

public final class MqConstants {

    public static final String EXCHANGE_CONTENT = "firefly.content";
    public static final String EXCHANGE_SOCIAL = "firefly.social";
    public static final String EXCHANGE_DLX = "firefly.dlx";

    public static final String QUEUE_FEED_NOTE = "feed.note.events";
    public static final String QUEUE_FEED_NOTE_DLQ = "feed.note.events.dlq";
    public static final String QUEUE_FEED_FOLLOW = "feed.follow.events";
    public static final String QUEUE_FEED_FOLLOW_DLQ = "feed.follow.events.dlq";

    public static final String RK_NOTE_CREATED = "note.created";
    public static final String RK_NOTE_DELETED = "note.deleted";
    public static final String RK_FOLLOW_CREATED = "follow.created";
    public static final String RK_FOLLOW_DELETED = "follow.deleted";

    private MqConstants() {
    }
}
