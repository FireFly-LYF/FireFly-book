package contentservice.mq;

/** 与 search-service 消费端保持一致 */
public final class MqConstants {

    public static final String EXCHANGE_CONTENT = "firefly.content";

    public static final String RK_NOTE_CREATED = "note.created";
    public static final String RK_NOTE_UPDATED = "note.updated";
    public static final String RK_NOTE_DELETED = "note.deleted";

    private MqConstants() {
    }
}
