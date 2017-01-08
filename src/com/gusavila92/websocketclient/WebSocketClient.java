package com.gusavila92.websocketclient;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Random;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public abstract class WebSocketClient implements Runnable
{
    //-------------------------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------------------------

    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private URI uri;
    private long reconnectionPeriod;

    private Socket socket;

    private boolean isClosed;

    //-------------------------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------------------------

    public WebSocketClient(URI uri, long reconnectionPeriod)
    {
        this.uri = uri;
        this.reconnectionPeriod = reconnectionPeriod;

        isClosed = false;
    }

    //-------------------------------------------------------------------------------------------
    // Abstract methods
    // ------------------------------------------------------------------------------------------

    public abstract void onOpen();

    public abstract void onTextReceived(String message);

    public abstract void onBinaryReceived(byte[] data);

    public abstract void onPingReceived(byte[] data);

    public abstract void onPongReceived();

    public abstract void onDisconnect(IOException exception);

    public abstract void onClose();

    //-------------------------------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------------------------------

    /**
     * Start a new connection
     */
    public void connect()
    {
        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * Send a string message
     * @param message
     */
    public void send(String message)
    {
        byte[] payload = null;
        try
        {
            payload = message.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }

        send(OPCODE_TEXT, payload);
    }

    /**
     * Send a binary message
     * @param payload
     */
    public void send(byte[] payload)
    {
        send(OPCODE_BINARY, payload);
    }

    /**
     * Close the WebSocket
     */
    public void close()
    {
        try
        {
            isClosed = true;

            if(socket != null)
                socket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //-------------------------------------------------------------------------------------------
    // Private methods
    // ------------------------------------------------------------------------------------------

    private void createSocket() throws IOException
    {
        if(uri.getScheme().equals("ws"))
        {
            SocketFactory socketFactory = SocketFactory.getDefault();
            socket = socketFactory.createSocket();
        }
        else if(uri.getScheme().equals("wss"))
        {
            SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = socketFactory.createSocket();
        }
    }

    /**
     * Start the connection
     * @throws IOException
     */
    private void startConnection() throws IOException
    {
        if(socket instanceof SSLSocket)
        {
            if(uri.getPort() == -1)
                socket.connect(new InetSocketAddress(uri.getHost(), 443));
            else
                socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
        }
        else
        {
            if(uri.getPort() == -1)
                socket.connect(new InetSocketAddress(uri.getHost(), 80));
            else
                socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
        }

        byte[] handshake = createHandshake();
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(handshake);
        outputStream.flush();

        verifyServerHandshake();

        onOpen();

        read();
    }

    /**
     * Create and return a byte array containing the handshake
     * @return
     */
    private byte[] createHandshake()
    {
        StringBuilder builder = new StringBuilder();

        String path = uri.getRawPath();
        String query = uri.getRawQuery();

        String requestUri;
        if(query == null)
            requestUri = path;
        else
            requestUri = path + "?" + query;

        builder.append("GET " + requestUri + " HTTP/1.1");
        builder.append("\r\n");

        if(uri.getPort() == -1)
            builder.append("Host: " + uri.getHost());
        else
            builder.append("Host: " + uri.getHost() + ":" + uri.getPort());

        builder.append("\r\n");

        builder.append("Upgrade: websocket");
        builder.append("\r\n");

        builder.append("Connection: Upgrade");
        builder.append("\r\n");

        byte[] key = new byte[16];
        Random random = new Random();
        random.nextBytes(key);

        String base64key = Base64.encodeToString(key, Base64.NO_WRAP);

        builder.append("Sec-WebSocket-Key: " + base64key);
        builder.append("\r\n");

        builder.append("Sec-WebSocket-Version: 13");
        builder.append("\r\n");
        builder.append("\r\n");

        String handshake = builder.toString();

        try
        {
            return handshake.getBytes("ASCII");
        }
        catch (UnsupportedEncodingException e)
        {
            return null;
        }
    }

    private void send(int opcode, byte[] payload)
    {
        int nextPosition;

        byte[] frame;
        if(payload.length < 126)
            frame = new byte[6 + payload.length];
        else if(payload.length < 65536)
            frame = new byte[8 + payload.length];
        else
            frame = new byte[14 + payload.length];

        frame[0] = (byte) (-128 | opcode);

        if(payload.length < 126)
        {
            frame[1] = (byte) (-128 | payload.length);
            nextPosition = 2;
        }
        else
        {
            frame[1] = 126;

            if(payload.length < 65536)
            {
                byte[] d = to2ByteArray(payload.length);
                frame[2] = d[0];
                frame[3] = d[1];
                nextPosition = 4;
            }
            else
            {
                byte[] a = to2ByteArray(127);
                frame[2] = a[0];
                frame[3] = a[1];

                byte[] d = to8ByteArray(payload.length);
                frame[4] = d[0];
                frame[5] = d[1];
                frame[6] = d[2];
                frame[7] = d[3];
                frame[8] = d[4];
                frame[9] = d[5];
                frame[10] = d[6];
                frame[11] = d[7];
                nextPosition = 12;
            }
        }

        byte[] mask = new byte[4];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(mask);

        frame[nextPosition] = mask[0];
        frame[nextPosition + 1] = mask[1];
        frame[nextPosition + 2] = mask[2];
        frame[nextPosition + 3] = mask[3];
        nextPosition += 4;

        for(int i = 0; i < payload.length; i++)
        {
            frame[nextPosition] = ((byte) (payload[i] ^ mask[i % 4]));
            nextPosition++;
        }

        try
        {
            if(socket != null)
            {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(frame);
                outputStream.flush();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Listen for changes coming from the WebSocket
     * @throws IOException
     */
    private void read() throws IOException
    {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream(), 65536);

        int firstByte;
        while((firstByte = bufferedInputStream.read()) != -1)
        {
            int fin = (firstByte << 24) >>> 31;
            int rsv1 = (firstByte << 25) >>> 31;
            int rsv2 = (firstByte << 26) >>> 31;
            int rsv3 = (firstByte << 27) >>> 31;
            int opcode = (firstByte << 28) >>> 28;

            int secondByte = bufferedInputStream.read();

            int mask = (secondByte << 24) >>> 31;
            int payloadLength = (secondByte << 25) >>> 25;

            if(payloadLength == 126)
            {
                int thirdByte = bufferedInputStream.read();
                int fourthByte = bufferedInputStream.read();

                byte[] b1 = new byte[]{0, 0, (byte)thirdByte, (byte)fourthByte};
                payloadLength = fromByteArray(b1);
            }
            else if(payloadLength == 127)
            {
                int fifthByte = bufferedInputStream.read();
                int sixthByte = bufferedInputStream.read();
                int seventhByte = bufferedInputStream.read();
                int eightByte = bufferedInputStream.read();
                int ninethByte = bufferedInputStream.read();
                int tenthByte = bufferedInputStream.read();
                int eleventhByte = bufferedInputStream.read();
                int twelfthByte = bufferedInputStream.read();

                byte[] b2 = new byte[]{(byte)ninethByte, (byte)tenthByte, (byte)eleventhByte, (byte)twelfthByte};
                payloadLength = fromByteArray(b2);
            }

            byte[] data = new byte[payloadLength];
            for(int i = 0; i < payloadLength; i++)
                data[i] = (byte) bufferedInputStream.read();

            if(opcode == OPCODE_TEXT)
                onTextReceived(new String(data, "UTF-8"));
            else if(opcode == OPCODE_BINARY)
                onBinaryReceived(data);
            else if(opcode == OPCODE_CLOSE)
                onClose();
            else if(opcode == OPCODE_PING)
                onPingReceived(data);
            else if(opcode == OPCODE_PONG)
                onPongReceived();
        }

        throw new IOException();
    }

    /**
     * Convert the int value to a 2 byte array
     * @param value
     * @return
     */
    private static byte[] to2ByteArray(int value)
    {
        return new byte[] {
                (byte) (value >>> 8),
                (byte) value};
    }

    /**
     * Convert the int value to a 8 byte array
     * @param value
     * @return
     */
    private static byte[] to8ByteArray(int value)
    {
        return new byte[] {
                (byte) (value >>> 56),
                (byte) (value >>> 48),
                (byte) (value >>> 40),
                (byte) (value >>> 32),
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    /**
     * Convert the byte array to an integer
     * @param bytes
     * @return
     */
    private static int fromByteArray(byte[] bytes)
    {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    /**
     * Try to reconnect automatically to the WebSocket
     */
    private void reconnect()
    {
        try
        {
            Thread.sleep(reconnectionPeriod);
            createSocket();
            startConnection();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            if(!isClosed)
            {
                onDisconnect(e);
                reconnect();
            }
        }
    }

    /**
     *
     * @throws IOException
     */
    private void verifyServerHandshake() throws IOException
    {
        InputStream inputStream = socket.getInputStream();

        byte[] buffer = new byte[8192];
        int quantity = inputStream.read(buffer);

        if(quantity == -1)
            throw new IOException();
        else
        {
            byte[] serverHandshake = new byte[quantity];

            for(int i = 0; i < quantity; i++)
            {
                serverHandshake[i] = buffer[i];
            }
        }
    }

    @Override
    public void run()
    {
        try
        {
            createSocket();
            startConnection();
        }
        catch (IOException e)
        {
            if(!isClosed)
            {
                onDisconnect(e);
                reconnect();
            }
        }
    }
}
