
package com.ing.ide.main.explorer;

import com.ing.ide.util.Canvas;
import com.ing.ide.util.SlideContainer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

/**
 *
 * 
 */
public class BaseEditor extends javax.swing.JFrame {

    /**
     * Creates new form BaseEditor
     */
    private static BaseEditor bedit;
    private final  SlideContainer slider = new SlideContainer();

    private BaseEditor() {

        this.setUndecorated(this.isUndecorated());
        initComponents();
        this.setIconImage(new ImageIcon(getClass().getResource("/explorer/explorer.png")).getImage());
        this.setBackground(new Color(255, 255, 255, 0));
        this.setAlwaysOnTop(true);
        add(slider);

    }

    /**
     * sets up the UI to fill in the component
     *
     * @param object - the UI instance
     */
    synchronized void setComponent(JPanel object) {

        slider.removeAll();
        slider.add(object);
        this.setTitle(object.getName());
        this.setSize(object.getSize());
        this.pack();
        this.setLocation(this.getLocation(object.getSize()));
        new Thread(new Runnable() {
            @Override
            public void run() {
                setVisible(true);
            }
        }, "UI:Explorer:ShowBaseEditor").start();
    }

    /**
     *
     * @return - the editor instance
     */
    public static BaseEditor getEditor() {
        if (bedit == null) {
            bedit = new BaseEditor();
        }
        return bedit;
    }

    @Override
    public synchronized void setVisible(boolean visible) {
        setOpacity(1.0f);
        if (!visible) {
            fadeOut();
//            ScriptLess.resetFrame();
        } else {
//            ScriptLess.setFrame(this);
        }
        super.setVisible(visible);

    }

    /**
     * enables the fade out animation for the container/window
     */
    private void fadeOut() {
        float factor = -0.1f;
        for (int i = 0; i < 9; i++) {
            setOpacity(getOpacity() + factor);
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Logger.getLogger(BaseEditor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void setSize(Dimension d) {
        super.setSize(d);
    }

    public int getX(int width) {
        return (Canvas.Window.W / 2) - (width / 2);
    }

    @Override
    public int getY() {
        return ExplorerBar.barHeight + Canvas.Window.winStart.y;
    }

    /**
     * the location relative to the given dimension
     *
     * @param d the relative dimension
     * @return - the location
     */
    public Point getLocation(Dimension d) {
        return new Point(getX(d.width), getY());
    }

    @Override
    public boolean isUndecorated() {
        return true;
    }

    /**
     *
     * @return - active module name
     */
    String getActive() {
        return editorPanel.getName();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        editorPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setType(java.awt.Window.Type.POPUP);

        editorPanel.setLayout(new java.awt.GridBagLayout());
        getContentPane().add(editorPanel, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel editorPanel;
    // End of variables declaration//GEN-END:variables

}
