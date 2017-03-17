package com.gusavila92.websocketclient;

import com.gusavila92.apache.commons.codec.binary.Base64;
import com.gusavila92.apache.commons.codec.digest.DigestUtils;
import com.gusavila92.apache.http.Header;
import com.gusavila92.apache.http.HttpException;
import com.gusavila92.apache.http.HttpResponse;
import com.gusavila92.apache.http.StatusLine;
import com.gusavila92.apache.http.impl.io.DefaultHttpResponseParser;
import com.gusavila92.apache.http.impl.io.HttpTransportMetricsImpl;
import com.gusavila92.apache.http.impl.io.SessionInputBufferImpl;
import com.gusavila92.apache.http.io.HttpMessageParser;
import com.gusavila92.websocketclient.common.Utils;
import com.gusavila92.websocketclient.exceptions.UnknownOpcodeException;
import com.gusavila92.websocketclient.exceptions.IllegalSchemeException;
import com.gusavila92.websocketclient.exceptions.InvalidServerHandshakeException;
import com.gusavila92.websocketclient.model.Payload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implements the WebSocket protocol
 * 
 * @author Gustavo Avila
 *
 */
public abstract class WebSocketClient {
	// GUID for Sec-WebSocket-Accept
	private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	// All supported opcodes
	private static final int OPCODE_CONTINUATION = 0x0;
	private static final int OPCODE_TEXT = 0x1;
	private static final int OPCODE_BINARY = 0x2;
	private static final int OPCODE_CLOSE = 0x8;
	private static final int OPCODE_PING = 0x9;
	private static final int OPCODE_PONG = 0xA;

	/**
	 * Connection URI
	 */
	private final URI uri;

	/**
	 * Custom headers to be included into the handshake
	 */
	private final Map<String, String> headers;

	/**
	 * The writer thread
	 */
	private final Thread writerThread;

	/**
	 * Flag indicating if there are pending changes waiting to be read by the
	 * writer thread It is used to avoid a missed signal between threads
	 */
	private volatile boolean pendingMessages;

	/**
	 * Flag indicating if the close method was called before establishing a
	 * connection If this is true, a connection will never be established
	 */
	private volatile boolean isClosed;

	/**
	 * The data waiting to be read from the writer thread
	 */
	private final LinkedList<Payload> outBuffer;

	/**
	 * Cryptographically secure random generator used for the masking key
	 */
	private final SecureRandom secureRandom;

	/**
	 * This will act as a lock for synchronized statements
	 */
	private final Object lock;

	/**
	 * Socket for the underlying connection
	 */
	private Socket socket;

	/**
	 * The socket input stream
	 */
	private BufferedInputStream bis;

	/**
	 * The socket output stream
	 */
	private BufferedOutputStream bos;

