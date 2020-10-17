package front_end_src.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import common_src.CommonUtilities;
import common_src.UsersRegisterInterface;
import common_src.exceptions.AlreadyRegisteredUserException;
import front_end_src.gui.custom_components.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Il client WordQuizzle, versione a interfaccia grafica.
 */
public class ClientGUI {

    //Alcuni flag di ausilio all'esecuzione del ClientGUI.

    private static boolean LOGGED_IN = false;
    private static boolean IN_GAME = false;
    static volatile boolean TIMEOUT = false;
    static volatile boolean FINISHED_QUESTIONS = false;
    private static String ID_ONLINE = null;
    private static int POINTS_ONLINE = -1;

    private static final boolean DEBUG_MODE = false;

    @SuppressWarnings("FieldCanBeLocal")
    private static int tcpPort;
    @SuppressWarnings("FieldCanBeLocal")
    private static int rmiPort;
    @SuppressWarnings("FieldCanBeLocal")

    static SocketChannel TCPSocket = null;
    private static final Gson gson = new Gson();

    //I principali componenti grafici.
    private static JFrame window = null;
    private static JPanel loginPanel = null;
    private static JPanel homePanel = null;
    private static JPanel gamePanel = null;

    @SuppressWarnings("FieldCanBeLocal")
    private static JPanel errorPanel = null;

    //I modelli che verranno poi associati alla lista di amicizie e alla lista delle sfide.
    //Dichiararli qui mi permette di poterli passare ad 'UDPReceiverGUI', il quale li aggiornerà
    //per avere aggiornamenti in tempo reale sulle rispettive liste.
    private static final DefaultListModel<String> friendsListModel = new DefaultListModel<>();
    private static final DefaultListModel<String> challengesListModel = new DefaultListModel<>();

    private static final InputField wordInput = null;
    private static final JButton sendButton = null;

    static ExecutorService executorService = Executors.newSingleThreadExecutor();
    static UDPReceiverGUI receiver;

    static final HashMap<String, String[]> requests = new HashMap<>();

    static DatagramSocket UDPSocket;

    /**
     * Il compito del main è sostanzialmente quello di instaurare la connesione con il server e
     * visualizzare la prima pagina: la pagina di login/registrazione.
     */
    public static void main(String[] args) throws IOException {

        window = new JFrame("Word Quizzle");
        window.setSize(800, 600);
        window.setLocation(200, 200);

        /*
            Recupero i settings dal file config.properties
         */
        try {
            Properties properties = new Properties();
            FileChannel fileChannel = FileChannel.open(Paths.get("./src/common_src/config.properties"), StandardOpenOption.READ);
            properties.load(Channels.newInputStream(fileChannel));

            //--//
            tcpPort = Integer.parseInt(properties.getProperty("server_port"));
            rmiPort = Integer.parseInt(properties.getProperty("rmi_port"));
            //--//

            fileChannel.close();
            properties.clear();

            //Connessione al server
            TCPSocket = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort));
            String TCPPort = TCPSocket.getLocalAddress().toString().split(":")[1];
            UDPSocket = new DatagramSocket(Integer.parseInt(TCPPort));

            receiver = new UDPReceiverGUI(UDPSocket, requests, challengesListModel, friendsListModel, gamePanel,
                    wordInput, sendButton);
            executorService.execute(receiver);

            if (ClientGUI.DEBUG_MODE) System.out.println("Connected to the server!");

            //-------------------------------//

            //La prima pagina da visualizzare sarà la pagina di login/registrazione.
            loginPanel = loginPage();
            loginPanel.setVisible(true);
            window.setContentPane(loginPanel);

