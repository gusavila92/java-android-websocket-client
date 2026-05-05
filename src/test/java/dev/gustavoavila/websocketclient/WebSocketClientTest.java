package dev.gustavoavila.websocketclient;

import dev.gustavoavila.websocketclient.exceptions.IllegalSchemeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketClientTest {

    private WebSocketClient client;

    @BeforeAll
    static void startServer() throws Exception {
        TestServerManager.start();
    }

    @AfterAll
    static void stopServer() {
        TestServerManager.stop();
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            try {
                client.close(1000, WebSocketClient.CLOSE_CODE_NORMAL, "done");
            } catch (Exception ignored) {}
        }
    }

    // --- Helper ---

    private WebSocketClient buildClient(
            URI uri,
            Runnable onOpen,
            Consumer<String> onText,
            Consumer<byte[]> onBinary,
            Consumer<byte[]> onPing,
            Consumer<byte[]> onPong,
            Consumer<Exception> onException,
            BiConsumer<Integer, String> onClose) {

        return new WebSocketClient(uri) {
            @Override public void onOpen() { if (onOpen != null) onOpen.run(); }
            @Override public void onTextReceived(String m) { if (onText != null) onText.accept(m); }
            @Override public void onBinaryReceived(byte[] d) { if (onBinary != null) onBinary.accept(d); }
            @Override public void onPingReceived(byte[] d) { if (onPing != null) onPing.accept(d); }
            @Override public void onPongReceived(byte[] d) { if (onPong != null) onPong.accept(d); }
            @Override public void onException(Exception e) { if (onException != null) onException.accept(e); }
            @Override public void onCloseReceived(int code, String reason) { if (onClose != null) onClose.accept(code, reason); }
        };
    }

    // --- Tests ---

    @Test
    void connectOpensCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                latch::countDown, null, null, null, null, null, null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onOpen never fired");
    }

    @Test
    void sendTextReceivesEcho() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                () -> client.send("hello"),
                msg -> { received.set(msg); latch.countDown(); },
                null, null, null,
                e -> latch.countDown(),
                null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onTextReceived never fired");
        assertEquals("hello", received.get());
    }

    @Test
    void sendBinaryReceivesEcho() throws Exception {
        byte[] payload = {0x01, 0x02, 0x03, 0x04};
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                () -> client.send(payload),
                null,
                data -> { received.set(data); latch.countDown(); },
                null, null,
                e -> latch.countDown(),
                null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onBinaryReceived never fired");
        assertArrayEquals(payload, received.get());
    }

    @Test
    void pingFromServerTriggersCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client = buildClient(new URI(TestServerManager.wsUrl("/ping")),
                null, null, null,
                data -> latch.countDown(),
                null,
                e -> latch.countDown(),
                null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onPingReceived never fired");
    }

    @Test
    void sendPingReceivesPong() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                () -> client.sendPing(new byte[]{0x42}),
                null, null, null,
                data -> latch.countDown(),
                e -> latch.countDown(),
                null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onPongReceived never fired");
    }

    @Test
    void serverCloseReceivedWithCodeAndReason() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger receivedCode = new AtomicInteger();
        AtomicReference<String> receivedReason = new AtomicReference<>();
        client = buildClient(new URI(TestServerManager.wsUrl("/close")),
                null, null, null, null, null,
                e -> latch.countDown(),
                (code, reason) -> {
                    receivedCode.set(code);
                    receivedReason.set(reason);
                    latch.countDown();
                });
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onCloseReceived never fired");
        assertEquals(1001, receivedCode.get());
        assertEquals("Going away", receivedReason.get());
    }

    @Test
    void clientInitiatedCloseNoException() throws Exception {
        CountDownLatch openLatch = new CountDownLatch(1);
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                openLatch::countDown,
                null, null, null, null,
                caughtException::set,
                null);
        client.connect();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "onOpen never fired");
        client.close(1000, WebSocketClient.CLOSE_CODE_NORMAL, "done");
        // Allow a brief moment for any spurious exceptions
        Thread.sleep(500);
        assertNull(caughtException.get(), "Unexpected exception on clean close");
    }

    @Test
    void readTimeoutTriggersException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client = buildClient(new URI(TestServerManager.wsUrl("/slow")),
                null, null, null, null, null,
                e -> latch.countDown(),
                null);
        client.setReadTimeout(2000);
        client.connect();
        // Wait up to 5 s (2 s timeout + margin)
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onException never fired for read timeout");
    }

    @Test
    void autoReconnectFiresSecondOnOpen() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        client = buildClient(new URI(TestServerManager.wsUrl("/close")),
                latch::countDown,
                null, null, null, null,
                e -> {},
                (code, reason) -> {});
        client.enableAutomaticReconnection(500);
        client.connect();
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Second onOpen (after reconnect) never fired");
    }

    @Test
    void illegalSchemeDeliversExceptionViaCallback() throws Exception {
        // IllegalSchemeException is thrown in the background connection thread
        // and delivered through onException, not thrown synchronously from connect().
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> caught = new AtomicReference<>();
        client = buildClient(new URI("http://localhost:" + TestServerManager.PORT + "/echo"),
                null, null, null, null, null,
                e -> { caught.set(e); latch.countDown(); },
                null);
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onException never fired for illegal scheme");
        assertInstanceOf(IllegalSchemeException.class, caught.get());
    }

    @Test
    void customHeaderDoesNotBreakHandshake() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client = buildClient(new URI(TestServerManager.wsUrl("/echo")),
                latch::countDown,
                null, null, null, null,
                e -> latch.countDown(),
                null);
        client.addHeader("Origin", "http://test.example.com");
        client.addHeader("X-Custom-Header", "test-value");
        client.connect();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onOpen never fired with custom headers");
    }
}
