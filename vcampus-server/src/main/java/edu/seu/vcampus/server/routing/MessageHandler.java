package edu.seu.vcampus.server.routing;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;

import java.io.Serializable;

/** Handles one registered command. */
@FunctionalInterface
public interface MessageHandler {
    /** Handles a message in its connection context. */
    ResponseBody<? extends Serializable> handle(Message message, ClientContext context);
}
