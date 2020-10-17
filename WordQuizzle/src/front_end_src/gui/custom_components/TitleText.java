package front_end_src.gui.custom_components;

import javax.swing.*;
import java.awt.*;

/**
 * {@link TitleText} è un componente custom che estende una semplice
 * {@link JLabel}, creato per personalizzare e poi riutilizzare
 * il componente standard. Il componente è stato implementato tramite
 * il pattern Builder/Factory.
 */
public class TitleText extends JLabel {

    private TitleText(String text) {
        super(text);
        this.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 50));
    }

    /**
     * Metodo builder/factory per la creazione del componente.
     *
     * @param text Il testo da visualizzare.
     * @return Un oggetto {@link TitleText}, e quindi {@link JLabel}.
     */
    public static TitleText newTitleText(String text) {
        return new TitleText(text);
    }

    /**
     * Metodo builder/factory per centrare orizzontalmente e verticalmente il testo
     * all'interno del {@link TitleText}.
     *
     * @return L'oggetto {@link TitleText} modificato.
     */
    public TitleText center() {
        this.setVerticalAlignment(SwingConstants.CENTER);
        this.setHorizontalAlignment(SwingConstants.CENTER);
        return this;
    }

    /**
     * Metodo builder/factory per cambiare la dimensione del font.
     *
     * @param size La nuova dimensione del font.
     * @return L'oggetto {@link TitleText} modificato.
     */
    public TitleText fontSize(int size) {
        if (size != 0) {
            this.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        }
        return this;
    }

}
