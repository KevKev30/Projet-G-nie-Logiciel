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
        scroll.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
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
/*package view;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import controller.SimulationController;
import interfaces.Observer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.RegionalGraph;
import models.types.AccessState;

public class SimulationView extends Application implements Observer{

    private SimulationController controller;
    private BorderPane root;
    private Label messageLabel;
    private Label userLabel;
    private Button playPauseBtn;
    private String currentRegion = null;
    private VBox rightPanel;
    private Pane mapPane;

    // Stats globales
    private Label statCasActifs;
    private Label statTauxNational;
    private Label statZones;

    // Mapping nom GeoJSON → nom Region
    private static final Map<String, String> GEO_TO_REGION = new HashMap<>();
    static {
        GEO_TO_REGION.put("Île-de-France",        "Île-de-France");
        GEO_TO_REGION.put("Bretagne",              "Bretagne");
        GEO_TO_REGION.put("Provence-Alpes-Côte d'Azur", "PACA");
        GEO_TO_REGION.put("Normandie",             "Normandie");
    }

    private static final int CELL_SIZE = 85;
    private static final int CELL_GAP  = 6;

    @Override
    public void start(Stage stage) {
        controller = new SimulationController(this);
        controller.getSimulationModel().attach(this);
        root = new BorderPane();
        root.setTop(buildNavBar());
        root.setBottom(buildBottomBar());
        root.setRight(buildRightPanel());
        renderNationalMap();

        Scene scene = new Scene(root, 1150, 720);
        stage.setTitle("EpiSim — Observatoire Epidémiologique National");
        stage.setScene(scene);
        stage.show();
    }

    // ==================== NAVBAR ====================

    private HBox buildNavBar() {
        Label logo = new Label("🦠 EpiSim");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Button carteBtn = buildNavBtn("Carte", true);
        carteBtn.setOnAction(e -> renderNationalMap());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        userLabel = new Label("👤 Visiteur");
        userLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");

        Button loginBtn = new Button("🔑 Connexion Expert");
        loginBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: white;" +
            "-fx-border-color: white;" +
            "-fx-border-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 12 5 12;"
        );
        loginBtn.setOnAction(e -> showLoginDialog());

        HBox nav = new HBox(20, logo,
            buildNavBtn("Carte", true),
            buildNavBtn("Simulation", false),
            buildNavBtn("Données", false),
            spacer, userLabel, loginBtn
        );
        nav.setPadding(new Insets(12, 20, 12, 20));
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle("-fx-background-color: #1a252f;");
        return nav;
    }

    private Button buildNavBtn(String text, boolean active) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + (active ? "white" : "#aaa") + ";" +
            "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 5 10 5 10;" +
            (active ? "-fx-border-color: transparent transparent white transparent; -fx-border-width: 0 0 2 0;" : "")
        );
        return btn;
    }

    // ==================== PANEL DROIT ====================

    private VBox buildRightPanel() {
        rightPanel = new VBox(0);
        rightPanel.setPrefWidth(270);
        rightPanel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1;");

        // Légende
        VBox legendSection = buildSection("Niveau de risque", "#2c3e50");
        legendSection.getChildren().addAll(
            buildRiskItem("🔴", "Élevé   (> 60%)",  "#e74c3c"),
            buildRiskItem("🟠", "Modéré (30-60%)", "#e67e22"),
            buildRiskItem("🟢", "Faible  (< 30%)",  "#27ae60")
        );

        // Région sélectionnée
        VBox regionSection = buildSection("Sélectionnez une région", "#2c3e50");
        regionSection.setId("regionSection");
        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        regionSection.getChildren().add(hint);

        // Contrôles
        VBox simSection = buildSection("Contrôles", "#2c3e50");

        playPauseBtn = new Button("▶  Lancer");
        styleBtn(playPauseBtn, "#27ae60");
        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setOnAction(e -> controller.togglePlayPause());

        Button stepBtn = new Button("⏭  Étape suivante");
        styleBtn(stepBtn, "#8e44ad");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> controller.stepForward());

        Button randomBtn = new Button("⚡  Événement aléatoire");
        styleBtn(randomBtn, "#e67e22");
        randomBtn.setMaxWidth(Double.MAX_VALUE);
        randomBtn.setOnAction(e -> controller.generateRandomEvent());

        Label speedLbl = new Label("Vitesse");
        speedLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        Slider speedSlider = new Slider(0.5, 3.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.valueProperty().addListener(
            (obs, o, n) -> controller.changeSpeed(n.doubleValue())
        );

        simSection.getChildren().addAll(
            playPauseBtn, stepBtn, randomBtn, speedLbl, speedSlider
        );

        rightPanel.getChildren().addAll(
            legendSection,
            new Separator(),
            regionSection,
            new Separator(),
            simSection
        );
        return rightPanel;
    }

    private VBox buildSection(String title, String color) {
        Label lbl = new Label(title);
        lbl.setStyle(
            "-fx-text-fill: " + color + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 0 0 6 0;"
        );
        VBox section = new VBox(8, lbl);
        section.setPadding(new Insets(15));
        return section;
    }

    private HBox buildRiskItem(String emoji, String text, String color) {
        Label dot = new Label(emoji);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        HBox item = new HBox(8, dot, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void styleBtn(Button btn, String color) {
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;" +
            "-fx-background-radius: 4;"
        );
    }

    // ==================== BARRE BAS ====================

    private HBox buildBottomBar() {
        statCasActifs    = new Label("0");
        statTauxNational = new Label("0.00%");
        statZones        = new Label("0");
        messageLabel     = new Label("Bienvenue sur EpiSim");

        HBox bar = new HBox(0,
            buildStatCell(statCasActifs,    "Cas actifs France", "#e74c3c"),
            buildStatCell(statTauxNational, "Taux national",     "#e67e22"),
            buildStatCell(statZones,        "Zones surveillées", "#3498db"),
            buildMsgCell()
        );
        bar.setStyle(
            "-fx-background-color: #1a252f;" +
            "-fx-border-color: #2c3e50;" +
            "-fx-border-width: 1 0 0 0;"
        );
        return bar;
    }

    private VBox buildStatCell(Label val, String title, String color) {
        val.setStyle(
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + color + ";"
        );
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        VBox cell = new VBox(2, val, lbl);
        cell.setPadding(new Insets(10, 20, 10, 20));
        cell.setAlignment(Pos.CENTER);
        cell.setStyle(
            "-fx-border-color: transparent #2c3e50 transparent transparent;" +
            "-fx-border-width: 0 1 0 0;"
        );
        return cell;
    }

    private HBox buildMsgCell() {
        messageLabel.setStyle("-fx-text-fill: #aaa; -fx-font-style: italic; -fx-font-size: 12px;");
        HBox cell = new HBox(messageLabel);
        cell.setPadding(new Insets(10, 20, 10, 20));
        cell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    // ==================== CARTE DE FRANCE ====================

    public void renderNationalMap() {
        currentRegion = null;
        updateBottomStats();

        mapPane = new Pane();
        mapPane.setStyle("-fx-background-color: #eaf2f8;");

        try {
            // Charger le GeoJSON
            InputStream is = getClass().getResourceAsStream("/regions.geojson");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject geojson = new JSONObject(json);
            JSONArray features = geojson.getJSONArray("features");

            // Calculer les bornes géographiques
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject geometry = feature.getJSONObject("geometry");
                String type = geometry.getString("type");

                if (type.equals("Polygon")) {
                    JSONArray coords = geometry.getJSONArray("coordinates")
                        .getJSONArray(0);
                    for (int j = 0; j < coords.length(); j++) {
                        double lon = coords.getJSONArray(j).getDouble(0);
                        double lat = coords.getJSONArray(j).getDouble(1);
                        minLon = Math.min(minLon, lon);
                        maxLon = Math.max(maxLon, lon);
                        minLat = Math.min(minLat, lat);
                        maxLat = Math.max(maxLat, lat);
                    }
                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    for (int p = 0; p < polys.length(); p++) {
                        JSONArray coords = polys.getJSONArray(p).getJSONArray(0);
                        for (int j = 0; j < coords.length(); j++) {
                            double lon = coords.getJSONArray(j).getDouble(0);
                            double lat = coords.getJSONArray(j).getDouble(1);
                            minLon = Math.min(minLon, lon);
                            maxLon = Math.max(maxLon, lon);
                            minLat = Math.min(minLat, lat);
                            maxLat = Math.max(maxLat, lat);
                        }
                    }
                }
            }

            double mapW = 750.0;
            double mapH = 580.0;
            double padding = 20.0;
            final double fMinLon = minLon, fMaxLon = maxLon;
            final double fMinLat = minLat, fMaxLat = maxLat;

            // Dessiner chaque région
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature  = features.getJSONObject(i);
                JSONObject props    = feature.getJSONObject("properties");
                JSONObject geometry = feature.getJSONObject("geometry");
                String geoName = props.getString("nom");
                String type    = geometry.getString("type");

                // Trouver la région correspondante
                String regionName = GEO_TO_REGION.getOrDefault(geoName, geoName);
                Region region = controller.getNationalGraph()
                    .getRegions().get(regionName);

                // Couleur selon risque
                Color fillColor;
                if (region != null) {
                    region.totalInfectedGraph();
                    fillColor = convertColor(region.getRiskColor());
                } else {
                    fillColor = Color.valueOf("#bdc3c7");
                }

                // Dessiner polygone(s)
                if (type.equals("Polygon")) {
                    JSONArray coords = geometry.getJSONArray("coordinates")
                        .getJSONArray(0);
                    Polygon poly = buildPolygon(
                        coords, mapW, mapH, padding,
                        fMinLon, fMaxLon, fMinLat, fMaxLat,
                        fillColor, geoName, regionName, region
                    );
                    mapPane.getChildren().add(poly);

                    // Label centroïde
                    double[] center = getCentroid(coords,
                        mapW, mapH, padding,
                        fMinLon, fMaxLon, fMinLat, fMaxLat);
                    addRegionLabel(geoName, center[0], center[1], region);

                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    double[] firstCenter = null;
                    for (int p = 0; p < polys.length(); p++) {
                        JSONArray coords = polys.getJSONArray(p)
                            .getJSONArray(0);
                        Polygon poly = buildPolygon(
                            coords, mapW, mapH, padding,
                            fMinLon, fMaxLon, fMinLat, fMaxLat,
                            fillColor, geoName, regionName, region
                        );
                        mapPane.getChildren().add(poly);
                        if (p == 0) {
                            firstCenter = getCentroid(coords,
                                mapW, mapH, padding,
                                fMinLon, fMaxLon, fMinLat, fMaxLat);
                        }
                    }
                    if (firstCenter != null) {
                        addRegionLabel(geoName, firstCenter[0], firstCenter[1], region);
                    }
                }
            }

        } catch (Exception e) {
            showMessage("Erreur chargement carte : " + e.getMessage());
            e.printStackTrace();
        }

        Label title = new Label("🗺  Carte Epidémiologique — cliquez sur une région");
        title.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;" +
            "-fx-padding: 10 15 8 15;"
        );

        ScrollPane scroll = new ScrollPane(mapPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #eaf2f8; -fx-background: #eaf2f8;");

        VBox center = new VBox(title, new Separator(), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(center);

        updateRightPanelDefault();
    }

    private Polygon buildPolygon(
            JSONArray coords,
            double mapW, double mapH, double padding,
            double minLon, double maxLon, double minLat, double maxLat,
            Color fillColor, String geoName, String regionName, Region region) {

        Polygon poly = new Polygon();
        for (int j = 0; j < coords.length(); j++) {
            double lon = coords.getJSONArray(j).getDouble(0);
            double lat = coords.getJSONArray(j).getDouble(1);
            double x = padding + (lon - minLon) / (maxLon - minLon) * (mapW - 2 * padding);
            double y = padding + (maxLat - lat) / (maxLat - minLat) * (mapH - 2 * padding);
            poly.getPoints().addAll(x, y);
        }

        poly.setFill(fillColor);
        poly.setStroke(Color.WHITE);
        poly.setStrokeWidth(1.5);

        // Hover
        poly.setOnMouseEntered(e -> {
            poly.setStroke(Color.valueOf("#2c3e50"));
            poly.setStrokeWidth(2.5);
            poly.setFill(fillColor.brighter());
        });
        poly.setOnMouseExited(e -> {
            poly.setStroke(Color.WHITE);
            poly.setStrokeWidth(1.5);
            poly.setFill(fillColor);
        });

        // Clic
        poly.setOnMouseClicked(e -> {
            if (region != null) {
                renderRegionalGrid(regionName);
                updateRightPanelRegion(region);
            } else {
                showMessage("ℹ Région '" + geoName + "' non configurée dans la simulation");
            }
        });
        poly.setStyle("-fx-cursor: hand;");

        // Tooltip
        String tipText = region != null
            ? geoName + "\nInfectés : " + region.getTotalInfected()
            : geoName + "\n(non configurée)";
        Tooltip.install(poly, new Tooltip(tipText));

        return poly;
    }

    private double[] getCentroid(
            JSONArray coords,
            double mapW, double mapH, double padding,
            double minLon, double maxLon, double minLat, double maxLat) {

        double sumX = 0, sumY = 0;
        for (int j = 0; j < coords.length(); j++) {
            double lon = coords.getJSONArray(j).getDouble(0);
            double lat = coords.getJSONArray(j).getDouble(1);
            sumX += padding + (lon - minLon) / (maxLon - minLon) * (mapW - 2 * padding);
            sumY += padding + (maxLat - lat) / (maxLat - minLat) * (mapH - 2 * padding);
        }
        return new double[]{sumX / coords.length(), sumY / coords.length()};
    }

    private void addRegionLabel(String name, double x, double y, Region region) {
        Label lbl = new Label(name);
        lbl.setStyle(
            "-fx-font-size: 9px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0, 0, 1);"
        );
        lbl.setLayoutX(x - 30);
        lbl.setLayoutY(y - 8);
        lbl.setMouseTransparent(true);

        if (region != null) {
            Label infLbl = new Label("🦠 " + region.getTotalInfected());
            infLbl.setStyle(
                "-fx-font-size: 8px;" +
                "-fx-text-fill: white;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0, 0, 1);"
            );
            infLbl.setLayoutX(x - 20);
            infLbl.setLayoutY(y + 4);
            infLbl.setMouseTransparent(true);
            mapPane.getChildren().add(infLbl);
        }

        mapPane.getChildren().add(lbl);
    }

    // ==================== VUE RÉGIONALE ====================

    public void renderRegionalGrid(String regionName) {
        currentRegion = regionName;
        Region region = controller.getNationalGraph()
            .getRegions().get(regionName);
        if (region == null) return;

        RegionalGraph rg = region.getRegionalGraph();
        List<City> cities = new ArrayList<>(rg.getCities().values());
        int cols = (int) Math.ceil(Math.sqrt(cities.size()));

        // Positions dans la grille
        Map<String, double[]> positions = new HashMap<>();
        for (int i = 0; i < cities.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            positions.put(cities.get(i).getName(), new double[]{
                col * (CELL_SIZE + CELL_GAP * 4) + 20,
                row * (CELL_SIZE + CELL_GAP * 4) + 20
            });
        }

        Pane gridPane = new Pane();
        gridPane.setStyle("-fx-background-color: #f8f9fa;");
        gridPane.setPrefSize(
            cols * (CELL_SIZE + CELL_GAP * 4) + 40,
            (int) Math.ceil((double) cities.size() / cols) * (CELL_SIZE + CELL_GAP * 4) + 40
        );

        // Routes
        for (Route route : rg.getRoutes()) {
            double[] posA = positions.get(route.getCityA().getName());
            double[] posB = positions.get(route.getCityB().getName());
            if (posA == null || posB == null) continue;

            double x1 = posA[0] + CELL_SIZE / 2.0;
            double y1 = posA[1] + CELL_SIZE / 2.0;
            double x2 = posB[0] + CELL_SIZE / 2.0;
            double y2 = posB[1] + CELL_SIZE / 2.0;

            boolean blocked = route.getAccess() == AccessState.BARRICATED;
            Line line = new Line(x1, y1, x2, y2);
            line.setStroke(blocked
                ? Color.valueOf("#e74c3c")
                : Color.valueOf("#3498db"));
            line.setStrokeWidth(blocked ? 2 : 3);
            if (blocked) line.getStrokeDashArray().addAll(8.0, 4.0);

            final String cA = route.getCityA().getName();
            final String cB = route.getCityB().getName();
            line.setOnMouseClicked(e -> {
                if (controller.isAdmin())
                    controller.toggleRoute(regionName, cA, cB);
                else
                    showMessage("⛔ Réservé à l'Expert !");
            });
            line.setStyle("-fx-cursor: hand;");
            Tooltip.install(line, new Tooltip(
                (blocked ? "🚧 Bloquée" : "✅ Ouverte")
                + " — " + cA + " ↔ " + cB
            ));
            gridPane.getChildren().add(line);
        }

        // Cellules
        for (City city : cities) {
            double[] pos = positions.get(city.getName());
            if (pos == null) continue;

            Color bg = convertColor(city.getRiskColor());

            Rectangle cell = new Rectangle(
                pos[0], pos[1], CELL_SIZE, CELL_SIZE);
            cell.setFill(bg);
            cell.setArcWidth(10);
            cell.setArcHeight(10);
            cell.setStroke(Color.WHITE);
            cell.setStrokeWidth(2);

            // Effet hover
            cell.setOnMouseEntered(e -> {
                cell.setFill(bg.brighter());
                cell.setStrokeWidth(3);
            });
            cell.setOnMouseExited(e -> {
                cell.setFill(bg);
                cell.setStrokeWidth(2);
            });

            Label nameLbl = new Label(city.getName());
            nameLbl.setLayoutX(pos[0] + 5);
            nameLbl.setLayoutY(pos[1] + 5);
            nameLbl.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 10px;"
            );
            nameLbl.setMaxWidth(CELL_SIZE - 10);
            nameLbl.setMouseTransparent(true);

            Label infLbl = new Label("🦠 " + city.getInfected());
            infLbl.setLayoutX(pos[0] + 5);
            infLbl.setLayoutY(pos[1] + CELL_SIZE - 30);
            infLbl.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
            infLbl.setMouseTransparent(true);

            double rate = city.getInfectionRate() * 100;
            Label rateLbl = new Label(String.format("%.0f%%", rate));
            rateLbl.setLayoutX(pos[0] + CELL_SIZE - 32);
            rateLbl.setLayoutY(pos[1] + CELL_SIZE - 18);
            rateLbl.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;"
            );
            rateLbl.setMouseTransparent(true);

            // Barre de progression
            Rectangle pbBg = new Rectangle(
                pos[0] + 4, pos[1] + CELL_SIZE - 8,
                CELL_SIZE - 8, 5);
            pbBg.setFill(Color.valueOf("rgba(255,255,255,0.3)"));
            pbBg.setArcWidth(3);
            pbBg.setArcHeight(3);
            pbBg.setMouseTransparent(true);

            double pw = (CELL_SIZE - 8) * Math.min(rate / 100, 1.0);
            Rectangle pb = new Rectangle(
                pos[0] + 4, pos[1] + CELL_SIZE - 8, pw, 5);
            pb.setFill(Color.WHITE);
            pb.setArcWidth(3);
            pb.setArcHeight(3);
            pb.setMouseTransparent(true);

            Tooltip tip = new Tooltip(
                "📍 " + city.getName() + "\n" +
                "─────────────────\n" +
                "Sains     : " + city.getSafe()      + "\n" +
                "Exposés : " + city.getExposed()    + "\n" +
                "Infectés  : " + city.getInfected()  + "\n" +
                "Guéris    : " + city.getRecovered() + "\n" +
                String.format("Taux      : %.1f%%", rate)
            );
            Tooltip.install(cell, tip);

            cell.setOnMouseClicked(e -> {
                if (controller.isAdmin())
                    showAdminCityPanel(regionName, city.getName());
                else
                    showMessage("🔒 Connexion Expert requise");
            });
            cell.setStyle("-fx-cursor: hand;");

            gridPane.getChildren().addAll(
                cell, nameLbl, infLbl, rateLbl, pbBg, pb
            );
        }

        // Bouton retour
        Button backBtn = new Button("← Carte Nationale");
        styleBtn(backBtn, "#2c3e50");
        backBtn.setOnAction(e -> renderNationalMap());

        Label title = new Label("📍 " + regionName);
        title.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        // Panel admin si connecté
        HBox topBar = new HBox(15, backBtn, title);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-border-color: #dee2e6;" +
            "-fx-border-width: 0 0 1 0;"
        );

        if (controller.isAdmin()) {
            HBox adminBar = buildAdminBar();
            topBar.getChildren().add(adminBar);
        }

        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-background: #f8f9fa;"
        );

        VBox center = new VBox(topBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(center);
    }

    private HBox buildAdminBar() {
        Label lbl = new Label("🔧 Mode Expert — clic sur ville : modifier | clic sur route : bloquer/ouvrir");
        lbl.setStyle(
            "-fx-text-fill: #e67e22;" +
            "-fx-font-size: 11px;" +
            "-fx-font-style: italic;"
        );
        HBox bar = new HBox(lbl);
        bar.setPadding(new Insets(0, 0, 0, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ==================== PANEL DROIT DYNAMIQUE ====================

    private void updateRightPanelDefault() {
        VBox section = (VBox) rightPanel.lookup("#regionSection");
        if (section == null) return;
        section.getChildren().clear();

        Label title = new Label("Sélectionnez une région");
        title.setStyle(
            "-fx-text-fill: #2c3e50;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;"
        );
        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        section.getChildren().addAll(title, hint);
    }

    private void updateRightPanelRegion(Region region) {
        VBox section = (VBox) rightPanel.lookup("#regionSection");
        if (section == null) return;
        section.getChildren().clear();

        String colorHex = toHex(convertColor(region.getRiskColor()));

        Label title = new Label(region.getName());
        title.setStyle(
            "-fx-text-fill: " + colorHex + ";" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;"
        );

        String riskText = region.getRiskColor() == models.types.Color.RED
            ? "Risque élevé"
            : region.getRiskColor() == models.types.Color.ORANGE
            ? "Risque modéré" : "Faible risque";

        Label badge = new Label(riskText);
        badge.setStyle(
            "-fx-background-color: " + colorHex + ";" +
            "-fx-text-fill: white;" +
            "-fx-padding: 3 8 3 8;" +
            "-fx-background-radius: 10;" +
            "-fx-font-size: 11px;"
        );

        int totalInf = region.getTotalInfected();
        int totalPop = 0;
        for (City c : region.getRegionalGraph().getCities().values())
            totalPop += c.getTotalPopulation();
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        section.getChildren().addAll(
            title, badge,
            new Separator(),
            buildInfoLine("Cas actifs",  "" + totalInf,               colorHex),
            buildInfoLine("Taux",        String.format("%.2f%%", rate), colorHex),
            buildInfoLine("Population",  "" + totalPop,                "#2c3e50"),
            new Separator()
        );

        // Actions rapides
        Label actTitle = new Label("Actions rapides");
        actTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Button simuBtn = new Button("▶  Voir simulation");
        simuBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(simuBtn, "#2980b9");
        simuBtn.setOnAction(e -> renderRegionalGrid(region.getName()));

        Button exportBtn = new Button("↓  Exporter données");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(exportBtn, "#27ae60");
        exportBtn.setOnAction(e ->
            showMessage("📥 Export : " + region.getName())
        );

        Button alertBtn = new Button("🔔  Activer alertes");
        alertBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(alertBtn, "#e67e22");

        section.getChildren().addAll(actTitle, simuBtn, exportBtn, alertBtn);
    }

    private Label buildInfoLine(String key, String val, String color) {
        Label lbl = new Label(key + " : " + val);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        return lbl;
    }

    // ==================== LOGIN ====================

    private void showLoginDialog() {
        Stage popup = new Stage();
        popup.setTitle("Connexion Expert");

        Label title = new Label("🔑 Connexion Expert");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        TextField userField = new TextField();
        userField.setPromptText("Identifiant");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Mot de passe");

        Label hint = new Label("admin / admin pour accès expert");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10px;");

        Button loginBtn = new Button("Se connecter");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(loginBtn, "#27ae60");
        loginBtn.setOnAction(e -> {
            if (userField.getText().equals("admin")
                    && passField.getText().equals("admin")) {
                controller.loginAsAdmin();
                popup.close();
            } else if (userField.getText().equals("user")) {
                controller.loginAsLambda();
                popup.close();
            } else {
                hint.setText("❌ Identifiants incorrects !");
                hint.setStyle("-fx-text-fill: #e74c3c;");
            }
        });

        VBox layout = new VBox(12,
            title, userField, passField, hint, loginBtn
        );
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: white;");

        popup.setScene(new Scene(layout, 280, 240));
        popup.show();
    }

    // ==================== ADMIN VILLE ====================

    private void showAdminCityPanel(String regionName, String cityName) {
        Stage popup = new Stage();
        popup.setTitle("Modifier : " + cityName);

        Label title = new Label("📍 " + cityName);
        title.setStyle(
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );
        Label sub = new Label("Modifier le niveau de risque");
        sub.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        Button g = buildColorBtn("🟢 Faible risque", "#27ae60", models.types.Color.GREEN, regionName, cityName, popup);
        Button o = buildColorBtn("🟠 Risque modéré", "#e67e22", models.types.Color.ORANGE, regionName, cityName, popup);
        Button r = buildColorBtn("🔴 Risque élevé",  "#e74c3c", models.types.Color.RED,    regionName, cityName, popup);
        VBox layout = new VBox(10, title, sub, g, o, r);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        popup.setScene(new Scene(layout, 260, 220));
        popup.show();
    }

    private Button buildColorBtn(String text, String color,
            models.types.Color modelColor,
            String regionName, String cityName, Stage popup) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(btn, color);
        btn.setOnAction(e -> {
            controller.setCityColor(regionName, cityName, modelColor);
            popup.close();
        });
        return btn;
    }

    // ==================== UTILITAIRES ====================

    public void update() {
        updateBottomStats();
        if (currentRegion != null) renderRegionalGrid(currentRegion);
        else renderNationalMap();
    }

    private void updateBottomStats() {
        int totalInf = 0, totalPop = 0, zones = 0;
        for (Region r : controller.getNationalGraph().getRegions().values()) {
            r.totalInfectedGraph();
            totalInf += r.getTotalInfected();
            zones    += r.getRegionalGraph().getCities().size();
            for (City c : r.getRegionalGraph().getCities().values())
                totalPop += c.getTotalPopulation();
        }
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;
        statCasActifs.setText("" + totalInf);
        statTauxNational.setText(String.format("%.2f%%", rate));
        statZones.setText("" + zones);
    }

    public void showMessage(String msg) {
        messageLabel.setText(msg);
    }

    public void updateUserLabel(String text) {
        userLabel.setText("👤 " + text);
        update();
    }

    public void setPlayPauseButton(String text) {
        playPauseBtn.setText(text);
        styleBtn(playPauseBtn,
            text.contains("Lancer") || text.contains("Play")
                ? "#27ae60" : "#e74c3c");
    }

    private Color convertColor(models.types.Color color) {
        switch (color) {
            case RED:    return Color.valueOf("#e74c3c");
            case ORANGE: return Color.valueOf("#e67e22");
            default:     return Color.valueOf("#27ae60");
        }
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int)(color.getRed()   * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue()  * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }
}*/