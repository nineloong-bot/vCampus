package edu.seu.vcampus.server.shop.logging;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShopBusinessLogger {
    private static final Logger LOG = LoggerFactory.getLogger("vcampus.business");

    public void commandCompleted(Message request, String userId,
            String code, long durationMs) {
        LOG.info("module=SHOP command={} requestId={} userId={} code={} durationMs={}",
                request.command(), request.requestId(), safe(userId), code, durationMs);
    }

    public void checkoutSucceeded(Message request, String userId,
            CheckoutCommand command, CheckoutResult result) {
        LOG.info("module=SHOP event=CHECKOUT requestId={} userId={} orderGroupId={} "
                        + "itemCount={} orderCount={} amount={}",
                request.requestId(), safe(userId), result.orderGroupId(),
                command.items().size(), result.orders().size(), result.totalAmount());
    }

    public void paymentCompleted(Message request, String userId, PaymentView result) {
        LOG.info("module=SHOP event=PAYMENT requestId={} userId={} paymentId={} "
                        + "channel={} amount={} result={}",
                request.requestId(), safe(userId), result.paymentId(),
                result.successfulChannel(), result.amount(), result.status());
    }

    public void stateChanged(String actorId, String targetType, String targetId,
            String oldStatus, String newStatus, String reason) {
        LOG.info("module=SHOP event=STATE_CHANGE actorId={} targetType={} targetId={} "
                        + "oldStatus={} newStatus={} reason={}",
                safe(actorId), targetType, targetId, oldStatus, newStatus, safe(reason));
    }

    private static String safe(String value) {
        return value == null ? "anonymous" : value;
    }
}
