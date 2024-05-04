package com.example;

import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles WebSocket communication and matchmaking logic.
 */
public class MatchMaking {
    private static final Logger logger = LoggerFactory.getLogger(MatchMaking.class);
    private static final ConcurrentLinkedQueue<Exchange> queue = new ConcurrentLinkedQueue<>();

    /**
     * Configures WebSocket behavior.
     * 
     * @param ws WebSocket configuration
     */
    public static void websocket(WsConfig ws) {
        ws.onConnect(user -> user.enableAutomaticPings());
        ws.onClose(user -> pairingAbort(user));
        ws.onMessage(user -> {
            logger.info("Received message: " + user.message());
            var message = user.messageAsClass(Message.class);
            switch (message.name()) {
                case "PAIRING_START" -> pairingStart(user);
                case "PAIRING_ABORT" -> pairingAbort(user);
                case "PAIRING_DONE" -> pairingDone(user);
                case "SDP_OFFER", "SDP_ANSWER", "SDP_ICE_CANDIDATE" -> forwardMessage(user, message);
            }
        });
    }

    /**
     * Initiates pairing between users.
     * 
     * @param user WebSocket context of the user initiating pairing
     */
    private static synchronized void pairingStart(WsContext user) {
        queue.removeIf(ex -> ex.a == user || ex.b == user); // Prevent double queueing
        var exchange = queue.stream()
                .filter(ex -> ex.b == null)
                .findFirst()
                .orElse(null);
        if (exchange != null) {
            exchange.b = user;
            send(exchange.a, new Message("PARTNER_FOUND", "GO_FIRST"));
            send(exchange.b, new Message("PARTNER_FOUND"));
        } else {
            queue.add(new Exchange(user));
        }
    }

    /**
     * Aborts pairing process.
     * 
     * @param user WebSocket context of the user aborting pairing
     */
    private static synchronized void pairingAbort(WsContext user) {
        var exchange = findExchange(user);
        if (exchange != null) {
            send(exchange.otherUser(user), new Message("PARTNER_LEFT"));
            queue.remove(exchange);
        }
    }

    /**
     * Marks pairing process as done for a user.
     * 
     * @param user WebSocket context of the user completing pairing
     */
    private static synchronized void pairingDone(WsContext user) {
        var exchange = findExchange(user);
        if (exchange != null) {
            exchange.incrementDoneCount();
        }
        queue.removeIf(ex -> ex.getDoneCount() == 2);
    }

    /**
     * Forwards SDP messages to the other user in the pairing.
     * 
     * @param user    WebSocket context of the user sending the message
     * @param message SDP message
     */
    private static synchronized void forwardMessage(WsContext user, Message message) {
        var exchange = findExchange(user);
        if (exchange != null && exchange.getA() != null && exchange.getB() != null) {
            send(exchange.otherUser(user), message); // Forward message to other user
        } else {
            logger.warn("Received SDP message from unpaired user");
        }
    }

    /**
     * Finds the exchange associated with a user.
     * 
     * @param user WebSocket context of the user
     * @return Exchange associated with the user
     */
    private static synchronized Exchange findExchange(WsContext user) {
        return queue.stream()
                .filter(ex -> user.equals(ex.getA()) || user.equals(ex.getB()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Safely sends a message to a user.
     * 
     * @param user    WebSocket context of the user
     * @param message Message to be sent
     */
    private static void send(WsContext user, Message message) {
        if (user != null) {
            try {
                user.send(message);
            } catch (Exception e) {
                logger.error("Failed to send message to user: " + user, e);
            }
        }
    }
}
