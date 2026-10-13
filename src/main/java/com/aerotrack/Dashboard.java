package com.aerotrack;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Main Swing dashboard. UI actions call Database methods; SQL is kept out of this class. */
public class Dashboard extends JFrame {
    private final Database database;
    private final UserAccount user;
    private final FlightSimulator simulator = new FlightSimulator();
    private final AnomalyAnalyzer analyzer = new AnomalyAnalyzer();

    private final JLabel status = new JLabel("Ready");
    private final JLabel flightValue = new JLabel("--"), altitudeValue = new JLabel("--");
    private final JLabel velocityValue = new JLabel("--"), temperatureValue = new JLabel("--");
    private final JLabel batteryValue = new JLabel("--"), phaseValue = new JLabel("--");
    private final JTextArea alerts = new JTextArea(4, 40);

    private final DefaultTableModel liveModel = model("Packet", "Time", "Altitude", "Velocity", "Temperature", "Battery", "Phase");
    private final DefaultTableModel historyModel = model("Flight ID", "Name", "Scenario", "Status", "Start", "End", "Packets", "Max Altitude", "Avg Temp", "Min Battery");
    private final DefaultTableModel packetModel = model("Packet", "Time", "Altitude", "Velocity", "Temperature", "Battery", "Phase");
    private final DefaultTableModel eventModel = model("Event ID", "Telemetry ID", "Time", "Type", "Severity", "Message", "Value");
    private final DefaultTableModel compareModel = model("Metric", "Flight A", "Flight B", "A - B");
    private final DefaultTableModel usersModel = model("Username", "Role", "Created");

