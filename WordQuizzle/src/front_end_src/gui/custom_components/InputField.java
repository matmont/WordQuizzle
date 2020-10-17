package front_end_src.gui.custom_components;

import javax.swing.*;
import java.awt.*;

/**
 * {@link InputField} è un componente custom che estende un semplice
 * {@link JTextField}, creato per personalizzare e poi riutilizzare
 * il componente standard. Il componente è stato implementato tramite
 * il pattern Builder/Factory.
 */
@SuppressWarnings("unused")
public class InputField extends JTextField {

    private InputField(String tooltipText) {
        super();
        this.setToolTipText(tooltipText);
        this.setEditable(true);
        this.setColumns(10);
    }

    private InputField() {
        super();
        this.setEditable(true);
        this.setColumns(10);
    }

    /**
     * Metodo builder/factory per la creazione del componente.
     *
     * @param tooltipText Il tooltipText che si vuole avere sul componente.
     * @return Un oggetto {@link InputField}, e quindi {@link JTextField}.
     */
    public static InputField newInputField(String tooltipText) {
        return new InputField(tooltipText);
    }

    /**
     * Metodo builder/factory per la creazione del componente. Differisce dal primo
     * per la possibilità di non definire alcun 'tooltipText'.
     *
     * @return Un oggetto {@link InputField}, e quindi {@link JTextField}.
     */
    public static InputField newInputField() {
        return new InputField();
    }

    /**
     * Metodo builder/factory per cambiare il numero di 'columns' del {@link JTextField}.
     *
     * @param columns Il nuovo numero di 'columns'
     * @return L'oggetto {@link InputField} modificato.
     */
    public InputField changeColumns(int columns) {
        this.setColumns(columns);
        return this;
    }

    /**
     * Metodo builder/factory per cambiare il 'tooltipText' del {@link JTextField}.
     *
     * @param tooltipText Il nuovo 'tooltipText'
     * @return L'oggetto {@link InputField} modificato.
     */
    public InputField setTooltipText(String tooltipText) {
        this.setToolTipText(tooltipText);
        return this;
    }

    /**
     * Metodo builder/factory per cambiare il 'padding' del {@link JTextField}.
     *
     * @param padding Il nuovo 'padding' che l'oggetto dovrà avere.
     * @return L'oggetto {@link InputField} modificato.
     */
    public InputField setPadding(int padding) {
        this.setMargin(new Insets(padding, padding, padding, padding));
        return this;
    }

    /**
     * Metodo builder/factory per cambiare il 'padding' del {@link JTextField}.
     *
     * @param top La quantità di 'padding' da avere sopra l'oggetto.
     * @param left La quantità di 'padding' da avere a sinistra l'oggetto.
     * @param bottom La quantità di 'padding' da avere sotto l'oggetto.
     * @param right La quantità di 'padding' da avere a destra l'oggetto.
     * @return L'oggetto {@link InputField} modificato.
     */
    public InputField setPadding(int top, int left, int bottom, int right) {
        this.setMargin(new Insets(top, left, bottom, right));
        return this;
    }
}
