package edu.seu.vcampus.server.shop.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ShopBusinessLoggerTest {
    @Test
    void writesStructuredBusinessFieldsAndOmitsTokenAndCredentials() {
        Logger logger = (Logger) LoggerFactory.getLogger("vcampus.business");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            Message request = new Message("request-1", MessageType.REQUEST,
                    "SHOP_CHECKOUT", "token-never-log", EmptyRequest.INSTANCE, 1L);
            ShopBusinessLogger shopLog = new ShopBusinessLogger();
            shopLog.commandCompleted(request, "buyer-1", "SUCCESS", 17L);
            String output = events.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(output).contains("command=SHOP_CHECKOUT", "requestId=request-1",
                    "userId=buyer-1", "code=SUCCESS", "durationMs=17");
            assertThat(output).doesNotContain("token-never-log", "DemoPassword7");
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void writesStructuredAdministrativeStateChange() {
        Logger logger = (Logger) LoggerFactory.getLogger("vcampus.business");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            new ShopBusinessLogger().stateChanged("admin-7", "SHOP", "shop-1",
                    "ACTIVE", "SUSPENDED", "违规商品");

            String output = events.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(output).contains("event=STATE_CHANGE", "actorId=admin-7", "targetType=SHOP",
                    "targetId=shop-1", "oldStatus=ACTIVE", "newStatus=SUSPENDED", "reason=违规商品");
        } finally {
            logger.detachAppender(events);
        }
    }
}
