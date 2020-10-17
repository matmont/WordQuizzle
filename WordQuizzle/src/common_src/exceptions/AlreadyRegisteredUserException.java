package common_src.exceptions;

/**
 * Eccezione custom per notificare il {@link front_end_src.Client} di una registrazione fallita a
 * causa di un username già occupato, già registrato.
 */
public class AlreadyRegisteredUserException extends Exception{

    public AlreadyRegisteredUserException() {
        super();
    }

    @SuppressWarnings("unused")
    public AlreadyRegisteredUserException(String s) {
        super(s);
    }
}
