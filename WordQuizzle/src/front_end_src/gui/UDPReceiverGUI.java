package front_end_src.gui;

import common_src.CommonUtilities;
import front_end_src.Client;
import front_end_src.UDPReceiver;
import front_end_src.gui.custom_components.InputField;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Dalla classe {@link UDPReceiver}:<br />
 * {@link UDPReceiver} ha un unico compito, ben preciso (single-responsibility principle, SRP):
 * ricevere eventuali messaggi UDP provenienti dal server.
 * Il progetto richiede infatti che, una volta ricevuta una richiesta di sfida da parte di un utente,
 * il server la inoltri al destinatario tramite il protocollo di rete UDP. <br /> <br />
 * <p>
 * Questa nuova versione, è stata creata appositamente per lo scenario in cui si utilizzi un
 * client con interfaccia grafica. Ciò è necessario perchè il server invierà, oltre alla sola richiesta
 * di sfida, ulteriori messaggi UDP al client.
 */
public class UDPReceiverGUI implements Runnable {

    private final HashMap<String, String[]> requests;
    private volatile boolean run = true;
    private final DatagramSocket UDPSocket;

    //List Model che verranno aggiornati con nuove entry, in modo tale che anche l'interfaccia grafica
    //si aggiorni in base agli elementi di queste. Basterà ad esempio aggiungere un elemento a 'challengesListModel'
    //per far sì che il JList a cui esso è associato si aggiorni immediatamente, in tempo reale.
    private final DefaultListModel<String> challengesListModel;
    private final DefaultListModel<String> friendsListModel;

    //Nel caso in cui arrivi il messaggio di timeout match, è necessario modificare la UI della pagina della sfida.
    private final JPanel gamePanel;
    private final InputField wordInput;
    private final JButton sendButton;

    private volatile boolean challengeAvailable = false;

    public UDPReceiverGUI(DatagramSocket UDPSocket, HashMap<String, String[]> requests,
                          DefaultListModel<String> challengesListModel, DefaultListModel<String> friendsListModel,
                          JPanel gamePanel, InputField wordInput, JButton sendButton) {
        this.UDPSocket = UDPSocket;
        this.requests = requests;
        this.challengesListModel = challengesListModel;
        this.friendsListModel = friendsListModel;

        this.gamePanel = gamePanel;
        this.wordInput = wordInput;
        this.sendButton = sendButton;
    }

    /**
     * Serve per terminare il ciclo while principale.
     */
    public void stopRun() {
        this.run = false;
        this.UDPSocket.close();
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
                    Posso ricevere cinque tipi di messaggi:
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
                        - newfriend -> nel caso in cui un altro utente abbia richiesto l'amicizia dell'utente collegato
                                       sul client corrispondente a questo UDPReceiverGUI. Ciò permette l'aggiornamento
                                       in tempo reale della JList contenente gli amici dell'utente.
                        - timeout -> nel caso in cui scada il timeout della sfida che l'utente sta giocando. Ciò permette
                                     l'aggiornamento in tempo reale della UI della game page, così che l'utente venga
                                     immediatamente notificato dello scadere del tempo disponibile per la sfida.
                 */
                switch (type) {
                    case "add":
                        synchronized (this.requests) {
                            String[] addressInfo = new String[2];
                            addressInfo[0] = serverAddress.toString().substring(1);
                            addressInfo[1] = String.valueOf(serverPort);
                            requests.put(id, addressInfo);
                            //Aggiornamento del model, il quale implica l'aggiornamento della JList contenente
                            //le varie richieste attive per l'utente collegato al client cui riferisce questo UDPReceiverGUI.
                            synchronized (challengesListModel) {
                                challengesListModel.clear();
                                for (String s : requests.keySet()) {
                                    challengesListModel.addElement(s);
                                }
                            }
                        }
                        break;
                    case "remove":
                        synchronized (this.requests) {
                            requests.remove(id);
                            //Aggiornamento del model, il quale implica l'aggiornamento della JList contenente
                            //le varie richieste attive per l'utente collegato al client cui riferisce questo UDPReceiverGUI.
                            synchronized (challengesListModel) {
                                challengesListModel.clear();
                                for (String s : requests.keySet()) {
                                    challengesListModel.addElement(s);
                                }
                            }
                        }
                        break;
                    case "starting":
                        this.challengeAvailable = true;
                        break;
                    case "newfriend":
                        //Aggiornamento del model, il quale implica l'aggiornamento della JList contenente
                        //le amicizie dell'utente collegato al client cui riferisce questo UDPReceiverGUI.
                        synchronized (friendsListModel) {
                            friendsListModel.addElement(id);
                        }
                        break;
                    case "timeout":
                        //Se ha già finito non deve però inviarlo!
                        if (!ClientGUI.FINISHED_QUESTIONS) CommonUtilities.writeIntoSocket("", ClientGUI.TCPSocket);
                        ClientGUI.TIMEOUT = true;
                        if (wordInput != null) {
                            wordInput.setVisible(false);
                            gamePanel.remove(wordInput);
                        }
                        if (sendButton != null) {
                            sendButton.setVisible(false);
                            gamePanel.remove(sendButton);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}