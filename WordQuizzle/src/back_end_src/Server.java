package back_end_src;

import back_end_src.exceptions.AlreadyFriendException;
import back_end_src.gui.UDPSender;
import com.google.gson.*;
import common_src.UsersRegisterInterface;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Il {@link Server} WordQuizzle. Implementa il cuore della comunicazione con i {@link front_end_src.Client}.
 * Di base ciò che fa è: riceve il comando -> esegue ciò che bisogna eseguire -> comunica il risponso al
 * {@link front_end_src.Client}.
 */
public class Server {

    /*
        Voglio che tutte queste variabili siano 'package-private', per questo
        non c'è alcun indicatore di visibilità.
     */

    //Impostare a TRUE prima dell'esecuzione per avere in output qualche messaggio di debug.
    static final boolean DEBUG_MODE = true;

    //Impostare a TRUE prima dell'esecuzione nel caso in cui si utilizzino client a linea di comando.
    static final boolean GUI_MODE = true;

    static int wrongTranslationDecrement;
    static int requestTimeExpire;
    static int winPointsIncrement;
    static int numberOfWords;
    static int correctTranslationIncrement;
    static int matchDuration;
    static ArrayList<String> dictionary;

    static int tcpPort;
    static int rmiPort;

    static int maxCommandLength;

    static final ArrayList<String> inGameUsers = new ArrayList<>();

