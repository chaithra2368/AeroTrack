package com.aerotrack;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Simple graph drawn with Java2D, so no extra chart library is required. */
public class TelemetryChart extends JPanel {
    private String title = "Choose a flight and metric";
    private List<Double> values = new ArrayList<>();

    public void setData(String title, List<Double> values) {
        this.title = title;
        this.values = new ArrayList<>(values);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth(), height = getHeight();
        g.setColor(new Color(30, 41, 59));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(title, 20, 25);
        if (values.isEmpty()) {
            g.drawString("No data. Run a flight first.", 20, 55);
            g.dispose();
            return;
        }
        int left = 65, right = 25, top = 50, bottom = 45;
        int plotWidth = Math.max(1, width - left - right);
        int plotHeight = Math.max(1, height - top - bottom);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (Math.abs(max - min) < 0.000001) { max += 1; min -= 1; }
        g.setColor(new Color(226, 232, 240));
        for (int i = 0; i <= 4; i++) {
            int y = top + i * plotHeight / 4;
            g.drawLine(left, y, left + plotWidth, y);
            g.setColor(new Color(71, 85, 105));
            g.drawString(String.format("%.1f", max - (max - min) * i / 4), 5, y + 4);
            g.setColor(new Color(226, 232, 240));
        }
        g.setColor(new Color(37, 99, 235));
        g.setStroke(new BasicStroke(2.5f));
        int oldX = 0, oldY = 0;
        for (int i = 0; i < values.size(); i++) {
            int x = left + (values.size() == 1 ? plotWidth / 2 : i * plotWidth / (values.size() - 1));
            int y = top + (int) ((max - values.get(i)) / (max - min) * plotHeight);
            if (i > 0) g.drawLine(oldX, oldY, x, y);
            g.fillOval(x - 3, y - 3, 6, 6);
            oldX = x; oldY = y;
        }
        g.setColor(Color.DARK_GRAY);
        g.drawString("Packet number →", left + plotWidth / 2 - 40, height - 12);
        g.dispose();
    }
}
