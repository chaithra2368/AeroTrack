package com.aerotrack;

import javax.swing.*;
import java.awt.*;

/** Simple modal login form. */
public final class LoginDialog {
    private LoginDialog() { }

    public record LoginResult(String username, String password) { }

    public static LoginResult showDialog(Frame owner) {
        JTextField username = new JTextField("admin", 16);
        JPasswordField password = new JPasswordField(16);
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Username"));
        form.add(username);
        form.add(new JLabel("Password"));
        form.add(password);
        int choice = JOptionPane.showConfirmDialog(owner, form, "AeroTrack Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return null;
        return new LoginResult(username.getText().trim(), new String(password.getPassword()));
    }
}
