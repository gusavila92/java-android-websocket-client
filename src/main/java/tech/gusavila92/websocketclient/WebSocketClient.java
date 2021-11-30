package tech.gusavila92.websocketclient;

import tech.gusavila92.apache.commons.codec.binary.Base64;
import tech.gusavila92.apache.commons.codec.digest.DigestUtils;
import tech.gusavila92.apache.http.Header;
import tech.gusavila92.apache.http.HttpException;
import tech.gusavila92.apache.http.HttpResponse;
import tech.gusavila92.apache.http.StatusLine;
import tech.gusavila92.apache.http.impl.io.DefaultHttpResponseParser;
import tech.gusavila92.apache.http.impl.io.HttpTransportMetricsImpl;
import tech.gusavila92.apache.http.impl.io.SessionInputBufferImpl;
import tech.gusavila92.apache.http.io.HttpMessageParser;
import tech.gusavila92.websocketclient.common.Utils;
import tech.gusavila92.websocketclient.exceptions.UnknownOpcodeException;
import tech.gusavila92.websocketclient.exceptions.IllegalSchemeException;
import tech.gusavila92.websocketclient.exceptions.InvalidServerHandshakeException;
import tech.gusavila92.websocketclient.model.Payload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implements the WebSocket protocol as defined in RFC 6455
 *
 * @author Gustavo Avila
 */
public abstract class WebSocketClient {
    /**
     * GUID used when processing Sec-WebSocket-Accept response header
     */
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /**
     * Denotes a continuation frame
     */
    private static final int OPCODE_CONTINUATION = 0x0;

    /**
     * Denotes a UTF-8 encoded text frame
     */
    private static final int OPCODE_TEXT = 0x1;

    /**
     * Denotes a binary frame
     */
    private static final int OPCODE_BINARY = 0x2;

    /**
     * Denotes a close frame
     */
    private static final int OPCODE_CLOSE = 0x8;

    /**
     * Denotes a Ping frame
     */
    private static final int OPCODE_PING = 0x9;

    /**
     * Denotes a Pong frame
     */
    private static final int OPCODE_PONG = 0xA;

    /**
     * Global lock for synchronized statements
     */
    private final Object globalLock;

    /**
     * Connection URI
     */
    private final URI uri;

    /**
     * Cryptographically secure random generator used for the masking key
     */
    private final SecureRandom secureRandom;

    /**
     * Timeout in milliseconds to be used while the WebSocket is being connected
     */
    private int connectTimeout;

    /**
     * Timeout in milliseconds for considering and idle connection as dead An
     * idle connection is a connection that has not received data for a long
     * time
     */
    private int readTimeout;

    /**
     * Indicates if a connection must be reopened automatically due to an
     * IOException
     */
    private boolean automaticReconnection;

    /**
     * Time in milliseconds to wait before opening a new WebSocket connection
     */
    private long waitTimeBeforeReconnection;

    /**
     * Indicates if the connect() method was called
     */
    private volatile boolean isRunning;

    /**
     * Custom headers to be included into the handshake
     */
    private Map<String, String> headers;

    /**
     * Underlying WebSocket connection This instance could change due to an
     * automatic reconnection Every time an automatic reconnection is fired,
     * this reference changes
     */
    private volatile WebSocketConnection webSocketConnection;

    /**
     * Thread used for reconnection intents
     */
    private volatile Thread reconnectionThread;


    private SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

