package back_end_src;

import back_end_src.exceptions.AlreadyFriendException;
import common_src.exceptions.AlreadyRegisteredUserException;
import common_src.UsersRegisterInterface;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La classe {@link UsersRegister}. Astrae l'oggetto database di tutto il progetto.
 * Sarà inoltre l'oggetto che il {@link Server} esporterà sul {@link java.rmi.registry.Registry},
 * così da renderlo accessibile ai {@link front_end_src.Client} tramite la tecnologia RMI.
 */
@SuppressWarnings("WeakerAccess")
public class UsersRegister extends RemoteServer implements UsersRegisterInterface {

    private ConcurrentHashMap<String, User> usersRegister;

    /**
     * L'unica istanza della classe {@link UsersRegister}.
     * Ho deciso di implementare un pattern Singleton poichè
     * l'oggetto rappresenta un punto di riferimento per tutto il programma.
     * E' sensato avere un'unica istanza per tutti, che rappresenta il nostro
     * database di utenti.
     */
    private static UsersRegister instance;

    //Costruttore privato, così che sia inutilizzabile da altre classi.
    private UsersRegister() {
    }

    /**
     * Il metodo statico che sarà utilizzato per recuperare l'unica istanza
     * di {@link UsersRegister}.
     *
     * @return L'unica istanza di {@link UsersRegister}
     * @throws RemoteException Lanciata in caso di problemi con l'RMI.
     */
    @SuppressWarnings("RedundantThrows")
    public static synchronized UsersRegister getInstance() throws RemoteException {
        if (instance == null) {
            instance = new UsersRegister();
            try {
                instance.usersRegister = ServerUtilities.readJson();
            } catch (IOException e) {
                //C'è stato qualche problema durante la lettura del file!
                e.printStackTrace();
            }
            if (instance.usersRegister == null) {
                instance.usersRegister = new ConcurrentHashMap<>();
            }
        }
        return instance;
    }

    /**
     * Il metodo non è utilizzato, se non in un codice di debug. Ignorare.
     *
     * @return La mappa {@link UsersRegister}.
     */
    public ConcurrentHashMap<String, User> getUsersRegister() {
        return this.usersRegister;
    }


    /**
     * Registra un nuovo utente al servizio WordQuizzle, inserendolo all'interno
     * di {@link #usersRegister} e riscrivendo sul file la struttura, così da
     * persistere le nuove modifiche. Per ulteriori informazioni riguardo l'implementazione
     * della scrittura su file, consultare la classe {@link ServerUtilities}, metodo 
     * {@link ServerUtilities#writeJson(ConcurrentHashMap)}
     *
     * @param nickUtente L'username da registrare.
     * @param password La password relativa all'utente.
     * @throws RemoteException In caso di problemi con la tecnologia RMI.
     * @throws AlreadyRegisteredUserException In caso di id già registrato.
     * @throws NullPointerException Nel caso in cui qualche parametro sia null.
     */
    @SuppressWarnings({"RedundantThrows"})
    @Override
    public void registerNewUser(String nickUtente, String password) throws RemoteException,
            AlreadyRegisteredUserException, NullPointerException {
        if (nickUtente == null || password == null) throw new NullPointerException();
        //Le operazioni composte non sarebbero di norma thread-safe. Ci viene però in aiuto la ConcurrentHashMap.
        if (this.usersRegister.putIfAbsent(nickUtente, new User(nickUtente, password)) != null)
            throw new AlreadyRegisteredUserException();
        try {
            //Thread-safe grazie all'utilizzo dei FileChannel.
            ServerUtilities.writeJson(this.usersRegister);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Exception: trying writing on GSON after new sign up. [IO EXCEPTION]");
        }

        if(Server.DEBUG_MODE) System.out.println("Nuovo utente registrato: " + nickUtente);
    }

    /**
     * Controlla l'esistenza o meno di 'id' all'interno della collezione.
     *
     * @param id L'id di cui verificare la presenza.
     * @return {@code true} se 'id' è presente nella collezione, {@code false} altrimenti.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean searchUser(String id) {
        return this.usersRegister.containsKey(id);
    }

    /**
     * Controlla la correttezza di 'psw' associata all'utente 'id'. Serve al server
     * per controllare la validità dei dati di login forniti dal client.
     *
     * @param id Username.
     * @param psw Password.
     * @return {@code true} se i dati sono corretti, {@code false} altrimenti.
     */
    public boolean checkPsw(String id, String psw) {
        /*
            Non metto la lock perche tanto la password non puo essere cambiata da altri
            thread ne tanto meno si puo eliminare un utente dalla collezione
         */
        return this.usersRegister.get(id).getPassword().equals(psw);
    }

