package view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import controller.SimulationController;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.RegionalGraph;
import models.types.AccessState;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;

public class SimulationView extends Application {

    private SimulationController controller;
    private BorderPane root;
    private Label messageLabel;
    private Label userLabel;
    private Button playPauseBtn;
    private String currentRegion = null;
    private Pane mapPane;
    private VBox rightPanel;

    // Stats globales
    private Label statCasActifs;
    private Label statTauxNational;
    private Label statZones;
    private Label statMaj;

    private static final int CELL_SIZE = 85;
    private static final int CELL_GAP = 5;

    @Override
    public void start(Stage stage) {
        controller = new SimulationController(this);
        root = new BorderPane();
        root.setTop(buildNavBar());
        root.setBottom(buildBottomStatsBar());
        root.setRight(buildRightPanel());
        renderNationalMap();

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("EpiSim — Observatoire Epidémiologique");
        stage.setScene(scene);
        stage.show();
    }

    // ==================== NAVBAR ====================

    private HBox buildNavBar() {
        // Logo
        Label logo = new Label("🦠 EpiSim");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Nav items
        Button carteBtn   = buildNavBtn("Carte",      true);
        Button simuBtn    = buildNavBtn("Simulation", false);
        Button donneesBtn = buildNavBtn("Données",    false);

        carteBtn.setOnAction(e -> renderNationalMap());

        // Séparateur flexible
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Bouton connexion
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

        // Statut utilisateur
        userLabel = new Label("👤 Visiteur");
        userLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");

        HBox navBar = new HBox(20,
            logo, carteBtn, simuBtn, donneesBtn,
            spacer, userLabel, loginBtn
        );
        navBar.setPadding(new Insets(12, 20, 12, 20));
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setStyle("-fx-background-color: #1a252f;");

        return navBar;
    }

