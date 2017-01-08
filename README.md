# java-websocketclient
A WebSocket Client library for Java / Android

This library implements the WebSocket protocol as defined in RFC 6455.

It can be used in production under your own risk. It was developed from scratch to solve some well-known problems with the WebSocket library I was using before for an Android project.
I have been using this library in my production system without any problems so far, but it has some limitations.

1. Secure WebSocket (WSS) hasn't been tested.
2. You have to create your own thread to send messages once the handshake is accepted.
3. It only works in Android by now, because it uses the package android.util.Base64. (I will change this ASAP)
4. It is not meant to implement the complete WebSocket protocol by now.

Cool feature: it implements an automatic reconnection once the connection fails for some reason.


This is an example of how you can start a new connection.
```
        URI uri;

        try
        {
            uri = new URI("ws://192.168.0.1");
        }
        catch (URISyntaxException e)
        {
            uri = null;
        }

        webSocketClient = new WebSocketClient(uri, 5000)
        {
            @Override
            public void onOpen()
            {
                
            }

            @Override
            public void onTextReceived(String message)
            {
               
            }

            @Override
            public void onBinaryReceived(byte[] data)
            {

            }

            @Override
            public void onPingReceived(byte[] data)
            {

            }

            @Override
            public void onPongReceived()
            {

            }

            @Override
            public void onDisconnect(IOException exception)
            {
                
            }

            @Override
            public void onClose()
            {
                
            }
        };

        webSocketClient.connect();
```
Next steps
1. Solve those limitations.

2. Provide a better documentation.

3. Provide a build manager like Maven or Gradle.

4. Publish a .jar into the Maven Repository.
