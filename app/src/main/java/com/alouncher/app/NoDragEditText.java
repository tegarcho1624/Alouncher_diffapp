package com.alouncher.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.EditText;

/**
 * EditText que NO acepta arrastres. Android hace que todo EditText acepte cualquier arrastre (aunque el
 * contenido no sea texto suyo); en el cajón eso lo volvía destino del soltar al arrastrar una app hacia la
 * Home, y la Home no recibía el drop. Con esto el buscador ignora los arrastres de apps.
 */
public class NoDragEditText extends EditText {

    public NoDragEditText(Context context) {
        super(context);
    }

    public NoDragEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoDragEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return false; // ni acepta el arrastre ni toma el foco al pasar por encima
    }
}
