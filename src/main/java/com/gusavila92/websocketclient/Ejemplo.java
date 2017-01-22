package com.gusavila92.websocketclient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Ejemplo {
private WebSocketClient webSocketClient;
	
	public Ejemplo()
	{
		URI uri;

        try
        {
            uri = new URI("ws://52.26.238.251:9090/socket_aplicacion_mensajero?token=1a425d05337e9513683eec390f8e2ceb31e33da198b3a573721356b52271cb8616567c5ae6faeed99ad8069f7d492939");
        }
        catch (URISyntaxException e)
        {
            uri = null;
        }
        
		webSocketClient = new WebSocketClient(uri, 5000)
		{
			@Override
			public void onBinaryReceived(byte[] arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onClose() {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onDisconnect(IOException arg0) {
				// TODO Auto-generated method stub
				System.out.println("error");
			}

			@Override
			public void onOpen() {
				// TODO Auto-generated method stub
				System.out.println("abierto");
			}

			@Override
			public void onPingReceived(byte[] arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onPongReceived() {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onTextReceived(String arg0) {
				// TODO Auto-generated method stub
				
			}
			
		};
		
		webSocketClient.connect();
	}
	
	
	public static void main(String[] args)
	{
		System.out.println("iniciando");
		new Ejemplo();
	}
}
