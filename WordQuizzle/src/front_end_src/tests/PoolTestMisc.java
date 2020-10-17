package front_end_src.tests;

import common_src.CommonUtilities;
import common_src.UsersRegisterInterface;
import common_src.exceptions.AlreadyRegisteredUserException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Struttura dati che serve a tenere traccia del numero di registrazioni andate a buon fine.
 * E' stato utilizzato per poter poi controllare che effettivamente, alla nuova apertura del Server,
 * tutti gli utenti registrati erano stati ben inseriti sia nella struttura sia nel file .json;
 */
class Counter {
    private int n;

    public Counter(int n) {
        this.n = n;
    }

    public void increment() {
        this.n++;
    }

    public int get() {
        return this.n;
    }
}

/**
 * {@link PoolTestMisc} serve a testare due aspetti: la registrazione e alcuni comandi di carattere "social"
 * che un utente può richiedere al {@link back_end_src.Server}.
 */
public class PoolTestMisc {

    //Quanti client vogliamo lanciare? Potenzialmente ne verranno lanciati due per ogni iterazione, quindi in realtà
    //se entrambi i flag TEST_REGISTRATION e TEST_MISC sono 'true', verranno lanciati NUM_CLIENT_TEST * 2 client;
    private static final int NUM_CLIENT_TEST = 500;
    //Quanti client sono registrati al servizio?
    private static final int NUM_CLIENT_REGISTERED = 1000;

    /*
        Settando TEST_REGISTRATION a true, verrà ad ogni iterazione lanciato un 'RegisterTest', ovvero un thread
        che simula un client che richiede una registrazione.

        Settando TEST_MISC a true, verrà ad ogni iterazione lanciato un 'RandomTests', ovvero un thread che simula
        un client che richiederà alcune richieste al Server, ad esempio: 'mostra_punteggio', 'lista_amici', ...
     */
    private static final boolean TEST_REGISTRATION = true;
    private static final boolean TEST_MISC = true;

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        Counter c;
        if (TEST_REGISTRATION) c = new Counter(0);
        //Il lancio dei "client simulati".
        for (int i = 0; i < NUM_CLIENT_TEST; i++) {
            if (TEST_REGISTRATION) {
                RegisterTest r = new RegisterTest((int) (Math.random()*4000), c);
                executor.execute(r);
                Thread.sleep(10);
            }
            if (TEST_MISC) {
                RandomTests ra = new RandomTests(i);
                executor.execute(ra);
                Thread.sleep(10);
            }
        }
        executor.shutdown();
        //Aspetto che tutti i thread abbiano finito sopra
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (TEST_REGISTRATION) System.out.println("NUOVE REGISTRAZIONI: " + c.get());
    }

    /**
     * {@link RegisterTest} simula il comportamento di un {@link front_end_src.Client} che richiede una registrazione
     * al {@link back_end_src.Server}.
     */
    public static class RegisterTest implements Runnable {
        private final int counter;
        private final Counter c;

        public RegisterTest(int counter, Counter c) {
            this.counter = counter;
            this.c = c;
        }

        @Override
        public void run() {
            Registry r;
            Remote usersRegisterRemote;
            try {
                //Il codice è lo stesso del programma 'Client'.
                r = LocateRegistry.getRegistry(30000);
                usersRegisterRemote = r.lookup("USERS-REGISTER-SERVER");
                UsersRegisterInterface usersRegister = (UsersRegisterInterface) usersRegisterRemote;
                usersRegister.registerNewUser("id" + this.counter, "psw" + this.counter);
                System.out.println("Registrazione avvenuta con successo!\n");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //Incremento il numero di registrazioni andate a buon fine.
                synchronized (this.c) {
                    c.increment();
                }
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            } catch (AlreadyRegisteredUserException e) {
                System.out.println("*** ERRORE: il nickname richiesto è già registrato. ***\n");
            } catch (NullPointerException | IndexOutOfBoundsException e) {
                //Nel caso in cui qualche parametro non venga inserito
                System.out.println("*** ERRORE: assicurati di avere inserito entrambi i campi. (Registration) ***\n");
            }
        }
    }

    /**
     * {@link RandomTests} simula il comportamento di un {@link front_end_src.Client} che richiede alcuni comandi
     * di carattere 'sociale' al {@link back_end_src.Server}.
     */
    public static class RandomTests implements Runnable {
        private boolean loggedIn;
        private int id;
        private SocketChannel socket;
        private final int counter;

        public RandomTests(int counter) {
            this.counter = counter;
            this.loggedIn = false;
            this.id = this.counter;
            this.socket = null;
            try {
                this.socket = SocketChannel.open();
                this.socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), 8000));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            try {
                Thread.sleep((long) (Math.random() * 4000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //I comandi testabili.
            String[] commands = {"aggiungi_amico", "lista_amici", "mostra_punteggio", "mostra_classifica"};
            //Ogni "client simulato" invierà 3 comandi al 'Server'.
            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep((long) (Math.random() * 100));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String command;
                String id;
                String psw;
                String otherId;
                //Scelta casuale di un comando
                command = commands[(int) (Math.random() * commands.length)];

                id = "id" + this.id;
                otherId = "id" + ((int) (Math.random() * NUM_CLIENT_REGISTERED));
                psw = "psw" + this.id;

                try {
                    String input = command + " " + id + " " + psw;
                    //Se non ha ancora fatto il login, prima si fa il login.
                    if (!this.loggedIn) {
                        input = "login " + id + " " + psw;
                    } else {
                        //Se il comando è 'aggiungi_amico' dobbiamo un attimo sistemare la sintassi.
                        if (command.equals("aggiungi_amico")) input = command + " " + otherId;
                    }
                    System.out.println(this.counter + " - Comando richiesto: " + input);
                    CommonUtilities.writeIntoSocket(input + " " + this.counter, this.socket);
                    String response = CommonUtilities.readFromSocket(this.socket);
                    if (response.equals("Login effettuato con successo.")) this.loggedIn = true;
                    if (response.equals("L'utente è già loggato.")) this.id = (int) (Math.random() * 10);
                    System.out.println(this.counter + " - Risposta: " + response);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
