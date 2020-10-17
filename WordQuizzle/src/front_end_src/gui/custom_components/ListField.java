package front_end_src.gui.custom_components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * {@link ListField} è un componente custom che estende una semplice
 * {@link JList}, creato per personalizzare e poi riutilizzare
 * il componente standard. Il componente è stato implementato tramite
 * il pattern Builder/Factory.
 */
@SuppressWarnings("unused")
public class ListField extends JList<String> {

    private ListField(DefaultListModel<String> defaultListModel) {
        super(defaultListModel);
        this.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    }

    /**
     * Metodo builder/factory per la creazione del componente.
     *
     * @param strings ArrayList di stringhe dal quale verrà creato il modello da associare alla lista.
     * @return Un oggetto {@link ListField}, e quindi {@link JList}.
     */
    public static ListField newListField(ArrayList<String> strings) {
        DefaultListModel<String> defaultListModel = new DefaultListModel<>();
        for (String string : strings) {
            defaultListModel.addElement(string);
        }
        return new ListField(defaultListModel);
    }

    /**
     * Metodo builder/factory per la creazione del componente.
     *
     * @param defaultListModel Il modello da associare alla lista.
     * @return Un oggetto {@link ListField}, e quindi {@link JList}.
     */
    public static ListField newListField(DefaultListModel<String> defaultListModel) {
        return new ListField(defaultListModel);
    }

    /**
     * Metodo builder/factory per la modifica della size della lista.
     *
     * @param width La nuova dimensione della larghezza.
     * @param height La nuova dimensione dell'altezza.
     * @return L'oggetto {@link ListField} modificato.
     */
    public ListField changeSize(int width, int height) {
        this.setPreferredSize(new Dimension(width, height));
        return this;
    }

    /**
     * Metodo builder/factory per la modifica del padding della lista.
     *
     * @param paddings La nuova dimensione per il padding. Il padding verrà applicato di tale
     *                 dimensione in tutte le direzioni (top, right, bottom, left).
     * @return L'oggetto {@link ListField} modificato.
     */
    public ListField setPaddings(int paddings) {
        this.setBorder(new EmptyBorder(paddings, paddings, paddings, paddings));
        return this;
    }

}
