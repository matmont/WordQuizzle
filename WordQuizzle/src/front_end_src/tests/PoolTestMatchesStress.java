/*
  NON UTILIZZABILE! NON UTILIZZARE QUESTA CLASSE. PREFERIBILE UTILIZZARE IL TEST {@link front_end_src.tests.TestMatches}
 */


package front_end_src.tests;

import common_src.CommonUtilities;
import front_end_src.UDPReceiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Un contatore di vittorie. In realtà non è utilizzato in maniera corretta. Lo era
 * precedentemente, quando i match venivano simulati con semplici sleep. Adesso, ogni match
 * terminerà in un pareggio, per tanto è inutile.
 */
class WinCounter {

    public WinCounter() {
    }

    public synchronized void voidincrement() {
    }
}

/**
 * Stress test per il meccanismo di gioco. L'obiettivo è
 * verificare la robustezza del Server in uno scenario in cui
 * ci sono molti utenti che si sfidano tra loro. Questo tipo di
 * test è di particolare importanza, soprattutto per verificare
 * la corretta gestione del multithreading.
 *
 * NON UTILIZZABILE, È PREFERIBILE USARE {@link TestMatches}.
 */

public class PoolTestMatchesStress {

    //Il numero di client con il quale eseguire il test.
    public static int numberOfClients = 1000;
    public static int numberOfWords = 2;

    public static void main(String[] args) throws InterruptedException {
        WinCounter w = new WinCounter();
        ThreadPoolExecutor loginExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        //Il lancio di 'numberOfClients' client.
        for (int i = 0; i < numberOfClients; i++) {
            ClientBehavior l = new ClientBehavior(i, w);
            loginExecutor.execute(l);
            //noinspection BusyWait
            Thread.sleep(100);
        }
        loginExecutor.shutdown();

        //Aspetto che tutti i thread lanciati precedentemente abbiano finito.
        try {
            loginExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Classe che astrae un simulatore di {@link front_end_src.Client} in uno scenario di richiesta o
     * accettazione di sfida (se il contatore passato all'oggetto è pari allora dovrà richiedere una sfida,
     * se dispari dovrà accettarla).
     */
    private static class ClientBehavior implements Runnable {

        private final int c;
        private final WinCounter w;

        public ClientBehavior(int c, WinCounter w) {
            this.c = c;
            this.w = w;
        }

        @Override
        public void run() {
            ExecutorService executorService = null;
            UDPReceiver receiver = null;

            try {
                //Sostanzialmente il codice del client.
                SocketChannel socket;
                socket = SocketChannel.open();
                socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), 8000));
                String TCPPort = socket.getLocalAddress().toString().split(":")[1];
                DatagramSocket UDPSocket = new DatagramSocket(Integer.parseInt(TCPPort));
                HashMap<String, String[]> requests = new HashMap<>();
                receiver = new UDPReceiver(UDPSocket, requests);
                executorService = Executors.newSingleThreadExecutor();
                executorService.execute(receiver);

                CommonUtilities.writeIntoSocket("login id" + this.c + " psw" + this.c, socket);
                String response = CommonUtilities.readFromSocket(socket);
                System.out.println(response);

                //Vediamo se QUESTA precisa istanza della classe dovrà comportarsi come colui che richiede la sfida
                //o come colui che la accetta.
                if (this.c % 2 == 0) {

                    //Colui che richiede la sfida.

                    Thread.sleep(1000);
                    CommonUtilities.writeIntoSocket("aggiungi_amico id" + (this.c + 1), socket);
                    response = CommonUtilities.readFromSocket(socket);
                    System.out.println(response);

                    Thread.sleep(1000);
                    CommonUtilities.writeIntoSocket("sfida id" + (this.c + 1), socket);
                    response = CommonUtilities.readFromSocket(socket);
                    System.out.println(response);

                    //Praticamente il medesimo codice di client.

                    if (response.startsWith("S")) {
                        response = CommonUtilities.readFromSocket(socket);
                        System.out.println(response); //Via alla sfida di traduz
                        if (response.equals("La richiesta non è stata accettata.")) {
                            receiver.stopRun();
                            executorService.shutdown();
                            return;
                        }
                        if (response.equals("Siamo spiacenti, il servizio di traduzione non è al momento disponibile. Riprovare più tardi.")) {
                            receiver.stopRun();
                            executorService.shutdown();
                            return;
                        }
                        for (int i = 0; i < numberOfWords; i++) {
                            response = CommonUtilities.readFromSocket(socket);
                            System.out.println(response);
                            if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è conteggiata.")) {
                                break;
                            }
                            String answer = "Ciao!";
                            CommonUtilities.writeIntoSocket(answer, socket);
                        }
                        response = CommonUtilities.readFromSocket(socket);
                        System.out.println(response);
                        // Messaggio di sfida terminata + Esito
                        response = CommonUtilities.readFromSocket(socket);
                        System.out.println(response);
                        if (response.contains("Congratulazioni!")) {
                            this.w.voidincrement();
                        }
                    } else {
                        System.out.println(response);

                    }
                } else {

                    //Colui che accetta la richiesta

                    Thread.sleep(numberOfClients * 10);
                    String[] addressInfo;
                    String idToAccept = "id" + (this.c - 1);
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (requests) {
                        addressInfo = requests.get(idToAccept);
                        if (addressInfo == null) {
                            System.out.println("L'id inserito non è stato trovato/La richiesta non è più valida.");
                            receiver.stopRun();
                            executorService.shutdown();
                            return;
                        }
                        requests.remove(idToAccept);
                    }
                    try {
                        InetAddress serverAddress = InetAddress.getByName(addressInfo[0]);
                        byte[] messageBytes = "accepted".getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, serverAddress, Integer.parseInt(addressInfo[1]));
                        UDPSocket.send(sendPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    response = CommonUtilities.readFromSocket(socket);
                    System.out.println(response);
                    if (response.equals("La richiesta non è stata accettata.")) {
                        receiver.stopRun();
                        executorService.shutdown();
                        return;
                    }
                    if (response.equals("Siamo spiacenti, il servizio di traduzione non è al " +
                            "momento disponibile. Riprovare più tardi.")) {
                        receiver.stopRun();
                        executorService.shutdown();
                        return;
                    }
                    for (int i = 0; i < numberOfWords; i++) {
                        response = CommonUtilities.readFromSocket(socket);
                        System.out.println(response);
                        if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è conteggiata.")) {
                            break;
                        }
                        String answer = "Ciao!";
                        CommonUtilities.writeIntoSocket(answer, socket);
                    }
                    response = CommonUtilities.readFromSocket(socket);
                    System.out.println(response);
                    // Messaggio di sfida terminata + Esito
                    response = CommonUtilities.readFromSocket(socket);
                    System.out.println(response);
                    if (response.contains("Congratulazioni!")) {
                        this.w.voidincrement();
                    }
                }

                receiver.stopRun();
                executorService.shutdown();

                System.out.println("Terminated: " + this.c);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                assert receiver != null;
                receiver.stopRun();
                executorService.shutdown();

            }
        }
    }

}
