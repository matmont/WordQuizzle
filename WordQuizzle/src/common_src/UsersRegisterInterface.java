package common_src;

import common_src.exceptions.AlreadyRegisteredUserException;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * L'interfaccia dell'oggetto RMI esportato dal {@link back_end_src.Server}.
 * È necessario creare un'interfaccia per permettere al {@link front_end_src.Client} di
 * chiamare i metodi dell'oggetto remoto.
 *
 * È stato necessario inserire l'interfaccia in questo package poichè
 * è necessaria ad entrambi le parti: il {@link back_end_src.Server} la utilizza
 * per implementarne i metodi, il {@link front_end_src.Client} per utilizzarli.
 *
 */
public interface UsersRegisterInterface extends Remote {

    /**
     * Registra un nuovo utente all'interno della
     * struttura dati del {@link back_end_src.Server}.
     *
     * @param nickUtente L'username da registrare.
     * @param password La password relativa all'utente.
     * @throws RemoteException In caso di problemi durante la sessione RMI.
     * @throws AlreadyRegisteredUserException In caso di username già registrato.
     * @throws NullPointerException Nel caso uno dei due parametri sia nullo.
     */
    void registerNewUser(String nickUtente, String password) throws RemoteException,
            AlreadyRegisteredUserException, NullPointerException;
}
