package searchservice.mq;

/**
 * 搜索相关 MQ 常量。
 * content / user 作为生产者也会声明同名 exchange（幂等），保证先启动谁都能用。
 */
public final class MqConstants {

    /** 笔记领域交换机（content-service 发） */
    public static final String EXCHANGE_CONTENT = "firefly.content";
    /** 用户领域交换机（user-service 发） */
    public static final String EXCHANGE_USER = "firefly.user";

    public static final String QUEUE_SEARCH_NOTE = "search.note.events";
    public static final String QUEUE_SEARCH_USER = "search.user.events";

    public static final String RK_NOTE_CREATED = "note.created";
    public static final String RK_NOTE_UPDATED = "note.updated";
    public static final String RK_NOTE_DELETED = "note.deleted";
    public static final String RK_USER_UPSERTED = "user.upserted";

    private MqConstants() {
    }
}
