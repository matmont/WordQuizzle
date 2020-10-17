package back_end_src;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link MatchManager} si occupa di implementare il meccanismo di un match tra due utenti.
 */
public class MatchManager implements Runnable {

    /*
        I seguenti indici servono, all'interno del selettore, per individuare in quale
        punto della sfida si trova un utente.
            -   Se l'indice è compreso tra 0 e il numero di parole da tradurre, allora sta ancora traducendo.
            -   Se l'indice è uguale al numero di parole da tradurre, allora ha appena finito di tradurre tutte
                le parole. Gli viene inviato il messaggio contenente le statistiche riguardo il suo match, invitandolo
                ad aspettare che l'avversario finisca o scada il timeout.
            -   Se l'indice è uguale al numero di parole da tradurre + 1, allora l'utente è pronto per essere staccato
                da questo selettore, dopo aver ricevuto il messaggio di fine match, con l'esito globale della partita
                (si passa a questi indici nel momento in cui entrambi i giocatori hanno concluso le traduzioni o scade
                il timeout)
            -   Se l'indice è uguale a -1, qualcosa è andato storto (non dovrebbe mai accadere);
            -   L'indice viene inizialmente messo a -2, per far capire al selettore di dover inviare il messaggio
                di inizio partita. ("Via alla sfida, avete tot secondi per tradurre tot parole...")

        Ho deciso, per questioni di leggibilità, di creare una costante per ogni indice, in modo da poter
        utilizzare dei nomi sensati, piuttosto che direttamente i numeri.
    */
    private static final int FIRST_QUESTION_INDEX = 0;
    private static final int ERROR_INDEX = -1;
    private static final int START_INDEX = -2;
    private static final int FINISHED_QUESTIONS_INDEX = Server.numberOfWords;
    private static final int END_INDEX = Server.numberOfWords + 1;

    private final String idA;
    private final String idB;
    private final SocketChannel TCPSocketA;
    private final SocketChannel TCPSocketB;
    private final String[] italianWords;
    private final ArrayList<ArrayList<String>> englishWords;

    private final ScoreStruct scoreStruct;
    private final int oldGlobalPointsA;
    private final int oldGlobalPointsB;

    int indexA, indexB;
    boolean receivedStatsA, receivedStatsB;
    boolean finishedA, finishedB;

    //Questo flag sarà la chiave del meccanismo della gestione di timeout.
    private volatile boolean timeout;

    public MatchManager(String idA, String idB, SocketChannel TCPSocketA, SocketChannel TCPSocketB,
                        String[] italianWords, ArrayList<ArrayList<String>> englishWords) throws RemoteException {
        this.idA = idA;
        this.idB = idB;
        this.TCPSocketA = TCPSocketA;
        this.TCPSocketB = TCPSocketB;
        this.italianWords = italianWords;
        this.englishWords = englishWords;

        this.scoreStruct = new ScoreStruct();

        this.oldGlobalPointsA = UsersRegister.getInstance().getPointOf(this.idA);
        this.oldGlobalPointsB = UsersRegister.getInstance().getPointOf(this.idB);

        this.indexA = 0;
        this.indexB = 0;
        this.receivedStatsA = false;
        this.receivedStatsB = false;
        this.finishedA = false;
        this.finishedB = false;

        this.timeout = false;
    }

    /**
     * Costruisce il messaggio, per l'utente 'id', che serve a notificare le statistiche della prestazione appena
     * avuta (traduzioni corrette, errate, non date). Si noti che i dati vengono recuperati dalla struttura di
     * supporto {@link ScoreStruct}.
     *
     * @param id L'id dell'utente al quale il messaggio deve riferire.
     * @return Il messaggio delle statistiche per 'id'.
     */
    private String statsMessage(String id) {
        String message = "";
        if (id.equals(this.idA)) {
            message = "Hai tradotto correttamente " +
                    this.scoreStruct.getGuessedA() + " parole, ne hai sbagliate " + this.scoreStruct.getWrongsA() +
                    " e non hai risposto a " +
                    this.scoreStruct.getNoneA() + ". Attendi...";

        } else if (id.equals(this.idB)) {
            message = "Hai tradotto correttamente " +
                    this.scoreStruct.getGuessedB() + " parole, ne hai sbagliate " + this.scoreStruct.getWrongsB() +
                    " e non hai risposto a " +
                    this.scoreStruct.getNoneB() + ". Attendi...";
        }
        return message;
    }