    /**
     * Costruisce e restituisce una classifica degli amici dell'utente, sottoforma di {@link LinkedHashMap}
     * ordinata in maniera decrescente sui punteggi.
     *
     * @param id Utente che richiede la classifica.
     * @return La classifica,
     */
    public LinkedHashMap<String, Integer> buildRank(String id) {
        LinkedHashMap<String, Integer> rank = new LinkedHashMap<>();

        User user = this.usersRegister.get(id);
        ArrayList<String> friends = user.getFriends();

        rank.put(id, user.getPoints());

        for (String friend : friends) {
            /*
                È necessario sincronizzarci su ogni utente, poichè tra i miei amici potrebbe essere che
                qualcuno abbia appena finito di giocare e quindi il thread MatchManager ne stia aggiornando
                il punteggio totale.
             */
            User u;
            int points;
            synchronized (u = this.usersRegister.get(friend)) {
                points = u.getPoints();
            }
            rank.put(friend, points);
        }

        /*
            Ordinamento della classifica, così da restituirla in ordine decrescente di punteggio.
         */
        List<Map.Entry<String, Integer>> list = new ArrayList<>(rank.entrySet());
        list.sort((userA, userB) -> -(userA.getValue().compareTo(userB.getValue())));
        rank.clear();
        for (Map.Entry<String, Integer> entry : list) {
            rank.put(entry.getKey(), entry.getValue());
        }

        return rank;

    }

    /**
     * Controlla se esiste un'amiciza tra l'utente 'id' e 'id2'.
     *
     * @param id Il primo utente.
     * @param id2 Il secondo utente.
     * @return {@code true}, se 'id' e 'id2' sono amici, {@code false} altrimenti.
     */
    public boolean isFriendOf(String id, String id2) {
        return this.usersRegister.get(id).getFriends().contains(id2);
    }

    /**
     * Restituisce il punteggio totale dell'utente.
     *
     * @param id L'id dell'utente di cui restituire il punteggio.
     * @return Il punteggio totale dell'utente.
     */
    public int getPointOf(String id) {
        User user;
        synchronized (user = this.usersRegister.get(id)) {
            return user.getPoints();
        }
    }

    /**
     * Restituisce la lista amici dell'utente 'id'.
     *
     * @param id L'utente di cui si richiede la lista amici.
     * @return La lista amici di 'id'.
     */
    public ArrayList<String> getFriendsOf(String id) {
        return this.usersRegister.get(id).getFriends();
    }

    /**
     * Aggiunge una nuova amicizia tra 'id' e 'id2'.
     *
     * @param id Il primo utente.
     * @param id2 Il secondo utente.
     * @throws AlreadyFriendException Nel caso in cui i due utenti siano già amici.
     * @throws IOException Nel caso in cui ci siano problemi durante la scrittura sul file della struttura.
     */
    public void addFriends(String id, String id2) throws AlreadyFriendException, IOException {
        this.usersRegister.get(id).addFriend(id2);
        this.usersRegister.get(id2).addFriend(id);
        ServerUtilities.writeJson(this.usersRegister);
    }

    /**
     * Incrementa il punteggio totale di 'id' di un fattore 'points'.
     *
     * @param id L'utente di cui incrementare il punteggio.
     * @param points Il fattore di cui incrementare il punteggio.
     */
    public void incrementPointsOf(String id, int points) {
        User u;
        synchronized (u = this.usersRegister.get(id)) {
            u.incrementPoints(points);
        }
        try {
            ServerUtilities.writeJson(this.usersRegister);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stampa la struttura dati, all'apertura del {@link Server}.
     */
    public void printRegister() {
        for (String key : this.usersRegister.keySet()) {
            System.out.println("Key --> Nickname: " + key + " --- Values --> " + this.usersRegister.get(key).toString());
        }
        System.out.println("Numero di utenti registrati: " + this.usersRegister.size());
    }

    /**
     * Indica la somma dei punteggi totali di tutti gli utenti. È stato utilizzato in fase di debug. Non
     * ha alcun ruolo all'interno del meccanismo.
     */
    @SuppressWarnings("unused")
    public void howMuchPoints() {
        int sum = 0;
        for (Map.Entry<String, User> entry : this.usersRegister.entrySet()) {
            String key = entry.getKey();
            sum += this.usersRegister.get(key).getPoints();
        }
        System.out.println("Numero di punti totali: " + sum);
    }

    /**
     * Restituisce il numero degli elementi contenuti in {@link #usersRegister}.
     *
     * @return La size di {@link #usersRegister}
     */
    public int size() {
        return this.usersRegister.size();
    }

    @Override
    public String toString() {
        return "UsersRegister{" +
                "usersRegister=" + usersRegister +
                '}';
    }
}
