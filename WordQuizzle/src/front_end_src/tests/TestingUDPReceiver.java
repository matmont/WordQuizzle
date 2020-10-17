/* NON UTILIZZABILE */

package front_end_src.tests;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TestingUDPReceiver} è stato utilizzato per testare il funzionamento di {@link front_end_src.UDPReceiver}.
 * Vengono semplicemente inviati dei messaggi di ogni tipo possibile (add, remove, starting, timeout) all'{@link front_end_src.UDPReceiver}
 * di un certo client, per capire se questo riesca o meno a leggerli tutti in maniera corretta. Proprio per questo
 * sono stati utilizzati dei contatori: grazie ad essi è stato possibile verificare che nessun messaggio viene perso
 * "per strada" da {@link front_end_src.UDPReceiver}
 *
 * Tuttavia per usare questo programma è necessario avere l'IP e la PORTA della {@link DatagramSocket} aperta dal
 * {@link front_end_src.Client}. Difatti quando lo utilizzavo facevo stampare al client queste informazioni, ma a cose
 * normali non le stampa! (Adesso per esempio non le stampa). Per questo, il test è NON UTILIZZABILE.
 */
@SuppressWarnings("unused")
public class TestingUDPReceiver {
    //Insert here the IP of your client.
    private final static String CLIENT_IP = "Insert here the IP of your client";
    //Insert here the PORT of your client (of UDP socket).
    private final static int CLIENT_PORT = -1;

    /**
     * La classe {@link Counter} ci aiuta a gestire tutti i contatori dei vari tipi di messaggi
     * inviati dai vari {@link SenderUDP} all'{@link front_end_src.UDPReceiver} che si sta testando.
     */
    private static class Counter {
        private int counter = 0;
        private int addNumber = 0;
        private int removeNumber = 0;
        private int startingNumber = 0;
        private int timeoutNumber = 0;

        public synchronized void incrementCounter() {
            counter++;
        }

        public synchronized void incrementAdd() {
            addNumber++;
        }

        public synchronized void incrementRemove() {
            removeNumber++;
        }

        public synchronized void incrementStart() {
            startingNumber++;
        }

        public synchronized void incrementTimeout() {
            timeoutNumber++;
        }

        public synchronized int getAddNumber() {
            return addNumber;
        }

        public synchronized int getCounter() {
            return counter;
        }

        public synchronized int getRemoveNumber() {
            return removeNumber;
        }

        public synchronized int getStartingNumber() {
            return startingNumber;
        }

        public synchronized int getTimeoutNumber() {
            return timeoutNumber;
        }
    }

    //Contatori che tengono traccia dei vari messaggi inviati.
    private static final AtomicInteger addNumber = new AtomicInteger(0);
    private static final AtomicInteger removeNumber = new AtomicInteger(0);
    private static final AtomicInteger startingNumber = new AtomicInteger(0);
    private static final AtomicInteger timeoutNumber = new AtomicInteger(0);

    //Oggetto COUNTER
    private static final Counter struct = new Counter();

    public static void main(String[] args) {
        //Lancio di 100 thread 'SenderUDP'
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < 100; i++) {
            executorService.execute(new SenderUDP(i));
        }
        executorService.shutdown();

        //Attendo la terminazione di tutti i thread.
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Stampo i contatori.
        System.out.println("Stats --> add: " + struct.getAddNumber() + " --- remove: " + struct.getRemoveNumber() +
                " --- start: " + struct.getStartingNumber() + " --- timeout: " + struct.getTimeoutNumber());

    }

    /**
     * {@link SenderUDP} ha il compito di inviare un messaggio UDP sulla {@link DatagramSocket} relativa all'{@link
     * front_end_src.UDPReceiver} che si vuole testare.
     */
    private static class SenderUDP implements Runnable {

        private final int counter;

        public SenderUDP(int counter) {
            this.counter = counter;
        }

        @Override
        public void run() {
            try {
                InetAddress clientAddress = InetAddress.getByName(CLIENT_IP);
                DatagramSocket UDPSocket = new DatagramSocket();
                String message;
                //In base all'indice corrispondente all'iterazione al quale è stato lanciato il thread
                //decide quale messaggio mandare.
                if (counter % 2 == 0) {
                    message = "add id" + counter;
                    struct.incrementAdd();
                } else if (counter % 3 == 0) {
                    message = "remove id" + counter;
                    struct.incrementRemove();
                } else if (counter % 5 == 0) {
                    message = "starting id" + counter;
                    struct.incrementStart();
                } else {
                    message = "timeout id" + counter;
                    struct.incrementTimeout();
                }
                struct.incrementCounter();
                byte[] messageBytes = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, clientAddress, CLIENT_PORT);
                UDPSocket.send(sendPacket);
                UDPSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
