package front_end_src;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import common_src.CommonUtilities;
import common_src.UsersRegisterInterface;
import common_src.exceptions.AlreadyRegisteredUserException;
import front_end_src.exceptions.WrongNumberOfArgumentsException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Il client WordQuizzle, versione command line.
 */
public class Client {

    /**
     * Costante contenente il messaggio di errore da visualizzare
     * in caso di tentata operazione senza precedente autenticazione.
     */
    private static final String notLoggedError = "Esegui prima il login.";

    /**
     * Costante contenente il messaggio di errore da visualizzare
     * in caso di tentata autenticazione quando già loggati.
     */
    private static final String alreadyLoggedError = "Sei già loggato.";

    @SuppressWarnings("FieldCanBeLocal")
    private static int tcpPort;
    @SuppressWarnings("FieldCanBeLocal")
    private static int rmiPort;

    /**
     * Rappresenta la lunghezza massima che un comando, inviato dal {@link Client}
     * al {@link back_end_src.Server}, può avere. Mi è stato utile avere questo
     * limite superiore per permettere un'allocazione base da seguire
     * dei {@link java.nio.ByteBuffer} di lettura (lato server).
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static int maxCommandLength;

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void main(String[] args) throws IOException {

        /*
          La struttura dati contenente le sfide per l'utente attualmente
          loggato. È una HashMap in cui le chiavi sono gli username degli
          utenti mentre i valori sono i dati di rete (indirizzo e porta) al
          quale inviare eventualmente la risposta (della socket UDP di RequestManager)
         */
        HashMap<String, String[]> requests = new HashMap<>();

        boolean on = true;

        //Indica se è stato fatto il login o meno.
        boolean loggedIn = false;
        //Indica se il client è stato appena aperto (per differenziare i print)
        boolean firstStart = true;

        Gson gson = new Gson();

        String response;

        /*
            In questo progetto sono presenti alcuni valori, o per meglio dire parametri,che ne regolano e caratterizzano
            il funzionamento.  Tra questi ci sono sicuramente,  in  primo  piano,  i  vari settaggi del  match  (si  pensi
            al  numero  di parole che i client devono tradurre,  il tempo disponibile per il match e altri...).
            Sono però parametri, seppur concettualmente di altro tipo, anche le porte utilizzate  nelle  varie
            comunicazioni  di  rete.   Ho  deciso  quindi  di  utilizzare dei file config.properties,
            all’interno  del  quale è possibile impostare i vari valori di configurazione.
            I file .properties sono, almeno pe rJava, perfetti per questo tipo di utilizzo: storage
            di parametri, valori di configurazione e perchè no anche piccoli dati (rigorosamente di tipo primitivo).
            Inoltre, sono offerte dal linguaggio stesso una serie di API che ne permettono l’interazione in maniera
            molto semplice ed efficace, attraverso la classe Properties.
         */
        Properties properties = new Properties();
        FileChannel fileChannel = FileChannel.open(Paths.get("./src/common_src/config.properties"), StandardOpenOption.READ);
        properties.load(Channels.newInputStream(fileChannel));

        //--//
        tcpPort = Integer.parseInt(properties.getProperty("server_port"));
        rmiPort = Integer.parseInt(properties.getProperty("rmi_port"));
        //--//
        maxCommandLength = Integer.parseInt(properties.getProperty("max_command_len"));

        fileChannel.close();
        properties.clear();

        SocketChannel TCPSocket = null;
        DatagramSocket UDPSocket = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        UDPReceiver receiver = null;

