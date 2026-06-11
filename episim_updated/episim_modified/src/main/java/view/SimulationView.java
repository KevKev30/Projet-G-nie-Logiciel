package view;

import controller.SimulationController;
import interfaces.Observer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import models.entities.City;
import models.entities.Region;
import models.entities.User;
import models.graph.NationalGraph;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * SimulationView — point d'entrée JavaFX et chef d'orchestre de l'interface.
 *
 * Rôle : assembler les sous-vues et répondre aux notifications du modèle
 * (pattern Observer). Cette classe NE contient plus aucune construction
 * de widget (Rectangle, Polygon, Label…) — tout est délégué aux sous-vues.
 *
 * Architecture du package view :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  SimulationView  (chef d'orchestre, ~200 lignes)        │
 *  │   ├── NavBarView        barre de navigation du haut     │
 *  │   ├── BottomBarView     barre de statistiques du bas    │
 *  │   ├── RightPanelView    légende + contrôles + timer     │
 *  │   ├── MapView           carte GeoJSON nationale         │
 *  │   └── RegionalGridView  grille de villes d'une région   │
 *  └─────────────────────────────────────────────────────────┘
 *
 * Implémente Observer : la méthode update() est appelée automatiquement
 * à chaque step de simulation par SimulationModel.notifyObservers().
 */
public class SimulationView extends Application implements Observer {

    // ── Contrôleur (MVC) ─────────────────────────────────────────────────────
    private SimulationController controller;

    // ── Conteneur racine JavaFX ───────────────────────────────────────────────
    private BorderPane root;

    // ── Sous-vues déléguées ───────────────────────────────────────────────────
    private NavBarView       navBar;
    private BottomBarView    bottomBar;
    private RightPanelView   rightPanel;
    private MapView          mapView;
    private RegionalGridView regionalGrid;

    /**
     * Nom de la région actuellement affichée en vue régionale.
     * null = on est sur la carte nationale.
     */
    private String currentRegion = null;

    // ────────────────────────────────────────────────────────────────────────
    //  DÉMARRAGE DE L'APPLICATION
    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        // Instanciation du contrôleur (qui crée le modèle et les données de test)
        controller = new SimulationController(this);
        controller.getSimulationModel().attach(this); // abonnement Observer

        // Instanciation des sous-vues (reçoivent le contrôleur en paramètre)
        navBar       = new NavBarView();
        bottomBar    = new BottomBarView();
        rightPanel   = new RightPanelView(controller);
        mapView      = new MapView(controller);
        regionalGrid = new RegionalGridView(controller);

        // Assemblage du BorderPane racine
        root = new BorderPane();
        root.setTop(navBar.build(
            () -> renderNationalMap(),     // clic "Carte" → carte nationale
            () -> showLoginDialog()        // clic "Connexion Expert" → popup login
        ));
        root.setBottom(bottomBar.build());
        root.setRight(rightPanel.getPanel());

        // Affichage initial : carte nationale
        renderNationalMap();

        Scene scene = new Scene(root, 1150, 720);
        stage.setTitle("EpiSim — Simulation Préventive COVID-19");
        stage.setScene(scene);
        stage.show();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  NAVIGATION ENTRE LES VUES
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Affiche la carte nationale (vue par défaut).
     * Réinitialise currentRegion à null.
     * Reconstruit entièrement la MapView (rafraîchit les couleurs du modèle).
     */
    public void renderNationalMap() {
        currentRegion = null;
        updateBottomStats();

        // Texte du timer pour le titre de la carte
        int totalDays = controller.getSimulationModel().getTotalDays();
        String dayInfo = totalDays == 0
            ? "Simulation non démarrée"
            : "Jour " + totalDays + " · Semaine " + controller.getSimulationModel().getCurrentWeek();

        // MapView construit le VBox complet avec callbacks
        root.setCenter(mapView.build(
            dayInfo,
            regionName -> {                              // clic sur une région
                renderRegionalGrid(regionName);
                Region r = controller.getNationalGraph().getRegions().get(regionName);
                if (r != null) rightPanel.showRegionDetails(r, () -> renderRegionalGrid(regionName));
            },
            msg -> showMessage(msg)                     // message d'erreur
        ));

        rightPanel.showDefaultRegion();
    }

    /**
     * Affiche la vue régionale (grille de villes) pour la région donnée.
     * Met à jour currentRegion pour que update() sache quelle vue rafraîchir.
     *
     * @param regionName nom de la région à afficher
     */
    public void renderRegionalGrid(String regionName) {
        currentRegion = regionName;

        root.setCenter(regionalGrid.build(
            regionName,
            () -> renderNationalMap(),       // bouton "← Carte Nationale"
            msg -> showMessage(msg)          // messages d'erreur / avertissements
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  PATTERN OBSERVER — appelé par SimulationModel.notifyObservers()
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Point d'entrée des notifications du modèle.
     * Rafraîchit la vue courante (nationale ou régionale) et les stats du bas.
     */
    @Override
    public void update() {
        updateBottomStats();
        if (currentRegion != null) renderRegionalGrid(currentRegion);
        else                       renderNationalMap();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  MISE À JOUR DES STATISTIQUES
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Calcule les métriques globales (cas actifs, taux, zones) en parcourant
     * toutes les régions du modèle, et délègue l'affichage à BottomBarView.
     *
     * Appelé à chaque step ET au démarrage/reset.
     */
    private void updateBottomStats() {
        int totalInf = 0, totalPop = 0, zones = 0;

        for (Region r : controller.getNationalGraph().getRegions().values()) {
            r.totalInfectedGraph();  // recalcule le cache de la région
            totalInf += r.getTotalInfected();
            zones    += r.getRegionalGraph().getCities().size();
            for (City c : r.getRegionalGraph().getCities().values())
                totalPop += c.getTotalPopulation();
        }

        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        // Récupération des données de temps depuis le modèle
        int totalDays = controller.getSimulationModel().getTotalDays();
        int week      = controller.getSimulationModel().getCurrentWeek();
        int dayOfWeek = controller.getSimulationModel().getCurrentDay();

        bottomBar.refresh(totalDays, week, dayOfWeek, totalInf, rate, zones);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  API PUBLIQUE — appelée par le contrôleur
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Affiche un message court dans la zone de texte de la barre du bas.
     * Ex : "⚡ Événement aléatoire détecté !" ou "🔒 Connexion requise"
     */
    public void showMessage(String msg) {
        bottomBar.showMessage(msg);
    }

    /**
     * Met à jour le label utilisateur dans la navbar après connexion/déconnexion.
     * Déclenche aussi un refresh complet de l'interface.
     *
     * @param text texte à afficher (ex. "Admin ✓")
     */
    public void updateUserLabel(String text) {
        navBar.setUserLabel(text);
        update();
    }

    /**
     * Change le texte et la couleur du bouton Play/Pause dans le panneau droit.
     * Appelé par le contrôleur quand l'état running change.
     *
     * @param text nouveau texte du bouton
     */
    public void setPlayPauseButton(String text) {
        rightPanel.setPlayPauseButton(text);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  POPUP DE CONNEXION EXPERT
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Affiche une petite fenêtre de connexion (identifiant + mot de passe).
     * Comptes prévus : admin/admin (Expert), user/[qqch] (Visiteur).
     *
     * Gardée dans SimulationView car elle crée une nouvelle Stage JavaFX
     * et interagit directement avec le contrôleur pour la session.
     */
    private void showLoginDialog() {
        Stage popup = new Stage();
        popup.setTitle("Connexion Expert");

        Label title = new Label("🔑 Connexion Expert");
        title.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;"
        );

        TextField userField = new TextField();
        userField.setPromptText("Identifiant");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Mot de passe");

        Label hint = new Label("admin / admin pour accès expert");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10px;");

        Button loginBtn = new Button("Se connecter");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle(
            "-fx-background-color: #27ae60; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8; -fx-background-radius: 4;"
        );
        loginBtn.setOnAction(e -> {
            String user = userField.getText();
            String pass = passField.getText();
            if (user.equals("admin") && pass.equals("admin")) {
                controller.loginAsAdmin();
                popup.close();
            } else if (user.equals("user")) {
                controller.loginAsLambda();
                popup.close();
            } else {
                hint.setText("❌ Identifiants incorrects !");
                hint.setStyle("-fx-text-fill: #e74c3c;");
            }
        });

        VBox layout = new VBox(12, title, userField, passField, hint, loginBtn);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: white;");
        popup.setScene(new Scene(layout, 280, 240));
        popup.show();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  POINT D'ENTRÉE JAVA
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }
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

public class SimulationView extends Application implements Observer {

    private SimulationController controller;
    private BorderPane root;
    private Label messageLabel;
    private Label userLabel;
    private Button playPauseBtn;
    private String currentRegion = null;
    private VBox rightPanel;
    private Pane mapPane;

    // Stats globales (barre du bas)
    private Label statCasActifs;
    private Label statTauxNational;
    private Label statZones;

    // ── Timer jour/jour ──────────────────────────────────────────────────────
    /** Label affiché dans la barre du bas : "Jour 1 — Semaine 1" */
    /*private Label statJour;
    /**
     * Slider de vitesse (steps/seconde) dans le panneau droit.
     * Exposé en champ pour pouvoir être mis à jour depuis update().
     */
   /*private Slider speedSlider;

    // Mapping nom GeoJSON → nom Region
    private static final Map<String, String> GEO_TO_REGION = new HashMap<>();
    static {
        GEO_TO_REGION.put("Île-de-France",            "Île-de-France");
        GEO_TO_REGION.put("Bretagne",                  "Bretagne");
        GEO_TO_REGION.put("Provence-Alpes-Côte d'Azur","PACA");
        GEO_TO_REGION.put("Normandie",                 "Normandie");
    }

    private static final int CELL_SIZE = 85;
    private static final int CELL_GAP  = 6;

    // ────────────────────────────────────────────────────────────────────────
    //  START
    // ────────────────────────────────────────────────────────────────────────

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
        stage.setTitle("EpiSim — Simulation Préventive COVID-19");
        stage.setScene(scene);
        stage.show();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  NAVBAR
    // ────────────────────────────────────────────────────────────────────────

    private HBox buildNavBar() {
        Label logo = new Label("🦠 EpiSim");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Badge COVID visible en permanence dans la navbar
        Label covidBadge = new Label("COVID-19");
        covidBadge.setStyle(
            "-fx-background-color: #c0392b;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 3 7 3 7;" +
            "-fx-background-radius: 10;"
        );

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

        HBox nav = new HBox(12, logo, covidBadge,
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

    // ────────────────────────────────────────────────────────────────────────
    //  PANNEAU DROIT
    // ────────────────────────────────────────────────────────────────────────

    private VBox buildRightPanel() {
        rightPanel = new VBox(0);
        rightPanel.setPrefWidth(270);
        rightPanel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1;");

        // ── Section : info simulation COVID ─────────────────────────────────
        VBox covidSection = buildSection("Simulation — COVID-19", "#c0392b");
        Label covidInfo = new Label(
            "Modèle SEIR · β=0.30 · σ=0.14 · γ=0.07\n" +
            "Létalité : 2 % · Asymptomatiques : 40 %\n" +
            "Simulation à vocation préventive"
        );
        covidInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-wrap-text: true;");
        covidSection.getChildren().add(covidInfo);

        // ── Section : légende ────────────────────────────────────────────────
        VBox legendSection = buildSection("Niveau de risque", "#2c3e50");
        legendSection.getChildren().addAll(
            buildRiskItem("🔴", "Élevé   (> 60%)",  "#e74c3c"),
            buildRiskItem("🟠", "Modéré (30-60%)", "#e67e22"),
            buildRiskItem("🟢", "Faible  (< 30%)",  "#27ae60")
        );

        // ── Section : région sélectionnée ───────────────────────────────────
        VBox regionSection = buildSection("Sélectionnez une région", "#2c3e50");
        regionSection.setId("regionSection");
        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        regionSection.getChildren().add(hint);

        // ── Section : contrôles ──────────────────────────────────────────────
        VBox simSection = buildSection("Contrôles", "#2c3e50");

        playPauseBtn = new Button("▶  Lancer");
        styleBtn(playPauseBtn, "#27ae60");
        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setOnAction(e -> controller.togglePlayPause());

        Button stepBtn = new Button("⏭  Étape suivante (+1 jour)");
        styleBtn(stepBtn, "#8e44ad");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> controller.stepForward());

        Button randomBtn = new Button("⚡  Événement aléatoire");
        styleBtn(randomBtn, "#e67e22");
        randomBtn.setMaxWidth(Double.MAX_VALUE);
        randomBtn.setOnAction(e -> controller.generateRandomEvent());

        // Bouton "Revenir au départ"
        Button resetBtn = new Button("⏮  Revenir au départ");
        styleBtn(resetBtn, "#7f8c8d");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setOnAction(e -> {
            controller.resetSimulation();
            showMessage("↩ Simulation réinitialisée au Jour 0");
        });

        // ── Timer modulable ──────────────────────────────────────────────────
        // Séparateur visuel
        Separator sep = new Separator();

        Label timerTitle = new Label("⏱  Vitesse (jours / seconde)");
        timerTitle.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Slider : 0.2 → 5 jours/s ; valeur par défaut = 1
        speedSlider = new Slider(0.2, 5.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.setMinorTickCount(1);
        speedSlider.setSnapToTicks(false);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.valueProperty().addListener(
            (obs, o, n) -> controller.changeSpeed(n.doubleValue())
        );

        Label speedValueLabel = new Label("1.0 j/s");
        speedValueLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        speedSlider.valueProperty().addListener((obs, o, n) -> {
            speedValueLabel.setText(String.format("%.1f j/s", n.doubleValue()));
            controller.changeSpeed(n.doubleValue());
        });

        simSection.getChildren().addAll(
            playPauseBtn, stepBtn, randomBtn, resetBtn,
            sep, timerTitle, speedSlider, speedValueLabel
        );

        rightPanel.getChildren().addAll(
            covidSection,
            new Separator(),
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

    // ────────────────────────────────────────────────────────────────────────
    //  BARRE DU BAS  (inclut maintenant le timer Jour/Semaine)
    // ────────────────────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        statCasActifs    = new Label("0");
        statTauxNational = new Label("0.00%");
        statZones        = new Label("0");
        statJour         = new Label("Jour 0 — Sem. 1");
        messageLabel     = new Label("Simulation préventive COVID-19 · Modèle SEIR");

        HBox bar = new HBox(0,
            buildStatCell(statJour,         "Progression",      "#9b59b6"),
            buildStatCell(statCasActifs,    "Cas actifs France","#e74c3c"),
            buildStatCell(statTauxNational, "Taux national",    "#e67e22"),
            buildStatCell(statZones,        "Zones surveillées","#3498db"),
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
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + color + ";"
        );
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        VBox cell = new VBox(2, val, lbl);
        cell.setPadding(new Insets(8, 16, 8, 16));
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

    // ────────────────────────────────────────────────────────────────────────
    //  CARTE DE FRANCE
    // ────────────────────────────────────────────────────────────────────────

    public void renderNationalMap() {
        currentRegion = null;
        updateBottomStats();

        mapPane = new Pane();
        mapPane.setStyle("-fx-background-color: #eaf2f8;");

        try {
            InputStream is = getClass().getResourceAsStream("/regions.geojson");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject geojson = new JSONObject(json);
            JSONArray features = geojson.getJSONArray("features");

            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject geometry = feature.getJSONObject("geometry");
                String type = geometry.getString("type");

                if (type.equals("Polygon")) {
                    JSONArray coords = geometry.getJSONArray("coordinates").getJSONArray(0);
                    for (int j = 0; j < coords.length(); j++) {
                        double lon = coords.getJSONArray(j).getDouble(0);
                        double lat = coords.getJSONArray(j).getDouble(1);
                        minLon = Math.min(minLon, lon); maxLon = Math.max(maxLon, lon);
                        minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat);
                    }
                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    for (int p = 0; p < polys.length(); p++) {
                        JSONArray coords = polys.getJSONArray(p).getJSONArray(0);
                        for (int j = 0; j < coords.length(); j++) {
                            double lon = coords.getJSONArray(j).getDouble(0);
                            double lat = coords.getJSONArray(j).getDouble(1);
                            minLon = Math.min(minLon, lon); maxLon = Math.max(maxLon, lon);
                            minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat);
                        }
                    }
                }
            }

            double mapW = 750.0, mapH = 580.0, padding = 20.0;
            final double fMinLon = minLon, fMaxLon = maxLon;
            final double fMinLat = minLat, fMaxLat = maxLat;

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature  = features.getJSONObject(i);
                JSONObject props    = feature.getJSONObject("properties");
                JSONObject geometry = feature.getJSONObject("geometry");
                String geoName   = props.getString("nom");
                String type      = geometry.getString("type");
                String regionName = GEO_TO_REGION.getOrDefault(geoName, geoName);
                Region region    = controller.getNationalGraph().getRegions().get(regionName);

                Color fillColor = (region != null)
                    ? convertColor(region.getRiskColor())
                    : Color.valueOf("#bdc3c7");

                if (type.equals("Polygon")) {
                    JSONArray coords = geometry.getJSONArray("coordinates").getJSONArray(0);
                    mapPane.getChildren().add(buildPolygon(
                        coords, mapW, mapH, padding,
                        fMinLon, fMaxLon, fMinLat, fMaxLat,
                        fillColor, geoName, regionName, region));
                    double[] c = getCentroid(coords, mapW, mapH, padding, fMinLon, fMaxLon, fMinLat, fMaxLat);
                    addRegionLabel(geoName, c[0], c[1], region);

                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    double[] firstCenter = null;
                    for (int p = 0; p < polys.length(); p++) {
                        JSONArray coords = polys.getJSONArray(p).getJSONArray(0);
                        mapPane.getChildren().add(buildPolygon(
                            coords, mapW, mapH, padding,
                            fMinLon, fMaxLon, fMinLat, fMaxLat,
                            fillColor, geoName, regionName, region));
                        if (p == 0)
                            firstCenter = getCentroid(coords, mapW, mapH, padding, fMinLon, fMaxLon, fMinLat, fMaxLat);
                    }
                    if (firstCenter != null)
                        addRegionLabel(geoName, firstCenter[0], firstCenter[1], region);
                }
            }

        } catch (Exception e) {
            showMessage("Erreur chargement carte : " + e.getMessage());
            e.printStackTrace();
        }

        // ── Titre de la carte avec contexte COVID ────────────────────────────
        int totalDays = controller.getSimulationModel().getTotalDays();
        String dayInfo = totalDays == 0
            ? "Simulation non démarrée"
            : "Jour " + totalDays + " · Semaine " + controller.getSimulationModel().getCurrentWeek();

        Label title = new Label("🗺  Carte Épidémiologique COVID-19 — " + dayInfo);
        title.setStyle(
            "-fx-font-size: 14px;" +
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
        poly.setOnMouseClicked(e -> {
            if (region != null) {
                renderRegionalGrid(regionName);
                updateRightPanelRegion(region);
            } else {
                showMessage("ℹ Région '" + geoName + "' non configurée dans la simulation");
            }
        });
        poly.setStyle("-fx-cursor: hand;");

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

    // ────────────────────────────────────────────────────────────────────────
    //  VUE RÉGIONALE
    // ────────────────────────────────────────────────────────────────────────

    public void renderRegionalGrid(String regionName) {
        currentRegion = regionName;
        Region region = controller.getNationalGraph().getRegions().get(regionName);
        if (region == null) return;

        RegionalGraph rg = region.getRegionalGraph();
        List<City> cities = new ArrayList<>(rg.getCities().values());
        int cols = (int) Math.ceil(Math.sqrt(cities.size()));

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

            double x1 = posA[0] + CELL_SIZE / 2.0, y1 = posA[1] + CELL_SIZE / 2.0;
            double x2 = posB[0] + CELL_SIZE / 2.0, y2 = posB[1] + CELL_SIZE / 2.0;

            boolean blocked = route.getAccess() == AccessState.BARRICATED;
            Line line = new Line(x1, y1, x2, y2);
            line.setStroke(blocked ? Color.valueOf("#e74c3c") : Color.valueOf("#3498db"));
            line.setStrokeWidth(blocked ? 2 : 3);
            if (blocked) line.getStrokeDashArray().addAll(8.0, 4.0);

            final String cA = route.getCityA().getName();
            final String cB = route.getCityB().getName();
            line.setOnMouseClicked(e -> {
                if (controller.isAdmin()) controller.toggleRoute(regionName, cA, cB);
                else showMessage("⛔ Réservé à l'Expert !");
            });
            line.setStyle("-fx-cursor: hand;");
            Tooltip.install(line, new Tooltip(
                (blocked ? "🚧 Bloquée" : "✅ Ouverte") + " — " + cA + " ↔ " + cB
            ));
            gridPane.getChildren().add(line);
        }

        // Cellules villes
        for (City city : cities) {
            double[] pos = positions.get(city.getName());
            if (pos == null) continue;

            Color bg = convertColor(city.getRiskColor());
            Rectangle cell = new Rectangle(pos[0], pos[1], CELL_SIZE, CELL_SIZE);
            cell.setFill(bg); cell.setArcWidth(10); cell.setArcHeight(10);
            cell.setStroke(Color.WHITE); cell.setStrokeWidth(2);
            cell.setOnMouseEntered(e -> { cell.setFill(bg.brighter()); cell.setStrokeWidth(3); });
            cell.setOnMouseExited(e  -> { cell.setFill(bg); cell.setStrokeWidth(2); });

            Label nameLbl = new Label(city.getName());
            nameLbl.setLayoutX(pos[0] + 5); nameLbl.setLayoutY(pos[1] + 5);
            nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
            nameLbl.setMaxWidth(CELL_SIZE - 10); nameLbl.setMouseTransparent(true);

            Label infLbl = new Label("🦠 " + city.getInfected());
            infLbl.setLayoutX(pos[0] + 5); infLbl.setLayoutY(pos[1] + CELL_SIZE - 30);
            infLbl.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
            infLbl.setMouseTransparent(true);

            double rate = city.getInfectionRate() * 100;
            Label rateLbl = new Label(String.format("%.0f%%", rate));
            rateLbl.setLayoutX(pos[0] + CELL_SIZE - 32); rateLbl.setLayoutY(pos[1] + CELL_SIZE - 18);
            rateLbl.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            rateLbl.setMouseTransparent(true);

            Rectangle pbBg = new Rectangle(pos[0] + 4, pos[1] + CELL_SIZE - 8, CELL_SIZE - 8, 5);
            pbBg.setFill(Color.valueOf("rgba(255,255,255,0.3)"));
            pbBg.setArcWidth(3); pbBg.setArcHeight(3); pbBg.setMouseTransparent(true);

            Rectangle pb = new Rectangle(pos[0] + 4, pos[1] + CELL_SIZE - 8,
                (CELL_SIZE - 8) * Math.min(rate / 100, 1.0), 5);
            pb.setFill(Color.WHITE); pb.setArcWidth(3); pb.setArcHeight(3); pb.setMouseTransparent(true);

            Tooltip.install(cell, new Tooltip(
                "📍 " + city.getName() + "\n─────────────────\n" +
                "Sains     : " + city.getSafe()      + "\n" +
                "Exposés   : " + city.getExposed()   + "\n" +
                "Infectés  : " + city.getInfected()  + "\n" +
                "Guéris    : " + city.getRecovered() + "\n" +
                String.format("Taux      : %.1f%%", rate)
            ));
            cell.setOnMouseClicked(e -> {
                if (controller.isAdmin()) showAdminCityPanel(regionName, city.getName());
                else showMessage("🔒 Connexion Expert requise");
            });
            cell.setStyle("-fx-cursor: hand;");

            gridPane.getChildren().addAll(cell, nameLbl, infLbl, rateLbl, pbBg, pb);
        }

        // Barre du haut avec bouton retour + timer
        Button backBtn = new Button("← Carte Nationale");
        styleBtn(backBtn, "#2c3e50");
        backBtn.setOnAction(e -> renderNationalMap());

        int totalDays = controller.getSimulationModel().getTotalDays();
        Label title = new Label("📍 " + regionName +
            "   |   Jour " + totalDays +
            " · Sem. " + controller.getSimulationModel().getCurrentWeek());
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox topBar = new HBox(15, backBtn, title);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;"
        );

        if (controller.isAdmin()) {
            topBar.getChildren().add(buildAdminBar());
        }

        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setStyle("-fx-background-color: #f8f9fa; -fx-background: #f8f9fa;");

        VBox center = new VBox(topBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(center);
    }

    private HBox buildAdminBar() {
        Label lbl = new Label("🔧 Mode Expert — clic sur ville : modifier | clic sur route : bloquer/ouvrir");
        lbl.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 11px; -fx-font-style: italic;");
        HBox bar = new HBox(lbl);
        bar.setPadding(new Insets(0, 0, 0, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  PANEL DROIT DYNAMIQUE
    // ────────────────────────────────────────────────────────────────────────

    private void updateRightPanelDefault() {
        VBox section = (VBox) rightPanel.lookup("#regionSection");
        if (section == null) return;
        section.getChildren().clear();
        Label title = new Label("Sélectionnez une région");
        title.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px; -fx-font-weight: bold;");
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
        title.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        String riskText = region.getRiskColor() == models.types.Color.RED ? "Risque élevé"
            : region.getRiskColor() == models.types.Color.ORANGE ? "Risque modéré" : "Faible risque";

        Label badge = new Label(riskText);
        badge.setStyle(
            "-fx-background-color: " + colorHex + ";" +
            "-fx-text-fill: white; -fx-padding: 3 8 3 8;" +
            "-fx-background-radius: 10; -fx-font-size: 11px;"
        );

        int totalInf = region.getTotalInfected();
        int totalPop = 0;
        for (City c : region.getRegionalGraph().getCities().values())
            totalPop += c.getTotalPopulation();
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        section.getChildren().addAll(
            title, badge, new Separator(),
            buildInfoLine("Cas actifs",  "" + totalInf,               colorHex),
            buildInfoLine("Taux",        String.format("%.2f%%", rate), colorHex),
            buildInfoLine("Population",  "" + totalPop,                "#2c3e50"),
            new Separator()
        );

        Label actTitle = new Label("Actions rapides");
        actTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Button simuBtn = new Button("▶  Voir simulation");
        simuBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(simuBtn, "#2980b9");
        simuBtn.setOnAction(e -> renderRegionalGrid(region.getName()));

        Button exportBtn = new Button("↓  Exporter données");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(exportBtn, "#27ae60");
        exportBtn.setOnAction(e -> showMessage("📥 Export : " + region.getName()));

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

    // ────────────────────────────────────────────────────────────────────────
    //  LOGIN
    // ────────────────────────────────────────────────────────────────────────

    private void showLoginDialog() {
        Stage popup = new Stage();
        popup.setTitle("Connexion Expert");

        Label title = new Label("🔑 Connexion Expert");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

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
            if (userField.getText().equals("admin") && passField.getText().equals("admin")) {
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

        VBox layout = new VBox(12, title, userField, passField, hint, loginBtn);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: white;");
        popup.setScene(new Scene(layout, 280, 240));
        popup.show();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ADMIN VILLE
    // ────────────────────────────────────────────────────────────────────────

    private void showAdminCityPanel(String regionName, String cityName) {
        Stage popup = new Stage();
        popup.setTitle("Modifier : " + cityName);

        Label title = new Label("📍 " + cityName);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label sub = new Label("Modifier le niveau de risque");
        sub.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        Button g = buildColorBtn("🟢 Faible risque", "#27ae60", models.types.Color.GREEN,  regionName, cityName, popup);
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

    // ────────────────────────────────────────────────────────────────────────
    //  UTILITAIRES / OBSERVER
    // ────────────────────────────────────────────────────────────────────────

    @Override
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

        // ── Timer jour/jour ──────────────────────────────────────────────────
        int totalDays = controller.getSimulationModel().getTotalDays();
        int week      = controller.getSimulationModel().getCurrentWeek();
        int dayOfWeek = controller.getSimulationModel().getCurrentDay();
        statJour.setText("J" + totalDays + " · S" + week + " · J" + dayOfWeek);
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
            text.contains("Lancer") || text.contains("Play") ? "#27ae60" : "#e74c3c");
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
}
*/