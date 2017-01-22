# java-websocketclient
A WebSocket Client library for Java / Android

This library implements the WebSocket protocol as defined in RFC 6455.

It can be used in production under your own risk. It was developed from scratch to solve some well-known problems with the WebSocket library I was using before for an Android project.
I have been using this library in my production system without any problems so far, but it has some limitations.

1. Secure WebSocket (WSS) hasn't been tested.
2. You have to create your own thread to send messages once the handshake is accepted.
3. It is not meant to implement the complete WebSocket protocol by now.

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

3. Publish a .jar into the Maven Repository.

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