    private final JTextField existingFlight = new JTextField(7), anomalyIndex = new JTextField("10", 4);
    private final JComboBox<String> scenario = new JComboBox<>(new String[]{"Normal", "High Temperature", "Low Battery", "Abnormal Altitude"});
    private final JComboBox<String> anomalyMode = new JComboBox<>(new String[]{"Automatic scenario", "Manual injection"});
    private final JComboBox<String> manualAnomaly = new JComboBox<>(new String[]{"High Temperature", "Low Battery", "Abnormal Altitude"});
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"1x", "2x", "5x"});
    private final JButton start = new JButton("Start flight"), pause = new JButton("Pause"), resume = new JButton("Resume"), stop = new JButton("Stop");

    private final JTextField filterId = new JTextField(5), filterPhase = new JTextField(7), filterTemp = new JTextField(5), filterBattery = new JTextField(5);
    private final JComboBox<String> filterStatus = new JComboBox<>(new String[]{"Any status", "RUNNING", "COMPLETED", "ABORTED"});
    private final JComboBox<String> graphFlight = new JComboBox<>(), graphMetric = new JComboBox<>(new String[]{"Altitude", "Velocity", "Temperature", "Battery"});
    private final JComboBox<String> compareA = new JComboBox<>(), compareB = new JComboBox<>();
    private final JTextField newUsername = new JTextField(9);
    private final JPasswordField newPassword = new JPasswordField(9);
    private final JComboBox<String> newRole = new JComboBox<>(new String[]{"VIEWER", "ANALYST", "OPERATOR", "ADMINISTRATOR"});
    private final TelemetryChart chart = new TelemetryChart();

    private JTable historyTable;
    private javax.swing.Timer liveTimer;
    private ThreadGroup simulationGroup = new ThreadGroup("AeroTrackSimulation");
    private volatile boolean running, paused;
    private volatile int flightId = -1, packetIndex, nextPacket;
    private volatile long delayMillis = 1000;
    private Thread simulationThread;

    public Dashboard(Database database, UserAccount user) {
        this.database = database;
        this.user = user;
        setTitle("AeroTrack | Flight Telemetry Monitoring and Analysis");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildUI();
        applyPermissions();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });
        refreshAll();
        liveTimer = new javax.swing.Timer(1500, e -> refreshLive());
        liveTimer.start();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 14, 12, 14));
        root.setBackground(new Color(241, 245, 249));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 23, 42));
        header.setBorder(new EmptyBorder(15, 18, 15, 18));
        JLabel title = new JLabel("AEROTRACK / FLIGHT TELEMETRY COMMAND CENTER");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        JLabel account = new JLabel("SIGNED IN: " + user.username() + " | " + user.role());
        account.setForeground(new Color(191, 219, 254));
        header.add(title, BorderLayout.WEST);
        header.add(account, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Live Monitor", liveTab());
        tabs.addTab("Flight Simulator", simulatorTab());
        tabs.addTab("History & Search", historyTab());
        tabs.addTab("Graphs", graphsTab());
        tabs.addTab("Compare Flights", compareTab());
        tabs.addTab("Users & Roles", usersTab());
        tabs.addTab("Database", databaseTab());
        root.add(tabs, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(8, 4, 2, 4));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel liveTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel cards = new JPanel(new GridLayout(2, 3, 10, 10));
        cards.add(card("Latest flight", flightValue));
        cards.add(card("Altitude", altitudeValue));
        cards.add(card("Velocity", velocityValue));
        cards.add(card("Temperature", temperatureValue));
        cards.add(card("Battery", batteryValue));
        cards.add(card("Flight phase", phaseValue));
        panel.add(cards, BorderLayout.NORTH);
        JTable table = new JTable(liveModel);
        table.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        alerts.setEditable(false);
        alerts.setLineWrap(true);
        alerts.setWrapStyleWord(true);
        JScrollPane alertScroll = new JScrollPane(alerts);
        alertScroll.setBorder(BorderFactory.createTitledBorder("Anomaly alerts"));
        alertScroll.setPreferredSize(new Dimension(0, 110));
        panel.add(alertScroll, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel simulatorTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Flight configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 7, 6, 7);
        c.anchor = GridBagConstraints.WEST;
        addField(form, c, 0, "Existing Flight ID (blank = new):", existingFlight);
        addField(form, c, 1, "Scenario:", scenario);
        addField(form, c, 2, "Anomaly mode:", anomalyMode);
        addField(form, c, 3, "Manual anomaly:", manualAnomaly);
        addField(form, c, 4, "Manual packet index (0-29):", anomalyIndex);
        addField(form, c, 5, "Speed:", speed);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(start); buttons.add(pause); buttons.add(resume); buttons.add(stop);
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2; form.add(buttons, c);
        pause.setEnabled(false); resume.setEnabled(false); stop.setEnabled(false);
        start.addActionListener(e -> startFlight());
        pause.addActionListener(e -> pauseFlight());
        resume.addActionListener(e -> resumeFlight());
        stop.addActionListener(e -> stopFlight());
        anomalyMode.addActionListener(e -> updateAnomalyControls());
        updateAnomalyControls();
        JTextArea note = new JTextArea("The simulator creates 30 packets per run. Each packet is saved in MySQL.\n"
                + "Automatic rules: temperature > 35 C, battery < 11 V, altitude > 1100 m.\n"
                + "Manual mode injects one selected anomaly at packet index 0-29.\n"
                + "These are educational demo thresholds, not real rocket safety limits.");
        note.setEditable(false); note.setLineWrap(true); note.setWrapStyleWord(true);
        note.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(form, BorderLayout.NORTH);
        panel.add(note, BorderLayout.CENTER);
        return panel;
    }

    private void addField(JPanel p, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; p.add(new JLabel(label), c);
        c.gridx = 1; p.add(field, c);
    }

    private JPanel historyTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Flight ID")); filters.add(filterId);
        filters.add(new JLabel("Status")); filters.add(filterStatus);
        filters.add(new JLabel("Phase")); filters.add(filterPhase);
        filters.add(new JLabel("Temp >")); filters.add(filterTemp);
        filters.add(new JLabel("Battery <")); filters.add(filterBattery);
        JButton search = new JButton("Search"), all = new JButton("All"), load = new JButton("Load selected"), export = new JButton("Export CSV");
        filters.add(search); filters.add(all); filters.add(load); filters.add(export);
        search.addActionListener(e -> searchFlights());
        all.addActionListener(e -> { clearFilters(); loadHistory(); });
        load.addActionListener(e -> loadSelected());
        export.addActionListener(e -> exportCSV());
        historyTable = new JTable(historyModel);
        historyTable.setAutoCreateRowSorter(true);
        JTabbedPane details = new JTabbedPane();
        details.addTab("Telemetry packets", new JScrollPane(new JTable(packetModel)));
        details.addTab("Anomaly events", new JScrollPane(new JTable(eventModel)));
        details.setPreferredSize(new Dimension(0, 220));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(historyTable), details);
        split.setResizeWeight(0.65);
        panel.add(filters, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel graphsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Flight")); controls.add(graphFlight);
        controls.add(new JLabel("Metric")); controls.add(graphMetric);
        JButton draw = new JButton("Load graph");
        controls.add(draw);
        draw.addActionListener(e -> drawGraph());
        graphFlight.addActionListener(e -> drawGraph());
        graphMetric.addActionListener(e -> drawGraph());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }

    private JPanel compareTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Flight A")); controls.add(compareA);
        controls.add(new JLabel("Flight B")); controls.add(compareB);
        JButton compare = new JButton("Compare");
        compare.addActionListener(e -> compareFlights());
        controls.add(compare);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(compareModel)), BorderLayout.CENTER);
        return panel;
    }

    private JPanel usersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setBorder(BorderFactory.createTitledBorder("Create account (administrator only)"));
        form.add(new JLabel("Username")); form.add(newUsername);
        form.add(new JLabel("Password")); form.add(newPassword);
        form.add(new JLabel("Role")); form.add(newRole);
        JButton create = new JButton("Create user"), refresh = new JButton("Refresh");
        create.addActionListener(e -> createUser());
        refresh.addActionListener(e -> loadUsers());
        form.add(create); form.add(refresh);
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(usersModel)), BorderLayout.CENTER);
        JTextArea note = new JTextArea("VIEWER: live monitor and history.\nANALYST: viewer + graphs and comparison.\nOPERATOR: analyst + simulation.\nADMINISTRATOR: all tabs and user management.");
        note.setEditable(false); note.setRows(4);
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel databaseTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JTabbedPane workspace = new JTabbedPane();

        // Overview: live counts are read directly from MySQL, not hardcoded.
        JPanel overview = new JPanel(new BorderLayout(8, 8));
        JTextArea overviewText = new JTextArea("Database workspace\n\nClick Refresh to read current row counts from MySQL.");
        overviewText.setEditable(false);
        overviewText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JButton refreshCounts = new JButton("Refresh database counts");
        refreshCounts.addActionListener(e -> {
            try {
                long[] counts = database.databaseCounts();
                overviewText.setText("Connected database: rocket_telemetry\n\n"
                        + "flights       : " + counts[0] + "\n"
                        + "telemetry     : " + counts[1] + "\n"
                        + "events        : " + counts[2] + "\n"
                        + "app_users     : " + counts[3] + "\n\n"
                        + "Each simulated packet is inserted into telemetry.\n"
                        + "Anomaly findings are saved in events in the same transaction.\n"
                        + "Table counts are fetched live from MySQL.");
            } catch (Exception ex) { showError(ex); }
        });
        overview.add(refreshCounts, BorderLayout.NORTH);
        overview.add(new JScrollPane(overviewText), BorderLayout.CENTER);
        workspace.addTab("Overview", overview);

        // Table explorer: real records and real schema metadata.
        JPanel explorer = new JPanel(new BorderLayout(6, 6));
        JPanel explorerControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> tableChoice = new JComboBox<>(new String[]{"flights", "telemetry", "events", "app_users"});
        JComboBox<String> viewChoice = new JComboBox<>(new String[]{"Records (latest 200)", "Column definitions"});
        JButton loadTable = new JButton("Load table");
        DefaultTableModel explorerModel = model("Result");
        JTable explorerTable = new JTable(explorerModel);
        explorerTable.setAutoCreateRowSorter(true);
        explorerControls.add(new JLabel("Table")); explorerControls.add(tableChoice);
        explorerControls.add(new JLabel("View")); explorerControls.add(viewChoice); explorerControls.add(loadTable);
        loadTable.addActionListener(e -> {
            try {
                Database.TableData data = "Column definitions".equals(viewChoice.getSelectedItem())
                        ? database.inspectColumns((String) tableChoice.getSelectedItem())
                        : database.inspectTable((String) tableChoice.getSelectedItem());
                setTableData(explorerModel, data);
            } catch (Exception ex) { showError(ex); }
        });
        explorer.add(explorerControls, BorderLayout.NORTH);
        explorer.add(new JScrollPane(explorerTable), BorderLayout.CENTER);
        workspace.addTab("Table explorer", explorer);

        // Predefined read-only SQL analytics make joins and aggregates visible without unsafe free-form SQL.
        JPanel analytics = new JPanel(new BorderLayout(6, 6));
        JPanel analyticsControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> reportChoice = new JComboBox<>(new String[]{"Flight summary", "Anomalies by flight",
                "High temperature packets", "Low battery packets", "Flight state distribution", "Flights with no telemetry"});
        JButton runReport = new JButton("Run SQL report");
        DefaultTableModel reportModel = model("Result");
        JTable reportTable = new JTable(reportModel);
        reportTable.setAutoCreateRowSorter(true);
        analyticsControls.add(new JLabel("Report")); analyticsControls.add(reportChoice); analyticsControls.add(runReport);
        runReport.addActionListener(e -> {
            try { setTableData(reportModel, database.runAnalytics((String) reportChoice.getSelectedItem())); }
            catch (Exception ex) { showError(ex); }
        });
        analytics.add(analyticsControls, BorderLayout.NORTH);
        analytics.add(new JScrollPane(reportTable), BorderLayout.CENTER);
        workspace.addTab("SQL analytics", analytics);

        JTextArea footer = new JTextArea("DBMS concepts demonstrated with live MySQL data: SELECT, JOIN, GROUP BY, aggregate functions, "
                + "LEFT JOIN, primary/foreign-key relationships, schema metadata, JDBC, and transactional packet/event inserts. "
                + "Reports are read-only and use predefined SQL.");
        footer.setEditable(false); footer.setLineWrap(true); footer.setWrapStyleWord(true);
        footer.setRows(2);
        panel.add(workspace, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void setTableData(DefaultTableModel model, Database.TableData data) {
        model.setRowCount(0);
        model.setColumnCount(0);
        for (String column : data.columns()) model.addColumn(column);
        for (Object[] row : data.rows()) model.addRow(row);
        status.setText("Database query complete: " + data.rows().size() + " row(s) returned.");
    }

    private JPanel card(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                new EmptyBorder(14, 14, 14, 14)));
        JLabel heading = new JLabel(title.toUpperCase());
        heading.setForeground(new Color(100, 116, 139));
        heading.setFont(new Font("SansSerif", Font.BOLD, 11));
        value.setFont(new Font("SansSerif", Font.BOLD, 21));
        p.add(heading, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private void updateAnomalyControls() {
        boolean manual = "Manual injection".equals(anomalyMode.getSelectedItem());
        manualAnomaly.setEnabled(manual);
        anomalyIndex.setEnabled(manual);
        scenario.setEnabled(!running && !manual);
    }

    private void applyPermissions() {
        boolean admin = user.isAdministrator();
        boolean operator = user.canOperate();
        boolean analyst = user.canAnalyze();
        // Tab order: Live, Simulator, History, Graphs, Compare, Users, Database.
        // Viewer gets live/history/database; analyst gets graphs and compare; operator gets simulator.
        // Disabled controls are also checked by each action method.
        start.setEnabled(operator);
        pause.setEnabled(false); resume.setEnabled(false); stop.setEnabled(false);
        if (!operator) {
            existingFlight.setEnabled(false); scenario.setEnabled(false);
            anomalyMode.setEnabled(false); manualAnomaly.setEnabled(false);
            anomalyIndex.setEnabled(false); speed.setEnabled(false);
        }
        // The tabbed pane is found through the root content hierarchy.
        JTabbedPane tabs = findTabs(getContentPane());
        if (tabs != null) {
            tabs.setEnabledAt(1, operator);
            tabs.setEnabledAt(3, analyst);
            tabs.setEnabledAt(4, analyst);
            tabs.setEnabledAt(5, admin);
        }
    }

    private JTabbedPane findTabs(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTabbedPane pane) return pane;
            if (component instanceof Container child) {
                JTabbedPane found = findTabs(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void startFlight() {
        if (!user.canOperate()) { showMessage("Operator role required."); return; }
        if (running) { showMessage("A simulation is already running."); return; }
        try {
            boolean manual = "Manual injection".equals(anomalyMode.getSelectedItem());
            String selectedScenario = manual ? "Normal" : (String) scenario.getSelectedItem();
            int manualAt = manual ? Integer.parseInt(anomalyIndex.getText().trim()) : -1;
            if (manualAt < -1 || manualAt > 29) throw new IllegalArgumentException("Packet index must be 0-29.");
            String selectedSpeed = (String) speed.getSelectedItem();
            delayMillis = "5x".equals(selectedSpeed) ? 200 : "2x".equals(selectedSpeed) ? 500 : 1000;

            String existing = existingFlight.getText().trim();
            if (existing.isBlank()) {
                flightId = database.createFlight("AeroTrack " + selectedScenario + " " + System.currentTimeMillis(), selectedScenario);
                nextPacket = 0;
            } else {
                flightId = Integer.parseInt(existing);
                nextPacket = database.nextPacketNumber(flightId);
                if (nextPacket >= FlightSimulator.PACKETS_PER_RUN) {
                    throw new IllegalArgumentException(
                            "This flight already has all 30 packets. Choose a new flight ID.");
                }
                database.markFlight(flightId, "RUNNING");
            }
            // Continue at the next unused packet number to avoid duplicate-key errors.
            packetIndex = nextPacket;
            running = true; paused = false;
            updateButtons();
            status.setText("Flight " + flightId + " running");
            final boolean manualMode = manual;
            final int injectAt = manualAt;
            final String scenarioName = selectedScenario;
            simulationThread = new Thread(simulationGroup, () -> runSimulation(manualMode, injectAt, scenarioName),
                    "AeroTrack-Flight-" + flightId);
            simulationThread.setDaemon(false);
            simulationThread.start();
        } catch (Exception ex) {
            running = false; updateButtons(); showError(ex);
        }
    }

    private void runSimulation(boolean manual, int manualAt, String scenarioName) {
        try {
            while (running && packetIndex < FlightSimulator.PACKETS_PER_RUN) {
                if (!paused) {
                    TelemetryPacket packet = simulator.createPacket(packetIndex, scenarioName,
                            manual ? (String) manualAnomaly.getSelectedItem() : "None", manualAt);
                    List<AnomalyAnalyzer.Finding> findings = analyzer.analyze(packet);
                    database.savePacket(flightId, packet, findings);
                    int storedIndex = packetIndex;
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Flight " + flightId + " saved packet " + storedIndex);
                        refreshLive();
                    });
                    packetIndex++;
                    nextPacket++;
                }
                Thread.sleep(delayMillis);
            }
            if (running) {
                database.markFlight(flightId, "COMPLETED");
                running = false;
                SwingUtilities.invokeLater(() -> {
                    updateButtons(); refreshAll();
                    status.setText("Flight " + flightId + " completed and stored in MySQL.");
                });
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            running = false;
            SwingUtilities.invokeLater(() -> { updateButtons(); showError(ex); });
        }
    }

    private void pauseFlight() { if (running && !paused) { paused = true; updateButtons(); status.setText("Paused"); } }
    private void resumeFlight() { if (running && paused) { paused = false; updateButtons(); status.setText("Resumed"); } }

    private void stopFlight() {
        if (!running) return;
        running = false; paused = false;
        if (simulationThread != null) simulationThread.interrupt();
        try { database.markFlight(flightId, "ABORTED"); }
        catch (Exception ex) { showError(ex); }
        updateButtons(); refreshAll();
    }

    private void updateButtons() {
        start.setEnabled(!running && user.canOperate());
        pause.setEnabled(running && !paused && user.canOperate());
        resume.setEnabled(running && paused && user.canOperate());
        stop.setEnabled(running && user.canOperate());
        scenario.setEnabled(!running && !"Manual injection".equals(anomalyMode.getSelectedItem()) && user.canOperate());
        speed.setEnabled(!running && user.canOperate());
    }

    private void refreshAll() { refreshLive(); loadHistory(); refreshFlightChoices(); loadUsers(); }

    private void refreshLive() {
        try {
            List<Object[]> flights = database.loadFlights("", "Any status", "", "", "");
            if (flights.isEmpty()) { flightValue.setText("No flights"); return; }
            Object[] latest = flights.get(0);
            int latestId = ((Number) latest[0]).intValue();
            flightValue.setText("#" + latestId + " " + latest[1] + " (" + latest[3] + ")");
            liveModel.setRowCount(0);
            List<Object[]> rows = database.loadTelemetry(latestId);
            List<String> warnings = new ArrayList<>();
            for (Object[] row : rows) {
                liveModel.addRow(row);
                double altitude = ((Number) row[2]).doubleValue();
                double temperature = ((Number) row[4]).doubleValue();
                double battery = ((Number) row[5]).doubleValue();
                int packet = ((Number) row[0]).intValue();
                if (temperature > 35) warnings.add("Packet " + packet + ": high temperature " + temperature + " C");
                if (battery < 11) warnings.add("Packet " + packet + ": low battery " + battery + " V");
                if (altitude > 1100) warnings.add("Packet " + packet + ": abnormal altitude " + altitude + " m");
            }
            if (!rows.isEmpty()) {
                Object[] last = rows.get(rows.size() - 1);
                altitudeValue.setText(last[2] + " m"); velocityValue.setText(last[3] + " m/s");
                temperatureValue.setText(last[4] + " C"); batteryValue.setText(last[5] + " V");
                phaseValue.setText(String.valueOf(last[6]));
            }
            alerts.setText(warnings.isEmpty() ? "No anomaly thresholds crossed for latest flight." : String.join("\n", warnings));
        } catch (Exception ex) { status.setText("Live retrieval error: " + ex.getMessage()); }
    }

    private void loadHistory() {
        try { fill(historyModel, database.loadFlights("", "Any status", "", "", "")); }
        catch (Exception ex) { status.setText("History error: " + ex.getMessage()); }
    }

    private void searchFlights() {
        try {
            fill(historyModel, database.loadFlights(filterId.getText().trim(),
                    (String) filterStatus.getSelectedItem(), filterPhase.getText().trim(),
                    filterTemp.getText().trim(), filterBattery.getText().trim()));
            status.setText("Search returned " + historyModel.getRowCount() + " flight(s).");
        } catch (Exception ex) { showError(ex); }
    }

    private void clearFilters() {
        filterId.setText(""); filterPhase.setText(""); filterTemp.setText(""); filterBattery.setText("");
        filterStatus.setSelectedItem("Any status");
    }

    private void loadSelected() {
        int selected = historyTable.getSelectedRow();
        if (selected < 0) { showMessage("Select a flight row first."); return; }
        int row = historyTable.convertRowIndexToModel(selected);
        int id = ((Number) historyModel.getValueAt(row, 0)).intValue();
        try {
            fill(packetModel, database.loadTelemetry(id));
            fill(eventModel, database.loadEvents(id));
            status.setText("Loaded packets and events for flight " + id);
        } catch (Exception ex) { showError(ex); }
    }

    private void exportCSV() {
        if (packetModel.getRowCount() == 0) { showMessage("Load a flight's telemetry first."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("aerotrack-flight.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter writer = new PrintWriter(chooser.getSelectedFile(), StandardCharsets.UTF_8)) {
            for (int c = 0; c < packetModel.getColumnCount(); c++) {
                if (c > 0) writer.print(",");
                writer.print(packetModel.getColumnName(c));
            }
            writer.println();
            for (int r = 0; r < packetModel.getRowCount(); r++) {
                for (int c = 0; c < packetModel.getColumnCount(); c++) {
                    if (c > 0) writer.print(",");
                    String value = String.valueOf(packetModel.getValueAt(r, c)).replace("\"", "\"\"");
                    writer.print("\"" + value + "\"");
                }
                writer.println();
            }
            status.setText("CSV exported.");
        } catch (Exception ex) { showError(ex); }
    }

    private void refreshFlightChoices() {
        refreshChoices(graphFlight); refreshChoices(compareA); refreshChoices(compareB);
    }

    private void refreshChoices(JComboBox<String> combo) {
        Object old = combo.getSelectedItem();
        combo.removeAllItems();
        try {
            for (String[] row : database.flightChoices()) combo.addItem(row[0] + " | " + row[1]);
        } catch (Exception ex) { status.setText("Flight choices error: " + ex.getMessage()); }
        if (old != null) combo.setSelectedItem(old);
    }

    private int selectedFlightId(JComboBox<String> combo) {
        String text = String.valueOf(combo.getSelectedItem());
        try { return Integer.parseInt(text.substring(0, text.indexOf('|')).trim()); }
        catch (Exception ex) { return -1; }
    }

    private void drawGraph() {
        int id = selectedFlightId(graphFlight);
        if (id < 0) return;
        String metric = (String) graphMetric.getSelectedItem();
        try {
            List<Double> values = new ArrayList<>();
            for (Object[] row : database.graphValues(id, metric)) values.add(((Number) row[1]).doubleValue());
            chart.setData(metric + " | Flight " + id, values);
        } catch (Exception ex) { showError(ex); }
    }

    private void compareFlights() {
        int a = selectedFlightId(compareA), b = selectedFlightId(compareB);
        if (a < 0 || b < 0 || a == b) { showMessage("Select two different flights."); return; }
        try { fill(compareModel, database.compareFlights(a, b)); }
        catch (Exception ex) { showError(ex); }
    }

    private void createUser() {
        if (!user.isAdministrator()) { showMessage("Administrator role required."); return; }
        String username = newUsername.getText().trim();
        String password = new String(newPassword.getPassword());
        if (username.isBlank() || password.length() < 4) {
            showMessage("Enter a username and password of at least four characters."); return;
        }
        try {
            database.createUser(username, password, (String) newRole.getSelectedItem());
            newUsername.setText(""); newPassword.setText("");
            loadUsers(); status.setText("User created.");
        } catch (Exception ex) { showError(ex); }
    }

    private void loadUsers() {
        try { fill(usersModel, database.loadUsers()); }
        catch (Exception ex) { status.setText("Users could not be loaded: " + ex.getMessage()); }
    }


    private void shutdown() {
        running = false;
        if (simulationThread != null) simulationThread.interrupt();
        if (liveTimer != null) liveTimer.stop();
        dispose();
    }

    private void showMessage(String message) { JOptionPane.showMessageDialog(this, message); }
    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "AeroTrack error", JOptionPane.ERROR_MESSAGE);
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static void fill(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] row : rows) model.addRow(row);
    }

}
