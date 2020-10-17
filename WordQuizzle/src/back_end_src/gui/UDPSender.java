package back_end_src.gui;

import java.io.IOException;
import java.net.*;

/**
 * {@link UDPSender} ha il compito di inviare il messaggio '<{@link #message}> <{@link #id}>'
 * al client di indirizzo {@link #addressInfo}.
 */
public class UDPSender implements Runnable {

    private final String[] addressInfo;
    private final String id;
    private final String message;

    public UDPSender(String[] addressInfo, String id, String message) {
        this.addressInfo = addressInfo;
        this.id = id;
        this.message = message;
    }

    @Override
    public void run() {
        DatagramSocket UDPSocket;
        try {
            UDPSocket = new DatagramSocket();
            InetAddress clientAddress = InetAddress.getByName(this.addressInfo[0]);
            int clientPort = Integer.parseInt(this.addressInfo[1]);
            String message = this.message + " " + id;
            byte[] messageBytes = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, clientAddress, clientPort);
            UDPSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
