package com.vectura.server.util;

import javafx.animation.AnimationTimer;
import javafx.scene.control.TextArea;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UILogManager {
    private static final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Metodo para enviar logs
    public static void log(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        logQueue.offer("[" + timestamp + "] " + message + "\n");
    }

    public static void setup(TextArea textArea) {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (logQueue.isEmpty()) return;

                StringBuilder batch = new StringBuilder();
                String msg;
                while ((msg = logQueue.poll()) != null) {
                    batch.append(msg);
                }

                textArea.appendText(batch.toString());
                textArea.setScrollTop(Double.MAX_VALUE);
            }
        };
        timer.start();
    }
}