    private Button buildNavBtn(String text, boolean active) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + (active ? "white" : "#aaa") + ";" +
            "-fx-font-size: 14px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 10 5 10;" +
            (active ? "-fx-border-color: transparent transparent white transparent; -fx-border-width: 0 0 2 0;" : "")
        );
        return btn;
    }

    // ==================== PANEL DROIT ====================

    private VBox buildRightPanel() {
        rightPanel = new VBox(0);
        rightPanel.setPrefWidth(280);
        rightPanel.setStyle("-fx-background-color: #1e2d3d;");

        // Section niveau de risque
        VBox riskSection = buildSection("Niveau de risque");
        riskSection.getChildren().addAll(
            buildRiskItem("🔴", "Élevé  (taux > 60%)",  "#e74c3c"),
            buildRiskItem("🟠", "Modéré (taux 30-60%)", "#e67e22"),
            buildRiskItem("🟢", "Faible  (taux < 30%)", "#27ae60")
        );

        // Section région sélectionnée (vide au départ)
        VBox regionSection = buildSection("Sélectionnez une région");
        regionSection.setId("regionSection");

        // Section contrôles simulation
        VBox simSection = buildSection("Simulation");

        playPauseBtn = new Button("▶  Lancer la simulation");
        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setStyle(
            "-fx-background-color: #27ae60;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;" +
            "-fx-background-radius: 4;"
        );
        playPauseBtn.setOnAction(e -> controller.togglePlayPause());

        Button stepBtn = new Button("⏭  Étape suivante");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setStyle(
            "-fx-background-color: #8e44ad;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;" +
            "-fx-background-radius: 4;"
        );
        stepBtn.setOnAction(e -> controller.stepForward());

        Button randomBtn = new Button("⚡  Événement aléatoire");
        randomBtn.setMaxWidth(Double.MAX_VALUE);
        randomBtn.setStyle(
            "-fx-background-color: #e67e22;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;" +
            "-fx-background-radius: 4;"
        );
        randomBtn.setOnAction(e -> controller.generateRandomEvent());

        Label speedLabel = new Label("Vitesse de simulation");
        speedLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        Slider speedSlider = new Slider(0.5, 3.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.setStyle("-fx-control-inner-background: #2c3e50;");
        speedSlider.valueProperty().addListener(
            (obs, o, n) -> controller.changeSpeed(n.doubleValue())
        );

        simSection.getChildren().addAll(
            playPauseBtn, stepBtn, randomBtn,
            speedLabel, speedSlider
        );

        rightPanel.getChildren().addAll(riskSection, new Separator(), regionSection, new Separator(), simSection);
        return rightPanel;
    }

    private VBox buildSection(String title) {
        Label lbl = new Label(title);
        lbl.setStyle(
            "-fx-text-fill: #ecf0f1;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 0 0 8 0;"
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

    // ==================== BARRE BAS ====================

    private HBox buildBottomStatsBar() {
        statCasActifs    = new Label("0");
        statTauxNational = new Label("0.00");
        statZones        = new Label("0");
        statMaj          = new Label("0%");
        messageLabel     = new Label("Bienvenue sur EpiSim");

        HBox bar = new HBox(0,
            buildStatCell(statCasActifs,    "Cas actifs France", "#e74c3c"),
            buildStatCell(statTauxNational, "Taux national",     "#e67e22"),
            buildStatCell(statZones,        "Zones surveillées", "#3498db"),
            buildStatCell(statMaj,          "Données à jour",    "#27ae60"),
            buildMessageCell()
        );
        bar.setStyle("-fx-background-color: #1a252f;");
        return bar;
    }

    private VBox buildStatCell(Label valueLabel, String title, String color) {
        valueLabel.setStyle(
            "-fx-font-size: 22px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + color + ";"
        );
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        VBox cell = new VBox(2, valueLabel, titleLabel);
        cell.setPadding(new Insets(12, 20, 12, 20));
        cell.setAlignment(Pos.CENTER);
        cell.setStyle("-fx-border-color: transparent #2c3e50 transparent transparent; -fx-border-width: 0 1 0 0;");
        return cell;
    }

    private HBox buildMessageCell() {
        messageLabel.setStyle("-fx-text-fill: #aaa; -fx-font-style: italic; -fx-font-size: 12px;");
        HBox cell = new HBox(messageLabel);
        cell.setPadding(new Insets(12, 20, 12, 20));
        cell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    // ==================== VUE NATIONALE ====================

    public void renderNationalMap() {
        currentRegion = null;
        updateBottomStats();

        // Titre
        Label title = new Label("Carte Nationale — cliquez sur une région");
        title.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #ecf0f1;" +
            "-fx-padding: 12 15 8 15;"
        );

        // Grille des régions
        mapPane = new Pane();
        List<Region> regions = new ArrayList<>(
            controller.getNationalGraph().getRegions().values()
        );

        int cols = (int) Math.ceil(Math.sqrt(regions.size()));
        for (int i = 0; i < regions.size(); i++) {
            Region region = regions.get(i);
            region.totalInfectedGraph();

            int col = i % cols;
            int row = i / cols;
            double x = col * (CELL_SIZE + CELL_GAP) + 15;
            double y = row * (CELL_SIZE + CELL_GAP) + 15;

            VBox card = buildRegionMapCard(region, x, y);
            mapPane.getChildren().add(card);
        }

        ScrollPane scroll = new ScrollPane(mapPane);
        scroll.setStyle("-fx-background-color: #1e2d3d; -fx-background: #1e2d3d;");
        scroll.setFitToWidth(true);

        VBox center = new VBox(title, scroll);
        center.setStyle("-fx-background-color: #1e2d3d;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.setCenter(center);
        updateRightPanelDefault();
    }

    private VBox buildRegionMapCard(Region region, double x, double y) {
        Color bgColor = convertColor(region.getRiskColor());
        String bgHex  = toHex(bgColor);
        String darkHex = darken(bgHex);

        // En-tête
        Label nameLabel = new Label(region.getName());
        nameLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;" +
            "-fx-padding: 6 8 6 8;"
        );
        HBox header = new HBox(nameLabel);
        header.setStyle("-fx-background-color: " + darkHex + "; -fx-background-radius: 6 6 0 0;");
        header.setAlignment(Pos.CENTER);

        // Stats
        int totalInf = region.getTotalInfected();
        int totalPop = 0;
        for (City c : region.getRegionalGraph().getCities().values())
            totalPop += c.getTotalPopulation();
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        Label infLabel = new Label("🦠 " + totalInf + " cas");
        infLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label rateLabel = new Label(String.format("%.1f%%", rate));
        rateLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        ProgressBar pb = new ProgressBar(Math.min(rate / 100, 1.0));
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: white; -fx-control-inner-background: rgba(255,255,255,0.3);");
        pb.setPrefHeight(6);

        VBox body = new VBox(4, infLabel, rateLabel, pb);
        body.setPadding(new Insets(8));
        body.setStyle("-fx-background-color: " + bgHex + ";");

        // Bouton voir
        Button viewBtn = new Button("Voir →");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setStyle(
            "-fx-background-color: " + darkHex + ";" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4;" +
            "-fx-background-radius: 0 0 6 6;"
        );
        viewBtn.setOnAction(e -> renderRegionalGrid(region.getName()));

        VBox card = new VBox(header, body, viewBtn);
        card.setPrefWidth(CELL_SIZE + 20);
        card.setLayoutX(x);
        card.setLayoutY(y);
        card.setStyle(
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 2);" +
            "-fx-background-radius: 6; -fx-border-radius: 6;"
        );

        // Hover + clic
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.8), 12, 0, 0, 3);" +
            "-fx-background-radius: 6; -fx-border-radius: 6; -fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 2);" +
            "-fx-background-radius: 6; -fx-border-radius: 6;"
        ));
        card.setOnMouseClicked(e -> {
            renderRegionalGrid(region.getName());
            updateRightPanelRegion(region);
        });

        return card;
    }

    // ==================== VUE RÉGIONALE ====================

    public void renderRegionalGrid(String regionName) {
        currentRegion = regionName;
        Region region = controller.getNationalGraph().getRegions().get(regionName);
        if (region == null) return;

        updateRightPanelRegion(region);

        RegionalGraph rg = region.getRegionalGraph();
        List<City> cities = new ArrayList<>(rg.getCities().values());
        int cols = (int) Math.ceil(Math.sqrt(cities.size()));

        // Positions
        Map<String, double[]> positions = new HashMap<>();
        for (int i = 0; i < cities.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            positions.put(cities.get(i).getName(), new double[]{
                col * (CELL_SIZE + CELL_GAP * 3) + 15,
                row * (CELL_SIZE + CELL_GAP * 3) + 15
            });
        }

        Pane gridPane = new Pane();
        gridPane.setStyle("-fx-background-color: #1e2d3d;");

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
            line.setStroke(blocked ? Color.valueOf("#e74c3c") : Color.valueOf("#3498db"));
            line.setStrokeWidth(blocked ? 2 : 3);
            if (blocked) line.getStrokeDashArray().addAll(8.0, 4.0);

            final String cA = route.getCityA().getName();
            final String cB = route.getCityB().getName();
            line.setOnMouseClicked(e -> {
                if (controller.isAdmin()) controller.toggleRoute(regionName, cA, cB);
                else showMessage("⛔ Action réservée à l'Expert !");
            });
            line.setStyle("-fx-cursor: hand;");
            Tooltip.install(line, new Tooltip(
                (blocked ? "🚧 Bloquée" : "✅ Ouverte") + " — " + cA + " ↔ " + cB +
                (controller.isAdmin() ? "\nCliquer pour " + (blocked ? "ouvrir" : "bloquer") : "")
            ));
            gridPane.getChildren().add(line);
        }

        // Cellules
        for (City city : cities) {
            double[] pos = positions.get(city.getName());
            if (pos == null) continue;

            Color bg = convertColor(city.getRiskColor());
            String bgHex   = toHex(bg);
            String darkHex = darken(bgHex);

            // Fond cellule
            Rectangle cell = new Rectangle(pos[0], pos[1], CELL_SIZE, CELL_SIZE);
            cell.setFill(bg);
            cell.setArcWidth(10);
            cell.setArcHeight(10);
            cell.setStroke(Color.WHITE);
            cell.setStrokeWidth(1.5);

            // Nom
            Label nameLabel = new Label(city.getName());
            nameLabel.setLayoutX(pos[0] + 5);
            nameLabel.setLayoutY(pos[1] + 5);
            nameLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 10px;"
            );
            nameLabel.setMaxWidth(CELL_SIZE - 10);

            // Infectés
            Label infLabel = new Label("🦠 " + city.getInfected());
            infLabel.setLayoutX(pos[0] + 5);
            infLabel.setLayoutY(pos[1] + CELL_SIZE - 30);
            infLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");

            double rate = city.getInfectionRate() * 100;
            Label rateLabel = new Label(String.format("%.0f%%", rate));
            rateLabel.setLayoutX(pos[0] + CELL_SIZE - 32);
            rateLabel.setLayoutY(pos[1] + CELL_SIZE - 18);
            rateLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;"
            );

            // Mini barre de progression
            Rectangle progressBg = new Rectangle(pos[0] + 4, pos[1] + CELL_SIZE - 8, CELL_SIZE - 8, 4);
            progressBg.setFill(Color.valueOf("rgba(255,255,255,0.3)"));
            progressBg.setArcWidth(3);
            progressBg.setArcHeight(3);

            double progressWidth = (CELL_SIZE - 8) * Math.min(rate / 100, 1.0);
            Rectangle progressBar = new Rectangle(pos[0] + 4, pos[1] + CELL_SIZE - 8, progressWidth, 4);
            progressBar.setFill(Color.WHITE);
            progressBar.setArcWidth(3);
            progressBar.setArcHeight(3);

            // Tooltip
            Tooltip tip = new Tooltip(
                "📍 " + city.getName() + "\n" +
                "─────────────────\n" +
                "Sains      : " + city.getSafe()      + "\n" +
                "Exposés  : " + city.getExposed()    + "\n" +
                "Infectés   : " + city.getInfected()  + "\n" +
                "Guéris     : " + city.getRecovered() + "\n" +
                String.format("Taux       : %.1f%%", rate)
            );
            Tooltip.install(cell, tip);

            // Clic Admin
            cell.setOnMouseClicked(e -> {
                if (controller.isAdmin()) showAdminCityPanel(regionName, city.getName());
                else showMessage("🔒 Connexion Expert requise pour modifier");
            });
            cell.setStyle("-fx-cursor: hand;");

            gridPane.getChildren().addAll(
                cell, nameLabel, infLabel, rateLabel,
                progressBg, progressBar
            );
        }

        // Bouton retour
        Button backBtn = new Button("← Carte Nationale");
        backBtn.setStyle(
            "-fx-background-color: #2c3e50;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-background-radius: 4;"
        );
        backBtn.setOnAction(e -> renderNationalMap());

        Label title = new Label("📍 " + regionName);
        title.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #ecf0f1;"
        );

        HBox topBar = new HBox(15, backBtn, title);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #1a252f;");

        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setStyle("-fx-background-color: #1e2d3d; -fx-background: #1e2d3d;");

        VBox center = new VBox(topBar, scroll);
        center.setStyle("-fx-background-color: #1e2d3d;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.setCenter(center);
    }

    // ==================== PANEL DROIT DYNAMIQUE ====================

    private void updateRightPanelDefault() {
        // Remet le panel droit par défaut
        VBox regionSection = (VBox) rightPanel.lookup("#regionSection");
        if (regionSection == null) return;
        regionSection.getChildren().clear();

        Label title = new Label("Sélectionnez une région");
        title.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        regionSection.getChildren().addAll(title, hint);
    }

    private void updateRightPanelRegion(Region region) {
        VBox regionSection = (VBox) rightPanel.lookup("#regionSection");
        if (regionSection == null) return;
        regionSection.getChildren().clear();

        String color = toHex(convertColor(region.getRiskColor()));

        Label title = new Label(region.getName());
        title.setStyle(
            "-fx-text-fill: " + color + ";" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;"
        );

        // Badge risque
        String riskText = region.getRiskColor() == models.types.Color.RED ? "Risque élevé" :
                          region.getRiskColor() == models.types.Color.ORANGE ? "Risque modéré" : "Faible risque";
        Label badge = new Label(riskText);
        badge.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-padding: 3 8 3 8;" +
            "-fx-background-radius: 10;" +
            "-fx-font-size: 11px;"
        );

        // Stats
        int totalInf = region.getTotalInfected();
        int totalPop = 0;
        for (City c : region.getRegionalGraph().getCities().values())
            totalPop += c.getTotalPopulation();
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        Label casLabel  = buildStatLine("Cas actifs", "" + totalInf, color);
        Label rateLabel = buildStatLine("Taux", String.format("%.2f%%", rate), color);
        Label popLabel  = buildStatLine("Population", "" + totalPop, "#ecf0f1");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2c3e50;");

        // Actions rapides
        Label actionsTitle = new Label("Actions rapides");
        actionsTitle.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px; -fx-padding: 5 0 5 0;");

        Button simuBtn = new Button("▶  Voir simulation SEIR");
        simuBtn.setMaxWidth(Double.MAX_VALUE);
        simuBtn.setStyle(
            "-fx-background-color: #2980b9;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 7;" +
            "-fx-background-radius: 4;" +
            "-fx-font-size: 11px;"
        );
        simuBtn.setOnAction(e -> renderRegionalGrid(region.getName()));

        Button exportBtn = new Button("↓  Exporter les données");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setStyle(
            "-fx-background-color: #27ae60;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 7;" +
            "-fx-background-radius: 4;" +
            "-fx-font-size: 11px;"
        );
        exportBtn.setOnAction(e -> showMessage("📥 Export des données de " + region.getName()));

        Button alertBtn = new Button("🔔  Activer les alertes");
        alertBtn.setMaxWidth(Double.MAX_VALUE);
        alertBtn.setStyle(
            "-fx-background-color: #e67e22;" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 7;" +
            "-fx-background-radius: 4;" +
            "-fx-font-size: 11px;"
        );

        regionSection.getChildren().addAll(
            title, badge, sep,
            casLabel, rateLabel, popLabel, sep,
            actionsTitle, simuBtn, exportBtn, alertBtn
        );
    }

    private Label buildStatLine(String key, String value, String color) {
        Label lbl = new Label(key + " : " + value);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        return lbl;
    }

    // ==================== DIALOGUE LOGIN ====================

    private void showLoginDialog() {
        Stage popup = new Stage();
        popup.setTitle("Connexion Expert");

        Label title = new Label("🔑 Connexion Expert");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");

        TextField userField = new TextField();
        userField.setPromptText("Identifiant");
        userField.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-prompt-text-fill: #7f8c8d;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Mot de passe");
        passField.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-prompt-text-fill: #7f8c8d;");

        Label hint = new Label("Utilisez admin/admin pour l'accès expert");
        hint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");

        Button loginBtn = new Button("Se connecter");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle(
            "-fx-background-color: #27ae60;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;"
        );
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
        layout.setStyle("-fx-background-color: #1e2d3d;");

        popup.setScene(new Scene(layout, 300, 250));
        popup.show();
    }

    // ==================== PANEL ADMIN VILLE ====================

    private void showAdminCityPanel(String regionName, String cityName) {
        Stage popup = new Stage();
        popup.setTitle("Modifier : " + cityName);

        Label title = new Label("📍 " + cityName);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");

        Label subtitle = new Label("Modifier le niveau de risque");
        subtitle.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        Button greenBtn  = buildAdminColorBtn("🟢 Faible risque",  "#27ae60", models.types.Color.GREEN,  regionName, cityName, popup);
        Button orangeBtn = buildAdminColorBtn("🟠 Risque modéré",  "#e67e22", models.types.Color.ORANGE, regionName, cityName, popup);
        Button redBtn    = buildAdminColorBtn("🔴 Risque élevé",   "#e74c3c", models.types.Color.RED,    regionName, cityName, popup);

        VBox layout = new VBox(10, title, subtitle, greenBtn, orangeBtn, redBtn);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #1e2d3d;");

        popup.setScene(new Scene(layout, 260, 220));
        popup.show();
    }

    private Button buildAdminColorBtn(String text, String color,
            models.types.Color modelColor, String regionName, String cityName, Stage popup) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8;" +
            "-fx-background-radius: 4;"
        );
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
            zones += r.getRegionalGraph().getCities().size();
            for (City c : r.getRegionalGraph().getCities().values())
                totalPop += c.getTotalPopulation();
        }
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;
        statCasActifs.setText("" + totalInf);
        statTauxNational.setText(String.format("%.2f%%", rate));
        statZones.setText("" + zones);
        statMaj.setText("100%");
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
        if (text.contains("Play") || text.contains("Lancer")) {
            playPauseBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white;" +
                "-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8; -fx-background-radius: 4;"
            );
        } else {
            playPauseBtn.setStyle(
                "-fx-background-color: #e74c3c; -fx-text-fill: white;" +
                "-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8; -fx-background-radius: 4;"
            );
        }
    }

    /**
     * Converts a models.types.Color enum to a JavaFX Color for rendering.
     * Using fully qualified name to avoid conflict with javafx.scene.paint.Color.
     */
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
            (int)(color.getRed()   * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue()  * 255));
    }

    private String darken(String hex) {
        Color c = Color.web(hex);
        return toHex(c.darker());
    }

    public static void main(String[] args) {
        launch(args);
    }
}