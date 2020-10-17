package back_end_src;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe di utilità per il {@link Server}. Contiene diversi metodi, ma i più importanti sono
 * 'writeJson' e 'readJson', per scrivere e leggere il file JSON (e quindi persistere le informazioni
 * riguardo gli utenti), e 'readingDictionary' per recuperare all'avvio del {@link Server} le parole
 * di cui gli utenti dovranno dare le traduzioni.
 */
@SuppressWarnings("WeakerAccess")
class ServerUtilities {

    /**
     * Scrive sul file JSON la struttura dati astratta da {@link UsersRegister}. In questo modo
     * riusciamo a persistere tutte le informazioni di cui avremo bisogno al prossimo avvio.
     * Si noti che, piuttosto che avere diversi file JSON per le varie informazioni da persistere, ho
     * deciso di salvarmi ogni volta l'intera struttura dati, la quale viene deserializzata all'avvio
     * del {@link Server} per essere poi utilizzata durante tutta la sessione.
     *
     * @param usersRegister La struttura dati da scrivere sul file.
     * @throws IOException In caso di problemi durante la scrittura del file.
     */
    public static void writeJson(ConcurrentHashMap<String, User> usersRegister) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonInfo;
        /*
            Piccola nota. Ho voluto utilizzare i FileChannel per le operazioni di scrittura e lettura poichè sono
            più efficienti (essendo a più basso livello) ma anche perchè "autogestiscono" in modo corretto
            la concorrenza. Difatti, è possibile che più thread chiamino questo metodo contemporaneamente, ad esempio:
            un thread 'MatchManager' vuole aggiornare il punteggio totale di un utente mentre un client ha richiesto
            al server di aggiungere un'amicizia, entrambe le cose comportano una sovrascrittura sul file per poter
            salvare le nuove modifiche.
            Devo però notificare un piccolo problemino che ho verificato esserci: tutto ciò che è stato detto fino ad ora
            è vero, funziona benissimo, però quando le cose vanno bene.
            Durante alcuni stress test sull'RMI, sono capitate alcune eccezioni di rete, eccezioni che in realtà non
            sono veri e propri errori bensì eccezioni dovute al troppo carico di connessioni, o comunque di tipo tecnico.
            Cose che devono essere risolte ad hardware (basti pensare che queste eccezioni arrivavano in maniera del tutto
            differente in base al OS sul quale ho fatto girare il server, su Linux la situazione era molto più che
            accettabile). Ecco, quando queste eccezioni venivano lanciate, c'erano alcuni inconsistenze durante la scrittura
            del file (le quali si notavano al successivo avvio del server, quando questo provava a leggere il JSON),
            problemi che venivano risolti solo mettendo la 'synchronized' sulla struttura.
         */
        ////noinspection SynchronizationOnLocalVariableOrMethodParameter
        //synchronized (usersRegister) {
            jsonInfo = gson.toJson(usersRegister);
        //}
        FileChannel channel = FileChannel.open(Paths.get("./src/back_end_src/usersRegister.json"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        ByteBuffer bb = ByteBuffer.allocate(jsonInfo.getBytes().length);
        bb.put(jsonInfo.getBytes());
        bb.flip();
        while (bb.hasRemaining()) {
            channel.write(bb);
        }
        channel.close();
    }

    /**
     * Legge i dati dal file JSON; deserializza quindi la struttura dati e ritorna l'oggetto {@link ConcurrentHashMap},
     * la quale conterrà appunto tutti gli {@link User} registrati sul server (i vari dati per ognuno di essi
     * sono incapsulati, come attributi, nell'oggetto {@link User} stesso).
     *
     * @return La struttura dati {@link ConcurrentHashMap} contenente gli {@link User} e i loro dati.
     * @throws IOException In caso di problemi durante la lettura del file.
     */
    public static ConcurrentHashMap<String, User> readJson() throws IOException {
        ConcurrentHashMap<String, User> usersRegister;
        Gson gson = new Gson();
        try {
            FileChannel channel = FileChannel.open(Paths.get("./src/back_end_src/usersRegister.json"), StandardOpenOption.READ);

            ByteBuffer bb = ByteBuffer.allocate((int) channel.size());
            //Non serve fare un ciclo per assicurarsi la lettura completa: il ByteBuffer viene allocato della precisa size.
            channel.read(bb);
            channel.close();
            //Converto la stringa letta nell'oggetto corrispondente.
            Type usersRegisterType = new TypeToken<ConcurrentHashMap<String, User>>() {
            }.getType();
            usersRegister = gson.fromJson(new String(bb.array()), usersRegisterType);
            return usersRegister;
        } catch (NoSuchFileException e) {
            //Nel caso in cui il file non si trovi, non esista ancora, voglio che ritorni NULL, così che il server
            //possa capire che è stato aperto per la prima vera volta, e procedere alla creazione di esso.
            return null;
        }
    }

    /**
     * Serve al {@link Server} per tokenizzare e poi analizzare il messaggio ricevuto dal {@link front_end_src.Client}.
     * Per esempio, grazie alla tokenizzazione, prende il comando e vede cosa è stato richiesto, poi prende l'id
     * e vede a chi destinare il comando, e così via...
     *
     * @param request La stringa da tokenizzare.
     * @return La stringa tokenizzata.
     */
    public static ArrayList<String> tokenizeString(String request) {
        ArrayList<String> tokenized = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(request);
        while (st.hasMoreTokens()) {
            tokenized.add(st.nextToken());
        }
        return tokenized;
    }

    /**
     * Permette al {@link Server} di poter recuperare l'id associato alla {@link SocketChannel} che sta
     * per servire (la {@link SocketChannel} pronta del selettore). Grazie a ciò, il {@link Server} saprà sempre
     * chi è l'id mittente! Questo fa sì che l'utente possa inviare comandi del tipo 'mostra_punteggio' piuttosto
     * che 'mostra_punteggio --proprio_nome--'.
     *
     *
     * @param onlineUsers La struttura dati mantenuta dal {@link Server} per tenere traccia degli utenti attualmente online.
     *                    Gioca un ruolo molto più importante di quel che sembra, in alcune situazioni. Per ulteriori
     *                    informazioni guardare la classe {@link Server} o leggere la relazione.
     * @param socketChannel Il value del quale vogliamo recuperare la chiave. Infatti, 'onlineUsers' è una mapppa
     *                      con chiave il nome dell'utente e valore la {@link SocketChannel} associata al client
     *                      con il quale è collegato l'utente.
     * @return Il nome dell'utente loggato con il client avente quella {@link SocketChannel}.
     */
    public static String getKey(HashMap<String, SocketChannel> onlineUsers, SocketChannel socketChannel) {
        String id = null;
        for (Map.Entry<String, SocketChannel> entry : onlineUsers.entrySet()) {
            if (socketChannel.equals(entry.getValue())) {
                id = entry.getKey();
            }
        }
        return id;
    }

    /**
     * Si legga il funzionamento sulla relazione, o nelle classi {@link Server} o {@link front_end_src.UDPReceiver}. In
     * breve, l'{@link front_end_src.UDPReceiver} apre la propria {@link java.net.DatagramSocket} sulla medesima porta
     * della sua {@link SocketChannel}, la stessa che, ricordiamolo, il {@link Server} si è salvato al momento del
     * login dell'utente. Ciò ci permette, attraverso questo metodo che ci 'smonta' l'indirizzo remoto della
     * {@link SocketChannel}, di recuperare la porta in questione e poter così inviare eventuali inoltri di richieste
     * all'utente designato.
     *
     * @param clientSocket La {@link SocketChannel} dalla quale recupereremo l'indirizzo tokenizzato.
     * @return Un array di {@link String} contenente i dati dell'indirizzo.
     */
    public static String[] tokenizeAddress(SocketChannel clientSocket) {
        String[] tokenizedAddress = new String[2];
        try {
            tokenizedAddress = clientSocket.getRemoteAddress().toString().split(":");
            tokenizedAddress[0] = tokenizedAddress[0].substring(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tokenizedAddress;
    }

    /**
     * Le parole che verranno utilizzate durante il game sono mantenute all'interno di un file
     * di testo. Al momento dell'avvio, il {@link Server} legge dal file di testo tutte le parole
     * e le salva in un {@link ArrayList} (struttura dati scelta per la sua dinamicità e
     * semplicità) il quale fungerà da dizionario. I vari {@link RequestManager} che si occuperanno
     * del setup della sfida, sceglieranno casualmente da esso {@link Server#numberOfWords} parole.
     *
     * @return Il dizionario, come {@link ArrayList} di parole.
     * @throws IOException In caso di problemi durante la lettura del file.
     */
    public static ArrayList<String> readingDictionary() throws IOException {
        ArrayList<String> words = new ArrayList<>();
        File file = new File("./src/back_end_src/dictionary.txt");
        BufferedReader b = new BufferedReader(new FileReader(file));
        String newLine;
        while ((newLine = b.readLine()) != null) {
            words.add(newLine);
        }
        return words;
    }

    public static int[] generateRandomNumbers(int count, int min, int max) {
        int[] numbers = new int[count];
        ArrayList<Integer> choosen = new ArrayList<>();
        int n;
        for (int i = 0; i < numbers.length; i++) {
            do {
                n = ((int) (Math.random() * max)) + min;
            } while (choosen.contains(n));
            choosen.add(n);
            numbers[i] = n;
        }
        return numbers;
    }


}
