package co.frenchpress;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Built-in {@link CredentialPrompt} for desktop (Linux/macOS/Windows) JVM
 * environments where AWT/Swing is present and a display is available.
 *
 * <p>Not instantiated on Android or headless servers — {@link #createIfSupported}
 * returns {@code null} in those environments so the caller can fall through to
 * a different strategy.
 */
public final class SwingCredentialPrompt implements CredentialPrompt {

  /** Pixels per QR module, and the quiet zone around the code in modules. */
  private static final int MODULE_PX = 6;
  private static final int QUIET_MODULES = 4;

  private volatile boolean cancelled;

  // Event dispatch thread only.
  private JDialog dialog;
  private JLabel qrLabel;
  private JTextField urlField;

  /**
   * @return a new instance if AWT/Swing is present and a display is available,
   *         otherwise {@code null}
   */
  static SwingCredentialPrompt createIfSupported () {
    try {
      if (GraphicsEnvironment.isHeadless()) return null;
      Class.forName("javax.swing.JDialog"); // absent on Android
      return new SwingCredentialPrompt();
    } catch (Throwable t) {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // CredentialPrompt
  // -------------------------------------------------------------------------

  @Override public void showSignIn (String challengeUrl, String qrModules) {
    SwingUtilities.invokeLater(() -> {
      if (dialog == null) buildDialog();
      qrLabel.setIcon(new ImageIcon(render(qrModules)));
      urlField.setText(challengeUrl);
      urlField.setCaretPosition(0);
      dialog.pack();
      if (!dialog.isVisible()) {
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true); // non-modal: returns at once
      }
    });
  }

  @Override public void hideSignIn () {
    SwingUtilities.invokeLater(() -> {
      if (dialog != null) {
        dialog.dispose();
        dialog = null;
      }
    });
  }

  @Override public boolean isSignInCancelled () {
    return cancelled;
  }

  // -------------------------------------------------------------------------
  // Dialog
  // -------------------------------------------------------------------------

  private void buildDialog () {
    dialog = new JDialog((Frame) null, "Steam Sign-in — Spiral Knights", false);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing (WindowEvent e) { cancel(); }
    });

    JLabel hint = new JLabel(
      "<html>Scan this code with the <b>Steam mobile app</b> to sign in,"
      + "<br>or open the link below on a device that has it.</html>");
    hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

    qrLabel = new JLabel();
    qrLabel.setHorizontalAlignment(SwingConstants.CENTER);

    urlField = new JTextField(34);
    urlField.setEditable(false);

    JButton cancelBtn = new JButton("Use Web Account");
    cancelBtn.addActionListener(e -> cancel());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    buttons.add(cancelBtn);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    south.add(urlField, BorderLayout.NORTH);
    south.add(buttons, BorderLayout.SOUTH);

    JPanel content = new JPanel(new BorderLayout());
    content.setBorder(BorderFactory.createEmptyBorder(14, 18, 10, 18));
    content.add(hint, BorderLayout.NORTH);
    content.add(qrLabel, BorderLayout.CENTER);
    content.add(south, BorderLayout.SOUTH);
    dialog.setContentPane(content);
  }

  private void cancel () {
    cancelled = true;
    if (dialog != null) {
      dialog.dispose();
      dialog = null;
    }
  }

  /** Draws the module grid black on white with a quiet zone, {@link #MODULE_PX} pixels per module. */
  static BufferedImage render (String qrModules) {
    String[] rows = qrModules.split("\n");
    int size = (rows.length + 2 * QUIET_MODULES) * MODULE_PX;
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, size, size);
      g.setColor(Color.BLACK);
      for (int y = 0; y < rows.length; y++) {
        for (int x = 0; x < rows[y].length(); x++) {
          if (rows[y].charAt(x) == '1') {
            g.fillRect((x + QUIET_MODULES) * MODULE_PX, (y + QUIET_MODULES) * MODULE_PX,
              MODULE_PX, MODULE_PX);
          }
        }
      }
    } finally {
      g.dispose();
    }
    return img;
  }
}
