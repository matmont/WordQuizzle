package front_end_src;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * {@link UDPReceiver} ha un unico compito, ben preciso (single-responsibility principle, SRP):
 * ricevere eventuali messaggi UDP provenienti dal server.
 * Il progetto richiede infatti che, una volta ricevuta una richiesta di sfida da parte di un utente,
 * il server la inoltri al destinatario tramite il protocollo di rete UDP.
 */
public class UDPReceiver implements Runnable {

    /**
     * La struttura dati verrà condivisa con il {@link Client}. All'interno
     * di essa saranno immagazzinate le varie richieste di sfida dell'utente loggato.
     * Ho deciso di utilizzare una Map così da poter immagazzinare sia il nome dell'utente
     * che sta richiedendo la sfida, sia il suo indirizzo UDP: dovrò, eventualmente, rispondere
     * alla richiesta!
     */
    private final HashMap<String, String[]> requests;

    /**
     * Si noti l'utilizzo del modificatore 'volatile': è necessario poichè
     * il booleano è letto da questo thread e scritto da un altro (si setta a 'false' al momento
     * della chiusura del {@link Client}).
     */
    private volatile boolean run = true;

    private final DatagramSocket UDPSocket;

    /**
     * Flag utilizzato per implementare un controllo aggiuntivo sulla validità
     * di una sfida: supponiamo che io riceva una richiesta di sfida. Rispondo a questa. Siamo in rete, quindi
     * nulla vieta che ci sia una qualche congestione che rallenta la risposta. Lato server scade
     * il timeout della richiesta prima che la mia risposta arrivi (ma io, client, sono convinto,
     * avendo accettato la richiesta prima che il server mi notificasse il timeout, che la partita si giocherà):
     * che si fa? Il server, dopo aver ricevuto una risposta PRIMA dello scadere del timeout invierà
     * un ACK al client, per indicare appunto che tutto è andato bene, tutto torna, si gioca! In un caso
     * come il precedente, il client, dopo aver inviato il messaggio di accettazione della sfida, non riceverà
     * l'ACK e capirà che è incappato in quel particolarissimo scenario (forse ai limiti dell'impossibile,
     * ma ho trovato interessante provare ad implementare un meccanismo che ricordasse un po' l'handshake a tre vie).
     * In ogni caso, vedremo dopo come sarà realmente implementato nella classe {@link Client}.
     * Si noti l'utilizzo del modificatore 'volatile': è difatti necessario poichè il flag è scritto in questo
     * thread e letto nel thread del {@link Client}.
     */
    private volatile boolean challengeAvailable = false;

    public UDPReceiver(DatagramSocket UDPSocket, HashMap<String, String[]> requests) {
        this.UDPSocket = UDPSocket;
        this.requests = requests;
    }

    /**
     * Serve per terminare il ciclo while principale.
     */
    public void stopRun() {
        this.run = false;
    }

    /**
     * Metodo di gestione del flag {@link #challengeAvailable}. Si legga il commento
     * del flag, si guardi la classe {@link Client} per vederne l'utilizzo.
     *
     * @param available Booleano da assegnare a {@link #challengeAvailable}
     */
    public void setChallengeAvailable(boolean available) {
        challengeAvailable = available;
    }

    /**
     * Metodo di gestione del flag {@link #challengeAvailable}. Si legga il commento
     * del flag, si guardi la classe {@link Client} per vederne l'utilizzo.
     *
     * @return Il valore del flag {@link #challengeAvailable}
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isChallengeAvailable() {
        return challengeAvailable;
    }

    @Override
    public void run() {
        try {

            byte[] receiveBuffer;
            DatagramPacket receivePacket;
            while (this.run) {

                //Mi metto in ascolto di nuovi messaggi UDP
                receiveBuffer = new byte[128];
                receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                UDPSocket.receive(receivePacket);

                /*
                    Salvo l'indirizzo e la porta del mittente, recuperandola dal pacchetto UDP appena ricevuto.
                    Questo mi permette di avere i dati necessari per inviare la risposta al RequestManager corrispondente.
                    Ho infatti deciso di aprire, per ogni RequestManager (sarà lui ad inoltrare tramite UDP la richiesta
                    al client corrispondente; ne verrà lanciato uno per ogni richiesta di sfida), una socket UDP
                    dedicata a quell'unica richiesta, in modo da non avere interferenze di nessun tipo.
                */
                InetAddress serverAddress = receivePacket.getAddress();
                int serverPort = receivePacket.getPort();
                String receivedString = new String(receivePacket.getData()).trim();

                StringTokenizer stringTokenizer = new StringTokenizer(receivedString);
                String type = stringTokenizer.nextToken();
                String id = stringTokenizer.nextToken();

                /*
                    Posso ricevere tre tipi di messaggi:
                        - add -> ho ricevuto una nuova richiesta, 'synchronized' sulla struttura
                                 dati e si inserisce la nuova richiesta.
                        - remove -> il server, o meglio il RequestManager, mi ha notificato dello scadere
                                    del timeout della richiesta, 'synchronized' sulla struttura dati e
                                    la si rimuove
                        - starting -> rappresenta per UDPReceiver l'ACK di cui abbiamo parlato sopra. Ricevere un
                                      messaggio di questo tipo implica impostare a true il flag che il client
                                      controllerà per verificare la validità della sfida (si noti che se un client si
                                      trova ad accettare una richiesta, quella è l'unica sul quale è focalizzato,
                                      per questo basta questo unico flag; in ogni caso si veda la classe Client per
                                      leggere il codice inerente).
                 */
                switch (type) {
                    case "add":
                        synchronized (this.requests) {
                            String[] addressInfo = new String[2];
                            addressInfo[0] = serverAddress.toString().substring(1);
                            addressInfo[1] = String.valueOf(serverPort);
                            requests.put(id, addressInfo);
                        }
                        break;
                    case "remove":
                        synchronized (this.requests) {
                            requests.remove(id);
                        }
                        break;
                    case "starting":
                        this.challengeAvailable = true;
                        break;
                }
            }

        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
