package view;

import controller.SimulationController;
import interfaces.Observer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.types.AccessState;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main JavaFX view — epidemic simulation.
 *
 * Layout : LEFT (controls) | CENTER (France map) | RIGHT (legend + log)
 * Implements Observer : controller calls update() after each step.
 */
public class SimulationView extends Application implements Observer {

    private Stage primaryStage;
    private SimulationController controller;

    // UI elements updated at runtime
    private Canvas mapCanvas;
    private Label  timerLabel;
    private Label  userLabel;
    private Label  messageLabel;
    private Button playPauseButton;

    // Currently open region popup (null = none open)
    private Stage  openPopup;
    private String openPopupRegion;

    // ── Region positions: name → (cx%, cy%) as fractions of MAP_W/MAP_H ──────
    // Positions are hand-tuned for a 520×600 canvas to approximate France's map.
    private static final Map<String, double[]> REGION_POS = new LinkedHashMap<>();
    static {
        REGION_POS.put("Hauts-de-France",          new double[]{0.52, 0.10});
        REGION_POS.put("Normandie",                 new double[]{0.30, 0.18});
        REGION_POS.put("Île-de-France",             new double[]{0.54, 0.24});
        REGION_POS.put("Grand Est",                 new double[]{0.72, 0.22});
        REGION_POS.put("Bretagne",                  new double[]{0.13, 0.30});
        REGION_POS.put("Pays de la Loire",          new double[]{0.27, 0.40});
        REGION_POS.put("Centre-Val de Loire",       new double[]{0.47, 0.38});
        REGION_POS.put("Bourgogne-Franche-Comté",  new double[]{0.66, 0.40});
        REGION_POS.put("Nouvelle-Aquitaine",        new double[]{0.27, 0.60});
        REGION_POS.put("Auvergne-Rhône-Alpes",     new double[]{0.63, 0.56});
        REGION_POS.put("Occitanie",                 new double[]{0.44, 0.74});
        REGION_POS.put("PACA",                      new double[]{0.68, 0.76});
        REGION_POS.put("Corse",                     new double[]{0.82, 0.88});
    }

    private static final int    MAP_W  = 520;
    private static final int    MAP_H  = 600;
    private static final double RADIUS = 26; // pixel radius of each region circle

    // =========================================================================
    // JavaFX start
    // =========================================================================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.controller   = new SimulationController(this);

        HBox root = new HBox(10,
            buildControlPanel(),
            buildMapPanel(),
            buildInfoPanel()
        );
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1a1a2e;");

        stage.setTitle("EpiSim – Simulation épidémique");
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();

