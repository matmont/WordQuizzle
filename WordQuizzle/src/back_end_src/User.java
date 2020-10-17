package back_end_src;

import back_end_src.exceptions.AlreadyFriendException;
import java.util.ArrayList;

/**
 * La classe {@link User} astrae un utente e tutte le sue informazioni, fornendo alcuni
 * metodi per la gestione di queste.
 */
public class User {
    private final String nickUtente;
    private final String password;
    private int points;
    private final ArrayList<String> friends;

    public User(String nickUtente, String password) {
        this.nickUtente = nickUtente;
        this.password = password;
        this.points = 0;
        this.friends = new ArrayList<>();
    }

    /**
     * Restituisce la password associata all'utente.
     *
     * @return La password dell'utente.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Incrementa il punteggio totale dell'utente di una certa quantità 'incrementFactor'.
     *     --- points = points + incrementFactor ---
     *
     * @param incrementFactor La quantità del quale incrementare il punteggio dell'utente.
     */
    public void incrementPoints(int incrementFactor) {
        this.points += incrementFactor;
    }

    /**
     * Restituisce la lista amici dell'utente.
     *
     * @return La lista amici dell'utente.
     */
    public ArrayList<String> getFriends() {
        return this.friends;
    }

    /**
     * Restituisce il punteggio totale associato all'utente.
     *
     * @return Il punteggio totale dell'utente.
     */
    public int getPoints() {
        return this.points;
    }

    /**
     * Aggiunge 'friendId' agli amici dell'utente, nel caso in cui questi non lo siano già.
     *
     * @param friendId L'username dell'utente da aggiungere come nuovo amico.
     * @throws AlreadyFriendException Nel caso in cui 'friendId' sia già amico di questo utente.
     */
    public void addFriend(String friendId) throws AlreadyFriendException {
        if(friendId == null) throw new NullPointerException();
        if(this.friends.contains(friendId)) throw new AlreadyFriendException();
        this.friends.add(friendId);
    }

    @Override
    public String toString() {
        return "Nickname: " + this.nickUtente + " | Password: " + this.password + " | Points: " + this.points + " | Friends: " + this.friends.toString();
    }


}
