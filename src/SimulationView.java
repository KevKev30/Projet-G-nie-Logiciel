import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;
import models.types.AccessState;

import java.util.*;

public class SimulationView extends Application {

    private SimulationController controller;
    private BorderPane root;
    private Label messageLabel;
    private Label userLabel;
    private Button playPauseBtn;
    private String currentRegion = null;

    private static final int CELL_SIZE = 90;
    private static final int GRID_GAP = 4;

    @Override
    public void start(Stage stage) {
        controller = new SimulationController(this);
        root = new BorderPane();
        root.setTop(buildTopBar());
        root.setBottom(buildBottomBar());
        renderNationalMap();

        Scene scene = new Scene(root, 1100, 750);
        scene.getStylesheets().add("style.css");
        stage.setTitle("Observatoire Epidémiologique National");
        stage.setScene(scene);
        stage.show();
    }

    // ==================== BARRES ====================

    private VBox buildTopBar() {
        // Barre utilisateur
        userLabel = new Label("Utilisateur");
        userLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        Button adminBtn = new Button("🔑 Admin");
        adminBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        adminBtn.setOnAction(e -> controller.loginAsAdmin());

        Button lambdaBtn = new Button("👤 Utilisateur");
        lambdaBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        lambdaBtn.setOnAction(e -> controller.loginAsLambda());

        Region spacer = null;
        HBox.setHgrow(new Label(), Priority.ALWAYS);
        HBox userBar = new HBox(10);
        userBar.getChildren().addAll(
            new Label("") {{ setStyle("-fx-text-fill:white;"); }},
            createSpacer(),
            new Label("Connecté :") {{ setStyle("-fx-text-fill:#aaa;"); }},
            userLabel, adminBtn, lambdaBtn
        );
        userBar.setPadding(new Insets(6, 15, 6, 15));
        userBar.setAlignment(Pos.CENTER_RIGHT);
        userBar.setStyle("-fx-background-color: #2c3e50;");

        // Barre de contrôle
        playPauseBtn = new Button("▶ Play");
        playPauseBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 90px;");
        playPauseBtn.setOnAction(e -> controller.togglePlayPause());

        Button stepBtn = new Button("⏭ Étape");
        stepBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        stepBtn.setOnAction(e -> controller.stepForward());

        Button randomBtn = new Button("⚡ Événement aléatoire");
        randomBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        randomBtn.setOnAction(e -> controller.generateRandomEvent());

        Slider speedSlider = new Slider(0.5, 3.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setPrefWidth(120);
        speedSlider.valueProperty().addListener((obs, o, n) -> controller.changeSpeed(n.doubleValue()));

        HBox controlBar = new HBox(12,
            playPauseBtn, stepBtn, randomBtn,
            new Separator() {{ setStyle("-fx-orientation: vertical;"); }},
            new Label("Vitesse :") {{ setStyle("-fx-font-weight: bold;"); }},
            speedSlider,
            new Separator() {{ setStyle("-fx-orientation: vertical;"); }},
            buildLegend()
        );
        controlBar.setPadding(new Insets(8, 15, 8, 15));
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 2 0;");

        return new VBox(userBar, controlBar);
    }

    private HBox createSpacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private HBox buildLegend() {
        HBox legend = new HBox(12,
            buildLegendItem(Color.valueOf("#27ae60"), "Faible risque"),
            buildLegendItem(Color.valueOf("#e67e22"), "Risque moyen"),
            buildLegendItem(Color.valueOf("#e74c3c"), "Risque élevé"),
            buildLegendItem(Color.DARKGRAY, "Route bloquée")
        );
        legend.setAlignment(Pos.CENTER);
        return legend;
    }

    private HBox buildLegendItem(Color color, String text) {
        Rectangle r = new Rectangle(14, 14, color);
        r.setStroke(Color.BLACK);
        r.setArcWidth(3);
        r.setArcHeight(3);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px;");
        HBox item = new HBox(5, r, lbl);
        item.setAlignment(Pos.CENTER);
        return item;
    }

    private HBox buildBottomBar() {
        messageLabel = new Label("Bienvenue dans l'Observatoire Epidémiologique National");
        messageLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-style: italic;");
        HBox bar = new HBox(messageLabel);
        bar.setPadding(new Insets(6, 15, 6, 15));
        bar.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // ==================== VUE NATIONALE ====================

    public void renderNationalMap() {
        currentRegion = null;

        // Titre + stats globales
        int totalInfected = 0;
        for (Region r : controller.getNationalGraph().getRegions().values()) {
            r.totalInfectedGraph();
            totalInfected += r.getTotalInfected();
        }

        Label title = new Label("🗺  Carte Nationale");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label statsLabel = new Label("Total infectés (national) : " + totalInfected);
        statsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");

        HBox titleBar = new HBox(20, title, statsLabel);
        titleBar.setPadding(new Insets(15, 20, 10, 20));
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // Grille des régions
        FlowPane regionsPane = new FlowPane();
        regionsPane.setHgap(20);
        regionsPane.setVgap(20);
        regionsPane.setPadding(new Insets(10, 20, 20, 20));

        for (Region region : controller.getNationalGraph().getRegions().values()) {
            regionsPane.getChildren().add(buildRegionCard(region));
        }

        VBox center = new VBox(titleBar, new Separator(), regionsPane);
        root.setCenter(center);
    }

    private VBox buildRegionCard(Region region) {
        // Couleur de fond selon risque
        Color bgColor = convertColor(region.getRiskColor());
        String bgHex = toHex(bgColor);

        // En-tête colorée
        Label nameLabel = new Label(region.getName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 8 12 8 12;");

        HBox header = new HBox(nameLabel);
        header.setStyle("-fx-background-color: " + bgHex + "; -fx-background-radius: 8 8 0 0;");
        header.setAlignment(Pos.CENTER);

        // Statistiques
        int totalPop = 0;
        int totalInf = 0;
        int totalSafe = 0;
        for (City city : region.getRegionalGraph().getCities().values()) {
            totalPop += city.getTotalPopulation();
            totalInf += city.getInfected();
            totalSafe += city.getSafe();
        }
        double rate = totalPop > 0 ? (double) totalInf / totalPop * 100 : 0;

        Label popLabel = new Label("Population : " + totalPop);
        Label infLabel = new Label("Infectés : " + totalInf);
        infLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        Label safeLabel = new Label("Sains : " + totalSafe);
        safeLabel.setStyle("-fx-text-fill: #27ae60;");
        Label rateLabel = new Label(String.format("Taux : %.1f%%", rate));
        rateLabel.setStyle("-fx-font-weight: bold;");

        // Barre de progression infection
        ProgressBar progressBar = new ProgressBar(Math.min(rate / 100, 1.0));
        progressBar.setPrefWidth(160);
        progressBar.setStyle(rate > 60 ? "-fx-accent: #e74c3c;" :
                             rate > 30 ? "-fx-accent: #e67e22;" : "-fx-accent: #27ae60;");

        VBox stats = new VBox(6, popLabel, infLabel, safeLabel, rateLabel, progressBar);
        stats.setPadding(new Insets(10, 12, 10, 12));
        stats.setStyle("-fx-background-color: white;");

        // Bouton zoom
        Button zoomBtn = new Button("🔍 Voir la région");
        zoomBtn.setStyle("-fx-background-color: " + bgHex + "; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 0 0 8 8;");
        zoomBtn.setMaxWidth(Double.MAX_VALUE);
        zoomBtn.setOnAction(e -> renderRegionalGrid(region.getName()));

        VBox card = new VBox(header, stats, zoomBtn);
        card.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 2);");
        card.setPrefWidth(200);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-border-color: #3498db; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(52,152,219,0.4), 10, 0, 0, 3); -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 2);"));

        return card;
    }

    // ==================== VUE RÉGIONALE (GRILLE 2D) ====================

    public void renderRegionalGrid(String regionName) {
        currentRegion = regionName;
        Region region = controller.getNationalGraph().getRegions().get(regionName);
        if (region == null) return;

        RegionalGraph rg = region.getRegionalGraph();
        List<City> cities = new ArrayList<>(rg.getCities().values());

        // Calcul taille de la grille
        int cols = (int) Math.ceil(Math.sqrt(cities.size()));
        int rows = (int) Math.ceil((double) cities.size() / cols);

        // Positions des villes dans la grille
        Map<String, int[]> gridPositions = new HashMap<>();
        for (int i = 0; i < cities.size(); i++) {
            gridPositions.put(cities.get(i).getName(), new int[]{i % cols, i / cols});
        }

        // Pane pour dessiner routes + cellules
        Pane gridPane = new Pane();
        int paneW = cols * (CELL_SIZE + GRID_GAP) + GRID_GAP + 20;
        int paneH = rows * (CELL_SIZE + GRID_GAP) + GRID_GAP + 20;
        gridPane.setPrefSize(paneW, paneH);

        // Dessin des routes
        for (Route route : rg.getRoutes()) {
            int[] posA = gridPositions.get(route.getCityA().getName());
            int[] posB = gridPositions.get(route.getCityB().getName());
            if (posA == null || posB == null) continue;

            double x1 = posA[0] * (CELL_SIZE + GRID_GAP) + CELL_SIZE / 2.0 + 10;
            double y1 = posA[1] * (CELL_SIZE + GRID_GAP) + CELL_SIZE / 2.0 + 10;
            double x2 = posB[0] * (CELL_SIZE + GRID_GAP) + CELL_SIZE / 2.0 + 10;
            double y2 = posB[1] * (CELL_SIZE + GRID_GAP) + CELL_SIZE / 2.0 + 10;

            Line line = new Line(x1, y1, x2, y2);
            boolean blocked = route.getAccess() == AccessState.BARRICATED;
            line.setStroke(blocked ? Color.DARKGRAY : Color.STEELBLUE);
            line.setStrokeWidth(blocked ? 2 : 3);
            if (blocked) line.getStrokeDashArray().addAll(8.0, 4.0);

            final String cityA = route.getCityA().getName();
            final String cityB = route.getCityB().getName();
            line.setOnMouseClicked(e -> {
                if (controller.isAdmin()) {
                    controller.toggleRoute(regionName, cityA, cityB);
                } else {
                    showMessage("⛔ Seul l'Admin peut modifier les routes !");
                }
            });
            line.setStyle("-fx-cursor: hand;");
            Tooltip.install(line, new Tooltip(
                (blocked ? "🚧 Bloquée" : "✅ Ouverte") + " : " + cityA + " ↔ " + cityB +
                (controller.isAdmin() ? "\nCliquez pour " + (blocked ? "ouvrir" : "bloquer") : "")
            ));

            gridPane.getChildren().add(line);
        }

        // Dessin des cellules (villes)
        for (City city : cities) {
            int[] pos = gridPositions.get(city.getName());
            if (pos == null) continue;

            double x = pos[0] * (CELL_SIZE + GRID_GAP) + 10;
            double y = pos[1] * (CELL_SIZE + GRID_GAP) + 10;

            // Cellule colorée
            Rectangle cell = new Rectangle(x, y, CELL_SIZE, CELL_SIZE);
            cell.setFill(convertColor(city.getRiskColor()));
            cell.setStroke(Color.WHITE);
            cell.setStrokeWidth(2);
            cell.setArcWidth(10);
            cell.setArcHeight(10);

            // Nom de la ville
            Label nameLabel = new Label(city.getName());
            nameLabel.setLayoutX(x + 4);
            nameLabel.setLayoutY(y + 5);
            nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
            nameLabel.setMaxWidth(CELL_SIZE - 8);

            // Nombre infectés
            Label infLabel = new Label("🦠 " + city.getInfected());
            infLabel.setLayoutX(x + 4);
            infLabel.setLayoutY(y + CELL_SIZE - 22);
            infLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");

            // Taux
            double rate = city.getInfectionRate() * 100;
            Label rateLabel = new Label(String.format("%.0f%%", rate));
            rateLabel.setLayoutX(x + CELL_SIZE - 32);
            rateLabel.setLayoutY(y + 5);
            rateLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");

            // Tooltip complet
            Tooltip tip = new Tooltip(
                city.getName() + "\n" +
                "Sains      : " + city.getSafe() + "\n" +
                "Exposés  : " + city.getExposed() + "\n" +
                "Infectés   : " + city.getInfected() + "\n" +
                "Guéris     : " + city.getRecovered() + "\n" +
                String.format("Taux       : %.1f%%", rate)
            );
            Tooltip.install(cell, tip);

            // Admin : clic sur cellule
            cell.setOnMouseClicked(e -> {
                if (controller.isAdmin()) {
                    showAdminCityPanel(regionName, city.getName());
                }
            });
            cell.setStyle("-fx-cursor: " + (controller.isAdmin() ? "hand" : "default") + ";");

            gridPane.getChildren().addAll(cell, nameLabel, infLabel, rateLabel);
        }

        // ScrollPane si la grille est grande
        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setFitToWidth(false);
        scroll.setStyle("-fx-background-color: #ecf0f1;");

        // Panel stats de la région
        VBox statsPanel = buildRegionStatsPanel(region);

        // Layout principal
        Button backBtn = new Button("← Carte Nationale");
        backBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        backBtn.setOnAction(e -> renderNationalMap());

        Label title = new Label("📍 Région : " + regionName);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox topBar = new HBox(15, backBtn, title);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // Panel admin
        HBox adminPanel = controller.isAdmin() ? buildAdminPanel() : new HBox();

        HBox content = new HBox(15, scroll, statsPanel);
        content.setPadding(new Insets(15));

        VBox layout = new VBox(topBar, adminPanel, content);
        root.setCenter(layout);
    }

    private VBox buildRegionStatsPanel(Region region) {
        Label title = new Label("📊 Statistiques");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        VBox panel = new VBox(10, title, new Separator());
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-background-radius: 8;");
        panel.setPrefWidth(220);

        for (City city : region.getRegionalGraph().getCities().values()) {
            Label cityTitle = new Label(city.getName());
            cityTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: " + toHex(convertColor(city.getRiskColor())) + ";");

            ProgressBar pb = new ProgressBar(Math.min(city.getInfectionRate(), 1.0));
            pb.setPrefWidth(180);
            pb.setStyle(city.getInfectionRate() > 0.6 ? "-fx-accent: #e74c3c;" :
                        city.getInfectionRate() > 0.3 ? "-fx-accent: #e67e22;" : "-fx-accent: #27ae60;");

            Label details = new Label(
                "🦠 " + city.getInfected() + " infectés / " + city.getTotalPopulation() + " hab."
            );
            details.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

            panel.getChildren().addAll(cityTitle, pb, details, new Separator());
        }

        return panel;
    }

    private HBox buildAdminPanel() {
        Label title = new Label("🔧 Mode Admin");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        Label info = new Label("Cliquez sur une ville pour changer son statut | Cliquez sur une route pour la bloquer/ouvrir");
        info.setStyle("-fx-font-style: italic; -fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        HBox panel = new HBox(15, title, info);
        panel.setPadding(new Insets(6, 15, 6, 15));
        panel.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffc107; -fx-border-width: 0 0 1 0;");
        panel.setAlignment(Pos.CENTER_LEFT);
        return panel;
    }

    private void showAdminCityPanel(String regionName, String cityName) {
        Stage popup = new Stage();
        popup.setTitle("Modifier : " + cityName);

        Label label = new Label("Changer le statut de " + cityName);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Button greenBtn = new Button("🟢 Faible risque (Vert)");
        Button orangeBtn = new Button("🟠 Risque moyen (Orange)");
        Button redBtn = new Button("🔴 Risque élevé (Rouge)");

        greenBtn.setMaxWidth(Double.MAX_VALUE);
        orangeBtn.setMaxWidth(Double.MAX_VALUE);
        redBtn.setMaxWidth(Double.MAX_VALUE);

        greenBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
        orangeBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-cursor: hand;");
        redBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");

        greenBtn.setOnAction(e -> { controller.setCityColor(regionName, cityName, models.types.Color.GREEN); popup.close(); });
        orangeBtn.setOnAction(e -> { controller.setCityColor(regionName, cityName, models.types.Color.ORANGE); popup.close(); });
        redBtn.setOnAction(e -> { controller.setCityColor(regionName, cityName, models.types.Color.RED); popup.close(); });

        VBox layout = new VBox(15, label, greenBtn, orangeBtn, redBtn);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        popup.setScene(new Scene(layout, 280, 220));
        popup.show();
    }

    // ==================== UTILITAIRES ====================

    public void update() {
        if (currentRegion != null) {
            renderRegionalGrid(currentRegion);
        } else {
            renderNationalMap();
        }
    }

    public void showMessage(String msg) {
        messageLabel.setText(msg);
    }

    public void updateUserLabel(String text) {
        userLabel.setText(text);
    }

    public void setPlayPauseButton(String text) {
        playPauseBtn.setText(text);
        if (text.contains("Play")) {
            playPauseBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 90px;");
        } else {
            playPauseBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 90px;");
        }
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
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }
}