        try {
            //Apro la socket TCP verso il server
            TCPSocket = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort));

            //Recupero da essa i dati, così da aprire con stesso indirizzo e porta la socket UDP in ricezione
            String TCPPort = TCPSocket.getLocalAddress().toString().split(":")[1];
            UDPSocket = new DatagramSocket(Integer.parseInt(TCPPort));
            receiver = new UDPReceiver(UDPSocket, requests);

            //System.out.println(TCPSocket.getLocalAddress());
            executorService.execute(receiver);

            while (on) {

                ClientUtilities.printMenu(firstStart, loggedIn);
                firstStart = false;

                /*
                    Chiedo e gestisco l'input. Chiaramente non permetto alcun input vuoto o maggiore
                    del limite prestabilito. Superati questi due controlli, l'input inserito
                    verrà passato al metodo di utilità (ClientUtilities) 'checkCommandArguments' il quale
                    controllerà la sintassi del comando utilizzato.
                 */
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
                String input = inputReader.readLine();
                while (input.equals("")) {
                    System.out.println("Inserire un comando.");
                    input = inputReader.readLine();
                }
                while (input.length() > maxCommandLength) {
                    System.out.println("Il comando inserito è troppo lungo.");
                    input = inputReader.readLine();
                }
                //Controllo la correttezza dell'input inserito
                ArrayList<String> tokenizedInput;
                try {
                    tokenizedInput = ClientUtilities.checkCommandArguments(input);

                } catch (WrongNumberOfArgumentsException e) {
                    System.out.println("Bad usage, ricontrolla gli argomenti dal menù.");
                    continue;
                }

                //Se il flusso di esecuzione arriva qui, il comando inserito è corretto! Si decide cosa fare in base ad esso.
                switch (tokenizedInput.get(0)) {
                    case "registra_utente":
                        try {
                            /*
                                Abbiamo qui il collegamento, tramite RMI, al server. Il client recupera dal registro
                                un'istanza dello Stub e su essa chiama i metodi dell'oggetto remoto (dichiarati nell'
                                interfaccia 'UsersRegisterInterface' del package 'common_src') i quali verranno
                                recapitati allo Skeleton del Server, che li eseguirà sull'oggetto reale. Grazie alla
                                tecnologia RMI tutta la parte di rete che collega Stub e Skeleton viene nascosta
                                allo sviluppatore, il quale può usufruire del collegamento senza dover
                                uscire dal paradigma della Programmazione a Oggetti.
                             */
                            Registry r = LocateRegistry.getRegistry(InetAddress.getLocalHost().getHostName(), rmiPort);
                            Remote usersRegisterRemote = r.lookup("USERS-REGISTER-SERVER");
                            UsersRegisterInterface usersRegister = (UsersRegisterInterface) usersRegisterRemote;

                            //Si richiede di fare una nuova registrazione
                            usersRegister.registerNewUser(tokenizedInput.get(1), tokenizedInput.get(2));

                            System.out.println("Registrazione avvenuta con successo.\n");

                            //Qualsiasi problema viene coperto da un'eccezione. Si noti l'eccezione custom
                            //'AlreadyRegisteredUserException', lanciata in caso di username già registrato.
                        } catch (RemoteException | NotBoundException e) {
                            e.printStackTrace();
                        } catch (AlreadyRegisteredUserException e) {
                            System.out.println("Il nickname richiesto è già registrato.\n");
                        } catch (NullPointerException | IndexOutOfBoundsException e) {
                            //Quest'eccezione viene lanciata nel caso in cui qualche parametro non sia stato inserito.
                            System.out.println("Assicurati di avere inserito entrambi i campi.\n");
                        }
                        break;
                    case "login":
                        if (loggedIn) {
                            System.out.println(Client.alreadyLoggedError);
                            break;
                        }
                        //Nuovo utente, nuove sfide! Quelle dell'utente vecchio devono sparire.
                        synchronized (requests) {
                            requests.clear();
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        if (response.equals("Login effettuato con successo.")) loggedIn = true;
                        System.out.println(response);
                        break;
                    case "logout":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        //Nuovo utente, nuove sfide! Quelle dell'utente vecchio devono sparire.
                        synchronized (requests) {
                            requests.clear();
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        if (response.equals("Logout effettuato con successo.")) loggedIn = false;
                        System.out.println(response);
                        break;
                    case "aggiungi_amico":
                    case "mostra_punteggio":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println(response);
                        break;
                    case "lista_amici":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        if (response.equals("Non hai nessuna amicizia.")) {
                            System.out.println(response);
                        } else {
                            //La lista amici è inviata tramite stringa Json, bisogna riconvertirla per
                            //stamparla nel modo più appropriato.
                            Type friendsType = new TypeToken<ArrayList<String>>() {
                            }.getType();
                            ArrayList<String> friends = gson.fromJson(response, friendsType);
                            System.out.println("Lista amici: ");
                            for (String friend : friends) {
                                System.out.println(" - " + friend);
                            }
                        }
                        break;
                    case "sfida":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println(response);
                        /*
                            Spiegare tutto questo è a mio avviso inutile. Sono una serie di lettura e controlli
                            della socket che implementano il corretto comportamento del client durante la sfida.
                            Dico questo perchè è basato (deve essere perfettamente analogo) su come il server
                            invia i messaggi, e quindi poco interessante (nonchè estremamente variabile da
                            implementazione a implementazione).
                         */
                        if (response.contains("inviata")) {
                            response = CommonUtilities.readFromSocket(TCPSocket);
                            System.out.println(response);
                            if (response.equals("La richiesta non è stata accettata.")) break;
                            if (response.equals("Siamo spiacenti, il servizio di traduzione non è al momento " +
                                    "disponibile. Riprovare più tardi."))
                                break;
                            while (true) {
                                response = CommonUtilities.readFromSocket(TCPSocket);
                                System.out.println(response);
                                if (response.contains("Attendi...")) break;
                                if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è " +
                                        "conteggiata.")) {
                                    response = CommonUtilities.readFromSocket(TCPSocket);
                                    System.out.println(response);
                                    break;
                                }
                                String answer = inputReader.readLine();
                                while (answer.equals("")) { //non ho usato il do-while perche voglio dare il messaggio di feedback
                                    System.out.println("Inserisci una risposta!");
                                    answer = inputReader.readLine();
                                }
                                while (answer.length() > maxCommandLength) {
                                    System.out.println("La risposta inserita è troppo lunga, reinserire.");
                                    answer = inputReader.readLine();
                                }
                                CommonUtilities.writeIntoSocket(answer, TCPSocket);
                            }
                            response = CommonUtilities.readFromSocket(TCPSocket);
                            System.out.println(response);
                        }
                        break;
                    case "mostra_classifica":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        //La classifica è inviata tramite stringa Json, bisogna riconvertirla per
                        //stamparla nel modo più appropriato.
                        Type rankType = new TypeToken<LinkedHashMap<String, Integer>>() {
                        }.getType();
                        LinkedHashMap<String, Integer> rank = gson.fromJson(response, rankType);
                        System.out.println("Classifica amici: ");
                        for (String user : rank.keySet()) {
                            System.out.println(user + " - " + rank.get(user));
                        }
                        break;
                    case "mostra_sfide":
                        if (!loggedIn) {
                            System.out.println(Client.notLoggedError);
                            break;
                        }
                        //Visualizziamo le richieste, importante l'uso di synchronized
                        synchronized (requests) {
                            if (requests.size() > 0) {
                                System.out.println("Le tue sfide: ");
                                for (String id : requests.keySet()) {
                                    System.out.println(" - " + id);
                                }
                                System.out.println("Inserisci l'id di cui vuoi accettare la richiesta.");
                            } else {
                                System.out.println("Non hai nessuna richiesta di sfida.");
                                break;
                            }
                        }

                        //Chiediamo al client l'id di cui accettare la richiesta di sfida.
                        String idToAccept = inputReader.readLine();
                        /*
                            Qui è implementato il meccanismo di ulteriore controllo di cui
                            si parla nei commenti della classe UDPReceiver.
                         */

                        //Anzitutto setto a false il flag, se diventa true bene, se rimane false, sfida non valida.
                        receiver.setChallengeAvailable(false);
                        String[] addressInfo;

                        //Come al solito, l'accesso alla struttura dev'essere sempre accompagnato dalla synchronized.
                        synchronized (requests) {
                            addressInfo = requests.get(idToAccept);
                            //Nel caso in cui fosse scaduta la richiesta tra il momento in cui è stata richiesta
                            //la stampa delle sfide ad ora (si noti che una volta stampata la lista questa non si
                            //aggiorna in tempo reale, è solo testo! Questo aspetto è vissuto molto meglio
                            //dagli utenti se presente l'interfaccia grafica, la quale può essere aggiornata in
                            //tempo reale).
                            if (addressInfo == null) {
                                System.out.println("L'id inserito non è stato trovato/La richiesta non è più valida.");
                                break;
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
                                System.out.println("Richiesta scaduta.");
                                break;
                            }

                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }

                        //Può partire la sfida!
                        response = CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println(response);

                        if (response.equals("Siamo spiacenti, il servizio di traduzione non è al " +
                                "momento disponibile. Riprovare più tardi.")) break;

                        while (true) {
                            response = CommonUtilities.readFromSocket(TCPSocket);
                            System.out.println(response);
                            if (response.contains("Attendi...")) break;
                            if (response.equals("Il tempo per la sfida è scaduto, l'ultima risposta data non è conteggiata.")) {
                                response = CommonUtilities.readFromSocket(TCPSocket);
                                System.out.println(response);
                                break;
                            }
                            String answer = inputReader.readLine();
                            while (answer.equals("")) {
                                System.out.println("Inserisci una risposta.");
                                answer = inputReader.readLine();
                            }
                            while (answer.length() > maxCommandLength) {
                                System.out.println("La risposta inserita è troppo lunga.");
                                answer = inputReader.readLine();
                            }
                            CommonUtilities.writeIntoSocket(answer, TCPSocket);
                        }

                        response = CommonUtilities.readFromSocket(TCPSocket);
                        System.out.println(response);

                        break;
                    case "esci":
                        //Serve per chiudere in maniera corretta il client.
                        on = false;
                        break;
                    default:
                        //Se si dovesse inserire un comando non supportato, si arriva qui.
                        System.out.println("Il comando inserito non è supportato.");
                }
            }

            /*
                Una volta uscito dal 'while' principale, il client deve chiudersi. Prima però, cerca di
                chiudere tutto ciò che aveva lasciato aperto: la socket TCP, la socket UDP, termina il thread
                UDPReceiver e termina il pool.
             */
            TCPSocket.close();

            receiver.stopRun();
            UDPSocket.close();
            executorService.shutdown();

        } catch (IOException e) {
            //Si arriva qui se ci sono problemi con il server.
            System.out.println("Il server WQ non è attualmente raggiungibile, riprovare più tardi.");

            //Anche quando viene chiuso non esplicitamente, il client cerca di liberare tutto ciò che aveva creato.
            if (TCPSocket != null) TCPSocket.close();
            if (receiver != null) receiver.stopRun();
            if (UDPSocket != null) UDPSocket.close();
            executorService.shutdown();
        }
    }
}

