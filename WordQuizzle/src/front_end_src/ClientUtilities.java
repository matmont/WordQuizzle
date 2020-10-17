package front_end_src;

import front_end_src.exceptions.WrongNumberOfArgumentsException;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Classe di utilità per il {@link Client}. Contiene due metodi, il primo si occupa di stampare
 * a linea di comando il menù WordQuizzle. Il secondo è invece utilizzato dal Client per fare un controllo
 * dell'input inserito dall'utente.
 */
public class ClientUtilities {

    /**
     * Stampa su console il menù WordQuizzle. Il metodo è utilizzato dal {@link Client} a linea di comando
     * per permettere all'utente un'esperienza quanto più adatta e piacevole (nonostante i limiti che inevitabilmente
     * l'approcco CLI ha). Ho racchiuso questa serie di stampe in un metodo separato permigliorare la leggibilità
     * del {@link Client}.
     *
     * @param welcomePrint Booleano che serve ad indicare se la stampa richiesta è la prima
     *                     della sessione oppure no. Serve semplicemente per personalizzare la
     *                     prima stampa, aggiungendo un messaggio di benvenuto.
     * @param loggedIn Dal momento che praticamente tutte le operazioni non possono essere eseguite
     *                 se non dopo essersi loggato, nel caso in cui l'utente ancora non lo sia verrà aggiunta
     *                 una linea nel menù, la quale ha il compito di ricordare all'utente di eseguire il login.
     */
    public static void printMenu(boolean welcomePrint, boolean loggedIn) {
        //Non c'è molto da commentare; una serie di stampe predefinite.
        if (welcomePrint)
            System.out.println("----------------------------------------\nBENVENUTO IN WORD QUIZZLE!\n----------------------------------------");
        if (!loggedIn) System.out.println("\n--- REGISTRARSI/LOGGARSI PRIMA DI ESEGUIRE ALTRE OPERAZIONI! ---");
        if (loggedIn) System.out.println(); //motivi grafici
        System.out.println("--- USAGE: command [args ...] ---");
        System.out.println("registra_utente <nickUtente> <password> --- Registra un nuovo utente, con le credenziali indicate.");
        System.out.println("login <nickUtente> <password> --- Effettua il login.");
        System.out.println("logout  --- Effettua il logout.");
        System.out.println("aggiungi_amico <nickAmico> --- Aggiungi <nickAmico> alla tua lista amici.");
        System.out.println("lista_amici --- Visualizza la tua lista amici.");
        System.out.println("sfida <nickAmico> --- Richiedi di sfidare <nickAmico>.");
        System.out.println("mostra_punteggio --- Visualizza il tuo punteggio globale attuale.");
        System.out.println("mostra_classifica --- Mostra una classifica dei tuoi amici (incluso te stesso).");
        System.out.println("mostra_sfide --- Mostra le richieste di sfida attive.");
        System.out.println("esci --- Chiudi l'applicazione.\n");
    }

    /**
     * Controlla la correttezza dell'input inserito dall'utente. Ritorna l'input tokenizzato, così che il
     * client lo trovi "già pronto" per essere utilizzato in tutte le sue parti (comando ed eventuale destinatario
     * dell'operazione).
     *
     * @param input L'input inserito dall'utente tramite command line.
     * @return L'input tokenizzato, pronto per essere interpretato ("già spezzettato") dal {@link Client}.
     * @throws WrongNumberOfArgumentsException Nel caso in cui un comando sia stato utilizzato nel modo sbagliato.
     */
    public static ArrayList<String> checkCommandArguments(String input) throws WrongNumberOfArgumentsException {
        ArrayList<String> tokenizedInput = new ArrayList<>();

        //Utilizzo uno StringTokenizer per tokenizzare l'input.
        StringTokenizer tokenizer = new StringTokenizer(input, " ");
        while (tokenizer.hasMoreTokens()) {
            tokenizedInput.add(tokenizer.nextToken());
        }

        String command = tokenizedInput.get(0);

        //Faccio i vari controlli; in caso di errore lancio un'eccezione custom: WrongNumberOfArgumentsException.
        switch (command) {
            case "registra_utente":
            case "login":
                if (tokenizedInput.size() != 3) {
                    throw new WrongNumberOfArgumentsException();
                }
                break;
            case "aggiungi_amico":
            case "sfida":
                if (tokenizedInput.size() != 2) {
                    throw new WrongNumberOfArgumentsException();
                }
                break;
            case "mostra_sfide":
            case "mostra_punteggio":
            case "logout":
            case "lista_amici":
            case "mostra_classifica":
            case "esci":
                if (tokenizedInput.size() != 1) {
                    throw new WrongNumberOfArgumentsException();
                }
                break;
        }

        //Restituisco l'input tokenizzato.
        return tokenizedInput;
    }


}