    /**
     * Initialize all the variables
     *
     * @param uri URI of the WebSocket server
     */
    public WebSocketClient(URI uri) {
        this.globalLock = new Object();
        this.uri = uri;
        this.secureRandom = new SecureRandom();
        this.connectTimeout = 0;
        this.readTimeout = 0;
        this.automaticReconnection = false;
        this.waitTimeBeforeReconnection = 0;
        this.isRunning = false;
        this.headers = new HashMap<String, String>();
        webSocketConnection = new WebSocketConnection();
    }


    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        socketFactory = sslSocketFactory;
    }

    /**
     * Called when the WebSocket handshake has been accepted and the WebSocket
     * is ready to send and receive data
     */
    public abstract void onOpen();

    /**
     * Called when a text message has been received
     *
     * @param message The UTF-8 encoded text received
     */
    public abstract void onTextReceived(String message);

    /**
     * Called when a binary message has been received
     *
     * @param data The binary message received
     */
    public abstract void onBinaryReceived(byte[] data);

    /**
     * Called when a ping message has been received
     *
     * @param data Optional data
     */
    public abstract void onPingReceived(byte[] data);

    /**
     * Called when a pong message has been received
     *
     * @param data Optional data
     */
    public abstract void onPongReceived(byte[] data);

    /**
     * Called when an exception has occurred
     *
     * @param e The exception that occurred
     */
    public abstract void onException(Exception e);

    /**
     * Called when a close code has been received
     */
    public abstract void onCloseReceived();

    /**
     * Adds a new header to the set of headers that will be send into the
     * handshake This header will be added to the set of headers: Host, Upgrade,
     * Connection, Sec-WebSocket-Key, Sec-WebSocket-Version
     *
     * @param key   Name of the new header
     * @param value Value of the new header
     */
    public void addHeader(String key, String value) {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException("Cannot add header while WebSocketClient is running");
            }
            this.headers.put(key, value);
        }
    }

    /**
     * Set the timeout that will be used while the WebSocket is being connected
     * If timeout expires before connecting, an IOException will be thrown
     *
     * @param connectTimeout Timeout in milliseconds
     */
    public void setConnectTimeout(int connectTimeout) {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException("Cannot set connect timeout while WebSocketClient is running");
            } else if (connectTimeout < 0) {
                throw new IllegalStateException("Connect timeout must be greater or equal than zero");
            }
            this.connectTimeout = connectTimeout;
        }
    }

    /**
     * Sets the timeout for considering and idle connection as dead An idle
     * connection is a connection that has not received data for a long time If
     * timeout expires, an IOException will be thrown and you should consider
     * opening a new WebSocket connection, or delegate this functionality to
     * this WebSocketClient using the method setAutomaticReconnection(true)
     *
     * @param readTimeout Read timeout in milliseconds before considering an idle
     *                    connection as dead
     */
    public void setReadTimeout(int readTimeout) {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException("Cannot set read timeout while WebSocketClient is running");
            } else if (readTimeout < 0) {
                throw new IllegalStateException("Read timeout must be greater or equal than zero");
            }
            this.readTimeout = readTimeout;
        }
    }

    /**
     * Indicates that a connection must be reopened automatically due to an
     * IOException. Every time a connection fails due to an IOException,
     * onException() method is called before establishing a new connection. A
     * connection will be reopened automatically if an IOException occurred, but
     * other kinds of Exception will not reopen a connection
     *
     * @param waitTimeBeforeReconnection Wait time in milliseconds before trying to establish a new
     *                                   WebSocket connection. For performance reasons, you should set
     *                                   a wait time greater than zero
     */
    public void enableAutomaticReconnection(long waitTimeBeforeReconnection) {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException(
                        "Cannot enable automatic reconnection while WebSocketClient is running");
            } else if (waitTimeBeforeReconnection < 0) {
                throw new IllegalStateException("Wait time between reconnections must be greater or equal than zero");
            }
            this.automaticReconnection = true;
            this.waitTimeBeforeReconnection = waitTimeBeforeReconnection;
        }
    }

    /**
     * Indicates that a connection must not be reopened automatically due to an
     * IOException
     */
    public void disableAutomaticReconnection() {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException(
                        "Cannot disable automatic reconnection while WebSocketClient is running");
            }
            this.automaticReconnection = false;
        }
    }

    /**
     * Starts a new connection to the WebSocket server
     */
    public void connect() {
        synchronized (globalLock) {
            if (isRunning) {
                throw new IllegalStateException("WebSocketClient is not reusable");
            }

            this.isRunning = true;
            createAndStartConnectionThread();
        }
    }

    /**
     * Creates and starts the thread that will handle the WebSocket connection
     */
    private void createAndStartConnectionThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = webSocketConnection.createAndConnectTCPSocket();
                    if (success) {
                        webSocketConnection.startConnection();
                    }
                } catch (Exception e) {
                    synchronized (globalLock) {
                        if (isRunning) {
                            webSocketConnection.closeInternal();

                            onException(e);

                            if (e instanceof IOException && automaticReconnection) {
                                createAndStartReconnectionThread();
                            }else if (e instanceof InvalidServerHandshakeException && automaticReconnection) {
                                createAndStartReconnectionThread();
                            }
                        }
                    }
                }
            }
        }).start();
    }

    /**
     * Creates and starts the thread that will open a new WebSocket connection
     */
    private void createAndStartReconnectionThread() {
        reconnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(waitTimeBeforeReconnection);

                    synchronized (globalLock) {
                        if (isRunning) {
                            webSocketConnection = new WebSocketConnection();
                            createAndStartConnectionThread();
                        }
                    }
                } catch (InterruptedException e) {
                    // Expected behavior when the WebSocket connection is closed
                }
            }
        });
        reconnectionThread.start();
    }

    /**
     * If the close method wasn't called, call onOpen method.
     */
    private void notifyOnOpen() {
        synchronized (globalLock) {
            if (isRunning) {
                onOpen();
            }
        }
    }

    /**
     * If the close method wasn't called, call onTextReceived(String message)
     * method.
     */
    private void notifyOnTextReceived(String message) {
        synchronized (globalLock) {
            if (isRunning) {
                onTextReceived(message);
            }
        }
    }

    /**
     * If the close method wasn't called, call onBinaryReceived(byte[] data)
     * method.
     */
    private void notifyOnBinaryReceived(byte[] data) {
        synchronized (globalLock) {
            if (isRunning) {
                onBinaryReceived(data);
            }
        }
    }

    /**
     * If the close method wasn't called, call onPingReceived(byte[] data)
     * method.
     */
    private void notifyOnPingReceived(byte[] data) {
        synchronized (globalLock) {
            if (isRunning) {
                onPingReceived(data);
            }
        }
    }

    /**
     * If the close method wasn't called, call onPongReceived(byte[] data)
     * method.
     */
    private void notifyOnPongReceived(byte[] data) {
        synchronized (globalLock) {
            if (isRunning) {
                onPongReceived(data);
            }
        }
    }

    /**
     * If the close method wasn't called, call onException(Exception e) method.
     */
    private void notifyOnException(Exception e) {
        synchronized (globalLock) {
            if (isRunning) {
                onException(e);
            }
        }
    }

    /**
     * If the close method wasn't called, call onCloseReceived() method.
     */
    private void notifyOnCloseReceived() {
        synchronized (globalLock) {
            if (isRunning) {
                onCloseReceived();
            }
        }
    }

    /**
     * Sends a text message If the WebSocket is not connected yet, message will
     * be send the next time the connection is opened
     *
     * @param message Message that will be send to the WebSocket server
     */
    public void send(String message) {
        byte[] data = message.getBytes(Charset.forName("UTF-8"));
        final Payload payload = new Payload(OPCODE_TEXT, data);

        new Thread(new Runnable() {
            @Override
            public void run() {
                webSocketConnection.sendInternal(payload);
            }

        }).start();
    }

    /**
     * Sends a binary message If the WebSocket is not connected yet, message
     * will be send the next time the connection is opened
     *
     * @param data Binary data that will be send to the WebSocket server
     */
    public void send(byte[] data) {
        final Payload payload = new Payload(OPCODE_BINARY, data);

        new Thread(new Runnable() {
            @Override
            public void run() {
                webSocketConnection.sendInternal(payload);
            }
        }).start();
    }

    /**
     * Sends a PING frame with an optional data.
     *
     * @param data Data to be sent, or null if there is no data.
     */
    public void sendPing(byte[] data) {
        final Payload payload = new Payload(OPCODE_PING, data);

        new Thread(new Runnable() {
            @Override
            public void run() {
                webSocketConnection.sendInternal(payload);
            }
        }).start();
    }

    /**
     * Sends a PONG frame with an optional data.
     *
     * @param data Data to be sent, or null if there is no data.
     */
    public void sendPong(byte[] data) {
        final Payload payload = new Payload(OPCODE_PONG, data);

        new Thread(new Runnable() {
            @Override
            public void run() {
                webSocketConnection.sendInternal(payload);
            }
        }).start();
    }

    /**
     * Closes the WebSocket connection
     */
    public void close() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (globalLock) {
                    isRunning = false;

                    if (reconnectionThread != null) {
                        reconnectionThread.interrupt();
                    }

                    webSocketConnection.closeInternal();
                }
            }
        }).start();
    }

    /**
     * This represents an existing WebSocket connection
     *
     * @author Gustavo Avila
     */
    private class WebSocketConnection {
        /**
         * Flag indicating if there are pending changes waiting to be read by
         * the writer thread. It is used to avoid a missed signal between
         * threads
         */
        private volatile boolean pendingMessages;

        /**
         * Flag indicating if the closeInternal() method was called
         */
        private volatile boolean isClosed;

        /**
         * Data waiting to be read from the writer thread
         */
        private final LinkedList<Payload> outBuffer;

        /**
         * This will act as a lock for synchronized statements
         */
        private final Object internalLock;

        /**
         * Writer thread
         */
        private final Thread writerThread;

        /**
         * TCP socket for the underlying WebSocket connection
         */
        private Socket socket;

        /**
         * Socket input stream
         */
        private BufferedInputStream bis;

        /**
         * Socket output stream
         */
        private BufferedOutputStream bos;

        /**
         * Initialize the variables that will be used during a valid WebSocket
         * connection
         */
        private WebSocketConnection() {
            this.pendingMessages = false;
            this.isClosed = false;
            this.outBuffer = new LinkedList<Payload>();
            this.internalLock = new Object();

            this.writerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (internalLock) {
                        while (true) {
                            if (!pendingMessages) {
                                try {
                                    internalLock.wait();
                                } catch (InterruptedException e) {
                                    // This should never happen
                                }
                            }

                            pendingMessages = false;

                            if (socket.isClosed()) {
                                return;
                            } else {
                                while (outBuffer.size() > 0) {
                                    Payload payload = outBuffer.removeFirst();
                                    int opcode = payload.getOpcode();
                                    byte[] data = payload.getData();

                                    try {
                                        send(opcode, data);
                                    } catch (IOException e) {
                                        // Reader thread will notify this
                                        // exception
                                        // This thread just need to stop
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        /**
         * Creates and connects a TCP socket for the underlying connection
         *
         * @return true is the socket was successfully connected, false
         * otherwise
         * @throws IOException
         */
        private boolean createAndConnectTCPSocket() throws IOException {
            synchronized (internalLock) {
                if (!isClosed) {
                    String scheme = uri.getScheme();
                    int port = uri.getPort();
                    if (scheme != null) {
                        if (scheme.equals("ws")) {
                            SocketFactory socketFactory = SocketFactory.getDefault();
                            socket = socketFactory.createSocket();
                            socket.setSoTimeout(readTimeout);

                            if (port != -1) {
                                socket.connect(new InetSocketAddress(uri.getHost(), port), connectTimeout);
                            } else {
                                socket.connect(new InetSocketAddress(uri.getHost(), 80), connectTimeout);
                            }
                        } else if (scheme.equals("wss")) {
                            socket = socketFactory.createSocket();
                            socket.setSoTimeout(readTimeout);

                            if (port != -1) {
                                socket.connect(new InetSocketAddress(uri.getHost(), port), connectTimeout);
                            } else {
                                socket.connect(new InetSocketAddress(uri.getHost(), 443), connectTimeout);
                            }
                        } else {
                            throw new IllegalSchemeException("The scheme component of the URI should be ws or wss");
                        }
                    } else {
                        throw new IllegalSchemeException("The scheme component of the URI cannot be null");
                    }

                    return true;
                }

                return false;
            }
        }

        /**
         * Starts the WebSocket connection
         *
         * @throws IOException
         */
        private void startConnection() throws IOException {
            bos = new BufferedOutputStream(socket.getOutputStream(), 65536);

            byte[] key = new byte[16];
            Random random = new Random();
            random.nextBytes(key);
            String base64Key = Base64.encodeBase64String(key);

            byte[] handshake = createHandshake(base64Key);
            bos.write(handshake);
            bos.flush();

            InputStream inputStream = socket.getInputStream();
            verifyServerHandshake(inputStream, base64Key);

            writerThread.start();

            notifyOnOpen();

            bis = new BufferedInputStream(socket.getInputStream(), 65536);
            read();
        }

        /**
         * Creates and returns a byte array containing the client handshake
         *
         * @param base64Key Random generated Sec-WebSocket-Key
         * @return Byte array containing the client handshake
         */
        private byte[] createHandshake(String base64Key) {
            StringBuilder builder = new StringBuilder();

            String path = uri.getRawPath();
            String query = uri.getRawQuery();

            String requestUri;
            if (query == null) {
                requestUri = path;
            } else {
                requestUri = path + "?" + query;
            }

            builder.append("GET " + requestUri + " HTTP/1.1");
            builder.append("\r\n");

            String host;
            if (uri.getPort() == -1) {
                host = uri.getHost();
            } else {
                host = uri.getHost() + ":" + uri.getPort();
            }

            builder.append("Host: " + host);
            builder.append("\r\n");

            builder.append("Upgrade: websocket");
            builder.append("\r\n");

            builder.append("Connection: Upgrade");
            builder.append("\r\n");

            builder.append("Sec-WebSocket-Key: " + base64Key);
            builder.append("\r\n");

            builder.append("Sec-WebSocket-Version: 13");
            builder.append("\r\n");

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.append(entry.getKey() + ": " + entry.getValue());
                builder.append("\r\n");
            }

            builder.append("\r\n");

            String handshake = builder.toString();
            return handshake.getBytes(Charset.forName("ASCII"));
        }

        /**
         * Verifies the validity of the server handshake
         *
         * @param inputStream     Socket input stream
         * @param secWebSocketKey Random generated Sec-WebSocket-Key
         * @throws IOException
         */
        private void verifyServerHandshake(InputStream inputStream, String secWebSocketKey) throws IOException {
            try {
                SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(),
                        8192);
                sessionInputBuffer.bind(inputStream);
                HttpMessageParser<HttpResponse> parser = new DefaultHttpResponseParser(sessionInputBuffer);
                HttpResponse response = parser.parse();

                StatusLine statusLine = response.getStatusLine();
                if (statusLine == null) {
                    throw new InvalidServerHandshakeException("There is no status line");
                }

                int statusCode = statusLine.getStatusCode();
                if (statusCode != 101) {
                    throw new InvalidServerHandshakeException(
                            "Invalid status code. Expected 101, received: " + statusCode);
                }

                Header[] upgradeHeader = response.getHeaders("Upgrade");
                if (upgradeHeader.length == 0) {
                    throw new InvalidServerHandshakeException("There is no header named Upgrade");
                }
                String upgradeValue = upgradeHeader[0].getValue();
                if (upgradeValue == null) {
                    throw new InvalidServerHandshakeException("There is no value for header Upgrade");
                }
                upgradeValue = upgradeValue.toLowerCase();
                if (!upgradeValue.equals("websocket")) {
                    throw new InvalidServerHandshakeException(
                            "Invalid value for header Upgrade. Expected: websocket, received: " + upgradeValue);
                }

                Header[] connectionHeader = response.getHeaders("Connection");
                if (connectionHeader.length == 0) {
                    throw new InvalidServerHandshakeException("There is no header named Connection");
                }
                String connectionValue = connectionHeader[0].getValue();
                if (connectionValue == null) {
                    throw new InvalidServerHandshakeException("There is no value for header Connection");
                }
                connectionValue = connectionValue.toLowerCase();
                if (!connectionValue.equals("upgrade")) {
                    throw new InvalidServerHandshakeException(
                            "Invalid value for header Connection. Expected: upgrade, received: " + connectionValue);
                }

                Header[] secWebSocketAcceptHeader = response.getHeaders("Sec-WebSocket-Accept");
                if (secWebSocketAcceptHeader.length == 0) {
                    throw new InvalidServerHandshakeException("There is no header named Sec-WebSocket-Accept");
                }
                String secWebSocketAcceptValue = secWebSocketAcceptHeader[0].getValue();
                if (secWebSocketAcceptValue == null) {
                    throw new InvalidServerHandshakeException("There is no value for header Sec-WebSocket-Accept");
                }

                String keyConcatenation = secWebSocketKey + GUID;
                byte[] sha1 = DigestUtils.sha1(keyConcatenation);
                String secWebSocketAccept = Base64.encodeBase64String(sha1);
                if (!secWebSocketAcceptValue.equals(secWebSocketAccept)) {
                    throw new InvalidServerHandshakeException(
                            "Invalid value for header Sec-WebSocket-Accept. Expected: " + secWebSocketAccept
                                    + ", received: " + secWebSocketAcceptValue);
                }
            } catch (HttpException e) {
                throw new InvalidServerHandshakeException(e.getMessage());
            }
        }

        /**
         * Sends a message to the WebSocket server
         *
         * @param opcode  Message opcode
         * @param payload Message payload
         * @throws IOException
         */
        private void send(int opcode, byte[] payload) throws IOException {
            // The position of the data frame in which the next portion of code
            // will start writing bytes
            int nextPosition;

            // The data frame
            byte[] frame;

            // The length of the payload data.
            // If the payload is null, length will be 0.
            int length = payload == null ? 0 : payload.length;

            if (length < 126) {
                // If payload length is less than 126,
                // the frame must have the first two bytes, plus 4 bytes for the
                // masking key
                // plus the length of the payload
                frame = new byte[6 + length];

                // The first two bytes
                frame[0] = (byte) (-128 | opcode);
                frame[1] = (byte) (-128 | length);

                // The masking key will start at position 2
                nextPosition = 2;
            } else if (length < 65536) {
                // If payload length is greater than 126 and less than 65536,
                // the frame must have the first two bytes, plus 2 bytes for the
                // extended payload length,
                // plus 4 bytes for the masking key, plus the length of the
                // payload
                frame = new byte[8 + length];

                // The first two bytes
                frame[0] = (byte) (-128 | opcode);
                frame[1] = -2;

                // Puts the length into the data frame
                byte[] array = Utils.to2ByteArray(length);
                frame[2] = array[0];
                frame[3] = array[1];

                // The masking key will start at position 4
                nextPosition = 4;
            } else {
                // If payload length is greater or equal than 65536,
                // the frame must have the first two bytes, plus 8 bytes for the
                // extended payload length,
                // plus 4 bytes for the masking key, plus the length of the
                // payload
                frame = new byte[14 + length];

                // The first two bytes
                frame[0] = (byte) (-128 | opcode);
                frame[1] = -1;

                // Puts the length into the data frame
                byte[] array = Utils.to8ByteArray(length);
                frame[2] = array[0];
                frame[3] = array[1];
                frame[4] = array[2];
                frame[5] = array[3];
                frame[6] = array[4];
                frame[7] = array[5];
                frame[8] = array[6];
                frame[9] = array[7];

                // The masking key will start at position 10
                nextPosition = 10;
            }

            // Generate a random 4-byte masking key
            byte[] mask = new byte[4];
            secureRandom.nextBytes(mask);

            // Puts the masking key into the data frame
            frame[nextPosition] = mask[0];
            frame[nextPosition + 1] = mask[1];
            frame[nextPosition + 2] = mask[2];
            frame[nextPosition + 3] = mask[3];
            nextPosition += 4;

            // Puts the masked payload data into the data frame
            for (int i = 0; i < length; i++) {
                frame[nextPosition] = ((byte) (payload[i] ^ mask[i % 4]));
                nextPosition++;
            }

            // Sends the data frame
            bos.write(frame);
            bos.flush();
        }

        /**
         * Listen for changes coming from the WebSocket server
         *
         * @throws IOException
         */
        private void read() throws IOException {
            // The first byte of every data frame
            int firstByte;

            // Loop until end of stream is reached.
            while ((firstByte = bis.read()) != -1) {
                // Data contained in the first byte
                // int fin = (firstByte << 24) >>> 31;
                // int rsv1 = (firstByte << 25) >>> 31;
                // int rsv2 = (firstByte << 26) >>> 31;
                // int rsv3 = (firstByte << 27) >>> 31;
                int opcode = (firstByte << 28) >>> 28;

                // Reads the second byte
                int secondByte = bis.read();

                // Data contained in the second byte
                // int mask = (secondByte << 24) >>> 31;
                int payloadLength = (secondByte << 25) >>> 25;

                // If the length of payload data is less than 126, that's the
                // final
                // payload length
                // Otherwise, it must be calculated as follows
                if (payloadLength == 126) {
                    // Attempts to read the next 2 bytes
                    byte[] nextTwoBytes = new byte[2];
                    for (int i = 0; i < 2; i++) {
                        byte b = (byte) bis.read();
                        nextTwoBytes[i] = b;
                    }

                    // Those last 2 bytes will be interpreted as a 16-bit
                    // unsigned
                    // integer
                    byte[] integer = new byte[]{0, 0, nextTwoBytes[0], nextTwoBytes[1]};
                    payloadLength = Utils.fromByteArray(integer);
                } else if (payloadLength == 127) {
                    // Attempts to read the next 8 bytes
                    byte[] nextEightBytes = new byte[8];
                    for (int i = 0; i < 8; i++) {
                        byte b = (byte) bis.read();
                        nextEightBytes[i] = b;
                    }

                    // Only the last 4 bytes matter because Java doesn't support
                    // arrays with more than 2^31 -1 elements, so a 64-bit
                    // unsigned
                    // integer cannot be processed
                    // Those last 4 bytes will be interpreted as a 32-bit
                    // unsigned
                    // integer
                    byte[] integer = new byte[]{nextEightBytes[4], nextEightBytes[5], nextEightBytes[6],
                            nextEightBytes[7]};
                    payloadLength = Utils.fromByteArray(integer);
                }

                // Attempts to read the payload data
                byte[] data = new byte[payloadLength];
                for (int i = 0; i < payloadLength; i++) {
                    byte b = (byte) bis.read();
                    data[i] = b;
                }

                // Execute the action depending on the opcode
                switch (opcode) {
                    case OPCODE_CONTINUATION:
                        // Should be implemented
                        break;
                    case OPCODE_TEXT:
                        notifyOnTextReceived(new String(data, Charset.forName("UTF-8")));
                        break;
                    case OPCODE_BINARY:
                        notifyOnBinaryReceived(data);
                        break;
                    case OPCODE_CLOSE:
                        closeInternal();
                        notifyOnCloseReceived();
                        return;
                    case OPCODE_PING:
                        notifyOnPingReceived(data);
                        sendPong(data);
                        break;
                    case OPCODE_PONG:
                        notifyOnPongReceived(data);
                        break;
                    default:
                        closeInternal();
                        Exception e = new UnknownOpcodeException("Unknown opcode: 0x" + Integer.toHexString(opcode));
                        notifyOnException(e);
                        return;
                }
            }

            // If there are not more data to be read,
            // and if the connection didn't receive a close frame,
            // an IOException must be thrown because the connection didn't close
            // gracefully
            throw new IOException("Unexpected end of stream");
        }

        /**
         * Puts the payload into the out buffer and notifies the writer thread
         * that new data is available
         *
         * @param payload Payload to be send to the WebSocket server
         */
        private void sendInternal(Payload payload) {
            synchronized (internalLock) {
                outBuffer.addLast(payload);
                pendingMessages = true;
                internalLock.notify();
            }
        }

        /**
         * Closes the underlying WebSocket connection and notifies the writer
         * thread and the reconnection thread that they must finish
         */
        private void closeInternal() {
            try {
                synchronized (internalLock) {
                    if (!isClosed) {
                        isClosed = true;
                        if (socket != null) {
                            socket.close();
                            pendingMessages = true;
                            internalLock.notify();
                        }
                    }
                }
            } catch (IOException e) {
                // This should never happen
            }
        }
    }
}
