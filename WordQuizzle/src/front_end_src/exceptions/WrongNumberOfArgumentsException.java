package front_end_src.exceptions;

/**
 * Eccezione custom per notificare il {@link front_end_src.Client} di un cattivo utilizzo
 * di un qualche comando.
 */
public class WrongNumberOfArgumentsException extends Exception {
    public WrongNumberOfArgumentsException() {
        super();
    }

    @SuppressWarnings("unused")
    public WrongNumberOfArgumentsException(String s) {
        super(s);
    }
}
