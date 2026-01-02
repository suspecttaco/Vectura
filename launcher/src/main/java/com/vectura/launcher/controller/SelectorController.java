package com.vectura.launcher.controller;

import com.vectura.client.VecturaApp;
import com.vectura.server.ServerApp;
import javafx.fxml.FXML;
import javafx.stage.Stage;

public class SelectorController {

    @FXML
    public void launchClient() {
        closeSelector();
        try {
            new VecturaApp().start(new Stage());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void launchServer() {
        closeSelector();
        try {
            new ServerApp().start(new Stage());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void closeSelector() {
        Stage current = (Stage) Stage.getWindows().stream()
                .filter(w -> w.isShowing())
                .findFirst()
                .orElse(null);

        if (current != null) {
            current.close();
        }
    }
}