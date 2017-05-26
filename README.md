# Java/Android WebSocket Client
A very lightweight WebSocket client library for Java/Android which aims to implement the WebSocket protocol as defined in RFC 6455.

## Download
This library is published into JCenter and Maven Central.

### Gradle
```
compile 'tech.gusavila92:java-android-websocket-client:1.1.2'
```
### Maven
```
<dependency>
  <groupId>tech.gusavila92</groupId>
  <artifactId>java-android-websocket-client</artifactId>
  <version>1.1.2</version>
  <type>pom</type>
</dependency>
```

This is an example of how you can start a new connection.
```
private WebSocketClient webSocketClient;

private void createWebSocketClient() {
	URI uri;
        try {
            uri = new URI(ws://localhost:8080/test);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                System.out.println("onOpen");
                webSocketClient.send("Hello, World!");
            }

            @Override
            public void onTextReceived(String message) {
                System.out.println("onTextReceived");
            }

            @Override
            public void onBinaryReceived(byte[] data) {
                System.out.println("onBinaryReceived");
            }

            @Override
            public void onPingReceived(byte[] data) {
                System.out.println("onPingReceived");
            }

            @Override
            public void onPongReceived(byte[] data) {
                System.out.println("onPongReceived");
            }

            @Override
            public void onException(Exception e) {
                System.out.println(e.getMessage());
            }

            @Override
            public void onCloseReceived() {
                System.out.println("onCloseReceived");
            }
        };

        webSocketClient.setConnectTimeout(10000);
        webSocketClient.setReadTimeout(60000);
        webSocketClient.addHeader("Origin", "http://developer.example.com");
        webSocketClient.enableAutomaticReconnection(5000);
        webSocketClient.connect();
}
```
If you don't specify a port into the URI, the default port will be 80 for *ws* and 443 for *wss*.

This is the list of the default HTTP Headers that will be included into the WebSocket client handshake
- Host
- Upgrade
- Connection
- Sec-WebSocket-Key
- Sec-WebSocket-Version

If you wish to include more headers, like *Origin*, you can add them using ```addHeader(String key, String value)``` method.

When an Exception occurs, the library calls ```onException(Exception e)```.

When you are finished using the WebSocket, you can call ```webSocketClient.close()``` to close the connection.

## Automatic reconnection
Automatic reconnection is supported through ```enableAutomaticReconnection(long waitTimeBeforeReconnection)``` method. Every time an *IOException* occurs, ```onException(Exception e)``` method is called and automatically a new connection is created and started. For performance reasons, between every reconnection intent you should specify a wait time before trying to reconnect again, using ```waitTimeBeforeReconnection``` parameter.

## Timeouts
Connect and read timeouts are supported through ```setConnectTimeout(int connectTimeout)``` and ```setReadTimeout(int readTimeout)```. If one of those timeouts expires, ```onException(Exception e)``` is called. If automatic reconnection is enabled, a new connection could be established automatically.

Connect timeout is used in establishing a TCP connection between this client and the server. Read timeout is used when this WebSocket Client doesn't received data for a long time. A server could send data periodically to ensure that the underlying TCP connection is not closed unexpectedly due to an idle connection, and this read timeout is designed for this purpose.

## ws and wss
This library supports secure and insecure WebSockets. You just need to define the scheme as *wss* or *ws* (case-sensitive) into the URI.

## Build
You need Gradle to build the project. Any version will do.
```
gradle build
```

Then you cand find a ```.jar``` file inside the ```build/libs``` folder
### Eclipse
If you want to open the project in Eclipse, just type
```
gradle eclipse
```

and Gradle will automatically generate the source files required to open the project in Eclipse.
## Minimum requirements
This libary requires at minimum Java 1.6 or Android 1.6 (API 4)

## License

Copyright 2017 Gustavo Avila

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
