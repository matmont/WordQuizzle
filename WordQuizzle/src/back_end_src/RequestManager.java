package back_end_src;

import back_end_src.gui.UDPSender;
import common_src.CommonUtilities;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * {@link RequestManager} ha il compito inoltrare la richiesta di un utente al destinatario designato.
 * In caso di accettazione, farà il setup della sfida, recuperando le traduzioni e lanciando poi {@link MatchManager}.
 */
public class RequestManager implements Runnable {

    //Il flag condiviso con APIFetcher per la notifica di errori durante le richieste HTTP.
    static volatile boolean API_ERROR = false;

    private final String idA;
    private final SocketChannel TCPSocketA;
    private final String idB;
    private final SocketChannel TCPSocketB;
    private final String[] addressInfo;
    private DatagramSocket serverUDPSocket;

    public RequestManager(String idA, SocketChannel TCPSocketA, String idB, SocketChannel TCPSocketB) {
        this.idA = idA;
        this.TCPSocketA = TCPSocketA;
        this.idB = idB;
        this.TCPSocketB = TCPSocketB;
        this.addressInfo = ServerUtilities.tokenizeAddress(this.TCPSocketB);
    }

    /**
     * Costruisce l'url HTTP per richiedere la traduzione di una certa parola.
     *
     * @param index Indice della parola di cui si vuole la traduzione.
     * @param italianWords Array di parole italiane.
     * @return L'URL pronto per essere 'fetchato'.
     */
    private String buildUrlFromIndex(int index, String[] italianWords) {
        return "https://api.mymemory.translated.net/get?q=" + italianWords[index] + "&langpair=it|en";
    }

    /**
     * Stampa un semplice messaggio di notifica, nel caso in cui un utente chiuda il client
     * durante l'esecuzione di {@link RequestManager}.
     *
     * @param id L'utente che ha abbandonato.
     */
    private void quitError(String id) {
        System.out.println("MATCH [" + this.idA + "|" + this.idB + "] --> il giocatore " + id + " non è raggiungibile.");
    }

    /**
     * Stampa un semplice messaggio di notifica, nel caso in cui ci sia stato un errore durante
     * la comunicazione HTTP con il servizio API.
     */
    private void apiError() {
        System.out.println("MATCH [" + this.idA + "|" + this.idB + "] --> il servizio di traduzione non è al momento disponibile");
    }

    @Override
    public void run() {
        try {

            //Inoltro della richiesta di sfida.
            this.serverUDPSocket = new DatagramSocket();
            InetAddress clientAddress = InetAddress.getByName(this.addressInfo[0]);
            int clientPort = Integer.parseInt(this.addressInfo[1]);
            String message = "add " + this.idA;
            byte[] messageBytes = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, clientAddress, clientPort);
            this.serverUDPSocket.send(sendPacket);

            /*
                Si mette in attesa, con timeout, della richiesta di accettazione.
             */

            byte[] responseBytes = new byte["accepted".getBytes().length];
            DatagramPacket receivePacket = new DatagramPacket(responseBytes, responseBytes.length);

            this.serverUDPSocket.setSoTimeout(Server.requestTimeExpire * 1000);
            this.serverUDPSocket.receive(receivePacket);

            /*
                Invio dell'ulteriore ACK per confermare la validità della sfida all'utente
                destinatario, il quale ha accettato. Per ulteriori informazioni leggere la relazione o la
                classe Client.
             */
            message = "starting " + this.idA;
            messageBytes = message.getBytes();
            sendPacket = new DatagramPacket(messageBytes, messageBytes.length, clientAddress, clientPort);
            this.serverUDPSocket.send(sendPacket);

            /*
                Recupero delle traduzioni dal Server
             */
            ExecutorService executorService = Executors.newFixedThreadPool(Server.numberOfWords);
            //Scelto 'numberOfWords' parole casuali dal dizionario
            int[] numbers = ServerUtilities.generateRandomNumbers(Server.numberOfWords, 0, Server.dictionary.size());
            String[] italianWords = new String[Server.numberOfWords];
            for (int i = 0; i < Server.numberOfWords; i++) {
                italianWords[i] = Server.dictionary.get(numbers[i]);
            }
            //Lancio i vari APIFetcher
            ArrayList<ArrayList<String>> englishWords = new ArrayList<>();
            for (int i = 0; i < Server.numberOfWords; i++) {
                ArrayList<String> translations = new ArrayList<>();
                englishWords.add(translations);
                executorService.execute(new APIFetcher(buildUrlFromIndex(i, italianWords), translations));
            }

            /* Attendo la terminazioni degli APIFetcher */
            executorService.shutdown();
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (Server.DEBUG_MODE) {
                System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> italian words: " +
                        Arrays.toString(italianWords) + " --- translations: " + englishWords.toString());
            }