    @SuppressWarnings({"InfiniteLoopStatement"})
    public static void main(String[] args) {
        try {
            //Viene recuperata l'istanza unica di 'UsersRegister' e se ne stampa il contenuto.
            UsersRegister usersRegister = UsersRegister.getInstance();
            usersRegister.printRegister();
            HashMap<String, SocketChannel> onlineUsers = new HashMap<>();
            ThreadPoolExecutor requestsPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

            /*
                Si procede alla lettura del file 'config.properties' e così all'inizializzazione dei vari
                settings per il match.
             */
            Properties properties = new Properties();
            FileChannel fileChannel = FileChannel.open(Paths.get("./src/back_end_src/config.properties"), StandardOpenOption.READ);
            properties.load(Channels.newInputStream(fileChannel));

            numberOfWords = Integer.parseInt(properties.getProperty("number_of_words"));
            wrongTranslationDecrement = Integer.parseInt(properties.getProperty("wrong_translation_decrement"));
            requestTimeExpire = Integer.parseInt(properties.getProperty("request_time_expire"));
            winPointsIncrement = Integer.parseInt(properties.getProperty("win_points_increment"));
            correctTranslationIncrement = Integer.parseInt(properties.getProperty("correct_translation_increment"));
            matchDuration = Integer.parseInt(properties.getProperty("match_duration"));

            fileChannel.close();

            fileChannel = FileChannel.open(Paths.get("./src/common_src/config.properties"), StandardOpenOption.READ);
            properties.load(Channels.newInputStream(fileChannel));

            //--//
            tcpPort = Integer.parseInt(properties.getProperty("server_port"));
            rmiPort = Integer.parseInt(properties.getProperty("rmi_port"));
            //--//
            maxCommandLength = Integer.parseInt(properties.getProperty("max_command_len"));

            fileChannel.close();

            properties.clear();

            //Viene creato il dizionario delle parole, lette dal file 'dictionary.txt'.
            dictionary = ServerUtilities.readingDictionary();

            Gson gson = new Gson();

            //----- SERVER START

            //RMI SHARING
            UsersRegisterInterface serverStub = (UsersRegisterInterface) UnicastRemoteObject.exportObject(usersRegister, 0);
            LocateRegistry.createRegistry(rmiPort);
            Registry registry = LocateRegistry.getRegistry(rmiPort);
            registry.rebind("USERS-REGISTER-SERVER", serverStub);

            //Da questo momento il Server attende le connessioni dei client.
            System.out.println("\nWQServer is running! ...");

            try {
                Selector selector;
                ServerSocketChannel serverSocket = ServerSocketChannel.open();
                serverSocket.bind(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort));
                serverSocket.configureBlocking(false);
                selector = Selector.open();
                serverSocket.register(selector, SelectionKey.OP_ACCEPT);
                while (true) {
                    selector.select();
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey currentKey = iterator.next();
                        iterator.remove();
                        try {
                            if (currentKey.isAcceptable()) {
                                ServerSocketChannel listener = (ServerSocketChannel) currentKey.channel();
                                SocketChannel socket = listener.accept();
                                socket.configureBlocking(false);

                                /*
                                    Stiamo per andare in lettura: ci portiamo dentro questo array di ByteBuffer
                                    che useremo per la lettura. Il primo leggerà la size del messaggio, il secondo
                                    il testo vero e proprio. È fondamentale l'utilizzo del primo buffer per renderci
                                    conto della completa lettura (o meno) del messaggio vero e proprio.
                                 */
                                ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                                ByteBuffer readBuffer = ByteBuffer.allocate(maxCommandLength);
                                ByteBuffer[] buffers = {sizeBuffer, readBuffer};

                                socket.register(selector, SelectionKey.OP_READ, buffers);
                            } else if (currentKey.isReadable()) {
                                SocketChannel socket = (SocketChannel) currentKey.channel();

                                ByteBuffer[] buffers = (ByteBuffer[]) currentKey.attachment();
                                String id = ServerUtilities.getKey(onlineUsers, socket);

                                /*
                                    Se l'utente loggato sulla socket corrente è in game, leggere i suoi messaggi
                                    non è di nostra responsabilità, ci penserà MatchManager.
                                 */
                                synchronized (inGameUsers) {
                                    if (inGameUsers.contains(id)) continue;
                                }

                                String request, response;

                                /*
                                    Si procede alla lettura del messaggio. Lo schema è quello base:
                                        - lettura size:
                                            - letta tutta? -> lettura messaggio
                                                -letto tutto? -> bene! Si fa ciò che bisogna fare e si registra
                                                                 il channel per andare in WRITE_OP.

                                            - altrimenti -> non registriamo il channel in WRITE_OP! Al prossimo "giro"
                                                            finiremo di leggere ciò che non abbiamo letto fino ad ora.
                                 */

                                socket.read(buffers);
                                if (!buffers[0].hasRemaining()) {
                                    buffers[0].flip();
                                    int size = buffers[0].getInt();

                                    if (buffers[1].position() == size) {
                                        buffers[1].flip();
                                        request = new String(buffers[1].array()).trim();

                                        ArrayList<String> tokenizedRequest = ServerUtilities.tokenizeString(request);

                                        switch (tokenizedRequest.get(0)) {
                                            case "login":
                                                if (onlineUsers.containsKey(tokenizedRequest.get(1))) {
                                                    response = "L'utente è già loggato.";
                                                    break;
                                                }
                                                /*
                                                    È importante che questo check sia prima di 'checkPsw', altrimenti
                                                    'checkPsw' stesso potrebbe dare un'eccezione NullPointer (nel caso in
                                                    cui si inserisca un 'id' non esistente.
                                                */
                                                if (!usersRegister.searchUser(tokenizedRequest.get(1))) {
                                                    response = "L'id inserito non è registrato.";
                                                    break;
                                                }
                                                if (!usersRegister.checkPsw(tokenizedRequest.get(1),
                                                        tokenizedRequest.get(2))) {
                                                    response = "La password inserita è errata.";
                                                    break;
                                                }

                                                onlineUsers.put(tokenizedRequest.get(1), socket);
                                                if (DEBUG_MODE)
                                                    System.out.println("L'utente " + tokenizedRequest.get(1)
                                                            + " ha eseguito il login");
                                                response = "Login effettuato con successo.";
                                                break;
                                            case "logout":
                                                //Nulla può andare male per natura del client. Logout non richiede argomenti!
                                                onlineUsers.remove(id);
                                                if (DEBUG_MODE)
                                                    System.out.println("L'utente " + id + " ha eseguito il logout");
                                                response = "Logout effettuato con successo.";
                                                break;
                                            case "aggiungi_amico":
                                                if (!usersRegister.searchUser(tokenizedRequest.get(1))) {
                                                    response = "L'utente indicato non esiste.";
                                                    break;
                                                }
                                                if (id != null && id.equals(tokenizedRequest.get(1))) {
                                                    response = "Non puoi richiedere un'amiciza con te stesso.";
                                                    break;
                                                }
                                                try {
                                                    /*
                                                        Non serve controllare che 'id' sia null, poichè un client
                                                        che richiede un'amicizia ha per forza fatto anche il login!
                                                        (altrimenti è il client stesso a dare errore prima di inviare
                                                        il comando al server).
                                                     */
                                                    usersRegister.addFriends(id, tokenizedRequest.get(1));
                                                    response = "L'amicizia è stata aggiunta con successo.";
                                                    /*
                                                        Questa funzionalità è attiva solo nel caso in cui si utilizzi
                                                        un client con interfaccia grafica (e quindi impostato correttamente
                                                        il flag corrispondente qui nel server). Ciò che succede è che viene
                                                        lanciato un 'UDPSender', il quale si occupa di inviare un UDP
                                                        message ad 'UDPReceiver' (del client) per indicare l'aggiunta di
                                                        una nuova amicizia. Ciò fa sì che l'interfaccia dell'utente aggiunto
                                                        venga aggiornata in tempo reale! Questo non ha senso per il client
                                                        a linea di comando, poichè la lista viene richiesta tramite un
                                                        preciso comando, mentre con l'interfaccia grafica è sempre visibile.
                                                     */
                                                    if (GUI_MODE) {
                                                        //Quest'operazione può essere fatta (ed effettivamente è utile)
                                                        //solo se l'altro utente è anch'esso online.
                                                        if (onlineUsers.containsKey(tokenizedRequest.get(1))) {
                                                            String[] addressInfo = ServerUtilities.
                                                                    tokenizeAddress(onlineUsers.get(tokenizedRequest.get(1)));
                                                            Executors.newSingleThreadExecutor().
                                                                    execute(new UDPSender(addressInfo, id, "newfriend"));
                                                        }
                                                    }
                                                } catch (AlreadyFriendException e) {
                                                    response = "Hai già un'amicizia con l'utente indicato.";
                                                }
                                                break;
                                            case "lista_amici":
                                                ArrayList<String> friends = usersRegister.getFriendsOf(id);
                                                if (friends.size() != 0) {
                                                    response = gson.toJson(friends);
                                                } else response = "Non hai nessuna amicizia.";
                                                break;
                                            case "sfida":
                                                if (!usersRegister.searchUser(tokenizedRequest.get(1))) {
                                                    response = "L'utente indicato non esiste.";
                                                    break;
                                                }
                                                if (id != null && id.equals(tokenizedRequest.get(1))) {
                                                    response = "Non puoi richiedere una sfida con te stesso.";
                                                    break;
                                                }
                                                if (!usersRegister.isFriendOf(id, tokenizedRequest.get(1))) {
                                                    response = "Non puoi richiedere una sfida con un utente con il quale non sei amico.";
                                                    break;
                                                }
                                                if (!onlineUsers.containsKey(tokenizedRequest.get(1))) {
                                                    response = "L'utente indicato non è online.";
                                                    break;
                                                }

                                                requestsPool.execute(new RequestManager(id, socket, tokenizedRequest.get(1), onlineUsers.get(tokenizedRequest.get(1))));
                                                response = "Sfida a " + tokenizedRequest.get(1) + " inviata. In attesa di accettazione...";

                                                break;
                                            case "mostra_punteggio":
                                                int points = usersRegister.getPointOf(id);
                                                response = "Il tuo punteggio e': " + points + ".";
                                                break;
                                            case "mostra_classifica":
                                                LinkedHashMap<String, Integer> rank = usersRegister.buildRank(id);
                                                response = gson.toJson(rank);
                                                break;
                                            default:
                                                response = "Nessuna corrispondenza con i comandi permessi.";
                                        }

                                        socket.register(selector, SelectionKey.OP_WRITE, response);

                                        if (DEBUG_MODE) {
                                            System.out.println("Utenti online -> " + Arrays.toString(onlineUsers.keySet().toArray()));
                                            synchronized (inGameUsers) {
                                                System.out.println("Utenti in game -> " + inGameUsers.toString());
                                            }
                                        }
                                    }
                                } else if (buffers[0].position() == 0) {
                                    //Il client ha chiuso la socket, è uscito (correttamente, tramite comando 'esci').
                                    manageQuit(id, currentKey, onlineUsers);
                                }
                            } else if (currentKey.isWritable()) {

                                /*
                                    Non c'è niente di speciale in questa parte di codice. È un banalissimo
                                    invio di messaggio tramite selettore, della forma standard.
                                 */

                                SocketChannel socket = (SocketChannel) currentKey.channel();

                                String response = (String) currentKey.attachment();
                                ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                                sizeBuffer.putInt(response.getBytes().length);
                                sizeBuffer.flip();
                                socket.write(sizeBuffer);
                                if (!sizeBuffer.hasRemaining()) {
                                    sizeBuffer.clear();

                                    ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes());
                                    socket.write(writeBuffer);

                                    if (!writeBuffer.hasRemaining()) {
                                        writeBuffer.clear();

                                        sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                                        ByteBuffer readBuffer = ByteBuffer.allocate(maxCommandLength);
                                        ByteBuffer[] buffers = {sizeBuffer, readBuffer};

                                        socket.register(selector, SelectionKey.OP_READ, buffers);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            /*
                                Nel caso in cui un client si chiuda inaspettatamente, dobbiamo gestire
                                il suo non essere più online!
                             */
                            SocketChannel socketChannel = (SocketChannel) currentKey.channel();
                            String id = ServerUtilities.getKey(onlineUsers, socketChannel);
                            manageQuit(id, currentKey, onlineUsers);
                        }
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Si occupa di gestire l'abbandono da parte di un certo client. Ciò vuol dire che
     * provvederà a cancellare la chiave dal selettore, a rimuovere l'utente dagli utenti online ed eventualmente
     * stampare il messaggio di debug (chiaramente se il flag per il debug è settato a TRUE).
     *
     * @param id          L'id dell'utente che ha abbandonato.
     * @param currentKey  La key del selettore relativa al channel che ha provocato l'eccezione.
     * @param onlineUsers La struttura dati degli utenti online.
     * @throws IOException In caso di problemi con il channel.
     */
    private static void manageQuit(String id, SelectionKey currentKey, HashMap<String, SocketChannel> onlineUsers) throws IOException {
        //Controlliamo anzitutto se il client che ha abbandonato aveva un qualche utente loggato o meno.
        if (id != null) {
            if (DEBUG_MODE) System.out.println("Il client (con login di " + id + ") ha abbandonato");
            onlineUsers.remove(id);
        } else {
            if (DEBUG_MODE) System.out.println("Un client (non attualmente loggato) ha abbandonato");
        }
        //Chiusura del channel e rimozione della key (de-registrazione).
        (currentKey.channel()).close();
        currentKey.cancel();
        //ONLY FOR DEBUG PURPOSES
        if (DEBUG_MODE) System.out.println("Utenti online -> " + Arrays.toString(onlineUsers.keySet().toArray()));
    }

    /**
     * È un metodo creato per motivi di test. Popola la struttura dati di tipo {@link UsersRegister},
     * inserendo alcuni parametri casuali per i vari utenti. È stato creato, in particolare, per il testing
     * della funzione 'mostra_classifica' e del corretto ordinamento della classifica stessa.
     */
    @SuppressWarnings("unused")
    private static void prepopulateForDebug() throws RemoteException {
        /*
            Assuming id0...id9 already into the data structure.
         */

        UsersRegister usersRegister = UsersRegister.getInstance();
        int size = usersRegister.size();

        //Aggiungo le amicizie
        for (int i = 1; i < size; i++) {
            try {
                usersRegister.addFriends("id0", "id" + i);
            } catch (AlreadyFriendException | IOException ignored) {
            }
            for (User user : usersRegister.getUsersRegister().values()) {
                user.incrementPoints((int) (Math.random() * 10));
            }
        }

    }

}