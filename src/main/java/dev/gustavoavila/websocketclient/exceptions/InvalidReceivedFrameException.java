package dev.gustavoavila.websocketclient.exceptions;

/**
 * Used to indicate a protocol problem with a received frame
 *
 * @author Gustavo Avila
 *
 */
public class InvalidReceivedFrameException extends RuntimeException {
    public InvalidReceivedFrameException(String message) {
        super(message);
    }
}