    /**
     * Costruisce il messaggio di fine sfida per l'utente 'id'. È con questo messaggio che l'utente scoprirà
     * di aver vinto, perso o pareggiato. Si noti che i dati vengono recuperati dalla struttura di supporto
     * {@link ScoreStruct}.
     *
     * @param id L'id dell'utente al quale il messaggio deve riferire.
     * @return Il messaggio dell'esito partita per 'id'.
     */
    private String outcomeMessage(String id) {
        String message;
        int selfPoints = -1, otherPoints = -1, newGlobalPoints = -1;
        if (id.equals(this.idA)) {
            selfPoints = this.scoreStruct.getPointsA();
            otherPoints = this.scoreStruct.getPointsB();
            newGlobalPoints = this.oldGlobalPointsA + Server.winPointsIncrement;
        } else if (id.equals(this.idB)) {
            selfPoints = this.scoreStruct.getPointsB();
            otherPoints = this.scoreStruct.getPointsA();
            newGlobalPoints = this.oldGlobalPointsB + Server.winPointsIncrement;
        } else {
            if (Server.DEBUG_MODE)
                System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> bad usage di 'sendOutcome'");
        }

        message = "Hai totalizzato " + selfPoints + " punti. Il tuo avversario ha totalizzato " + otherPoints + " punti.";

        if (selfPoints > otherPoints) {
            message = message + "\nCongratulazioni, hai vinto! Hai guadagnato " + Server.winPointsIncrement +
                    " punti extra, per un totale di " + newGlobalPoints + " punti.";
        } else if (selfPoints < otherPoints) {
            message = message + "\nPeccato, hai perso! La prossima volta andrà meglio.";
        } else {
            message = message + "\nPareggio! Bella partita.";
        }
        return message;
    }

