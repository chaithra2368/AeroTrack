package com.aerotrack;

import javax.swing.*;
import java.awt.*;

/** Application entry point. Login must succeed before the dashboard opens. */
public final class App {
    private App() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Database database = new Database();
                while (true) {
                    LoginDialog.LoginResult result = LoginDialog.showDialog(null);
                    if (result == null) return;
                    UserAccount user = database.authenticate(result.username(), result.password());
                    if (user != null) {
                        Dashboard dashboard = new Dashboard(database, user);
                        dashboard.setVisible(true);
                        return;
                    }
                    JOptionPane.showMessageDialog(null, "Invalid username or password.",
                            "Login failed", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Could not connect to MySQL.\n" + ex.getMessage()
                                + "\nSet AEROTRACK_DB_PASSWORD in the terminal.",
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
