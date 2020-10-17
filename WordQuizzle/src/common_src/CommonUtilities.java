package common_src;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Classe di utilità per entrambe le classi {@link back_end_src.RequestManager} e {@link front_end_src.Client}.
 * Contiene due metodi, rispettivamente per la scrittura e lettura da una {@link SocketChannel}, cuore della
 * comunicazione tra {@link front_end_src.Client} e {@link back_end_src.Server} in questo progetto.
 */
public class CommonUtilities {

    /**
     * Flag da impostare nel caso si vogliano vedere alcune stampe di debug.
     * Per ulteriori informazioni guardare all'interno dei vari metodi istruzioni
     * contenenti {@code if(DEBUG_MODE)}.
     */
    private static final boolean DEBUG_MODE = false;

    /**
     * Scrive nella {@link SocketChannel} il messaggio passato come parametro.
     * Implementa un protocollo di comunicazione che prevede l'invio
     * della size prima del messaggio vero e proprio. Questo permette
     * un'allocazione del {@link ByteBuffer} di ricezione senza sprechi.
     *
     * @param message Il messaggio da inviare.
     * @param socketChannel La {@link SocketChannel} sulla quale scrivere il messaggio.
     * @throws IOException Lanciata in caso di errori durante la scrittura sulla {@link SocketChannel}.
     */
    public static void writeIntoSocket(String message, SocketChannel socketChannel) throws IOException {
        /*
            Le successive 4 righe si occupano di inviare la lunghezza
            del messaggio da scriere.
         */
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        sizeBuffer.putInt(message.getBytes().length);
        sizeBuffer.flip();

        /*
            Questo while in realtà non servirebbe. Quando si utilizza NIO
            si può assumere che la scrittura sia "atomica". Ho però deciso
            di andare sul sicuro (defensive programming) implementando questo
            ciclo, che per il 99% delle volte non farà più di un'iterazione.
         */
        while (sizeBuffer.hasRemaining()) {
            socketChannel.write(sizeBuffer);
        }
        sizeBuffer.clear();

        /*
            Stesso discorso della precedente scrittura.
         */
        ByteBuffer writeBuffer = ByteBuffer.wrap(message.getBytes());
        while (writeBuffer.hasRemaining()) {
            socketChannel.write(writeBuffer);
        }
        writeBuffer.clear();
    }

    /**
     * Legge dalla {@link SocketChannel} il prossimo messaggio.
     * Implementa un protocollo di comunicazione che prevede l'invio
     * della size prima del messaggio vero e proprio. Questo permette
     * un'allocazione del {@link ByteBuffer} di ricezione senza sprechi.
     *
     * @param socketChannel La {@link SocketChannel} dalla quale leggere il messaggio.
     * @return Il messaggio letto.
     * @throws IOException Lanciata in caso di errori durante la lettura dalla {@link SocketChannel}.
     */
    public static String readFromSocket(SocketChannel socketChannel) throws IOException {
        /*
            Le successive 4 righe si occupano di recuperare la lunghezza
            del messaggio da leggere. Si noti che allocare un ByteBuffer
            per ricevere un intero non porta a sprechi, grazie alla costante Java.
         */
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        socketChannel.read(sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        //Stampa di debug, nel caso in cui DEBUG_MODE sia impostato a true
        if (DEBUG_MODE) System.out.println("Size da leggere: " + size);
        /*
            Si procede alla ricezione del messaggio vero e proprio. Si
            noti che la size recuperata prima ci permette di allocare
            il ByteBuffer della corretta dimensione, evitando alcun spreco.
         */
        ByteBuffer readBuffer = ByteBuffer.allocate(size);
        //Stampa di debug, nel caso in cui DEBUG_MODE sia impostato a true
        if (DEBUG_MODE) System.out.println(readBuffer);
        socketChannel.read(readBuffer);
        readBuffer.flip();
        //Stampa di debug, nel caso in cui DEBUG_MODE sia impostato a true
        if (DEBUG_MODE) System.out.println(readBuffer);
        /*
            Passaggio da ByteBuffer a String. Importante l'utilizzo di trim() per evitare
            problemi "strutturali" della stringa.
         */
        String message = new String(readBuffer.array()).trim();
        //Stampa di debug, nel caso in cui DEBUG_MODE sia impostato a true
        if (DEBUG_MODE) System.out.println("Messaggio ricevuto: " + message);
        readBuffer.clear();
        return message;
    }

}
