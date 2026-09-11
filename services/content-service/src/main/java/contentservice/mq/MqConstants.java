package contentservice.mq;

/** 与 search-service 消费端保持一致 */
public final class MqConstants {

    public static final String EXCHANGE_CONTENT = "firefly.content";

    /** 笔记入库待审：仅审核消费，不进 Feed/ES */
    public static final String RK_NOTE_CREATED = "note.created";
    /** 审核通过后发布：Feed 写扩散 / Search 建索引 */
    public static final String RK_NOTE_PUBLISHED = "note.published";
    public static final String RK_NOTE_UPDATED = "note.updated";
    public static final String RK_NOTE_DELETED = "note.deleted";

    private MqConstants() {
    }
}
