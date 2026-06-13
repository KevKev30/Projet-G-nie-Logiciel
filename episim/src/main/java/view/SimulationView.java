package view;

import controller.SimulationController;
import interfaces.Observer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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
import models.graph.NationalGraph;
import models.types.AccessState;

import java.io.File;
import java.util.Map;

/**
 * Main JavaFX view — Epidemic Simulation.
 *
 * The window has two tabs:
 *
 *   [Temps Réel]   — the live simulation running automatically.
 *                    Left panel: user/timer/play-pause/speed/save.
 *                    Center: MapCanvas fed by the REAL NationalGraph.
 *
 *   [Simulateur]   — a sandbox running on an INDEPENDENT copy of the data.
 *                    Left panel: scenario controls (random event, manual inject).
 *                    Center: MapCanvas fed by the SANDBOX NationalGraph.
 *
 * Both maps use the same MapCanvas class — only the data differs.
 * Clicking a region on either map opens a popup with city details.
 */
public class SimulationView extends Application implements Observer {

    private Stage primaryStage;
    private SimulationController controller;

    // ── Two independent map canvases ─────────────────────────────────────────
    private MapCanvas realMap;      // fed by the real simulation graph
    private MapCanvas sandboxMap;   // fed by the sandbox (copy) graph

    // ── UI labels updated at runtime ──────────────────────────────────────────
    private Label  timerLabel;
    private Label  userLabel;
    private Label  messageLabel;
    private Label  sandboxStepLabel;
    private Button playPauseButton;

    // ── Open region popup tracking (shared for both maps) ─────────────────────
    private Stage  openPopup;
    private String openPopupRegion;
    private NationalGraph openPopupGraph; // which graph the popup is showing

    // =========================================================================
    // JavaFX start
    // =========================================================================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.controller   = new SimulationController(this);

        // Build the two tabs
        TabPane tabs = new TabPane(
            buildRealTab(),
            buildSandboxTab()
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: #1a1a2e;");

        stage.setTitle("EpiSim – Simulation épidémique");
        stage.setScene(new Scene(tabs, 900, 700));
        stage.setResizable(false);
        stage.show();

        // Initial draw for both maps
        realMap.draw(controller.getNationalGraph());
        sandboxMap.draw(controller.getSandboxGraph());
    }

    // =========================================================================
    // TAB 1 — Temps Réel
    // =========================================================================

    /**
     * Builds the "Temps Réel" tab.
     * LEFT: user selector, timer, play/pause, speed, save.
     * CENTER: the real MapCanvas.
     * RIGHT: legend + event log.
     */
    private Tab buildRealTab() {
        realMap = new MapCanvas("Temps Réel");
        registerClickHandler(realMap, () -> controller.getNationalGraph());

        ScrollPane leftScroll = new ScrollPane(buildRealControlPanel());
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setMaxHeight(620);
        leftScroll.setStyle(
            "-fx-background: #16213e;" +
            "-fx-background-color: #16213e;"
        );

        HBox content = new HBox(10,
            leftScroll,
            wrapCanvas(realMap),
            buildLegendPanel()
        );
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1a1a2e;");

        Tab tab = new Tab("🗺 Temps Réel", content);
        return tab;
    }

    /**
     * Left panel for the real simulation tab.
     */
    /**
     * Left panel for the "Temps Réel" tab.
     *
     * This tab displays LIVE data from the running simulation.
     * The user cannot drive the simulation from here — that is the role
     * of the Simulateur tab.  We intentionally remove Play/Pause and the
     * speed slider so the researcher reads real data without interfering.
     *
     * Kept: user selector (role affects admin features), timer (read-only),
     * and the save button.
     */
    private VBox buildRealControlPanel() {
        VBox panel = styledPanel(195);

        // User selector — role determines whether admin actions are visible
        userLabel = infoLabel("Invité");
        Button btnAdmin = actionButton("🔑 Admin");
        Button btnGuest = actionButton("👤 Invité");
        btnAdmin.setOnAction(e -> controller.loginAsAdmin());
        btnGuest.setOnAction(e -> controller.loginAsLambda());

        // Timer — read-only display, updated by update() after each real step
        timerLabel = new Label("Jour 1 – Semaine 1");
        timerLabel.setFont(Font.font(13));
        timerLabel.setTextFill(Color.WHITE);

        // Info label explaining the tab's purpose
        Label infoLbl = new Label(
            "Données en temps réel.\nVeuillez accéder au simulateur pour tester des scénarios et voir leurs effets sur la simulation."
        );
        infoLbl.setTextFill(Color.LIGHTGRAY);
        infoLbl.setFont(Font.font(11));
        infoLbl.setWrapText(true);

        // Save button — identical to the one in the Simulateur tab
        Button btnSave = actionButton("💾 Sauvegarder");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> handleSave());