        drawMap();
    }

    // =========================================================================
    // LEFT panel – controls
    // =========================================================================

    private VBox buildControlPanel() {
        VBox panel = styledPanel(200);

        // User selector
        userLabel = infoLabel("Invité");
        Button btnAdmin = actionButton("🔑 Admin");
        Button btnGuest = actionButton("👤 Invité");
        btnAdmin.setOnAction(e -> controller.loginAsAdmin());
        btnGuest.setOnAction(e -> controller.loginAsLambda());

        // Timer
        timerLabel = new Label("Jour 1 – Semaine 1");
        timerLabel.setFont(Font.font(13));
        timerLabel.setTextFill(Color.WHITE);

        // Play / Pause
        playPauseButton = actionButton("▶ Play");
        playPauseButton.setMaxWidth(Double.MAX_VALUE);
        playPauseButton.setOnAction(e -> controller.togglePlayPause());

        // Speed slider
        // The value listener only updates the display label — no side effects.
        // changeSpeed() is called only on MouseReleased (user finished dragging).
        Slider speedSlider = new Slider(1, 10, 1);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(3);
        Label speedValue = infoLabel("1×");
        speedSlider.valueProperty().addListener(
            (obs, o, n) -> speedValue.setText(String.format("%.0f×", n.doubleValue()))
        );
        speedSlider.setOnMouseReleased(
            e -> controller.changeSpeed(speedSlider.getValue())
        );

        // Save
        Button btnSave = actionButton("💾 Sauvegarder");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> handleSave());

        // Random event
        Button btnEvent = actionButton("⚡ Événement aléatoire");
        btnEvent.setMaxWidth(Double.MAX_VALUE);
        btnEvent.setOnAction(e -> controller.generateRandomEvent());

        panel.getChildren().addAll(
            sectionLabel("Utilisateur"),
            new HBox(6, btnAdmin, btnGuest), userLabel,
            new Separator(),
            sectionLabel("Temps"), timerLabel,
            new Separator(),
            playPauseButton,
            new Separator(),
            sectionLabel("Vitesse (×)"), speedSlider, speedValue,
            new Separator(),
            btnSave, btnEvent
        );
        return panel;
    }

    // =========================================================================
    // CENTER panel – France map
    // =========================================================================

    /**
     * Creates the Canvas and registers the click handler.
     * A click inside a region circle opens (or refreshes) the region popup.
     */
    private Pane buildMapPanel() {
        mapCanvas = new Canvas(MAP_W, MAP_H);

        mapCanvas.setOnMouseClicked(e -> {
            for (Map.Entry<String, double[]> entry : REGION_POS.entrySet()) {
                double cx   = entry.getValue()[0] * MAP_W;
                double cy   = entry.getValue()[1] * MAP_H;
                double dist = Math.hypot(e.getX() - cx, e.getY() - cy);
                if (dist <= RADIUS) {
                    String name = entry.getKey();
                    Region reg  = controller.getNationalGraph().getRegions().get(name);
                    if (reg != null) openRegionPopup(name, reg);
                    break;
                }
            }
        });

        StackPane pane = new StackPane(mapCanvas);
        pane.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 8;");
        return pane;
    }

    /**
     * Redraws all region circles on the canvas.
     * Circle color = region risk level (GREEN / ORANGE / RED).
     * We use fully-qualified models.types.Color to avoid clash with
     * javafx.scene.paint.Color (both are imported as "Color").
     */
    private void drawMap() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();

        gc.setFill(Color.web("#0f3460"));
        gc.fillRect(0, 0, MAP_W, MAP_H);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(13));
        gc.fillText("Carte épidémique – France  (clic = détails)", 8, 20);

        Map<String, Region> regions = controller.getNationalGraph().getRegions();

        for (Map.Entry<String, double[]> entry : REGION_POS.entrySet()) {
            String name   = entry.getKey();
            Region region = regions.get(name);
            if (region == null) continue;

            double cx = entry.getValue()[0] * MAP_W;
            double cy = entry.getValue()[1] * MAP_H;

            // Fully-qualified to avoid ambiguity with javafx Color
            Color fill = riskToColor(region.getRiskColor());

            gc.setFill(fill);
            gc.fillOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);

            // First word of the region name + infected count
            String shortName = name.split("[\\s\\-–]")[0];
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(9));
            gc.fillText(shortName,                       cx - RADIUS + 3, cy - 4);
            gc.fillText("I:" + region.getTotalInfected(), cx - RADIUS + 3, cy + 9);
        }
    }

    // =========================================================================
    // Region popup
    // =========================================================================

    /**
     * Opens a popup for the clicked region.
     * If the same popup is already open, it is refreshed instead of duplicated.
     *
     * initOwner(primaryStage) + initModality(NONE) is the correct JavaFX way
     * to get a non-blocking secondary window that renders reliably on all OS.
     */
    private void openRegionPopup(String regionName, Region region) {
        // Same region already open → just refresh
        if (openPopup != null && openPopup.isShowing()
                && regionName.equals(openPopupRegion)) {
            refreshPopup(regionName, region);
            return;
        }
        // Different region open → close it first
        if (openPopup != null && openPopup.isShowing()) {
            openPopup.close();
        }

        openPopupRegion = regionName;

        Stage popup = new Stage();
        popup.initOwner(primaryStage);     // attach to main window → renders correctly
        popup.initModality(Modality.NONE); // non-blocking: simulation keeps running
        popup.setTitle("Région : " + regionName);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #8192c3; -fx-background-color: #16213e;");
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setContent(buildRegionContent(regionName, region));

        popup.setScene(new Scene(scroll, 360, 680));
        popup.getScene().setUserData(scroll); // store reference for refresh
        openPopup = popup;

        popup.setOnHidden(e -> { openPopup = null; openPopupRegion = null; });
        popup.show();
    }

    /**
     * Rebuilds the popup's inner content with fresh data.
     * Called both on open and from update() while simulation runs.
     */
    private void refreshPopup(String regionName, Region region) {
        if (openPopup == null || !openPopup.isShowing()) return;
        ScrollPane scroll = (ScrollPane) openPopup.getScene().getUserData();
        scroll.setContent(buildRegionContent(regionName, region));
    }

    /**
     * Builds the VBox displayed inside the popup.
     *
     * Each city shows:
     *   - colored dot + name (color = city risk, same logic as the map)
     *   - SEIR numbers
     *   - infection rate %
     *
     * Each route shows:
     *   - status icon (✅ open / 🚧 barricaded)
     *   - Admin button to toggle open ↔ barricaded in one click
     *
     * Routes are useful here: the admin can react to a high-risk city by
     * barricading its routes directly from the popup, without any extra menu.
     */
    private VBox buildRegionContent(String regionName, Region region) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        Label header = new Label("📍 " + regionName);
        header.setFont(Font.font(15));
        header.setTextFill(Color.WHITE);
        content.getChildren().add(header);

        // ── Cities ──────────────────────────────────────────────────────────
        content.getChildren().add(sectionLabel("Villes"));

        for (City city : region.getRegionalGraph().getCities().values()) {
            VBox card = new VBox(3);
            card.setPadding(new Insets(6));
            // Card background color = city risk level (subtle, not full red/green)
            card.setStyle(
                "-fx-background-color: " + cityCardBackground(city.getRiskColor()) + ";" +
                "-fx-background-radius: 5;"
            );

            // City name with colored dot
            String dot = riskToDot(city.getRiskColor());
            Label nameL = new Label(dot + "  " + city.getName());
            nameL.setFont(Font.font(13));
            nameL.setTextFill(Color.WHITE);

            Label seirL = infoLabel(
                "Sains: "   + city.getSafe()     + "   " +
                "Exposés: " + city.getExposed()
            );
            Label seirL2 = infoLabel(
                "Infectés: " + city.getInfected() + "   " +
                "Guéris: "   + city.getRecovered()
            );
            Label rateL = infoLabel(
                String.format("Taux d'infection : %.1f%%", city.getInfectionRate() * 100)
            );

            card.getChildren().addAll(nameL, seirL, seirL2, rateL);
            content.getChildren().add(card);
        }

        // ── Routes ──────────────────────────────────────────────────────────
        content.getChildren().add(sectionLabel("Routes"));

        for (Route route : region.getRegionalGraph().getRoutes()) {
            HBox routeRow = new HBox(8);
            routeRow.setPadding(new Insets(3));

            // Status icon + city names
            String icon   = routeIcon(route.getAccess());
            String label  = icon + " " +
                            route.getCityA().getName() + " ↔ " +
                            route.getCityB().getName();
            Label routeL  = infoLabel(label);
            HBox.setHgrow(routeL, Priority.ALWAYS);
            routeRow.getChildren().add(routeL);

            // Admin-only toggle button: open ↔ barricade this specific route
            if (controller.isAdmin()) {
                String btnText = (route.getAccess() == AccessState.BARRICATED)
                    ? "✅ Ouvrir" : "🚧 Bloquer";
                Button toggleBtn = smallButton(btnText);
                toggleBtn.setOnAction(e -> {
                    // Toggle the route state directly
                    if (route.getAccess() == AccessState.BARRICATED) {
                        route.setAccess(AccessState.OPEN);
                        showMessage("Route ouverte : " + route.getCityA().getName()
                                    + " ↔ " + route.getCityB().getName());
                    } else {
                        route.setAccess(AccessState.BARRICATED);
                        showMessage("🚧 Route bloquée : " + route.getCityA().getName()
                                    + " ↔ " + route.getCityB().getName());
                    }
                    // Refresh the popup immediately so the button label updates
                    refreshPopup(regionName, region);
                });
                routeRow.getChildren().add(toggleBtn);
            }

            content.getChildren().add(routeRow);
        }

        return content;
    }

    // =========================================================================
    // RIGHT panel – legend + log
    // =========================================================================

    private VBox buildInfoPanel() {
        VBox panel = styledPanel(175);

        messageLabel = new Label("–");
        messageLabel.setTextFill(Color.LIGHTGRAY);
        messageLabel.setFont(Font.font(11));
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(155);

        panel.getChildren().addAll(
            sectionLabel("Légende – régions"),
            coloredLabel("🟢 Faible  (< 30%)",  Color.web("#81c784")),
            coloredLabel("🟠 Modéré (30–60%)",  Color.web("#ffb74d")),
            coloredLabel("🔴 Élevé   (> 60%)",  Color.web("#e57373")),
            new Separator(),
            sectionLabel("Derniers événements"),
            messageLabel
        );
        return panel;
    }

    // =========================================================================
    // Observer – called after each simulation step
    // =========================================================================

    /**
     * Refreshes the map and the open popup (if any).
     * Platform.runLater ensures we are on the JavaFX Application Thread,
     * since this can be called from a Timeline callback.
     */
    @Override
    public void update() {
        Platform.runLater(() -> {
            drawMap();

            if (openPopup != null && openPopup.isShowing() && openPopupRegion != null) {
                Region r = controller.getNationalGraph()
                                     .getRegions().get(openPopupRegion);
                if (r != null) refreshPopup(openPopupRegion, r);
            }

            timerLabel.setText(
                "Jour "    + controller.getSimulationModel().getCurrentDay()  +
                " – Sem. " + controller.getSimulationModel().getCurrentWeek() +
                "  (J+"    + controller.getSimulationModel().getTotalDays()   + ")"
            );
        });
    }

    // =========================================================================
    // Methods called by the controller
    // =========================================================================

    public void setPlayPauseButton(String text) { playPauseButton.setText(text); }
    public void showMessage(String msg)          { messageLabel.setText(msg); }
    public void updateUserLabel(String text)     { userLabel.setText(text); }

    public void renderRegionalGrid(String regionName) {
        drawMap();
        if (regionName.equals(openPopupRegion)) {
            Region r = controller.getNationalGraph().getRegions().get(regionName);
            if (r != null) refreshPopup(regionName, r);
        }
    }

    // =========================================================================
    // Save
    // =========================================================================

    private void handleSave() {
        if (!controller.isAdmin()) {
            showMessage("⚠ Sauvegarde réservée à l'Admin.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sauvegarder la simulation");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichier JSON", "*.json")
        );
        File file = chooser.showSaveDialog(primaryStage);
        if (file != null) {
            controller.getSimulationModel()
                      .getPersistenceManager()
                      .save(controller.getSimulationModel(), file.getAbsolutePath());
            showMessage("💾 Sauvegardé : " + file.getName());
        }
    }

    // =========================================================================
    // Color helpers — all use fully-qualified models.types.Color to avoid
    // collision with javafx.scene.paint.Color
    // =========================================================================

    /** Converts a model risk color to a JavaFX fill color for the map circles. */
    private Color riskToColor(models.types.Color risk) {
        if (risk == models.types.Color.RED)    return Color.web("#c62828", 0.90);
        if (risk == models.types.Color.ORANGE) return Color.web("#e65100", 0.90);
        return Color.web("#2e7d32", 0.90);
    }

    /** Emoji dot matching a model risk color. */
    private String riskToDot(models.types.Color risk) {
        if (risk == models.types.Color.RED)    return "🔴";
        if (risk == models.types.Color.ORANGE) return "🟠";
        return "🟢";
    }

    /**
     * Subtle card background color for each city card in the popup.
     * Less saturated than the map circles so the text stays readable.
     */
    private String cityCardBackground(models.types.Color risk) {
        if (risk == models.types.Color.RED)    return "#4a1010";
        if (risk == models.types.Color.ORANGE) return "#4a2c00";
        return "#0f3460";
    }

    /** Icon for a route access state. */
    private String routeIcon(AccessState access) {
        if (access == AccessState.BARRICATED) return "🚧";
        if (access == AccessState.RESTRICTED) return "⚠";
        return "✅";
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private VBox styledPanel(int width) {
        VBox p = new VBox(10);
        p.setPrefWidth(width);
        p.setPadding(new Insets(10));
        p.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        return p;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font(13));
        l.setTextFill(Color.web("#90caf9"));
        return l;
    }

    private Label infoLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.LIGHTGRAY);
        l.setFont(Font.font(11));
        return l;
    }

    private Label coloredLabel(String text, Color color) {
        Label l = new Label(text);
        l.setTextFill(color);
        l.setFont(Font.font(12));
        return l;
    }

    private Button actionButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: #0f3460;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-cursor: hand;"
        );
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: #1a3a5c;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;"
        );
        return b;
    }

    public static void main(String[] args) { launch(args); }
}
