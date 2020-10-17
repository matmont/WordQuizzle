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
import java.util.concurrent.TimeUnit;

/**
 * {@link TestMatches} simula {@link #NUMBER_OF_MATCHES} sfide. Lancerà quindi i client necessari e li farà
 * sfidare. Ogni client invierà sempre una medesima futile parola: tutte le sfide termineranno in pareggio e quindi
 * non ha molto senso. È però utile per testare il corretto funzionamento del Server, indipendentemente dalle prestazioni
 * degli utenti che giocano.
 */
public class TestMatches {

    //Settare qui il numero di match che si vogliono simulare e la porta TCP sul quale il server è in ascolto.
    private static final int NUMBER_OF_MATCHES = 300;
    private static final int tcpPort = 8000;

    //Alcuni array utilizzati per implementare meccanismi di sincronizzazione tra il thread che richiede la sfida
    //e quello che la accetta. Sono necessari perchè potrebbero crearsi inconsistenze causate dal fatto che i vari
    //thread possono essere schedulati e deschedulati in maniera casuale.
    private static final boolean[] opponentLogged = new boolean[NUMBER_OF_MATCHES];
    private static final boolean[] friendshipAdded = new boolean[NUMBER_OF_MATCHES];
    private static final boolean[] requestsSended = new boolean[NUMBER_OF_MATCHES];