	/**
	 * Initialize all the variables
	 *
	 * @param uri
	 * @param headers
	 */
	public WebSocketClient(URI uri, Map<String, String> headers) {
		this.uri = uri;
		this.headers = headers;
		this.pendingMessages = false;
		this.isClosed = false;
		this.outBuffer = new LinkedList<Payload>();
		this.secureRandom = new SecureRandom();
		this.lock = new Object();

		this.writerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (lock) {
					while (true) {
						if (!pendingMessages) {
							try {
								lock.wait();
							} catch (InterruptedException e) {
								// This should never happen
							}
						}

						pendingMessages = false;

						if (socket.isClosed()) {
							return;
						} else {
							while (outBuffer.size() > 0) {
								Payload payload = outBuffer.poll();
								int opcode = payload.getOpcode();
								byte[] data = payload.getData();

								try {
									send(opcode, data);
								} catch (IOException e) {
									// Reader thread will notify this exception
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
	 * Called when the WebSocket handshake has been accepted and the WebSocket
	 * is ready to send and receive data
	 */
	public abstract void onOpen();

	/**
	 * Called when a text message has been received
	 *
	 * @param message
	 */
	public abstract void onTextReceived(String message);

	/**
	 * Called when a binary message has been received
	 *
	 * @param data
	 */
	public abstract void onBinaryReceived(byte[] data);

	/**
	 * Called when a ping message has been received
	 *
	 * @param data
	 */
	public abstract void onPingReceived(byte[] data);

	/**
	 * Called when a pong message has been received
	 */
	public abstract void onPongReceived();

	/**
	 * Called when an exception has occurred It it usually called on an
	 * IOException to indicate a connection error
	 *
	 * @param e
	 */
	public abstract void onException(Exception e);

	/**
	 * Called when a close code has been received
	 */
	public abstract void onCloseReceived();

	/**
	 * Starts a new asynchronous WebSocket connection
	 */
	public void connectAsync() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				connect();
			}
		}).start();
	}

	/**
	 * Starts a new synchronous WebSocket connection You have to manage you own
	 * thread
	 */
	public void connectBlocking() {
		connect();
	}

	/**
	 * Sends a text message
	 *
	 * @param message
	 */
	public void send(String message) {
		byte[] data = message.getBytes(Charset.forName("UTF-8"));
		final Payload payload = new Payload(OPCODE_TEXT, data);

		new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (lock) {
					outBuffer.add(payload);
					pendingMessages = true;
					lock.notify();
				}
			}
		}).start();
	}

	/**
	 * Sends a binary message
	 *
	 * @param data
	 */
	public void send(byte[] data) {
		final Payload payload = new Payload(OPCODE_BINARY, data);

		new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (lock) {
					outBuffer.add(payload);
					pendingMessages = true;
					lock.notify();
				}
			}
		}).start();
	}

	public void close() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				closeInternal();
			}

		}).start();
	}

	/**
	 * Starts a new WebSocket connection It could be asynchronous or synchronous
	 */
	private void connect() {
		try {
			boolean success = createAndConnectTCPSocket();
			if (success) {
				startConnection();
			}
		} catch (Exception e) {
			synchronized (lock) {
				if (!isClosed) {
					onException(e);
				}
			}
			closeInternal();
		}
	}

	/**
	 * Creates and connects a TCP socket for the underlying connection
	 *
	 * @return true is the socket was succesfully connected, false otherwise
	 * @throws IOException
	 */
	private boolean createAndConnectTCPSocket() throws IOException {
		synchronized (lock) {
			if (!isClosed) {
				String scheme = uri.getScheme();
				int port = uri.getPort();
				if (scheme != null) {
					if (scheme.equals("ws")) {
						SocketFactory socketFactory = SocketFactory.getDefault();
						socket = socketFactory.createSocket();

						if (port != -1) {
							socket.connect(new InetSocketAddress(uri.getHost(), port));
						} else {
							socket.connect(new InetSocketAddress(uri.getHost(), 80));
						}
					} else if (scheme.equals("wss")) {
						SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
						socket = socketFactory.createSocket();

						if (port != -1) {
							socket.connect(new InetSocketAddress(uri.getHost(), port));
						} else {
							socket.connect(new InetSocketAddress(uri.getHost(), 443));
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

		onOpen();

		bis = new BufferedInputStream(socket.getInputStream(), 65536);
		read();
	}

	/**
	 * Creates and returns a byte array containing the client handshake
	 *
	 * @param base64Key
	 * @return
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

		if (headers != null) {
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				builder.append(entry.getKey() + ": " + entry.getValue());
				builder.append("\r\n");
			}
		}

		builder.append("\r\n");

		String handshake = builder.toString();
		return handshake.getBytes(Charset.forName("ASCII"));
	}

	/**
	 * Verifies the validity of the server handshake
	 *
	 * @param inputStream
	 * @param secWebSocketKey
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
				throw new InvalidServerHandshakeException("Invalid status code. Expected 101, received: " + statusCode);
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
				throw new InvalidServerHandshakeException("Invalid value for header Sec-WebSocket-Accept. Expected: "
						+ secWebSocketAccept + ", received: " + secWebSocketAcceptValue);
			}
		} catch (HttpException e) {
			throw new InvalidServerHandshakeException(e.getMessage());
		}
	}

	/**
	 * Sends a message given an opcode and a payload
	 *
	 * @param opcode
	 * @param payload
	 * @throws IOException
	 */
	private void send(int opcode, byte[] payload) throws IOException {
		// The position of the data frame in which the next portion of code
		// will start writing bytes
		int nextPosition;

		// The data frame
		byte[] frame;

		// The length of the payload data
		int length = payload.length;

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
			// plus 4 bytes for the masking key, plus the length of the payload
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
			// plus 4 bytes for the masking key, plus the length of the payload
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
	 * Listen for changes coming from the WebSocket
	 *
	 * @throws IOException
	 */
	private void read() throws IOException {
		// The first byte of every data frame
		int firstByte;

		// Loop until there are not more data to be read from the InputStream
		while ((firstByte = bis.read()) != -1) {
			// Data contained in the first byte
			// int fin = (firstByte << 24) >>> 31;
			// int rsv1 = (firstByte << 25) >>> 31;
			// int rsv2 = (firstByte << 26) >>> 31;
			// int rsv3 = (firstByte << 27) >>> 31;
			int opcode = (firstByte << 28) >>> 28;

			// Reads the second byte
			int secondByte = bis.read();
			if (secondByte == -1) {
				throw new IOException("Unexpected end of stream");
			}

			// Data contained in the second byte
			// int mask = (secondByte << 24) >>> 31;
			int payloadLength = (secondByte << 25) >>> 25;

			// If the length of payload data is less than 126, that's the final
			// payload length
			// Otherwise, it must be calculated as follows
			if (payloadLength == 126) {
				// Attempts to read the next 2 bytes
				byte[] nextTwoBytes = new byte[2];
				for (int i = 0; i < 2; i++) {
					byte b = (byte) bis.read();
					if (b == -1) {
						throw new IOException("Unexpected end of stream");
					}
					nextTwoBytes[i] = b;
				}

				// Those last 2 bytes will be interpreted as a 16-bit unsigned
				// integer
				byte[] integer = new byte[] { 0, 0, nextTwoBytes[0], nextTwoBytes[1] };
				payloadLength = Utils.fromByteArray(integer);
			} else if (payloadLength == 127) {
				// Attempts to read the next 8 bytes
				byte[] nextEightBytes = new byte[8];
				for (int i = 0; i < 8; i++) {
					byte b = (byte) bis.read();
					if (b == -1) {
						throw new IOException("Unexpected end of stream");
					}
					nextEightBytes[i] = b;
				}

				// Only the last 4 bytes matter because Java doesn't support
				// arrays with more than 2^31 -1 elements, so a 64-bit unsigned
				// integer cannot be processed
				// Those last 4 bytes will be interpreted as a 32-bit unsigned
				// integer
				byte[] integer = new byte[] { nextEightBytes[4], nextEightBytes[5], nextEightBytes[6],
						nextEightBytes[7] };
				payloadLength = Utils.fromByteArray(integer);
			}

			// Attempts to read the payload data
			byte[] data = new byte[payloadLength];
			for (int i = 0; i < payloadLength; i++) {
				byte b = (byte) bis.read();
				if (b == -1) {
					throw new IOException("Unexpected end of stream");
				}
				data[i] = b;
			}

			// Execute the action depending on the opcode
			switch (opcode) {
			case OPCODE_CONTINUATION:
				// Should be implemented
				break;
			case OPCODE_TEXT:
				onTextReceived(new String(data, Charset.forName("UTF-8")));
				break;
			case OPCODE_BINARY:
				onBinaryReceived(data);
				break;
			case OPCODE_CLOSE:
				closeInternal();
				onCloseReceived();
				return;
			case OPCODE_PING:
				onPingReceived(data);
				break;
			case OPCODE_PONG:
				onPongReceived();
				break;
			default:
				closeInternal();
				Exception e = new UnknownOpcodeException("Unknown opcode: 0x" + Integer.toHexString(opcode));
				onException(e);
				return;
			}
		}

		// If there are not more data to be read,
		// and if the connection didn't receive a close frame,
		// an IOException must be thrown because the connection didn't close
		// gracefully
		throw new IOException("Unexpected end of stream");
	}

	private void closeInternal() {
		try {
			synchronized (lock) {
				if (!isClosed) {
					isClosed = true;
					if (socket != null) {
						socket.close();
						pendingMessages = true;
						lock.notify();
					}
				}
			}
		} catch (IOException e) {
			// This should never happen
		}
	}
}