            //Controllo il flag per gli errori HTTP.
            if (API_ERROR) {
                if (Server.DEBUG_MODE) apiError();
                try {
                    CommonUtilities.writeIntoSocket("Siamo spiacenti, il servizio di traduzione non è al " +
                            "momento disponibile. Riprovare più tardi.", this.TCPSocketA);
                } catch (IOException e) {
                    if (Server.DEBUG_MODE) quitError(this.idA);
                }
                try {
                    CommonUtilities.writeIntoSocket("Siamo spiacenti, il servizio di traduzione non è al " +
                            "momento disponibile. Riprovare più tardi.", this.TCPSocketB);
                } catch (IOException e) {
                    if (Server.DEBUG_MODE) quitError(this.idB);
                }

                //Se il servizio API non è disponibile, non si potrà giocare!
                return;
            }

            //----------------------------------------------------------------------------------------------------------

            /*
                Esecuzione del MatchManager, si gioca!
             */
            executorService = Executors.newSingleThreadExecutor();
            MatchManager matchManager = new MatchManager(this.idA, this.idB, this.TCPSocketA, this.TCPSocketB,
                    italianWords, englishWords);
            /*
                Il modo in cui è stato implementato il timeout della sfida.
             */
            Future<?> futurePoints = executorService.submit(matchManager);
            try {
                futurePoints.get(Server.matchDuration * 1000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (Server.DEBUG_MODE)
                    System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> tempo della sfida scaduto.");
                matchManager.timeout();
                if (Server.GUI_MODE) {

                    UDPSender sender1 = new UDPSender(ServerUtilities.tokenizeAddress(this.TCPSocketA), this.idA, "timeout");
                    UDPSender sender2 = new UDPSender(ServerUtilities.tokenizeAddress(this.TCPSocketB), this.idB, "timeout");
                    ExecutorService  sendersService = Executors.newFixedThreadPool(2);
                    sendersService.execute(sender1);
                    sendersService.execute(sender2);
                    sendersService.shutdown();
                }
            }
            executorService.shutdown();


        } catch (SocketTimeoutException e) {
            /*
                Se l'utente destinatario della richiesta non accetta la sfida, scadrà il timeout
                della socket UDP, e ci troveremo in questo pezzo di codice. Notifichiamo l'utente mittente
                che la sfida non è stata accettata ed indichiamo all'UDPReceiver del destinatario l'avvento
                del timeout.
             */
            if (Server.DEBUG_MODE)
                System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> tempo della richiesta scaduto.");
            String message = "remove " + this.idA;
            byte[] messageBytes = message.getBytes();
            try {
                DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, InetAddress.getByName(this.addressInfo[0]), Integer.parseInt(this.addressInfo[1]));
                serverUDPSocket.send(sendPacket);
                CommonUtilities.writeIntoSocket("La richiesta non è stata accettata.", this.TCPSocketA);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "RequestManager{" +
                "idA='" + idA + '\'' +
                ", idB='" + idB + '\'' +
                '}';
    }
}
