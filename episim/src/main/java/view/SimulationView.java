package view;

import controller.SimulationController;
import exceptions.CityStateException;
import exceptions.InvalidParameterException;
import exceptions.SimulationSaveException;
import interfaces.Observer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.types.AccessState;

import java.util.List;
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
    // Controller is the bridge between this view and the model.
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

    // ── Open region popup tracking (shared for both maps) : used so update() can refresh the content without close it and reopen it─────────────────────
    private Stage  openPopup;
    private String openPopupRegion;
    private NationalGraph openPopupGraph; // which graph the popup is showing

    // =========================================================================
    // JavaFX start method : called by the framework when the application launches (important order)
    // =========================================================================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage; 
        this.controller   = new SimulationController(this); // receive the stage and stock it, create the controller and pass this view as observer (so the controller knows how to update the view when the model changes)

        // Build the two tabs and add them to a TabPane
        TabPane tabs = new TabPane(
            buildRealTab(),
            buildSandboxTab()
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: #1a1a2e;");

        stage.setTitle("EpiSim – Simulation épidémique");
        stage.setScene(new Scene(tabs, 900, 700));
        stage.setResizable(false);
        stage.show(); // Show the window before drawing the maps, so the canvas has a size

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
    private VBox buildRealControlPanel() { // left panel for the real simulation tab
        VBox panel = styledPanel(195);

        // User selector — role determines whether admin actions are visible
        userLabel = infoLabel("Invité");
        Button btnAdmin = actionButton("🔑 Admin"); // call controller.loginAsAdmin()
        Button btnGuest = actionButton("👤 Invité"); // or controller.loginAsLambda()
        btnAdmin.setOnAction(e -> controller.loginAsAdmin());
        btnGuest.setOnAction(e -> controller.loginAsLambda());

        // Timer — read-only display, updated by update() after each real step
        timerLabel = new Label("Jour 1 – Semaine 1");
        timerLabel.setFont(Font.font(13));
        timerLabel.setTextFill(Color.WHITE);

        // Info label explaining the tab's purpose
        Label infoLbl = new Label(
            "📡 Données en temps réel. La simulation tourne automatiquement."
        );
        infoLbl.setTextFill(Color.LIGHTGRAY);
        infoLbl.setFont(Font.font(11));
        infoLbl.setWrapText(true);

        // no play/pause or speed slider here — the real simulation runs automatically

        // Save button
        Button btnSave = actionButton("💾 Sauvegarder");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> handleSave());

        // Load button — lists files from saves/ and lets the user pick one
        Button btnLoad = actionButton("📂 Charger");
        btnLoad.setMaxWidth(Double.MAX_VALUE);
        btnLoad.setOnAction(e -> handleLoad());

        panel.getChildren().addAll( // list of buttons and labels in the left panel, here to add buttons
            sectionLabel("Utilisateur"),
            new HBox(6, btnAdmin, btnGuest), userLabel,
            new Separator(),
            sectionLabel("Temps réel"), timerLabel,
            new Separator(),
            infoLbl,
            new Separator(),
            btnSave,
            btnLoad
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
        sandboxStepLabel = new javafx.scene.control.Label(sandboxTimerText()); // updated by sandboxTimerText()
        sandboxStepLabel.setFont(javafx.scene.text.Font.font(13));
        sandboxStepLabel.setTextFill(javafx.scene.paint.Color.WHITE);

        // Manual step button — advances only the sandbox, calls controller.sandboxStep()
        Button btnStep = actionButton("⏭ Avancer d'un jour");
        btnStep.setMaxWidth(Double.MAX_VALUE);
        btnStep.setOnAction(e -> {
            controller.sandboxStep();
            sandboxStepLabel.setText(sandboxTimerText());
            sandboxMap.draw(controller.getSandboxGraph());
        });

        // Random event button — injects a random outbreak in the sandbox, calls controller.sandboxRandomEvent()
        Button btnRandom = actionButton("⚡ Événement aléatoire");
        btnRandom.setMaxWidth(Double.MAX_VALUE);
        btnRandom.setOnAction(e -> {
            String city = controller.sandboxRandomEvent();
            showSandboxMessage("⚡ Foyer déclaré à " + city);
            sandboxMap.draw(controller.getSandboxGraph());
            refreshHistoryBox();
        });

        // Reset sandbox — recopies the real graph so you can start fresh, controller.resetSandbox() resets the step counter and clears the history
        Button btnReset = actionButton("↺ Réinitialiser");
        btnReset.setMaxWidth(Double.MAX_VALUE);
        btnReset.setOnAction(e -> {
            controller.resetSandbox();
            sandboxStepLabel.setText(sandboxTimerText());
            sandboxMap.draw(controller.getSandboxGraph());
            refreshHistoryBox();
            showSandboxMessage("Sandbox réinitialisée.");
        });

        // Save
        Button btnSave = actionButton("💾 Sauvegarder");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> handleSave());

        // Load — same file list, loads into real graph and resets sandbox
        Button btnLoad = actionButton("📂 Charger");
        btnLoad.setMaxWidth(Double.MAX_VALUE);
        btnLoad.setOnAction(e -> handleLoad());

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

        // TextField lets the user type any number freely,
        // instead of clicking arrows up/down on a Spinner.
        // We parse and validate the value when "Injecter" is clicked.
        TextField countField = new TextField("50");
        countField.setMaxWidth(Double.MAX_VALUE);
        countField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;" +
                            "-fx-prompt-text-fill: #aaaaaa;");
        countField.setPromptText("Nombre de cas…");

        Button btnInject = actionButton("💉 Injecter");
        btnInject.setMaxWidth(Double.MAX_VALUE);
        btnInject.setOnAction(e -> {
            String regionName = regionCombo.getValue();
            String cityName   = cityCombo.getValue();
            // Parse the TextField value — show a clear error if not a valid number
            int count;
            try {
                count = Integer.parseInt(countField.getText().trim());
            } catch (NumberFormatException nfe) {
                showSandboxMessage("⚠ Nombre invalide : entrez un entier (ex: 300)");
                return;
            }
            if (regionName == null || cityName == null) {
                showSandboxMessage("⚠ Choisir une région et une ville.");
                return;
            }
            try {
                controller.sandboxInject(regionName, cityName, count);
                sandboxMap.draw(controller.getSandboxGraph());
                refreshHistoryBox();
                showSandboxMessage("💉 " + count + " cas injectés à " + cityName);
            } catch (exceptions.InvalidParameterException | exceptions.CityStateException ex) {
                // Controller validation failed — show message, don't crash
                showSandboxMessage("⚠ " + ex.getMessage());
            }
        });

        // ── Sandbox speed slider ─────────────────────────────────────────────
        // Same logic as the real simulation slider:
        // the valueProperty().addListener only updates the label (visual feedback while dragging),
        // and controller.sandboxChangeSpeed() is called only on setOnMouseReleased -> it's to avoid calling the controller too many times while dragging the slider
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

        // ── Test button (admin only) ─────────────────────────────────────────
        // Visible only to admin users. Forces exception scenarios that cannot
        // be reached through the normal UI (spinner minimum = 1, etc.).
        // Each button triggers one specific exception so we can demonstrate
        // the error handling live during the presentation.
        VBox testBox = buildTestPanel(regionCombo, cityCombo);

        panel.getChildren().addAll( // list of buttons and labels in the left panel, here to add buttons simulation
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
            btnLoad,
            btnReport,
            new Separator(),
            formTitle,
            infoLabel("Région :"), regionCombo,
            infoLabel("Ville :"),  cityCombo,
            infoLabel("Cas :"),    countField,
            btnInject,
            new javafx.scene.control.Separator(),
            sectionLabel("📋 Historique"),
            buildHistoryBox(),
            new javafx.scene.control.Separator(),
            testBox
        );
        return panel;
    }

    // =========================================================================
    // Test panel — forces exception scenarios for demonstration
    // =========================================================================

    /**
     * Builds a collapsible test panel visible only to admin users.
     * Each button forces a specific exception that the normal UI cannot trigger,
     * so we can demonstrate exception handling during a presentation.
     *
     * The panel is a VBox initially hidden; a toggle button reveals it.
     * Passing regionCombo and cityCombo lets us reuse the user's current
     * selection as target for the injection tests.
     *
     * @param regionCombo the region selector from the sandbox panel
     * @param cityCombo   the city selector from the sandbox panel
     */
    private VBox buildTestPanel(
            javafx.scene.control.ComboBox<String> regionCombo,
            javafx.scene.control.ComboBox<String> cityCombo) {

        // The inner content — hidden by default
        VBox testContent = new VBox(6);
        testContent.setStyle(
            "-fx-background-color: #1a0a2e;" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 6;"
        );
        testContent.setVisible(false);
        testContent.setManaged(false);

        // Toggle button to show/hide the test panel
        Button btnToggle = new Button("🔬 Tests exceptions");
        btnToggle.setMaxWidth(Double.MAX_VALUE);
        btnToggle.setStyle(
            "-fx-background-color: #4a1060; -fx-text-fill: white;" +
            "-fx-background-radius: 5; -fx-cursor: hand; -fx-font-size: 10;"
        );
        btnToggle.setOnAction(e -> {
            boolean nowVisible = !testContent.isVisible();
            testContent.setVisible(nowVisible);
            testContent.setManaged(nowVisible);
            btnToggle.setText(nowVisible ? "🔬 Masquer les tests" : "🔬 Tests exceptions");
        });

        // ── Test 1 : InvalidParameterException — count = 0 ───────────────────
        // The TextField accepts any value, but count=0 is validated in the controller.
        Label lbl1 = infoLabel("① count = 0 → InvalidParameterException");
        Button btn1 = testButton("Lancer ①");
        btn1.setOnAction(e -> {
            String region = regionCombo.getValue();
            String city   = cityCombo.getValue();
            if (region == null || city == null) {
                showSandboxMessage("⚠ Sélectionne d'abord une région et une ville.");
                return;
            }
            try {
                controller.sandboxInject(region, city, 0); // count=0 → exception
            } catch (exceptions.InvalidParameterException ex) {
                showSandboxMessage("✅ InvalidParameterException attrapee : " + ex.getMessage() + " | Param: " + ex.getParameterName() + " | Valeur: " + ex.getOffendingValue());
            }
        });

        // ── Test 2 : InvalidParameterException — regionName vide ─────────────
        Label lbl2 = infoLabel("② regionName vide → InvalidParameterException");
        Button btn2 = testButton("Lancer ②");
        btn2.setOnAction(e -> {
            try {
                controller.sandboxInject("", "Paris", 10); // blank region → exception
            } catch (exceptions.InvalidParameterException ex) {
                showSandboxMessage("✅ InvalidParameterException attrapee : " + ex.getMessage());
            }
        });

        // ── Test 3 : CityStateException — population saine épuisée ───────────
        // Inject the entire safe population of a city, then inject again.
        Label lbl3 = infoLabel("③ Ville sans sains → CityStateException");
        Button btn3 = testButton("Lancer ③");
        btn3.setOnAction(e -> {
            String region = regionCombo.getValue();
            String city   = cityCombo.getValue();
            if (region == null || city == null) {
                showSandboxMessage("⚠ Sélectionne d'abord une région et une ville.");
                return;
            }
            try {
                // First: drain all safe population
                models.entities.Region r = controller.getSandboxGraph()
                    .getRegions().get(region);
                if (r == null) return;
                models.entities.City c = r.getRegionalGraph()
                    .getCities().get(city);
                if (c == null) return;
                // Inject exactly all safe people → city.safe becomes 0
                controller.sandboxInject(region, city, c.getSafe());
                sandboxMap.draw(controller.getSandboxGraph());
                // Second injection → CityStateException
                controller.sandboxInject(region, city, 1);
            } catch (exceptions.CityStateException ex) {
                showSandboxMessage("✅ CityStateException attrapee : " + ex.getMessage() + " | Ville: " + ex.getCityName());
            } catch (exceptions.InvalidParameterException ex) {
                showSandboxMessage("⚠ " + ex.getMessage());
            }
        });

        // ── Test 4 : InvalidParameterException — vitesse hors limites ─────────
        // The speed slider is capped at 10 so 999 is unreachable via UI.
        Label lbl4 = infoLabel("④ speed=999 → InvalidParameterException");
        Button btn4 = testButton("Lancer ④");
        btn4.setOnAction(e -> {
            try {
                controller.changeSpeed(999);
            } catch (exceptions.InvalidParameterException ex) {
                showSandboxMessage("✅ InvalidParameterException attrapee : " + ex.getMessage() + " | Param: " + ex.getParameterName() + " | Valeur: " + ex.getOffendingValue());
            }
        });

        testContent.getChildren().addAll(lbl1, btn1, lbl2, btn2, lbl3, btn3, lbl4, btn4);

        VBox wrapper = new VBox(4, btnToggle, testContent);
        // Hide the entire test panel for non-admin users
        wrapper.visibleProperty().bind(
            javafx.beans.binding.Bindings.createBooleanBinding(
                () -> controller.isAdmin(),
                // Recheck when userLabel changes (proxy for role change)
                userLabel.textProperty()
            )
        );
        wrapper.managedProperty().bind(wrapper.visibleProperty());
        return wrapper;
    }

    /** Small styled button for test actions. */
    private Button testButton(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle(
            "-fx-background-color: #2a1040; -fx-text-fill: #cc99ff;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 10;"
        );
        return b;
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

        panel.getChildren().addAll( // here to add buttons and labels in the right panel, same for both tabs
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
                                      java.util.function.Supplier<NationalGraph> graphSupplier) { // Supplier lets us pass "which graph to read" at click time (not at registration time), allows the sandbox popup to always show sandbox data and the real popup to always show real data at the same time
        canvas.setOnMouseClicked(e -> {
            NationalGraph graph = graphSupplier.get();
            for (String regionName : graph.getRegions().keySet()) {
                double[] center = canvas.getCircleCenter(regionName);
                if (center == null) continue;
                double dist = Math.hypot(e.getX() - center[0], e.getY() - center[1]);
                if (dist <= canvas.getRadius()) { // Clicked inside the region circle
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
            refreshPopup(regionName, region, graph); // if the same region is already open, just refresh the content
            return;
        }
        if (openPopup != null) openPopup.close();

        openPopupRegion = regionName;
        openPopupGraph  = graph;

        Stage popup = new Stage();
        popup.initOwner(primaryStage); // Set the main window as the owner of the popup
        popup.initModality(Modality.NONE); // Allow interaction with the main window while the popup is open, the simulation continues running in the background
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

    /** Rebuilds the popup content in place while the simulation runs : it stills stay opened, only its content change */
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
            // here to change what is displayed in the popup for each city, we can add more info if needed
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
                if (isSandbox || controller.isAdmin()) { // Only show toggle buttons if the user is admin or if the sandbox is open
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
    // Observer — called by the controller after each step of the real simulation
    // =========================================================================

    /**
     * Refreshes the REAL map and open popup only.
     * The sandbox map is refreshed separately by its own buttons —
     * the two simulations are fully independent.
     */
    @Override
    public void update() {
        Platform.runLater(() -> { // Ensure we are on the JavaFX Application Thread, runLater is needed because the controller may call update() from a non-JavaFX thread
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
    // Report — Before / After chart drawn on Canvas (no javafx-charts needed)
    // =========================================================================

    /**
     * Generates a "Before / After" report for all regions.
     *
     * Drawn on a plain JavaFX Canvas using GraphicsContext — no javafx-charts
     * dependency required.  Each region gets two bars side by side:
     *   Blue  = infected before simulation
     *   Red   = infected after simulation
     *
     * "Before" = snapshot at first sandbox step.
     * "After"  = current sandbox state.
     */
    private void generateReport() {
        java.util.Map<String, int[]> data = controller.getSandboxBeforeAfter();
        if (data.isEmpty()) {
            showSandboxMessage("⚠ Lancez au moins un step avant de générer le rapport.");
            return;
        }

        // ── Canvas dimensions ─────────────────────────────────────────────────
        int regions    = data.size();
        int canvasW    = Math.max(900, regions * 80 + 80);
        int canvasH    = 420;
        int marginL    = 70;   // left margin for Y axis labels
        int marginB    = 60;   // bottom margin for X axis labels
        int marginT    = 50;   // top margin for title
        int plotW      = canvasW - marginL - 20;
        int plotH      = canvasH - marginT - marginB;

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(canvasW, canvasH);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        // Background
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, canvasW, canvasH);

        // Title
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(16));
        gc.fillText("Impact de la simulation - Avant / Après", marginL, 30);

        // Find max value for Y scale
        int maxVal = 1;
        for (int[] v : data.values()) {
            maxVal = Math.max(maxVal, Math.max(v[0], v[1]));
        }
        // Round up to a nice number
        int yMax = (int)(Math.ceil(maxVal / 1000.0) * 1000);

        // Draw Y axis grid lines and labels
        gc.setFont(Font.font(10));
        int ySteps = 5;
        for (int i = 0; i <= ySteps; i++) {
            int val  = yMax * i / ySteps;
            int yPix = canvasH - marginB - (plotH * i / ySteps);
            gc.setStroke(Color.web("#333355"));
            gc.setLineWidth(0.5);
            gc.strokeLine(marginL, yPix, canvasW - 20, yPix);
            gc.setFill(Color.LIGHTGRAY);
            gc.fillText(String.valueOf(val), 2, yPix + 4);
        }

        // Draw X axis line
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(1);
        gc.strokeLine(marginL, canvasH - marginB, canvasW - 20, canvasH - marginB);

        // Draw bars — two per region (before=blue, after=red)
        int groupW   = plotW / regions;   // width allocated per region
        int barW     = Math.max(6, groupW / 3);  // width of each individual bar
        int gap      = 2;
        int idx      = 0;

        for (java.util.Map.Entry<String, int[]> entry : data.entrySet()) {
            int before   = entry.getValue()[0];
            int after    = entry.getValue()[1];
            String label = entry.getKey().split("[ -]")[0];

            int groupX  = marginL + idx * groupW;
            int centerX = groupX + groupW / 2;

            // Before bar (blue)
            int beforeH = (int)((double) before / yMax * plotH);
            int beforeY = canvasH - marginB - beforeH;
            gc.setFill(Color.web("#1565c0", 0.85));
            gc.fillRect(centerX - barW - gap, beforeY, barW, beforeH);

            // After bar (red)
            int afterH  = (int)((double) after / yMax * plotH);
            int afterY  = canvasH - marginB - afterH;
            gc.setFill(Color.web("#c62828", 0.85));
            gc.fillRect(centerX + gap, afterY, barW, afterH);

            // Region label on X axis
            gc.setFill(Color.LIGHTGRAY);
            gc.setFont(Font.font(9));
            gc.fillText(label, centerX - barW, canvasH - marginB + 14);

            idx++;
        }

        // Legend
        gc.setFill(Color.web("#1565c0"));
        gc.fillRect(marginL, canvasH - marginB + 30, 14, 10);
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText("Avant simulation", marginL + 18, canvasH - marginB + 40);

        gc.setFill(Color.web("#c62828"));
        gc.fillRect(marginL + 160, canvasH - marginB + 30, 14, 10);
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("Après simulation", marginL + 178, canvasH - marginB + 40);

        // ── Summary label ─────────────────────────────────────────────────────
        int totalBefore = data.values().stream().mapToInt(arr -> arr[0]).sum();
        int totalAfter  = data.values().stream().mapToInt(arr -> arr[1]).sum();
        int delta       = totalAfter - totalBefore;
        String deltaText = (delta >= 0 ? "+" : "") + delta;

        Label summary = new Label(
            "Résumé : " + totalBefore + " infectés au départ -> " +
            totalAfter + " après simulation  (D " + deltaText + ")" +
            "   |   Durée simulée : " + controller.getSandboxStep() + " jours"
        );
        summary.setTextFill(Color.WHITE);
        summary.setFont(Font.font(13));
        summary.setPadding(new Insets(10));

        javafx.scene.control.ScrollPane scrollChart = new javafx.scene.control.ScrollPane(canvas);
        scrollChart.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");
        scrollChart.setFitToHeight(true);

        VBox reportContent = new VBox(10, summary, scrollChart);
        reportContent.setStyle("-fx-background-color: #1a1a2e;");
        reportContent.setPadding(new Insets(10));
        VBox.setVgrow(scrollChart, Priority.ALWAYS);

        // Add or replace the "Rapport" tab
        TabPane tabs = (TabPane) primaryStage.getScene().getRoot();
        tabs.getTabs().removeIf(t -> t.getText().contains("Rapport"));
        Tab reportTab = new Tab("📈 Rapport", reportContent);
        tabs.getTabs().add(reportTab);
        tabs.getSelectionModel().select(reportTab);
        showSandboxMessage("📊 Rapport généré pour " + data.size() + " régions.");
    }

    /**
     * Saves the simulation to a fixed directory: saves/ at the project root.
     * The filename is auto-generated with a timestamp so each save is unique.
     *
     * The user never chooses the path — this keeps saves organised and avoids
     * accidental overwrites in random folders.
     *
     * @throws SimulationSaveException if the save directory cannot be created
     *                                 or the file cannot be written
     */
    /**
     * Shows a ChoiceDialog listing all .json files from the saves/ directory,
     * most recent first.  The user picks one and the simulation is loaded.
     *
     * Using a ChoiceDialog instead of a FileChooser keeps the user inside
     * the saves/ folder — no browsing required.
     */
    private void handleLoad() {
        if (!controller.isAdmin()) {
            showMessage("⚠ Chargement réservé a l'Admin.");
            showSandboxMessage("⚠ Chargement réservé a l'Admin.");
            return;
        }
        List<String> files = controller.listSaveFiles();
        if (files.isEmpty()) {
            showMessage("⚠ Aucune sauvegarde trouvée dans saves/");
            return;
        }

        // ChoiceDialog presents a dropdown with all available saves
        javafx.scene.control.ChoiceDialog<String> dialog =
            new javafx.scene.control.ChoiceDialog<>(files.get(0), files);
        dialog.setTitle("Charger une simulation");
        dialog.setHeaderText("Sauvegardes disponibles (plus récente en premier)");
        dialog.setContentText("Fichier :");

        dialog.showAndWait().ifPresent(chosen -> {
            try {
                controller.loadSimulation(chosen);
                // Redraw both maps with the newly loaded data
                realMap.draw(controller.getNationalGraph());
                sandboxMap.draw(controller.getSandboxGraph());
                showMessage("📂 Chargé : " + chosen);
            } catch (exceptions.SimulationSaveException ex) {
                showMessage("⚠ Erreur de chargement : " + ex.getMessage());
                System.err.println("[LOAD] " + ex.getMessage());
            }
        });
    }

    /**
     * Saves the simulation to saves/ directory.
     * Shows the result in both message labels (real + sandbox) so it is
     * always visible regardless of which tab the user is on.
     *
     * NOTE: isAdmin() reads the model's current user — make sure you have
     * clicked "Admin" before saving.  The user role persists across tabs.
     */
    private void handleSave() {
        if (!controller.isAdmin()) {
            // Show in both panels so the message is never hidden by tab
            showMessage("⚠ Sauvegarde réservée a l'Admin.");
            showSandboxMessage("⚠ Sauvegarde réservée a l'Admin.");
            return;
        }
        try {
            controller.saveSimulation();
            showMessage("💾 Sauvegarde OK dans saves/");
            showSandboxMessage("💾 Sauvegarde OK dans saves/");
        } catch (SimulationSaveException e) {
            showMessage("⚠ Erreur : " + e.getMessage());
            showSandboxMessage("⚠ Erreur : " + e.getMessage());
            System.err.println("[SAVE] " + e.getMessage());
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