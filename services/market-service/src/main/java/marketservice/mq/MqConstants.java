package marketservice.mq;

public final class MqConstants {

    public static final String EXCHANGE_MARKET = "firefly.market";

    public static final String RK_ORDER_PAID = "order.paid";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_ORDER_REFUNDED = "order.refunded";

    private MqConstants() {
    }
}
