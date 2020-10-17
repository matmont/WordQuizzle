package front_end_src.gui.custom_components;

import javax.swing.*;
import java.awt.*;

/**
 * {@link NormalText} è un componente custom che estende una semplice
 * {@link JLabel}, creato per personalizzare e poi riutilizzare
 * il componente standard. Il componente è stato implementato tramite
 * il pattern Builder/Factory.
 */
public class NormalText extends JLabel {

    private NormalText(String text) {
        super(text);
        this.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    }

    /**
     * Metodo builder/factory per la creazione del componente.
     *
     * @param text Il testo da visualizzare.
     * @return Un oggetto {@link NormalText}, e quindi {@link JLabel}.
     */
    public static NormalText newText(String text) {
        return new NormalText(text);
    }

    /**
     * Metodo builder/factory per renderizzare il testo in 'grassetto'.
     *
     * @return L'oggetto {@link NormalText} modificato.
     */
    public NormalText boldText() {
        this.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        return this;
    }

    /**
     * Metodo builder/factory per centrare orizzontalmente e verticalmente il testo
     * all'interno del {@link NormalText}.
     *
     * @return L'oggetto {@link NormalText} modificato.
     */
    public NormalText center() {
        this.setVerticalAlignment(SwingConstants.CENTER);
        this.setHorizontalAlignment(SwingConstants.CENTER);
        return this;
    }

}