    /**
     * Stampa un messaggio di debug, per notificare l'abbandono del match da parte di uno dei due giocatori.
     *
     * @param id L'id dell'utente che ha abbandonato.
     */
    private void quitMessage(String id) {
        if (id.equals(this.idA)) {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> il giocatore " + this.idA +
                    " non è più raggiungibile.");
        } else if (id.equals(this.idB)) {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> il giocatore " + this.idB +
                    " non è più raggiungibile.");
        } else {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> un giocatore ha abbandonato la partita.");
        }

    }

    /**
     * Stampa un messaggio di debug, per notificare la terminazione, per l'utente 'id', delle traduzioni.
     *
     * @param id L'id dell'utente che ha terminato le traduzioni.
     */
    private void finishQuestionsMessage(String id) {
        if (id.equals(this.idA)) {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> il giocatore " + this.idA +
                    " ha risposto a tutte le domande.");
        } else if (id.equals(this.idB)) {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> il giocatore " + this.idB +
                    " ha risposto a tutte le domande.");
        } else {
            System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> problemi durante 'finishQuestionsMessage' " +
                    "(what id?)");
        }
    }

    /**
     * Il metodo viene chiamato da RequestManager nel momento in cui scade il timeout della sfida.
     */
    @SuppressWarnings("unused")
    public void timeout() {
        this.timeout = true;
    }

    /**
     * Incapsula il procedimento di scrittura in una {@link SocketChannel}. Ho deciso di creare un metodo esterno per
     * migliorare la leggibilità del codice.
     *
     * @param message    Il messaggio da inviare.
     * @param currentKey La {@link SelectionKey} contenente la {@link SocketChannel} sul quale scrivere.
     * @return {@code true} se la scrittura è andata a buon fine (completata), {@code false} altrimenti.
     * @throws IOException In caso di problemi durante la scrittura sulla {@link SocketChannel}.
     */
    private boolean writeIntoSocket(String message, SelectionKey currentKey) throws IOException {

        SocketChannel socketChannel = (SocketChannel) currentKey.channel();

        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);

        sizeBuffer.putInt(message.getBytes().length);
        sizeBuffer.flip();
        socketChannel.write(sizeBuffer);
        if (!sizeBuffer.hasRemaining()) {
            sizeBuffer.clear();

            ByteBuffer writeBuffer = ByteBuffer.wrap(message.getBytes());
            socketChannel.write(writeBuffer);

            if (!writeBuffer.hasRemaining()) {
                writeBuffer.clear();
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {

        /*
            Inizia il match, d'ora in poi idA e idB non devono essere letti dal selettore del Server,
            ma solo da quello di MatchManager.
         */
        synchronized (Server.inGameUsers) {
            Server.inGameUsers.add(this.idA);
            Server.inGameUsers.add(this.idB);
        }

        Selector selector;
        try {
            selector = Selector.open();

            /*
                Si tenta di registrare i vari channel. Potrebbe capitare che proprio un istante prima di arrivare
                in questo punto i client abbandonino: ripetendo il try-catch diamo la possiblità all'altro utente di
                continuare comunque a giocare. In caso di abbandono procedo a settare i punti dell'utente uscito
                al minimo possibile - 1, così da esser certi che, qualunque sia la sua prestazione, l'altro utente sarà
                dichiarato vincitore della sfida.
             */

            try {
                this.TCPSocketA.register(selector, SelectionKey.OP_WRITE, START_INDEX);
            } catch (ClosedChannelException e) {
                if (Server.DEBUG_MODE) this.quitMessage(this.idA);
                this.receivedStatsA = true;
                this.finishedA = true;
                this.scoreStruct.setPointsA(-(Server.numberOfWords * Server.wrongTranslationDecrement + 1));
                synchronized (Server.inGameUsers) {
                    Server.inGameUsers.remove(this.idA);
                }
            }

            try {
                this.TCPSocketB.register(selector, SelectionKey.OP_WRITE, START_INDEX);
            } catch (ClosedChannelException e) {
                if (Server.DEBUG_MODE) this.quitMessage(this.idB);
                this.receivedStatsB = true;
                this.finishedB = true;
                this.scoreStruct.setPointsB(-(Server.numberOfWords * Server.wrongTranslationDecrement + 1));
                synchronized (Server.inGameUsers) {
                    Server.inGameUsers.remove(this.idB);
                }
            }

            /*
                Si continuerà a ciclare in attesa di channel pronti fino a quando entrambi i client non hanno finito.
                Lo scadere del timeout provvederà a settare questi due flag a true, così che il loop possa terminare.
             */

            while (!this.finishedA || !this.finishedB) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey currentKey = iterator.next();
                    iterator.remove();
                    try {
                        if (currentKey.isWritable()) {
                            SocketChannel socketChannel = (SocketChannel) currentKey.channel();
                            int index = (int) currentKey.attachment();
                            String message = "";
                            /*
                                È il selettore il primo a scrivere ai client. La prima cosa da fare però è controllare
                                il flag relativo al timeout: se questo è true (quindi il tempo è scaduto) e l'utente
                                attuale (index riferisce l'indice dell'utente sul channel corrente) non ha ancora finito
                                le domande, allora non dobbiamo inviare la prossima domanda, ma notificarlo dello
                                scadere del timeout.
                             */
                            if (this.timeout && index < FINISHED_QUESTIONS_INDEX) {
                                message = "Il tempo per la sfida è scaduto, l'ultima risposta data non è conteggiata.";
                                if (!Server.GUI_MODE && writeIntoSocket(message, currentKey)) {
                                    /*
                                        Inoltre, setto l'indice dell'utente in modo tale che risulti come se avesse
                                        finito le domande, perchè effettivamente così è! Le risposte non date non verranno
                                        conteggiate. Si noti che il giocatore viene registrato nuovamente con operazione
                                        WRITE, poichè non dovrà più inserire alcun input, al prossimo giro gli invieremo
                                        il messaggio di fine partita, con le sue statistiche.
                                     */
                                    socketChannel.register(selector, SelectionKey.OP_WRITE, FINISHED_QUESTIONS_INDEX);
                                } else if(Server.GUI_MODE) {
                                    socketChannel.register(selector, SelectionKey.OP_WRITE, FINISHED_QUESTIONS_INDEX);
                                }
                                continue;
                            }

                            /*
                                È in realta questo il caso in cui all'inizio si trovano entrambi i channel (controllare
                                prima il tempo serve solo per guadagnare qualche ms sul ritardo di notifica di timeout).
                                Si invia il messaggio di inizio partita, e si registra nuovamente come WRITE, poichè
                                al prossimo ciclo invieremo la prima domanda all'utente.
                             */
                            if (index == START_INDEX) {
                                if (socketChannel.equals(this.TCPSocketA)) {
                                    message = "Via alla sfida di traduzione, il tuo avversario è: " + this.idB + "\nAvete "
                                            + Server.matchDuration + " secondi per tradurre " + Server.numberOfWords + " parole";
                                } else if (socketChannel.equals(this.TCPSocketB)) {
                                    message = "Via alla sfida di traduzione, il tuo avversario è: " + this.idA + "\nAvete "
                                            + Server.matchDuration + " secondi per tradurre " + Server.numberOfWords + " parole";
                                }
                                if (writeIntoSocket(message, currentKey)) {
                                    socketChannel.register(selector, SelectionKey.OP_WRITE, FIRST_QUESTION_INDEX);
                                }
                                continue;
                            }

                            /*
                                Nel caso in cui l'utente associato al channel corrente abbia finito di rispondere
                                a tutte le domande (o sia scaduto il timeout). Si procede allora all'invio del messaggio
                                contenente le statistiche. Si noti inoltre l'utilizzo del flag 'receivedStats', utile
                                a tenere traccia di chi tra i due utenti ha già ricevuto questo messaggio (serve per
                                implementare un controllo in caso di timeout)
                             */
                            if (index == FINISHED_QUESTIONS_INDEX) {
                                if (socketChannel.equals(this.TCPSocketA)) {
                                    message = statsMessage(this.idA);
                                    if (!this.receivedStatsA && writeIntoSocket(message, currentKey)) {
                                        this.receivedStatsA = true;
                                        socketChannel.register(selector, SelectionKey.OP_WRITE, END_INDEX);
                                    }
                                    if (Server.DEBUG_MODE)
                                        finishQuestionsMessage(this.idA);
                                } else if (socketChannel.equals(this.TCPSocketB)) {
                                    message = statsMessage(this.idB);
                                    if (!this.receivedStatsB && writeIntoSocket(message, currentKey)) {
                                        this.receivedStatsB = true;
                                        socketChannel.register(selector, SelectionKey.OP_WRITE, END_INDEX);
                                    }
                                    if (Server.DEBUG_MODE)
                                        finishQuestionsMessage(this.idB);
                                }

                                continue;
                            }

                            /*
                                Arrivati a questo punto il client ha finito, deve solo aspettare il messaggio finale!
                                Ci sono però alcuni controlli da fare. Sicuramente, per ricevere il messaggio finale
                                bisogna essere all'indice 'END_INDEX', ma non basta! Per uscire definitavemente dal match
                                entrambi gli utenti devono aver finito (e per questo usiamo i due booleani che indicano
                                se entrambi hanno ricevuto il messaggio delle statistiche: se lo hanno ricevuto hanno
                                finito!) oppure deve essere scaduto il timeout (se un utente ha finito e l'altro no,
                                ma scade il timeout, l'utente che ha finito deve poter uscire dal match e continuare
                                a fare ciò che vuole; l'altro utente verrà notificato non appena proverà ad inviare la
                                prossima risposta).
                                Si noti il set dei flag 'finishedA' e 'finishedB' e soprattutto la rimozione
                                dalla struttura 'inGameUsers' dell'utente che esce dal gioco. In questo modo potrà
                                subito tornare a comunicare con il Server.
                             */
                            if (index == END_INDEX &&
                                    (this.timeout || (this.receivedStatsA && this.receivedStatsB))) {

                                if (Server.DEBUG_MODE) {
                                    System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> Invio il messaggio finale");
                                }

                                if (socketChannel.equals(this.TCPSocketA)) {
                                    message = outcomeMessage(this.idA);
                                    this.finishedA = true;

                                    synchronized (Server.inGameUsers) {
                                        Server.inGameUsers.remove(this.idA);
                                    }
                                } else if (socketChannel.equals(this.TCPSocketB)) {
                                    message = outcomeMessage(this.idB);
                                    this.finishedB = true;
                                    synchronized (Server.inGameUsers) {
                                        Server.inGameUsers.remove(this.idB);
                                    }
                                }
                                if (writeIntoSocket(message, currentKey)) {
                                    currentKey.cancel();
                                }
                                continue;
                            }

                            /*
                                La situazione più comune: l'utente sta giocando, continuiamo ad inviare la
                                domanda e spostare il channel sull'operazione READ, per ricevere la risposta.
                             */
                            if (index < FINISHED_QUESTIONS_INDEX && index != ERROR_INDEX) {
                                int currentIndex;
                                if (socketChannel.equals(this.TCPSocketA)) currentIndex = this.indexA;
                                else currentIndex = this.indexB;
                                message = "Challenge " + (currentIndex + 1) + "/" + Server.numberOfWords + ": "
                                        + this.italianWords[currentIndex];
                                if (writeIntoSocket(message, currentKey)) {
                                    ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                                    ByteBuffer readBuffer = ByteBuffer.allocate(Server.maxCommandLength);
                                    ByteBuffer[] buffers = {sizeBuffer, readBuffer};
                                    socketChannel.register(selector, SelectionKey.OP_READ, buffers);
                                }
                                continue;
                            }

                            /*
                                Qualcosa è andato storto. Non dovrebbe mai accadere, non preoccupiamocene.
                             */
                            if (index == ERROR_INDEX) {
                                if (Server.DEBUG_MODE)
                                    System.out.println("MATCH [" + this.idA + " | " + this.idB + "] --> index -1!.");
                            }

                        } else if (currentKey.isReadable()) {
                            SocketChannel socketChannel = (SocketChannel) currentKey.channel();
                            ByteBuffer[] buffers = (ByteBuffer[]) currentKey.attachment();

                            String response;

                            /*
                                Fase di lettura di un selettore. È importante utilizzare la 'size' del messaggio
                                per capire se è stato letto tutto. In caso affermativo bene, ma altrimenti bisogna
                                NON passare alla fase WRITE, ma rimanere in questa fase per poter finire di leggere
                                al prossimo ciclo.
                             */

                            socketChannel.read(buffers);
                            if (!buffers[0].hasRemaining()) {
                                buffers[0].flip();
                                int size = buffers[0].getInt();

                                if (buffers[1].position() == size) {
                                    buffers[1].flip();
                                    response = new String(buffers[1].array()).trim().toLowerCase();

                                    /*
                                        Prima di valutare la risposta data, controllo il timeout: se è scaduto,
                                        la risposta non dev'essere conteggiata.
                                     */
                                    if (!this.timeout) {
                                        if (socketChannel.equals(this.TCPSocketA)) {
                                            if (this.englishWords.get(this.indexA).contains(response)) {
                                                this.scoreStruct.incrementPointsA(Server.correctTranslationIncrement);
                                                this.scoreStruct.incrementGuessedA();
                                            } else {
                                                this.scoreStruct.decrementPointsA(Server.wrongTranslationDecrement);
                                                this.scoreStruct.incrementWrongsA();
                                            }
                                            this.scoreStruct.decrementNoneA();
                                        } else if (socketChannel.equals(this.TCPSocketB)) {
                                            if (this.englishWords.get(this.indexB).contains(response)) {
                                                this.scoreStruct.incrementPointsB(Server.correctTranslationIncrement);
                                                this.scoreStruct.incrementGuessedB();
                                            } else {
                                                this.scoreStruct.decrementPointsB(Server.wrongTranslationDecrement);
                                                this.scoreStruct.incrementWrongsB();
                                            }
                                            this.scoreStruct.decrementNoneB();
                                        }
                                    }

                                    //Se la lettura è andata a buon fine, registro il channel per la WRITE.
                                    int newIndex = ERROR_INDEX;
                                    if (socketChannel.equals(this.TCPSocketA)) {
                                        this.indexA++;
                                        newIndex = this.indexA;
                                    } else if (socketChannel.equals(this.TCPSocketB)) {
                                        this.indexB++;
                                        newIndex = this.indexB;
                                    }
                                    socketChannel.register(selector, SelectionKey.OP_WRITE, newIndex);

                                }
                            }
                        }

                    } catch (IOException e) {
                        /*
                            Gestione dell'abbandono di un client.
                         */
                        if (currentKey.channel() == this.TCPSocketA) {
                            this.receivedStatsA = true;
                            this.finishedA = true;
                            if (Server.DEBUG_MODE) this.quitMessage(this.idA);
                            //Setto i punti al minimo - 1, così che il client non possa vincere.
                            this.scoreStruct.setPointsA(-(Server.numberOfWords * Server.wrongTranslationDecrement + 1));
                            synchronized (Server.inGameUsers) {
                                Server.inGameUsers.remove(this.idA);
                            }
                        } else {
                            this.receivedStatsB = true;
                            this.finishedB = true;
                            if (Server.DEBUG_MODE) this.quitMessage(this.idB);
                            //Setto i punti al minimo - 1, così che il client non possa vincere.
                            this.scoreStruct.setPointsB(-(Server.numberOfWords * Server.wrongTranslationDecrement + 1));
                            synchronized (Server.inGameUsers) {
                                Server.inGameUsers.remove(this.idB);
                            }
                        }
                        currentKey.cancel();
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
            Recupero l'istanza unica di UsersRegister e aggiorno il
            punteggio totale del vincitore.
         */

        UsersRegister usersRegister = null;
        try {
            usersRegister = UsersRegister.getInstance();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if (usersRegister != null) {
            if (scoreStruct.getPointsA() > scoreStruct.getPointsB()) {
                usersRegister.incrementPointsOf(this.idA, Server.winPointsIncrement);
            } else if (scoreStruct.getPointsA() < scoreStruct.getPointsB()) {
                usersRegister.incrementPointsOf(this.idB, Server.winPointsIncrement);
            }
        }
    }

    @Override
    public String toString() {
        return "MatchManager{" +
                "idA='" + idA + '\'' +
                ", idB='" + idB + '\'' +
                '}';
    }
}