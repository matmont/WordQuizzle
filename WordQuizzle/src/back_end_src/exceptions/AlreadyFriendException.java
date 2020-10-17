package back_end_src.exceptions;

/**
 * Eccezione custom lanciata nell'eventualità in cui si tenti di creare un'amicizia
 * già esistente. Viene utilizzata nel metodo {@link back_end_src.User#addFriend(String)}.
 */
public class AlreadyFriendException extends Exception{
    public AlreadyFriendException() {
        super();
    }

    @SuppressWarnings("unused")
    public AlreadyFriendException(String s) {
        super(s);
    }
}