        panel.getChildren().addAll(
            sectionLabel("Utilisateur"),
            new HBox(6, btnAdmin, btnGuest), userLabel,
            new Separator(),
            sectionLabel("Temps réel"), timerLabel,
            new Separator(),
            infoLbl,
            new Separator(),
            btnSave
        );
        return panel;
    }

    // =========================================================================
    // TAB 2 — Simulateur (sandbox)
    // =========================================================================

    /**
     * Builds the "Simulateur" tab.
     * LEFT: scenario controls (random event, manual inject by region/city/count).
     * CENTER: the sandbox MapCanvas (independent copy of the data).
     * RIGHT: legend + sandbox log.
     *
     * The sandbox graph is a deep copy — changes here never affect the real tab.
     */
    private Tab buildSandboxTab() {
        sandboxMap = new MapCanvas("Simulateur");
        registerClickHandler(sandboxMap, () -> controller.getSandboxGraph());

        // Wrap the left control panel in a ScrollPane so the user can scroll
        // down to see the history — the VBox is taller than the window height.
        ScrollPane leftScroll = new ScrollPane(buildSandboxControlPanel());
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setMaxHeight(620); // constrain to window height
        leftScroll.setStyle(
            "-fx-background: #16213e;" +
            "-fx-background-color: #16213e;"
        );

        HBox content = new HBox(10,
            leftScroll,
            wrapCanvas(sandboxMap),
            buildLegendPanel()
        );
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1a1a2e;");

        Tab tab = new Tab("🧪 Simulateur", content);
        return tab;
    }

    /**
     * Left panel for the sandbox tab.
     * Contains scenario tools: step counter, random event,
     * and a manual injection form (region / city / count).
     */
    private VBox buildSandboxControlPanel() {
        VBox panel = styledPanel(195);

        // Sandbox step counter
        // Timer label for the sandbox — same format as the real simulation
        // so the user can compare "Jour 5 Sem. 2 (J+12)" sandbox vs réel.
        sandboxStepLabel = new javafx.scene.control.Label(sandboxTimerText());
        sandboxStepLabel.setFont(javafx.scene.text.Font.font(13));
        sandboxStepLabel.setTextFill(javafx.scene.paint.Color.WHITE);

        // Manual step button — advances only the sandbox
        Button btnStep = actionButton("⏭ Avancer d'un jour");
        btnStep.setMaxWidth(Double.MAX_VALUE);
        btnStep.setOnAction(e -> {
            controller.sandboxStep();
            sandboxStepLabel.setText(sandboxTimerText());
            sandboxMap.draw(controller.getSandboxGraph());
        });

        // Random event button — injects a random outbreak in the sandbox
        Button btnRandom = actionButton("⚡ Événement aléatoire");
        btnRandom.setMaxWidth(Double.MAX_VALUE);
        btnRandom.setOnAction(e -> {
            String city = controller.sandboxRandomEvent();
            showSandboxMessage("⚡ Foyer déclaré à " + city);
            sandboxMap.draw(controller.getSandboxGraph());
            refreshHistoryBox();
        });

        // Reset sandbox — recopies the real graph so you can start fresh
        Button btnReset = actionButton("↺ Réinitialiser");
        btnReset.setMaxWidth(Double.MAX_VALUE);
        btnReset.setOnAction(e -> {
            controller.resetSandbox();
            sandboxStepLabel.setText(sandboxTimerText());
            sandboxMap.draw(controller.getSandboxGraph());
            refreshHistoryBox();
            showSandboxMessage("Sandbox réinitialisée.");
        });

        // Save — same behaviour as the real tab save button
        Button btnSave = actionButton("💾 Sauvegarder");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> handleSave());

        // Generate report — takes a snapshot of the current sandbox state,
        // compares it to the initial state (captured at sandbox creation),
        // and opens a BarChart in a new "Rapport" tab.
        Button btnReport = actionButton("📊 Générer le rapport");
        btnReport.setMaxWidth(Double.MAX_VALUE);
        btnReport.setStyle(
            "-fx-background-color: #1a5c3a; -fx-text-fill: white;" +
            "-fx-background-radius: 5; -fx-cursor: hand;"
        );
        btnReport.setOnAction(e -> generateReport());

        // Manual injection form: choose a region, then a city, then a count
        Label formTitle = sectionLabel("Injection manuelle");

        ComboBox<String> regionCombo = new ComboBox<>();
        regionCombo.setPromptText("Région…");
        regionCombo.setMaxWidth(Double.MAX_VALUE);
        regionCombo.getItems().addAll(controller.getSandboxGraph().getRegions().keySet());
        styleCombo(regionCombo);

        ComboBox<String> cityCombo = new ComboBox<>();
        cityCombo.setPromptText("Ville…");
        cityCombo.setMaxWidth(Double.MAX_VALUE);
        cityCombo.setDisable(true);
        styleCombo(cityCombo);

        // Populate city combo when a region is selected
        regionCombo.setOnAction(e -> {
            String regionName = regionCombo.getValue();
            cityCombo.getItems().clear();
            if (regionName != null) {
                Region r = controller.getSandboxGraph().getRegions().get(regionName);
                if (r != null) {
                    cityCombo.getItems().addAll(r.getRegionalGraph().getCities().keySet());
                    cityCombo.setDisable(false);
                }
            }
        });

        Spinner<Integer> countSpinner = new Spinner<>(1, 5000, 50, 10);
        countSpinner.setMaxWidth(Double.MAX_VALUE);
        countSpinner.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        Button btnInject = actionButton("💉 Injecter");
        btnInject.setMaxWidth(Double.MAX_VALUE);
        btnInject.setOnAction(e -> {
            String regionName = regionCombo.getValue();
            String cityName   = cityCombo.getValue();
            int    count      = countSpinner.getValue();
            if (regionName == null || cityName == null) {
                showSandboxMessage("⚠ Choisir une région et une ville.");
                return;
            }
            controller.sandboxInject(regionName, cityName, count);
            sandboxMap.draw(controller.getSandboxGraph());
            refreshHistoryBox();
            showSandboxMessage("💉 " + count + " cas injectés à " + cityName);
        });

        // ── Sandbox speed slider ─────────────────────────────────────────────
        // Same logic as the real simulation slider:
        // the value listener only updates the label (visual feedback while dragging),
        // and sandboxChangeSpeed() is called only on MouseReleased.
        Slider sandboxSpeedSlider = new Slider(1, 10, 1);
        sandboxSpeedSlider.setShowTickLabels(true);
        sandboxSpeedSlider.setMajorTickUnit(3);
        sandboxSpeedSlider.setMaxWidth(Double.MAX_VALUE);
        Label sandboxSpeedValue = infoLabel("1×");
        sandboxSpeedSlider.valueProperty().addListener(
            (obs, o, n) -> sandboxSpeedValue.setText(String.format("%.0f×", n.doubleValue()))
        );
        sandboxSpeedSlider.setOnMouseReleased(
            e -> controller.sandboxChangeSpeed(sandboxSpeedSlider.getValue())
        );

        // ── Sandbox Play / Pause ──────────────────────────────────────────────
        // Runs the sandbox automatically at the chosen speed, independently
        // from the real simulation timeline.
        Button btnSandboxPlay = actionButton("▶ Play");
        btnSandboxPlay.setMaxWidth(Double.MAX_VALUE);
        btnSandboxPlay.setOnAction(e -> {
            boolean nowRunning = controller.toggleSandboxPlayPause();
            btnSandboxPlay.setText(nowRunning ? "⏸ Pause" : "▶ Play");
            // Refresh step label when paused
            if (!nowRunning) sandboxStepLabel.setText(sandboxTimerText());
        });

        panel.getChildren().addAll(
            sectionLabel("🧪 Simulateur"),
            sandboxStepLabel,
            sectionLabel("Temps"),
            btnSandboxPlay,
            sectionLabel("Vitesse (×)"),
            sandboxSpeedSlider, sandboxSpeedValue,
            new Separator(),
            btnStep,
            new Separator(),
            btnRandom,
            btnReset,
            btnSave,
            btnReport,
            new Separator(),
            formTitle,
            infoLabel("Région :"), regionCombo,
            infoLabel("Ville :"),  cityCombo,
            infoLabel("Cas :"),    countSpinner,
            btnInject,
            new javafx.scene.control.Separator(),
            sectionLabel("📋 Historique"),
            buildHistoryBox()
        );
        return panel;
    }

    // =========================================================================
    // Shared legend panel (right side, same for both tabs)
    // =========================================================================

    private VBox buildLegendPanel() {
        VBox panel = styledPanel(155);
        messageLabel = new Label("–");
        messageLabel.setTextFill(Color.LIGHTGRAY);
        messageLabel.setFont(Font.font(11));
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(140);

        panel.getChildren().addAll(
            sectionLabel("Légende"),
            coloredLabel("🟢 Faible  (< 30%)", Color.web("#81c784")),
            coloredLabel("🟠 Modéré (30–60%)", Color.web("#ffb74d")),
            coloredLabel("🔴 Élevé   (> 60%)", Color.web("#e57373")),
            new Separator(),
            sectionLabel("Événements"),
            messageLabel
        );
        return panel;
    }

    // =========================================================================
    // Click handler — shared between both canvases
    // =========================================================================

    /**
     * Registers a mouse click handler on a MapCanvas.
     * When the user clicks inside a region circle, opens the region popup
     * using the graph provided by the graphSupplier.
     *
     * Using a Supplier lets us pass "which graph to read" at click time
     * (not at registration time), so the sandbox popup always shows
     * sandbox data and the real popup always shows real data.
     *
     * @param canvas        the MapCanvas to attach the handler to
     * @param graphSupplier a lambda that returns the NationalGraph to read
     */
    private void registerClickHandler(MapCanvas canvas,
                                      java.util.function.Supplier<NationalGraph> graphSupplier) {
        canvas.setOnMouseClicked(e -> {
            NationalGraph graph = graphSupplier.get();
            for (String regionName : graph.getRegions().keySet()) {
                double[] center = canvas.getCircleCenter(regionName);
                if (center == null) continue;
                double dist = Math.hypot(e.getX() - center[0], e.getY() - center[1]);
                if (dist <= canvas.getRadius()) {
                    Region region = graph.getRegions().get(regionName);
                    openRegionPopup(regionName, region, graph);
                    break;
                }
            }
        });
    }

    // =========================================================================
    // Region popup
    // =========================================================================

    /**
     * Opens a popup for a clicked region.
     * Receiving the NationalGraph as parameter lets us show either real
     * or sandbox data depending on which map was clicked.
     */
    private void openRegionPopup(String regionName, Region region, NationalGraph graph) {
        // If same region already open, just refresh
        if (openPopup != null && openPopup.isShowing()
                && regionName.equals(openPopupRegion)
                && graph == openPopupGraph) {
            refreshPopup(regionName, region, graph);
            return;
        }
        if (openPopup != null) openPopup.close();

        openPopupRegion = regionName;
        openPopupGraph  = graph;

        Stage popup = new Stage();
        popup.initOwner(primaryStage);
        popup.initModality(Modality.NONE);
        popup.setTitle("Région : " + regionName);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        scroll.setContent(buildRegionContent(regionName, region, graph));

        popup.setScene(new Scene(scroll, 360, 680));
        popup.getScene().setUserData(scroll);
        openPopup = popup;
        popup.setOnHidden(e -> {
            openPopup       = null;
            openPopupRegion = null;
            openPopupGraph  = null;
        });
        popup.show();
    }

    /** Rebuilds the popup content in place. */
    private void refreshPopup(String regionName, Region region, NationalGraph graph) {
        if (openPopup == null || !openPopup.isShowing()) return;
        ScrollPane scroll = (ScrollPane) openPopup.getScene().getUserData();
        scroll.setContent(buildRegionContent(regionName, region, graph));
    }

    /**
     * Builds the VBox displayed inside the region popup.
     * Shows SEIR data per city and the route list with toggle buttons.
     */
    private VBox buildRegionContent(String regionName, Region region, NationalGraph graph) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");

        Label header = new Label("📍 " + regionName);
        header.setFont(Font.font(15));
        header.setTextFill(Color.WHITE);
        content.getChildren().add(header);

        content.getChildren().add(sectionLabel("Villes"));

        for (City city : region.getRegionalGraph().getCities().values()) {
            VBox card = new VBox(3);
            card.setPadding(new Insets(6));
            card.setStyle("-fx-background-color: " + cityCardBg(city.getRiskColor()) +
                          "; -fx-background-radius: 5;");

            Label nameL = new Label(riskDot(city.getRiskColor()) + "  " + city.getName());
            nameL.setFont(Font.font(13));
            nameL.setTextFill(Color.WHITE);

            Label seirL  = infoLabel("Sains: " + city.getSafe() +
                                     "   Exposés: " + city.getExposed());
            Label seirL2 = infoLabel("Infectés: " + city.getInfected() +
                                     "   Guéris: " + city.getRecovered());
            Label rateL  = infoLabel(String.format(
                "Taux d'infection : %.1f%%", city.getInfectionRate() * 100));

            card.getChildren().addAll(nameL, seirL, seirL2, rateL);
            content.getChildren().add(card);
        }

        // ── Intra-regional routes ─────────────────────────────────────────────
        content.getChildren().add(sectionLabel("Routes locales"));

        for (Route route : region.getRegionalGraph().getRoutes()) {
            content.getChildren().add(buildRouteRow(route, regionName, region, graph));
        }

        // ── Inter-regional routes ─────────────────────────────────────────────
        // Filter the national graph's inter-regional routes to show only those
        // that have at least one endpoint inside this region.
        // This lets the user see and control national connections from the
        // region popup — no need for a separate inter-regional management screen.
        java.util.List<Route> interRoutes = controller.getInterRegionalRoutesFor(
            regionName, graph
        );

        if (!interRoutes.isEmpty()) {
            content.getChildren().add(sectionLabel("Routes inter-régionales"));

            for (Route route : interRoutes) {
                // For inter-regional routes we show both city names AND their regions
                // so the user understands which two regions are connected.
                String labelA = route.getCityA().getName() + " (" + controller.getRegionOf(route.getCityA(), graph) + ")";
                String labelB = route.getCityB().getName() + " (" + controller.getRegionOf(route.getCityB(), graph) + ")";

                HBox row = new HBox(8);
                row.setPadding(new Insets(3));

                Label routeL = infoLabel(routeIcon(route.getAccess()) + "  " + labelA + " ↔ " + labelB);
                HBox.setHgrow(routeL, Priority.ALWAYS);
                row.getChildren().add(routeL);

                boolean isSandbox = (graph == controller.getSandboxGraph());
                if (isSandbox || controller.isAdmin()) {
                    String btnText = (route.getAccess() == AccessState.BARRICATED)
                        ? "✅ Ouvrir" : "🚧 Bloquer";
                    Button toggleBtn = smallButton(btnText);
                    toggleBtn.setOnAction(e -> {
                        route.setAccess(route.getAccess() == AccessState.BARRICATED
                            ? AccessState.OPEN : AccessState.BARRICATED);
                        String msg = (route.getAccess() == AccessState.BARRICATED
                            ? "🚧 Bloqué : " : "✅ Ouvert : ")
                            + route.getCityA().getName() + " ↔ " + route.getCityB().getName();
                        showMessage(msg);
                        refreshPopup(regionName, region, graph);
                        if (isSandbox) sandboxMap.draw(controller.getSandboxGraph());
                        else           realMap.draw(controller.getNationalGraph());
                    });
                    row.getChildren().add(toggleBtn);
                }
                content.getChildren().add(row);
            }
        }

        return content;
    }

    /**
     * Builds a single route row with label + toggle button.
     * Extracted to avoid duplication between intra and inter sections.
     */
    private HBox buildRouteRow(Route route, String regionName, Region region, NationalGraph graph) {
        HBox row = new HBox(8);
        row.setPadding(new Insets(3));

        Label routeL = infoLabel(routeIcon(route.getAccess()) + "  " +
            route.getCityA().getName() + " ↔ " + route.getCityB().getName());
        HBox.setHgrow(routeL, Priority.ALWAYS);
        row.getChildren().add(routeL);

        boolean isSandbox = (graph == controller.getSandboxGraph());
        if (isSandbox || controller.isAdmin()) {
            String btnText = (route.getAccess() == AccessState.BARRICATED)
                ? "✅ Ouvrir" : "🚧 Bloquer";
            Button toggleBtn = smallButton(btnText);
            toggleBtn.setOnAction(e -> {
                route.setAccess(route.getAccess() == AccessState.BARRICATED
                    ? AccessState.OPEN : AccessState.BARRICATED);
                refreshPopup(regionName, region, graph);
                if (isSandbox) sandboxMap.draw(controller.getSandboxGraph());
                else           realMap.draw(controller.getNationalGraph());
            });
            row.getChildren().add(toggleBtn);
        }
        return row;
    }

    // =========================================================================
    // Observer — called by the real simulation after each step
    // =========================================================================

    /**
     * Refreshes the REAL map and open popup only.
     * The sandbox map is refreshed separately by its own buttons —
     * the two simulations are fully independent.
     */
    @Override
    public void update() {
        Platform.runLater(() -> {
            realMap.draw(controller.getNationalGraph());

            // Refresh popup if it is showing real data
            if (openPopup != null && openPopup.isShowing()
                    && openPopupRegion != null
                    && openPopupGraph == controller.getNationalGraph()) {
                Region r = controller.getNationalGraph().getRegions().get(openPopupRegion);
                if (r != null) refreshPopup(openPopupRegion, r, controller.getNationalGraph());
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
    public void showSandboxMessage(String msg)   { messageLabel.setText("[Sandbox] " + msg); }
    public void updateUserLabel(String text)     { userLabel.setText(text); }

    /**
     * Called by the sandbox Timeline after each automatic step.
     * Updates the sandbox map and the step counter label.
     * Platform.runLater ensures we are on the JavaFX Application Thread.
     */
    public void refreshSandboxMap() {
        javafx.application.Platform.runLater(() -> {
            sandboxMap.draw(controller.getSandboxGraph());
            sandboxStepLabel.setText(sandboxTimerText());
        });
    }


    /**
     * Formats the sandbox timer identical to the real simulation timer:
     *   "Jour 5 – Sem. 2  (J+12)"
     * Lets the user directly compare sandbox day vs real day side-by-side.
     */
    private String sandboxTimerText() {
        int total  = controller.getSandboxStep();
        int dayNum = (total % 7) + 1;
        int weekNum = (total / 7) + 1;
        return "Jour " + dayNum + " – Sem. " + weekNum + "  (J+" + total + ")";
    }

    // Reference kept so refreshHistoryBox() can repopulate without rebuilding
    private VBox historyBox;

    /**
     * Builds the initial history VBox and stores it in historyBox.
     * Called once from buildSandboxControlPanel().
     */
    private VBox buildHistoryBox() {
        historyBox = new VBox(4);
        historyBox.setStyle(
            "-fx-background-color: #0a1628;" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 6;"
        );
        refreshHistoryBox();
        return historyBox;
    }

    /**
     * Repopulates the history VBox with the 8 most recent sandbox events.
     * Events are stored in the controller as "Jour X — action" strings,
     * most recent first (index 0).
     */
    private void refreshHistoryBox() {
        if (historyBox == null) return;
        historyBox.getChildren().clear();
        java.util.List<String> history = controller.getSandboxHistory();
        int limit = Math.min(8, history.size());
        if (limit == 0) {
            Label empty = new Label("Aucun événement");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 10;");
            historyBox.getChildren().add(empty);
        } else {
            for (int i = 0; i < limit; i++) {
                Label entry = new Label(history.get(i));
                entry.setTextFill(Color.LIGHTGRAY);
                entry.setFont(Font.font(10));
                entry.setWrapText(true);
                entry.setMaxWidth(175);
                historyBox.getChildren().add(entry);
            }
        }
    }

    public void renderRegionalGrid(String regionName) {
        realMap.draw(controller.getNationalGraph());
        if (regionName.equals(openPopupRegion) && openPopupGraph == controller.getNationalGraph()) {
            Region r = controller.getNationalGraph().getRegions().get(regionName);
            if (r != null) refreshPopup(regionName, r, controller.getNationalGraph());
        }
    }

    // =========================================================================
    // Save
    // =========================================================================

    // =========================================================================
    // Report — Before / After BarChart
    // =========================================================================

    /**
     * Generates a "Before / After" report for all regions.
     *
     * "Before" = the snapshot taken when the sandbox was first created
     *            (a deep copy of the real graph at that moment).
     * "After"  = the current state of the sandbox graph right now.
     *
     * The report is a JavaFX BarChart displayed in a new tab called "📈 Rapport".
     * If a report tab already exists it is replaced so we always show fresh data.
     *
     * Chart structure:
     *   X axis : region names (shortened to first word)
     *   Y axis : number of infected cases
     *   Two bar series : "Avant simulation" and "Après simulation"
     */
    private void generateReport() {
        // Collect before/after data from the controller
        java.util.Map<String, int[]> data = controller.getSandboxBeforeAfter();
        if (data.isEmpty()) {
            showSandboxMessage("⚠ Lancez au moins un step avant de générer le rapport.");
            return;
        }

        // Build X axis with region names (short form for readability)
        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        xAxis.setLabel("Région");

        // Build Y axis
        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        yAxis.setLabel("Cas infectés");

        // ── BarChart setup ────────────────────────────────────────────────────
        javafx.scene.chart.BarChart<String, Number> chart =
            new javafx.scene.chart.BarChart<>(xAxis, yAxis);
        chart.setTitle("Impact de la simulation – Avant / Après");
        chart.setAnimated(false);

        // FIX (title invisible): do NOT override -fx-background-color at chart
        // level — that CSS property also controls the title text colour via the
        // internal chart stylesheet.  Instead, set the background on the parent
        // VBox only and leave the chart's own CSS intact.
        // We only change what we need: the plot background and text colours.
        chart.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-title-font-size: 16px;"
        );
        chart.lookup(".chart-title") ; // resolved after scene attachment

        // ── Series definition ─────────────────────────────────────────────────
        // Avant = blue (#1565c0), Après = red (#c62828)
        // We set the colour via the series node's -fx-bar-fill AFTER the chart
        // is attached to the scene.  Using the series legend symbol CSS class
        // ensures the legend square matches the bar colour.
        javafx.scene.chart.XYChart.Series<String, Number> seriesBefore =
            new javafx.scene.chart.XYChart.Series<>();
        seriesBefore.setName("Avant simulation");

        javafx.scene.chart.XYChart.Series<String, Number> seriesAfter =
            new javafx.scene.chart.XYChart.Series<>();
        seriesAfter.setName("Après simulation");

        // Populate one bar pair per region
        for (java.util.Map.Entry<String, int[]> entry : data.entrySet()) {
            String shortName = entry.getKey().split("[\\s\\-–]")[0];
            int before = entry.getValue()[0];
            int after  = entry.getValue()[1];
            seriesBefore.getData().add(
                new javafx.scene.chart.XYChart.Data<>(shortName, before)
            );
            seriesAfter.getData().add(
                new javafx.scene.chart.XYChart.Data<>(shortName, after)
            );
        }

        chart.getData().addAll(seriesBefore, seriesAfter);

        // FIX (wrong colours + legend mismatch):
        // JavaFX assigns default CSS colour classes (default-color0, default-color1…)
        // to each series in insertion order.  Overriding -fx-bar-fill per data node
        // via Platform.runLater is unreliable because the nodes may not exist yet.
        //
        // The reliable approach is to style the series node directly, which covers
        // BOTH the bars AND the legend symbol via the .chart-series-bar selector.
        //
        // We use a scene-change listener so the CSS is applied the moment the
        // chart is actually attached to the scene graph (guaranteed timing).
        chart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            // Give JavaFX one pulse to lay out the chart nodes
            javafx.application.Platform.runLater(() -> {
                // Style every bar in seriesBefore → blue
                for (javafx.scene.chart.XYChart.Data<String, Number> d : seriesBefore.getData()) {
                    if (d.getNode() != null)
                        d.getNode().setStyle("-fx-bar-fill: #1565c0;");
                }
                // Style every bar in seriesAfter → red
                for (javafx.scene.chart.XYChart.Data<String, Number> d : seriesAfter.getData()) {
                    if (d.getNode() != null)
                        d.getNode().setStyle("-fx-bar-fill: #c62828;");
                }
                // Style legend symbols to match — lookup returns all nodes
                // with the class .chart-legend-item-symbol for each series
                javafx.scene.Node legendBefore = chart.lookup(".default-color0.chart-legend-item-symbol");
                javafx.scene.Node legendAfter  = chart.lookup(".default-color1.chart-legend-item-symbol");
                if (legendBefore != null) legendBefore.setStyle("-fx-background-color: #1565c0;");
                if (legendAfter  != null) legendAfter.setStyle( "-fx-background-color: #c62828;");

                // FIX (title colour): lookup the title label after scene attach
                javafx.scene.Node titleNode = chart.lookup(".chart-title");
                if (titleNode != null)
                    titleNode.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

                // Axis labels white
                javafx.scene.Node xLabel = chart.lookup(".axis-label");
                if (xLabel != null) xLabel.setStyle("-fx-text-fill: white;");
            });
        });

        // Summary label: total infected delta
        int totalBefore = data.values().stream().mapToInt(v -> v[0]).sum();
        int totalAfter  = data.values().stream().mapToInt(v -> v[1]).sum();
        int delta       = totalAfter - totalBefore;
        String deltaText = (delta >= 0 ? "+" : "") + delta;

        Label summary = new Label(
            "Résumé : " + totalBefore + " infectés au départ → " +
            totalAfter + " après simulation  (Δ " + deltaText + ")" +
            "   |   Durée simulée : " + controller.getSandboxStep() + " jours"
        );
        summary.setTextFill(Color.WHITE);
        summary.setFont(Font.font(13));
        summary.setPadding(new Insets(10));

        VBox reportContent = new VBox(10, summary, chart);
        // Background set on the container, NOT on the chart, so the
        // chart's internal CSS (title colour, grid lines) stays intact.
        reportContent.setStyle("-fx-background-color: #1a1a2e;");
        reportContent.setPadding(new Insets(10));
        javafx.scene.layout.VBox.setVgrow(chart, javafx.scene.layout.Priority.ALWAYS);

        // Add or replace the "Rapport" tab in the main TabPane
        TabPane tabs = (TabPane) primaryStage.getScene().getRoot();
        tabs.getTabs().removeIf(t -> t.getText().contains("Rapport"));
        Tab reportTab = new Tab("📈 Rapport", reportContent);
        tabs.getTabs().add(reportTab);

        // Switch to the new tab automatically
        tabs.getSelectionModel().select(reportTab);
        showSandboxMessage("📊 Rapport généré pour " + data.size() + " régions.");
    }

    private void handleSave() {
        if (!controller.isAdmin()) { showMessage("⚠ Sauvegarde réservée à l'Admin."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sauvegarder la simulation");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichier JSON", "*.json"));
        File file = chooser.showSaveDialog(primaryStage);
        if (file != null) {
            controller.getSimulationModel().getPersistenceManager()
                .save(controller.getSimulationModel(), file.getAbsolutePath());
            showMessage("💾 Sauvegardé : " + file.getName());
        }
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private StackPane wrapCanvas(MapCanvas canvas) {
        StackPane pane = new StackPane(canvas);
        // Max height = window height - tab bar - padding, so the canvas never
        // overflows and pushes the layout down.
        pane.setMaxHeight(MapCanvas.H);
        pane.setMaxWidth(MapCanvas.W);
        pane.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 8;");
        return pane;
    }

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

    private Label coloredLabel(String text, Color c) {
        Label l = new Label(text);
        l.setTextFill(c);
        l.setFont(Font.font(12));
        return l;
    }

    private Button actionButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;" +
                   "-fx-background-radius: 5; -fx-cursor: hand;");
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1a3a5c; -fx-text-fill: white;" +
                   "-fx-font-size: 10; -fx-background-radius: 4; -fx-cursor: hand;");
        return b;
    }

    private <T> void styleCombo(ComboBox<T> c) {
        c.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
    }

    // Color helpers — fully-qualified to avoid javafx.scene.paint.Color collision
    private Color riskColor(models.types.Color r) {
        if (r == models.types.Color.RED)    return Color.web("#c62828", 0.90);
        if (r == models.types.Color.ORANGE) return Color.web("#e65100", 0.90);
        return Color.web("#2e7d32", 0.90);
    }
    private String riskDot(models.types.Color r) {
        if (r == models.types.Color.RED)    return "🔴";
        if (r == models.types.Color.ORANGE) return "🟠";
        return "🟢";
    }
    private String cityCardBg(models.types.Color r) {
        if (r == models.types.Color.RED)    return "#4a1010";
        if (r == models.types.Color.ORANGE) return "#4a2c00";
        return "#0f3460";
    }
    private String routeIcon(AccessState a) {
        if (a == AccessState.BARRICATED) return "🚧";
        if (a == AccessState.RESTRICTED) return "⚠";
        return "✅";
    }

    public static void main(String[] args) { launch(args); }
}