    public static void main(String[] args) {

        for (int i = 0; i < NUMBER_OF_MATCHES; i++) {
            opponentLogged[i] = false;
        }

        ExecutorService pool = Executors.newCachedThreadPool();
        for (int i = 0; i < NUMBER_OF_MATCHES; i = i + 2) {
            pool.execute(new Requester(i));
            try {
                Thread.sleep(500);
                pool.execute(new Accepter(i));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@link Requester} simula il comportamento di un client che richiede una sfida.
     */
    static class Requester implements Runnable {

        private final int counter;
        private final int idNumber;

        public Requester(int counter) {
            this.counter = counter;
            this.idNumber = this.counter;
        }

        @Override
        public void run() {
            SocketChannel TCPSocket;
            DatagramSocket UDPSocket;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            UDPReceiver receiver;
            HashMap<String, String[]> requests = new HashMap<>();
            String TCPPort;
            try {
                //Apro la socket TCP verso il server
                TCPSocket = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort));

                //Recupero da essa i dati, così da aprire con stesso indirizzo e porta la socket UDP in ricezione
                TCPPort = TCPSocket.getLocalAddress().toString().split(":")[1];
                System.out.println("[ id" + this.idNumber + " ] -> tcp port -> " + TCPPort);
                UDPSocket = new DatagramSocket(Integer.parseInt(TCPPort));
                receiver = new UDPReceiver(UDPSocket, requests);
                executorService.execute(receiver);

                /* LOGIN OF CLIENT */
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (requests) {
                    requests.clear();
                }
                CommonUtilities.writeIntoSocket("login id" + this.idNumber + " psw" + this.idNumber, TCPSocket);
                String response = CommonUtilities.readFromSocket(TCPSocket);
                if (!response.equals("Login effettuato con successo.")) return;

                System.out.println("[ id" + this.idNumber + " ] -> mi sono loggato!.");

                boolean goForward = false;
                //Primo meccanismo di sincronizzazione, dobbiamo aspettare che anche il client da sfidare
                //sia online (oltre a noi stessi, chiaramente)
                while (!goForward) {
                    synchronized (opponentLogged) {
                        if (opponentLogged[this.counter]) {
                            goForward = true;
                        }
                    }
                    Thread.sleep(500);
                }

                //Se arriva qua allora il suo avversario designato si è loggato!

                System.out.println("[ id" + this.idNumber + " ] -> il mio avversario si è loggato.");

                //Aggiunta dell'amicizia
                CommonUtilities.writeIntoSocket("aggiungi_amico id" + (this.idNumber + 1), TCPSocket);
                response = CommonUtilities.readFromSocket(TCPSocket);
                if (response.contains("L'amicizia è stata aggiunta con successo.") ||
                        response.equals("Hai già un'amicizia con l'utente indicato.")) {
                    //Altro meccanismo di sincronizzazione, attendiamo che l'amicizia venga stretta con successo.
                    synchronized (friendshipAdded) {
                        friendshipAdded[this.counter] = true;
                    }
                } else {
                    TCPSocket.close();
                    receiver.stopRun();
                    UDPSocket.close();
                    executorService.shutdown();
                    return;
                }

                System.out.println("[ id" + this.idNumber + " ] -> amicizia aggiunta con successo!");

                //Invio della richiesta di sfida
                CommonUtilities.writeIntoSocket("sfida id" + (this.idNumber + 1), TCPSocket);
                response = CommonUtilities.readFromSocket(TCPSocket);

                //Altro meccanismo di sincronizzazione. L'altro client aspetterà che il flag sarà true prima
                //di accettare la sfida, ovvero aspetterà che realmente la richiesta di sfida è stata fatta.
                if (response.contains("inviata")) {
                    synchronized (requestsSended) {
                        requestsSended[this.counter] = true;
                    }
                }

                response = CommonUtilities.readFromSocket(TCPSocket);

                if (response.equals("La richiesta non è stata accettata.") ||
                        response.equals("Siamo spiacenti, il servizio di traduzione non è al momento " +
                                "disponibile. Riprovare più tardi.")) {
                    TCPSocket.close();
                    receiver.stopRun();
                    UDPSocket.close();
                    executorService.shutdown();
                    System.out.println("[ id" + this.idNumber + " ] -> match finito, tutto ok!");
                    return;
                }

                System.out.println("[ id" + this.idNumber + " ] -> inizia il match");

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //Logica del match. La stessa della classe 'Client'
                while (true) {
                    response = CommonUtilities.readFromSocket(TCPSocket);
                    System.out.println("[ id" + this.idNumber + " ] -> " + response);
                    if (response.contains("Attendi...")) {
                        break;
                    }
                    if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è " +
                            "conteggiata.")) {
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println("[ id" + this.idNumber + " ] -> " + response);
                        break;
                    }
                    String answer = "ciao!";
                    CommonUtilities.writeIntoSocket(answer, TCPSocket);
                }
                response = CommonUtilities.readFromSocket(TCPSocket);
                System.out.println("[ id" + this.idNumber + " ] -> " + response);

                Thread.sleep(100);

                System.out.println("[ id" + this.idNumber + " ] -> match finito, tutto ok!");


                Thread.sleep(2000);

                TCPSocket.close();

                receiver.stopRun();
                UDPSocket.close();
                executorService.shutdown();


            } catch (IOException | InterruptedException e) {
                System.out.println("[ id" + this.idNumber + " ] -> IO EXCEPTION!!!");
                e.printStackTrace();
            }
        }
    }

    /**
     * {@link Accepter} simula il comportamento di un client che accetta la sfida.
     */
    static class Accepter implements Runnable {

        private final int counter;
        private final int idNumber;

        public Accepter(int counter) {
            this.counter = counter;
            this.idNumber = this.counter + 1;
        }

        @Override
        public void run() {
            SocketChannel TCPSocket;
            DatagramSocket UDPSocket;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            UDPReceiver receiver;
            HashMap<String, String[]> requests = new HashMap<>();

            try {
                //Apro la socket TCP verso il server
                TCPSocket = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort));

                //Recupero da essa i dati, così da aprire con stesso indirizzo e porta la socket UDP in ricezione
                String TCPPort = TCPSocket.getLocalAddress().toString().split(":")[1];
                System.out.println("[ id" + this.idNumber + " ] -> tcp port -> " + TCPPort);
                UDPSocket = new DatagramSocket(Integer.parseInt(TCPPort));
                receiver = new UDPReceiver(UDPSocket, requests);
                executorService.execute(receiver);

                /* LOGIN OF CLIENT */
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (requests) {
                    requests.clear();
                }
                CommonUtilities.writeIntoSocket("login id" + this.idNumber + " psw" + this.idNumber, TCPSocket);
                String response = CommonUtilities.readFromSocket(TCPSocket);
                if (!response.equals("Login effettuato con successo.")) {
                    TCPSocket.close();
                    receiver.stopRun();
                    UDPSocket.close();
                    executorService.shutdown();
                    return;
                }

                //Setto a true il flag, per indicare che ho fatto il login, così che l'altro sappia di poter
                //inviarmi la richiesta di sfida.
                synchronized (opponentLogged) {
                    opponentLogged[this.counter] = true;
                }

                System.out.println("[ id" + this.idNumber + " ] -> mi sono loggato!.");

                //Attesa dell'amicizia da parte del requester
                boolean goForward = false;

                while (!goForward) {
                    synchronized (friendshipAdded) {
                        if (friendshipAdded[this.counter]) {
                            goForward = true;
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("[ id" + this.idNumber + " ] -> amicizia aggiunta con successo!");

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //Attendo che l'altro richieda la sfida.
                goForward = false;
                //noinspection IdempotentLoopBody
                while (!goForward) {
                    synchronized (requestsSended) {
                        if (requestsSended[this.counter]) goForward = true;
                    }
                }

                //Accetta la sfida
                receiver.setChallengeAvailable(false);
                String[] addressInfo;
                String idToAccept = "id" + (this.idNumber - 1);
                System.out.println("[ id" + this.idNumber + " ] -> cerco sfide da parte di " + idToAccept);

                //Logica dell'accettazione della sfida. Copiata da 'Client'.
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (requests) {
                    addressInfo = requests.get(idToAccept);

                    if (addressInfo == null) {
                        System.out.println("[ id" + this.idNumber + " ] -> L'id inserito non è stato trovato/La richiesta " +
                                "non è più valida.");
                        //return + togli tutto
                        TCPSocket.close();
                        receiver.stopRun();
                        UDPSocket.close();
                        executorService.shutdown();
                        return;
                    }

                    //Bisogna rimuovere la sfida accettata dalla struttura dati.
                    requests.remove(idToAccept);
                }

                try {
                    InetAddress serverAddress = InetAddress.getByName(addressInfo[0]);
                    byte[] messageBytes = "accepted".getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length,
                            serverAddress, Integer.parseInt(addressInfo[1]));
                    UDPSocket.send(sendPacket);

                            /*
                                Dobbiamo purtroppo pagare pegno di "dormire" per 500 ms. Questo perchè dobbiamo dare
                                il tempo all ACK di arrivare all'UDPReceiver. Si noti che è fondamentale l'assunzione
                                fatta che l'UDP non dia poi troppi problemi. (Assunzione che in un progetto non
                                accademico non conviene fare; l'UDP avrebbe infatti bisogno di un continuo controllo
                                esplicito, manuale).
                            */
                    Thread.sleep(500);
                    //Se non è arrivato l'ACK, la richiesta è scaduta.
                    if (!receiver.isChallengeAvailable()) {
                        System.out.println("[ id" + this.idNumber + " ] -> Richiesta scaduta.");
                    }

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                response = CommonUtilities.readFromSocket(TCPSocket);
                if (response.equals("Siamo spiacenti, il servizio di traduzione non è al momento " +
                                "disponibile. Riprovare più tardi.")) {
                    TCPSocket.close();
                    receiver.stopRun();
                    UDPSocket.close();
                    executorService.shutdown();
                    System.out.println("[ id" + this.idNumber + " ] -> match finito, tutto ok!");
                    return;
                }

                System.out.println("[ id" + this.idNumber + " ] -> inizia il match");

                //Logica di gioco. Copiata da 'Client'.
                while (true) {
                    response = CommonUtilities.readFromSocket(TCPSocket);
                    System.out.println("[ id" + this.idNumber + " ] -> " + response);
                    if (response.contains("Attendi...")) {
                        break;
                    }
                    if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è " +
                            "conteggiata.")) {
                        CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println("[ id" + this.idNumber + " ] -> " + response);
                        break;
                    }
                    String answer = "ciao!";
                    CommonUtilities.writeIntoSocket(answer, TCPSocket);
                }
                response = CommonUtilities.readFromSocket(TCPSocket);
                System.out.println("[ id" + this.idNumber + " ] -> " + response);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("[ id" + this.idNumber + " ] -> match finito, tutto ok!");

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                TCPSocket.close();

                receiver.stopRun();
                UDPSocket.close();
                executorService.shutdown();

            } catch (IOException e) {
                System.out.println("[ id" + this.idNumber + " ] -> IO EXCEPTION!!!");
                e.printStackTrace();
            }
        }
    }

}


