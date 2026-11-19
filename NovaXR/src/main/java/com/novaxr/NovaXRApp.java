package com.novaxr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.awt.GraphicsEnvironment;
import java.net.InetAddress;
import java.util.List;

public class NovaXRApp extends Application {
    private final SteamScanner scanner = new SteamScanner();
    private final Label status = new Label("Ready");
    private final Label phoneUrl = new Label("Phone companion is stopped");
    private final ListView<SteamGame> games = new ListView<>();
    private final TextArea log = new TextArea();
    private LocalCompanionServer server;

    @Override public void start(Stage stage) {
        stage.setTitle("NovaXR — Steam + Phone XR");
        stage.setMinWidth(900); stage.setMinHeight(620);
        Label brand = new Label("NovaXR");
        brand.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #f3f5ff;");
        Label sub = new Label("STEAM LIBRARY  •  ANDROID COMPANION  •  LOCAL XR");
        sub.setStyle("-fx-text-fill: #9ba7c4; -fx-font-size: 10px;");

        Button scan = new Button("Scan Steam libraries");
        Button launch = new Button("Launch selected game");
        Button connect = new Button("Start phone connection");
        Button stop = new Button("Stop connection");
        scan.setOnAction(e -> scanGames());
        launch.setOnAction(e -> launchSelected());
        connect.setOnAction(e -> startServer());
        stop.setOnAction(e -> stopServer());

        games.setPlaceholder(new Label("No games found yet. Click Scan Steam libraries."));
        games.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(SteamGame g, boolean empty) {
                super.updateItem(g, empty);
                if (empty || g == null) setText(null);
                else setText(g.name() + "   •   AppID " + g.appId() + (g.isVrHint() ? "   •   VR candidate" : ""));
            }
        });

        VBox library = new VBox(12, section("Steam library"), games, new HBox(10, scan, launch));
        VBox.setVgrow(games, Priority.ALWAYS);
        library.setPadding(new Insets(18));
        library.setStyle("-fx-background-color: #171b29; -fx-background-radius: 14;");

        VBox phone = new VBox(12, section("Android phone / local XR"),
                new Label("Connect your Android phone to the same Wi-Fi network."),
                new HBox(10, connect, stop), phoneUrl,
                new Label("Phone features: live desktop preview, SBS viewer mode, orientation telemetry, touch input demo."),
                new Label("Note: true SteamVR/OpenXR head tracking needs a compatible runtime driver; this prototype does not inject pose data into games."));
        phone.setPadding(new Insets(18));
        phone.setStyle("-fx-background-color: #171b29; -fx-background-radius: 14;");
        phoneUrl.setStyle("-fx-text-fill: #72e3bd; -fx-font-size: 15px;");

        log.setEditable(false); log.setPrefRowCount(6);
        VBox bottom = new VBox(8, section("Activity"), log);
        bottom.setPadding(new Insets(16));
        VBox.setVgrow(log, Priority.ALWAYS);

        VBox content = new VBox(18, new VBox(4, brand, sub), new HBox(18, library, phone), bottom, status);
        HBox.setHgrow(library, Priority.ALWAYS); HBox.setHgrow(phone, Priority.ALWAYS);
        library.setPrefWidth(520); phone.setPrefWidth(390);
        content.setPadding(new Insets(22));
        content.setStyle("-fx-background-color: #0d1019; -fx-font-family: 'Segoe UI';");
        Scene scene = new Scene(content, 1100, 760);
        scene.getStylesheets().add(getClass().getResource("/novaxr.css") == null ? "" : getClass().getResource("/novaxr.css").toExternalForm());
        stage.setScene(scene); stage.show();
        scanGames();
    }

    private Label section(String text) {
        Label l = new Label(text); l.setStyle("-fx-text-fill: #f3f5ff; -fx-font-size: 17px; -fx-font-weight: bold;"); return l;
    }
    private void scanGames() {
        status.setText("Scanning Steam folders...");
        new Thread(() -> {
            List<SteamGame> found = scanner.scan();
            Platform.runLater(() -> {
                games.setItems(FXCollections.observableArrayList(found));
                status.setText("Found " + found.size() + " installed Steam games");
                log("Steam scan finished: " + found.size() + " games.");
            });
        }, "steam-scan").start();
    }
    private void launchSelected() {
        SteamGame game = games.getSelectionModel().getSelectedItem();
        if (game == null) { status.setText("Select a game first."); return; }
        try {
            SteamScanner.launch(game);
            log("Launch requested: " + game.name() + " (" + game.appId() + ")");
            status.setText("Sent launch request to Steam.");
        } catch (Exception ex) { log("Launch error: " + ex.getMessage()); status.setText("Could not launch game."); }
    }
    private void startServer() {
        if (server != null) { status.setText("Phone server is already running."); return; }
        try {
            server = new LocalCompanionServer(8765, 8766, this::log);
            server.start();
            String ip = InetAddress.getLocalHost().getHostAddress();
            phoneUrl.setText("Open on Android:  http://" + ip + ":8765/");
            status.setText("Phone companion running on ports 8765 (video/web) and 8766 (orientation).");
            log("Local companion started. LAN URL: http://" + ip + ":8765/");
        } catch (Exception ex) { log("Server error: " + ex.getMessage()); status.setText("Could not start phone server."); server = null; }
    }
    private void stopServer() {
        if (server != null) { server.stop(); server = null; phoneUrl.setText("Phone companion is stopped"); status.setText("Phone connection stopped."); log("Companion stopped."); }
    }
    private void log(String s) { Platform.runLater(() -> log.appendText(s + System.lineSeparator())); }
    @Override public void stop() { stopServer(); }
    public static void main(String[] args) { launch(args); }
}