            //Modifico il comportamento del programma in caso di chiusura forzata della finestra.
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e);
                    if (!IN_GAME) {
                        try {
                            TCPSocket.close();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                    //Terminazione del thread receiver UDP
                    receiver.stopRun();
                    executorService.shutdown();
                    if (ClientGUI.DEBUG_MODE) System.out.println("Terminato correttamente!");
                }
            });

        } catch (IOException e) {

            System.out.println("Il server WQ non è attualmente raggiungibile, riprovare più tardi.");
            if (TCPSocket != null) TCPSocket.close();

            //Terminazione del thread receiver UDP
            executorService.shutdown();

            errorPanel = errorPage();
            errorPanel.setVisible(true);
            window.setContentPane(errorPanel);

        }

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setVisible(true);
    }

    /**
     * Si occupa del setup e della creazione della pagina di login/registrazione.
     *
     * @return Il {@link JPanel} "contenente" la pagina di login/registrazione.
     */
    private static JPanel loginPage() {
        JPanel loginPanel = new JPanel();


        loginPanel.setPreferredSize(new Dimension(window.getWidth(), window.getHeight()));

        loginPanel.setLayout(new GridBagLayout());

        //Lo useremo per posizionare i vari oggetti sul 'loginPanel'
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(30, 0, 0, 0);

        //Creazione ed aggiunta al panel del titolo.
        TitleText title = TitleText.newTitleText("Word Quizzle")
                .center()
                .fontSize(90);
        title.setOpaque(true);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1;
        gridBagConstraints.gridheight = 1;

        loginPanel.add(title, gridBagConstraints);

        /*
            Creo il form per fare la login o la registrazione.
         */
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1;

        loginPanel.add(formPanel, gridBagConstraints);

        //Creazione ed aggiunta al panel della label per l'esito di login/registrazione
        NormalText outcomeText = NormalText.newText("")
                .center()
                .boldText();
        outcomeText.setText("");
        outcomeText.setForeground(Color.RED);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;

        outcomeText.setVisible(false);

        loginPanel.add(outcomeText, gridBagConstraints);

        GridBagConstraints formPanelGridBagConstraints = new GridBagConstraints();
        formPanelGridBagConstraints.fill = GridBagConstraints.HORIZONTAL;

        //Creazione ed aggiunta al panel della label dell'InputField per l'username.
        NormalText usernameLabel = NormalText.newText("Username");

        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 1;
        formPanelGridBagConstraints.insets = new Insets(1, 1, 1, 1);

        formPanel.add(usernameLabel, formPanelGridBagConstraints);

        //Creazione ed aggiunta al panel dell'InputField per l'username.
        InputField usernameField = InputField.newInputField("Insert your username here")
                .setPadding(10);

        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 2;
        formPanelGridBagConstraints.insets = new Insets(10, 10, 10, 10);

        formPanel.add(usernameField, formPanelGridBagConstraints);

        //Creazione ed aggiunta al panel della label dell'InputField per la password.
        NormalText passwordLabel = NormalText.newText("Password");

        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 3;
        formPanelGridBagConstraints.insets = new Insets(1, 1, 1, 1);

        formPanel.add(passwordLabel, formPanelGridBagConstraints);

        //Creazione ed aggiunta al panel dell'InputField per la password.
        InputField passwordField = InputField.newInputField("Insert your password here")
                .setPadding(10);

        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 4;
        formPanelGridBagConstraints.insets = new Insets(10, 10, 10, 10);

        formPanel.add(passwordField, formPanelGridBagConstraints);

        //Creazione ed aggiunta al panel del bottone per il login.
        JButton jButton = new JButton("Login");
        jButton.addActionListener(new LoginListener(usernameField, passwordField, outcomeText));
        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 5;

        formPanel.add(jButton, formPanelGridBagConstraints);

        //Creazione ed aggiunta al panel del bottone per la registrazione.
        JButton registerButton = new JButton("Register");
        registerButton.addActionListener(new RegisterListener(usernameField, passwordField, outcomeText));

        formPanelGridBagConstraints.gridx = 0;
        formPanelGridBagConstraints.gridy = 6;

        formPanel.add(registerButton, formPanelGridBagConstraints);

        return loginPanel;
    }

    /**
     * Si occupa del setup e della creazione della pagina di errore. Questa
     * pagina è visualizzata nel caso in cui il {@link back_end_src.Server} non
     * sia raggiungibile.
     *
     * @return Il {@link JPanel} "contenente" la pagina di errore.
     */
    private static JPanel errorPage() {
        JPanel errorPanel = new JPanel();

        errorPanel.setPreferredSize(new Dimension(window.getWidth(), window.getHeight()));

        errorPanel.setLayout(new GridBagLayout());

        //Lo useremo per posizionare i vari oggetti sul 'errorPanel'
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(20, 20, 20, 20);

        //Creazione ed aggiunta al panel del titolo.
        TitleText errorText = TitleText.newTitleText("Il Server WQ non è disponibile...")
                .center()
                .fontSize(40);

        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;

        errorPanel.add(errorText, gridBagConstraints);

        //Creazione ed aggiunta al panel del sottotitolo.
        TitleText suberrorText = TitleText.newTitleText("Riprovare più tardi")
                .center()
                .fontSize(30);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;

        errorPanel.add(suberrorText, gridBagConstraints);

        return errorPanel;
    }

    /**
     * Si occupa del setup e della creazione della pagina principale. Si arriva
     * in questa pagina dopo aver fatto il login.
     *
     * @return Il {@link JPanel} "contenente" la pagina principale.
     * @throws IOException In caso di problemi durante la richiesta di lista amici o classifica.
     */
    private static JPanel homePage() throws IOException {

        JPanel homePanel = new JPanel();

        homePanel.setPreferredSize(new Dimension(window.getWidth(), window.getHeight()));
        homePanel.setLayout(new GridBagLayout());

        //Lo useremo per posizionare i vari oggetti su 'homePage'
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(20, 20, 1, 20);

        //Creazione ed aggiunta al panel del titolo.
        TitleText titleText = TitleText.newTitleText("Benvenuto, " + ID_ONLINE)
                .center()
                .fontSize(35);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridheight = 1;

        homePanel.add(titleText, gridBagConstraints);

        //Creazione ed aggiunta al panel del bottone per il logout.
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(new LogoutListener());

        gridBagConstraints.fill = GridBagConstraints.CENTER;
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridheight = 2;

        homePanel.add(logoutButton, gridBagConstraints);

        //Creazione ed aggiunta al panel del testo contenente il punteggio totale dell'utente.
        NormalText pointsText = NormalText.newText("Il tuo punteggio: " + POINTS_ONLINE)
                .center()
                .boldText();

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridheight = 1;
        gridBagConstraints.insets = new Insets(20, 20, 50, 20);

        homePanel.add(pointsText, gridBagConstraints);

        /* FRIEND LIST AREA */

        //Creazione ed aggiunta al panel della label associata alla lista degli amici dell'utente.
        NormalText friendsListLabel = NormalText.newText("I tuoi amici")
                .boldText()
                .center();

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(1, 1, 1, 1);

        homePanel.add(friendsListLabel, gridBagConstraints);

        //Creazione ed aggiunta al panel del panel contenente l'oggetto JList (questo è necessario per poter scrollare).
        JPanel friendsListPanel = new JPanel(new BorderLayout());

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(10, 10, 10, 10);

        homePanel.add(friendsListPanel, gridBagConstraints);

        //Viene recuperata la lista degli amici ed aggiornato il modello associato alla lista.
        synchronized (friendsListModel) {
            friendsListModel.clear();
            friendsListModel.addAll(queryFriends());
        }
        //Creazione ed aggiunta al panel della lista vera e propria degli amici.
        ListField friendsListArea = ListField.newListField(friendsListModel)
                .setPaddings(10);
        friendsListArea.setToolTipText("Doppio click su un amico per sfidarlo!");
        friendsListArea.setLayoutOrientation(JList.VERTICAL);
        friendsListArea.addMouseListener(new RequestChallengeListener());

        //Creazione ed aggiunta al panel del pane scrollable per scrollare la lista degli amici.
        JScrollPane friendsListScroll = new JScrollPane();
        friendsListScroll.setMinimumSize(new Dimension(100, 100));
        friendsListScroll.setPreferredSize(new Dimension(200, 250));
        friendsListScroll.setViewportView(friendsListArea);
        friendsListScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        friendsListScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        friendsListPanel.add(friendsListScroll);

        friendsListPanel.setVisible(true);

        //Creazione ed aggiunta al panel del bottone per l'aggiunta di un nuovo amico.
        JButton addFriendButton = new JButton("Aggiungi amico");
        addFriendButton.addActionListener(new AddFriendListener());
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(4, 4, 4, 4);

        homePanel.add(addFriendButton, gridBagConstraints);

        /* RANK AREA */

        //Creazione ed aggiunta al panel della label associata alla classifica degli amici dell'utente.
        NormalText rankLabel = NormalText.newText("La classifica dei tuoi amici")
                .boldText()
                .center();

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(1, 1, 1, 1);

        homePanel.add(rankLabel, gridBagConstraints);

        //Viene recuperata la classifica degli amici ed aggiornato il modello associato alla lista.
        DefaultListModel<String> rankListModel = new DefaultListModel<>();
        rankListModel.addAll(queryRank());

        //Creazione ed aggiunta al panel del panel contenente l'oggetto JList (questo è necessario per poter scrollare).
        JPanel rankPanel = new JPanel(new BorderLayout());

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(10, 10, 10, 10);

        homePanel.add(rankPanel, gridBagConstraints);

        //Creazione ed aggiunta al panel della lista vera e propria contenente la classifica degli amici.
        ListField rankArea = ListField.newListField(rankListModel)
                .setPaddings(10);
        rankArea.setLayoutOrientation(JList.VERTICAL);

        //Creazione ed aggiunta al panel del pane scrollable per scrollare la classifica degli amici.
        JScrollPane rankScroll = new JScrollPane();
        rankScroll.setMinimumSize(new Dimension(100, 100));
        rankScroll.setPreferredSize(new Dimension(200, 250));
        rankScroll.setViewportView(rankArea);
        rankScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        rankScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        rankPanel.add(rankScroll);

        rankPanel.setVisible(true);

        //Creazione ed aggiunta al panel del bottone per richiedere l'aggiornamento della classifica.
        JButton updateRank = new JButton("Aggiorna classifica");
        updateRank.addActionListener(new UpdateRankListener(rankListModel));

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(4, 4, 4, 4);

        homePanel.add(updateRank, gridBagConstraints);

        /* CHALLENGES AREA */

        //Creazione ed aggiunta al panel della label associata alla lista di richieste di sfide dell'utente.
        NormalText challengesLabel = NormalText.newText("Le tue richieste di sfida")
                .boldText()
                .center();

        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(1, 1, 1, 1);

        homePanel.add(challengesLabel, gridBagConstraints);

        //Creazione ed aggiunta al panel della label associata alla classifica degli amici dell'utente.
        JPanel challengesPanel = new JPanel(new BorderLayout());

        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(10, 10, 10, 10);

        homePanel.add(challengesPanel, gridBagConstraints);

        //Creazione ed aggiunta al panel della lista vera e propria contenente le richieste di sfida ricevute dall'utente.
        ListField challengesArea = ListField.newListField(challengesListModel)
                .setPaddings(10);
        challengesArea.setToolTipText("Doppio click su una richiesta per accettarla!");
        challengesArea.setLayoutOrientation(JList.VERTICAL);
        challengesArea.addMouseListener(new AcceptChallengeListener());

        //Creazione ed aggiunta al panel del pane scrollable per scrollare la lista di richieste di sfida.
        JScrollPane challengesScroll = new JScrollPane();
        challengesScroll.setMinimumSize(new Dimension(100, 100));
        challengesScroll.setPreferredSize(new Dimension(200, 250));
        challengesScroll.setViewportView(challengesArea);
        challengesScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        challengesScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        challengesPanel.add(challengesScroll);

        challengesPanel.setVisible(true);

        //Creazione ed aggiunta al panel del testo 'hint': indica come agire, ovvero dice di fare il doppio click
        //sugli elementi delle varie liste per interagire.
        NormalText hintMatchText = NormalText.newText("Doppio click su un tuo amico/sfida per sfidarlo/accettarla!")
                .boldText()
                .center();

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new Insets(20, 10, 10, 10);

        homePanel.add(hintMatchText, gridBagConstraints);

        return homePanel;
    }

    /**
     * Si occupa del setup e della creazione della pagina di gioco..
     *
     * @return Il {@link JPanel} "contenente" la pagina di gioco.
     */
    private static JPanel gamePage() {

        IN_GAME = true;
        TIMEOUT = false;
        FINISHED_QUESTIONS = false;

        JPanel gamePanel = new JPanel();

        gamePanel.setPreferredSize(new Dimension(window.getWidth(), window.getHeight()));
        gamePanel.setLayout(new GridBagLayout());

        //Lo useremo per posizionare i vari oggetti sul 'homePage'
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(20, 20, 1, 20);

        //Creazione ed aggiunta al panel del titolo.
        TitleText wordText = TitleText.newTitleText("Game title...")
                .center();
        gridBagConstraints.gridy = 0;
        gamePanel.add(wordText, gridBagConstraints);

        //Creazione ed aggiunta al panel dell'InputField per la parola tradotta che l'utente dovrà inserire.
        InputField wordInput = InputField.newInputField("Inserisci qui la tua traduzione.").setPadding(10);
        wordInput.setColumns(10);
        gridBagConstraints.gridy = 1;
        gamePanel.add(wordInput, gridBagConstraints);

        //Creazione ed aggiunta al panel del bottone di invio traduzione.
        JButton sendButton = new JButton("Invia");
        sendButton.addActionListener(new SendListener(wordInput));
        gridBagConstraints.gridy = 2;
        gamePanel.add(sendButton, gridBagConstraints);

        //Creazione ed aggiunta al panel del bottone di fine game, per tornare alla home. Inizialmente NON
        //sarà visibile, diventandolo successivamente, quando la sfida terminerà.
        JButton endButton = new JButton("Torna alla home");
        endButton.addActionListener(new ExitGame());
        gridBagConstraints.gridy = 1;
        endButton.setVisible(false);
        gamePanel.add(endButton, gridBagConstraints);

        /*
            Lo SwingWorker che si occupa di realizzare la logica del game. Abbiamo in background un thread che comunica
            con il Server, mentre il thread UI aggiorna in maniera consistente e coerente l'interfaccia grafica.
         */
        SwingWorker<Void, Void> matchWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (true) {
                    StringBuilder message = new StringBuilder(CommonUtilities.readFromSocket(TCPSocket));
                    System.out.println(message);
                    String finalMessage = message.toString();
                    SwingUtilities.invokeAndWait(() -> wordText.setText(finalMessage));
                    if (message.toString().contains("Attendi...")) {
                        FINISHED_QUESTIONS = true;
                        SwingUtilities.invokeAndWait(() -> {
                            wordText.fontSize(15);
                            wordInput.setVisible(false);
                            gamePanel.remove(wordInput);
                            sendButton.setVisible(false);
                            gamePanel.remove(sendButton);
                        });
                        message = new StringBuilder(CommonUtilities.readFromSocket(TCPSocket));
                        System.out.println(message);
                        message.insert(0, finalMessage);
                        String[] tokenized = message.toString().split("\\.");
                        message = new StringBuilder("<html>");
                        for (String s : tokenized) {
                            if (!s.contains("Attendi") && !s.equals("")) message.append(s).append("<br>");
                        }
                        message.append("</html>");
                        String finalMessage1 = message.toString();
                        SwingUtilities.invokeAndWait(() -> wordText.setText(finalMessage1));
                        System.out.println("STO QUA!");
                        return null;
                    }
                }
            }

            @Override
            protected void done() {
                if (ClientGUI.DEBUG_MODE) System.out.println("Game end!");
                endButton.setVisible(true);

                super.done();
            }
        };
        matchWorker.execute();

        return gamePanel;
    }

    /*
        Listener dei vari bottoni/mouse.
     */

    /**
     * {@link ActionListener} per il bottone di login.
     */
    private static class LoginListener implements ActionListener {

        private final InputField usernameField;
        private final InputField passwordField;
        private final NormalText outcomeText;

        public LoginListener(InputField usernameField,
                             InputField passwordField,
                             NormalText outcomeText) {

            this.usernameField = usernameField;
            this.passwordField = passwordField;
            this.outcomeText = outcomeText;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {

            //Reset outcome text
            outcomeText.setText("");
            outcomeText.setVisible(false);
            outcomeText.setForeground(Color.RED);

            //Si recuperano i dati immessi e si cerca di fare il login.

            String id = usernameField.getText();
            String password = passwordField.getText();
            if (ClientGUI.DEBUG_MODE) {
                System.out.println("id: " + id + " --- psw: " + password);
            }
            String response;
            //Alcuni controlli basilari.
            if (id.equals("") || password.equals("")) {
                if (ClientGUI.DEBUG_MODE) System.out.println("Bad id or password");
                outcomeText.setText("Inserisci entrambi i campi!");
                outcomeText.setVisible(true);
                usernameField.setText("");
                passwordField.setText("");
                return;
            }
            try {
                CommonUtilities.writeIntoSocket("login " + id + " " + password, TCPSocket);
                response = CommonUtilities.readFromSocket(TCPSocket);
                if (response.equals("Login effettuato con successo.")) {
                    //Nel caso in cui il login vada a buon fine, si passa alla schermata 'home'.
                    LOGGED_IN = true;
                    ID_ONLINE = id;
                    POINTS_ONLINE = queryPoints();
                    loginPanel.setVisible(false);
                    loginPanel = null;
                    homePanel = homePage();
                    window.setContentPane(homePanel);
                    homePanel.setVisible(true);
                } else {
                    //Nel caso in cui il login non vada a buon fine, si stampa la risposta ricevuta dal server
                    //e si "puliscono" i campi inseriti.
                    if (ClientGUI.DEBUG_MODE) System.out.println(response);
                    outcomeText.setText(response);
                    outcomeText.setVisible(true);
                    usernameField.setText("");
                    passwordField.setText("");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@link ActionListener} per il bottone di registrazione.
     */
    private static class RegisterListener implements ActionListener {
        private final InputField usernameField;
        private final InputField passwordField;
        private final NormalText outcomeText;

        public RegisterListener(InputField usernameField, InputField passwordField, NormalText outcomeText) {
            this.usernameField = usernameField;
            this.passwordField = passwordField;
            this.outcomeText = outcomeText;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {

            //Reset outcome text
            outcomeText.setText("");
            outcomeText.setVisible(false);
            outcomeText.setForeground(Color.RED);

            //Si recuperano i dati immessi e si cerca di fare la registrazione.

            String id = usernameField.getText();
            String password = passwordField.getText();
            if (ClientGUI.DEBUG_MODE) {
                System.out.println("id: " + id + " --- psw: " + password);
            }
            //Alcuni controlli basilari
            if (id.equals("") || password.equals("")) {
                if (ClientGUI.DEBUG_MODE) System.out.println("Bad id or password");
                outcomeText.setText("Inserisci entrambi i campi!");
                outcomeText.setVisible(true);
                usernameField.setText("");
                passwordField.setText("");
                return;
            }
            try {
                Registry r = LocateRegistry.getRegistry(rmiPort);
                Remote usersRegisterRemote = r.lookup("USERS-REGISTER-SERVER");
                UsersRegisterInterface usersRegister = (UsersRegisterInterface) usersRegisterRemote;
                usersRegister.registerNewUser(id, password);
                if (ClientGUI.DEBUG_MODE) System.out.println("Registrazione avvenuta con successo.\n");
                outcomeText.setText("Registrazione avvenuta con successo!");
                outcomeText.setVisible(true);
                outcomeText.setForeground(Color.GREEN);
                //Nel caso in cui la registrazione sia andata a buon fine vengono liberati i
                //due campi, così da poter fare la login.
                usernameField.setText("");
                passwordField.setText("");
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            } catch (AlreadyRegisteredUserException e) {
                //Nel caso in cui si richieda la registrazione di un id già presente.
                if (ClientGUI.DEBUG_MODE) System.out.println("Il nickname richiesto è già registrato.\n");
                outcomeText.setText("Il nickname richiesto è già registrato.");
                outcomeText.setVisible(true);
                usernameField.setText("");
                passwordField.setText("");
            } catch (NullPointerException | IndexOutOfBoundsException e) {
                //Nel caso in cui qualcuno dei due dati non venga inserito
                if (ClientGUI.DEBUG_MODE) System.out.println("Assicurati di avere inserito entrambi i campi.\n");
                outcomeText.setText("Inserisci entrambi i campi!");
                outcomeText.setVisible(true);
            }
        }
    }

    /**
     * {@link ActionListener} per il bottone di richiesta di una nuova amicizia.
     */
    private static class AddFriendListener implements ActionListener {

        public AddFriendListener() {
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String otherId;
            /*
            otherId = JOptionPane.showInputDialog(mainPanel,
                    "Inserisci l'id da aggiungere", null);*/
            otherId = (String) JOptionPane.showInputDialog(homePanel, "Inserisci l'id da aggiungere",
                    "Aggiungi amico", JOptionPane.QUESTION_MESSAGE, null, null, null);
            if (otherId == null) {
                return;
            }
            try {
                CommonUtilities.writeIntoSocket("aggiungi_amico " + otherId, TCPSocket);

                String response = CommonUtilities.readFromSocket(TCPSocket);
                if (response.equals("L'amicizia è stata aggiunta con successo.")) {
                    synchronized (friendsListModel) {
                        /*
                        friendsListModel.clear();
                        friendsListModel.addAll(queryFriends());
                        */
                        friendsListModel.addElement(otherId);
                    }

                } else {
                    JOptionPane.showMessageDialog(homePanel, response,
                            "Nuova amicizia", JOptionPane.ERROR_MESSAGE, null);
                }
                if (ClientGUI.DEBUG_MODE) System.out.println(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@link ActionListener} per il bottone di richiesta della "nuova" classifica (aggiornata).
     */
    private static class UpdateRankListener implements ActionListener {
        private final DefaultListModel<String> listModel;

        public UpdateRankListener(DefaultListModel<String> listModel) {
            this.listModel = listModel;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            listModel.clear();
            try {
                listModel.addAll(queryRank());
                if (ClientGUI.DEBUG_MODE) System.out.println(queryRank().toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * {@link ActionListener} per il bottone di logout.
     */
    private static class LogoutListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent actionEvent) {

            if (LOGGED_IN) {

                try {
                    CommonUtilities.writeIntoSocket("logout", TCPSocket);

                    String response = CommonUtilities.readFromSocket(TCPSocket);
                    if (ClientGUI.DEBUG_MODE) System.out.println(response);

                    if (response.equals("Logout effettuato con successo.")) {
                        LOGGED_IN = false;
                        ID_ONLINE = null;
                        POINTS_ONLINE = -1;

                        loginPanel = loginPage();
                        loginPanel.setVisible(true);
                        homePanel.setVisible(false);
                        homePanel = null;
                        window.setContentPane(loginPanel);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * {@link MouseListener} per la lista contenente le amicizie di un utente.
     * Con un doppio click su di un amico, partirà una richiesta di sfida verso esso.
     */
    private static class RequestChallengeListener implements MouseListener {

        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
            ListField list = (ListField) mouseEvent.getSource();
            if (mouseEvent.getClickCount() == 2) {

                int index = list.locationToIndex(mouseEvent.getPoint());
                String id;
                synchronized (friendsListModel) {
                    id = friendsListModel.get(index);
                }

                if (ClientGUI.DEBUG_MODE) synchronized (friendsListModel) {
                    System.out.println("Clicked " + friendsListModel.get(index));
                }

                JDialog waitingDialog = new JDialog(window, "Attendi l'avversario...",
                        Dialog.ModalityType.APPLICATION_MODAL);

                SwingWorker<Integer, Void> requestChallenge = new SwingWorker<>() {
                    private final int ACCEPTED = 0;
                    private final int REFUSED = 1;
                    private final int API_ERROR = 2;
                    private final int NOT_ONLINE = 3;

                    @Override
                    protected Integer doInBackground() throws Exception {
                        CommonUtilities.writeIntoSocket("sfida " + id, TCPSocket);
                        //Leggo il responso della richiesta da parte del server
                        String response = CommonUtilities.readFromSocket(TCPSocket);
                        if (ClientGUI.DEBUG_MODE) System.out.println(response);
                        if (response.contains("inviata")) { //Se la sfida viene inoltrata
                            //Leggo il responso della richiesta da parte dell'altro client
                            response = CommonUtilities.readFromSocket(TCPSocket);
                            if (ClientGUI.DEBUG_MODE) System.out.println(response); //Via alla sfida di traduz
                            if (response.equals("La richiesta non è stata accettata.")) return REFUSED;
                            if (response.equals("Siamo spiacenti, il servizio di traduzione non è al momento disponibile. " +
                                    "Riprovare più tardi."))
                                return API_ERROR;
                        } else if (response.equals("L'utente indicato non è online.")) {
                            return NOT_ONLINE;
                        }
                        return ACCEPTED;
                    }

                    @Override
                    protected void done() {
                        if (ClientGUI.DEBUG_MODE) System.out.println("Finito!");
                        waitingDialog.setVisible(false);
                        waitingDialog.dispose();

                        int result = -1;
                        try {
                            result = get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }

                        String message = null;

                        if (result != -1) {
                            if (result == REFUSED) {
                                message = id + " non ha accettato la tua richiesta.";
                            } else if (result == API_ERROR) {
                                message = "Spiacenti, il servizio API non è disponibile.";
                            } else if (result == NOT_ONLINE) {
                                message = "L'utente non è online.";
                            } else if (result != ACCEPTED) {
                                message = "Spiacenti, qualcosa è andato storto...";
                            }

                            if (message != null) {
                                //Qualcosa è andato storto
                                JOptionPane.showMessageDialog(homePanel, message,
                                        "Richiesta a " + id, JOptionPane.ERROR_MESSAGE, null);

                            } else {

                                homePanel.setVisible(false);
                                homePanel = null;
                                gamePanel = gamePage();
                                gamePanel.setVisible(true);
                                window.setContentPane(gamePanel);
                            }
                        }
                        super.done();
                    }
                };
                requestChallenge.execute();

                waitingDialog.setLayout(new GridBagLayout());

                waitingDialog.setMinimumSize(new Dimension(200, 150));
                waitingDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                waitingDialog.setLocationRelativeTo(window);

                waitingDialog.add(NormalText.newText("Attendi...").center());

                waitingDialog.setVisible(true);
                waitingDialog.pack();

            }
        }

        @Override
        public void mousePressed(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseReleased(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseEntered(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseExited(MouseEvent mouseEvent) {
            //NOT NEEDED
        }
    }

    /**
     * {@link MouseListener} per la lista contenente le richieste di sfida attive per un utente.
     * Con un doppio click su di una richiesta, questa verrà accettata.
     */
    private static class AcceptChallengeListener implements MouseListener {

        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
            ListField list = (ListField) mouseEvent.getSource();
            if (mouseEvent.getClickCount() == 2) {

                int index = list.locationToIndex(mouseEvent.getPoint());
                String id;
                synchronized (challengesListModel) {
                    id = challengesListModel.get(index);
                    challengesListModel.remove(index);
                }

                if (ClientGUI.DEBUG_MODE)
                    System.out.println("Clicked " + id);

                JDialog waitingDialog = new JDialog(window, "Attendi l'avversario...",
                        Dialog.ModalityType.APPLICATION_MODAL);

                SwingWorker<Integer, Void> acceptChallenge = new SwingWorker<>() {
                    private final int NOT_AVAILABLE = 0;
                    private final int OK = 1;
                    private final int API_ERROR = 2;

                    @Override
                    protected Integer doInBackground() throws Exception {
                        String response;
                        String[] addressInfo;

                        receiver.setChallengeAvailable(false);

                        synchronized (requests) {
                            addressInfo = requests.get(id);
                            if (addressInfo == null) {
                                System.out.println("L'id inserito non è stato trovato/La richiesta non è più valida.");
                                return NOT_AVAILABLE;
                            }
                            requests.remove(id);
                        }

                        InetAddress serverAddress = InetAddress.getByName(addressInfo[0]);
                        byte[] messageBytes = "accepted".getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(messageBytes,
                                messageBytes.length, serverAddress, Integer.parseInt(addressInfo[1]));
                        UDPSocket.send(sendPacket);

                        Thread.sleep(500);
                        if (!receiver.isChallengeAvailable()) {
                            if (ClientGUI.DEBUG_MODE) System.out.println("MatchVs" + id + " --- Richiesta scaduta.");
                            return NOT_AVAILABLE;
                        }
                        response = CommonUtilities.readFromSocket(TCPSocket);

                        if (response.equals("Siamo spiacenti, il servizio di traduzione non è al " +
                                "momento disponibile. Riprovare più tardi.")) {
                            return API_ERROR;
                        }

                        return OK;
                    }

                    @Override
                    protected void done() {
                        waitingDialog.setVisible(false);
                        waitingDialog.dispose();

                        int result = -1;
                        try {
                            result = get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                        String message = null;

                        if (result == API_ERROR) {
                            message = "Spiacenti, il servizio API non è disponibile.";
                        } else if (result == NOT_AVAILABLE) {
                            message = "La richiesta non è più valida.";
                        }

                        if (message != null) JOptionPane.showMessageDialog(homePanel, message,
                                "Sfida con " + id, JOptionPane.ERROR_MESSAGE, null);

                        if (result == OK) {
                            homePanel.setVisible(false);
                            homePanel = null;
                            gamePanel = gamePage();
                            gamePanel.setVisible(true);
                            window.setContentPane(gamePanel);
                        }

                        super.done();
                    }
                };
                acceptChallenge.execute();

                waitingDialog.setLayout(new GridBagLayout());

                waitingDialog.setMinimumSize(new Dimension(200, 150));
                waitingDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                waitingDialog.setLocationRelativeTo(window);

                waitingDialog.add(NormalText.newText("Attendi...").center());

                waitingDialog.setVisible(true);
                waitingDialog.pack();
            }
        }

        @Override
        public void mousePressed(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseReleased(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseEntered(MouseEvent mouseEvent) {
            //NOT NEEDED
        }

        @Override
        public void mouseExited(MouseEvent mouseEvent) {
            //NOT NEEDED
        }
    }

    /**
     * {@link ActionListener} per il bottone di invio traduzione, nella pagina di gioco.
     */
    private static class SendListener implements ActionListener {

        InputField inputField;

        public SendListener(InputField inputField) {
            this.inputField = inputField;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!TIMEOUT) {
                String input = inputField.getText().toLowerCase();
                inputField.setText("");

                SwingWorker<Void, Void> sendResponse = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        CommonUtilities.writeIntoSocket(input, TCPSocket);
                        return null;
                    }

                    @Override
                    protected void done() {
                        super.done();
                    }
                };

                sendResponse.execute();
            }

        }

    }

    /**
     * {@link ActionListener} per il bottone di ritorno alla pagina principale, cliccabile
     * una volta finito il match.
     */
    private static class ExitGame implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            IN_GAME = false;
            SwingWorker<Integer, Void> getPoints = new SwingWorker<>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    return queryPoints();
                }

                @Override
                protected void done() {
                    super.done();
                    try {
                        POINTS_ONLINE = get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    gamePanel.setVisible(false);
                    gamePanel = null;
                    try {
                        homePanel = homePage();
                        window.setContentPane(homePanel);
                        homePanel.setVisible(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            getPoints.execute();
        }
    }

    /*
        Some utilities queries methods
     */

    /**
     * Si occupa di richiedere al server la lista amici dell'utente loggato. Ricalca la parte
     * di codice corrispondente della classe {@link front_end_src.Client}.
     *
     * @return La lista di amici dell'utente, sotto forma di {@link ArrayList} di {@link String}.
     * @throws IOException In caso di problemi durante la comunicazione con il server.
     */
    static ArrayList<String> queryFriends() throws IOException {

        ArrayList<String> friends;
        CommonUtilities.writeIntoSocket("lista_amici", TCPSocket);
        String response = CommonUtilities.readFromSocket(TCPSocket);
        if (response.equals("Non hai nessuna amicizia.")) {
            if (ClientGUI.DEBUG_MODE) System.out.println(response);
            friends = new ArrayList<>();
        } else {
            Type friendsType = new TypeToken<ArrayList<String>>() {
            }.getType();
            friends = gson.fromJson(response, friendsType);
        }
        return friends;
    }

    /**
     * Si occupa di richiedere al server la classifica degli amici dell'utente loggato. Ricalca la parte
     * di codice corrispondente della classe {@link front_end_src.Client}.
     *
     * @return La classifica degli amici dell'utente, sotto forma di {@link ArrayList} di {@link String}.
     * @throws IOException In caso di problemi durante la comunicazione con il server.
     */
    private static ArrayList<String> queryRank() throws IOException {
        ArrayList<String> rankLines;

        CommonUtilities.writeIntoSocket("mostra_classifica", TCPSocket);
        String response = CommonUtilities.readFromSocket(TCPSocket);
        Type rankType = new TypeToken<LinkedHashMap<String, Integer>>() {
        }.getType();
        LinkedHashMap<String, Integer> rank = gson.fromJson(response, rankType);
        rankLines = new ArrayList<>();
        for (String user : rank.keySet()) {
            rankLines.add(user + ": " + rank.get(user));
        }

        return rankLines;
    }

    /**
     * Si occupa di richiedere al server il punteggio totale dell'utente loggato. Ricalca la parte
     * di codice corrispondente della classe {@link front_end_src.Client}.
     *
     * @return Il punteggio totale dell'utente loggato.
     * @throws IOException In caso di problemi durante la comunicaione con il server.
     */
    private static int queryPoints() throws IOException {
        CommonUtilities.writeIntoSocket("mostra_punteggio", TCPSocket);
        String response = CommonUtilities.readFromSocket(TCPSocket);
        System.out.println("Punti letti: " + response);
        StringTokenizer stringTokenizer = new StringTokenizer(response, " ");
        for (int i = 0; i < 4; i++) {
            stringTokenizer.nextToken();
        }
        return Integer.parseInt(stringTokenizer.nextToken().split("\\.")[0]);

    }

}
