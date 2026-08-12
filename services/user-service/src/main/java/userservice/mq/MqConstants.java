package userservice.mq;

/** 扩展：除通知交换机外，增加用户领域交换机供 search 索引 */
public final class MqConstants {

    public static final String EXCHANGE = "firefly.social";
    public static final String QUEUE_NOTIFY = "notify.events";

    public static final String RK_LIKE_CREATED = "like.created";
    public static final String RK_COMMENT_CREATED = "comment.created";
    public static final String RK_FOLLOW_CREATED = "follow.created";

    /** 用户资料变更 → search 写 users 索引 */
    public static final String EXCHANGE_USER = "firefly.user";
    public static final String RK_USER_UPSERTED = "user.upserted";

    private MqConstants() {
    }
}
