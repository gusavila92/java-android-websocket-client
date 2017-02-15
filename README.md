# java-websocketclient
A WebSocket Client library for Java / Android

This is a very lightweight WebSocket Client library which aims to implement the WebSocket protocol as defined in RFC 6455.

This is an example of how you can start a new connection.
```
        private WebSocketClient client;
	
	private void createWebSocketClient() {
		URI uri = null;
		
		try {
			uri = new URI("ws://localhost:8080/test");
		} catch(URISyntaxException e) {
			e.printStackTrace();
		}
		
		Map<String, String> handshakeHeaders = new HashMap<String, String>();
		handshakeHeaders.put("Origin", "http://www.example.com");
		
		client = new WebSocketClient(uri, handshakeHeaders) {

			@Override
			public void onOpen() {
				System.out.println("open");
			}

			@Override
			public void onTextReceived(String message) {
				System.out.println(message);
			}

			@Override
			public void onBinaryReceived(byte[] data) {
				System.out.println("binary");
			}

			@Override
			public void onPingReceived(byte[] data) {
				System.out.println("ping");
			}

			@Override
			public void onPongReceived() {
				System.out.println("pong");
			}

			@Override
			public void onException(Exception e) {
				System.out.println(e.getMessage());
			}

			@Override
			public void onCloseReceived() {
				System.out.println("close");
			}
		};
		
		client.connect();
		
		client.send("sample message");
	}
```
If you don't specify a port into the URI, the default port will be 80 for *ws* and 443 for *wss*.

This is the list of the default HTTP Headers that will be included into the WebSocket client handshake
- Host
- Upgrade
- Connection
- Sec-WebSocket-Key
- Sec-WebSocket-Version

If you wish to include more headers, like *Origin*, you can pass a *Map* to the constructor; otherwise, just pass *null*.

When an Exception occurs, the library calls onException and automatically closes the socket.

When you are finished using the WebSocket, you can call ```client.close()``` to close the connection.

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
