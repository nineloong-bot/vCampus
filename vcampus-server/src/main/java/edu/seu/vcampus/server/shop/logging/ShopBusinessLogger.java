package edu.seu.vcampus.server.shop.logging;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides shop business logger behavior. */
public final class ShopBusinessLogger {
    private static final Logger LOG = LoggerFactory.getLogger("vcampus.business");

    /**
     * Performs the command completed operation.
     * @param request the request
     * @param userId the user identifier
     * @param code the code
     * @param durationMs the duration ms
     */
    public void commandCompleted(Message request, String userId,
            String code, long durationMs) {
        LOG.info("module=SHOP command={} requestId={} userId={} code={} durationMs={}",
                request.command(), request.requestId(), safe(userId), code, durationMs);
    }

    /**
     * Performs the checkout succeeded operation.
     * @param request the request
     * @param userId the user identifier
     * @param command the command
     * @param result the result
     */
    public void checkoutSucceeded(Message request, String userId,
            CheckoutCommand command, CheckoutResult result) {
        LOG.info("module=SHOP event=CHECKOUT requestId={} userId={} orderGroupId={} "
                        + "itemCount={} orderCount={} amount={}",
                request.requestId(), safe(userId), result.orderGroupId(),
                command.items().size(), result.orders().size(), result.totalAmount());
    }

    /**
     * Performs the payment completed operation.
     * @param request the request
     * @param userId the user identifier
     * @param result the result
     */
    public void paymentCompleted(Message request, String userId, PaymentView result) {
        LOG.info("module=SHOP event=PAYMENT requestId={} userId={} paymentId={} "
                        + "channel={} amount={} result={}",
                request.requestId(), safe(userId), result.paymentId(),
                result.successfulChannel(), result.amount(), result.status());
    }

    /**
     * Performs the state changed operation.
     * @param actorId the actor identifier
     * @param targetType the target type
     * @param targetId the target identifier
     * @param oldStatus the old status
     * @param newStatus the new status
     * @param reason the reason
     */
    public void stateChanged(String actorId, String targetType, String targetId,
            String oldStatus, String newStatus, String reason) {
        LOG.info("module=SHOP event=STATE_CHANGE actorId={} targetType={} targetId={} "
                        + "oldStatus={} newStatus={} reason={}",
                safe(actorId), targetType, targetId, oldStatus, newStatus, safe(reason));
    }

    /**
     * Performs the product changed operation.
     * @param actorId the actor identifier
     * @param shopId the shop identifier
     * @param productId the product identifier
     * @param change the change
     */
    public void productChanged(String actorId, String shopId, String productId, String change) {
        LOG.info("module=SHOP event=PRODUCT_CHANGE actorId={} shopId={} productId={} change={}",
                safe(actorId), shopId, productId, change);
    }

    private static String safe(String value) {
        return value == null ? "anonymous" : value;
    }
}
