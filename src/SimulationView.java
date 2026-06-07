import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;

public class SimulationView extends Application {

    private NationalGraph nationalGraph;
    private BorderPane root;
    private static final int CITY_RADIUS = 30;

    @Override
    public void start(Stage stage) {
        // Données de test — à remplacer par SimulationController plus tard
        nationalGraph = buildTestData();

        root = new BorderPane();
        root.setTop(buildControlBar());
        renderNationalMap();

        Scene scene = new Scene(root, 900, 650);
        stage.setTitle("Observatoire Epidémiologique National");
        stage.setScene(scene);
        stage.show();
    }

    // ===================== VUE NATIONALE =====================

    /**
     * Affiche toutes les régions sous forme de carte nationale
     * Chaque région est colorée selon son riskColor
     */
    public void renderNationalMap() {
        FlowPane mapPane = new FlowPane();
        mapPane.setHgap(20);
        mapPane.setVgap(20);
        mapPane.setPadding(new Insets(20));
        mapPane.setAlignment(Pos.CENTER);

        for (Region region : nationalGraph.getRegions().values()) {
            VBox regionBox = buildRegionBox(region);
            mapPane.getChildren().add(regionBox);
        }

        Label title = new Label("Carte Nationale — cliquez sur une région pour zoomer");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10;");

        VBox center = new VBox(10, title, mapPane);
        root.setCenter(center);
    }

    /**
     * Construit la boîte visuelle d'une région
     */
    private VBox buildRegionBox(Region region) {
        Rectangle rect = new Rectangle(120, 90);
        rect.setFill(convertColor(region.getRiskColor()));
        rect.setStroke(Color.BLACK);
        rect.setArcWidth(10);
        rect.setArcHeight(10);

        Label nameLabel = new Label(region.getName());
        nameLabel.setStyle("-fx-font-weight: bold;");

        region.totalInfectedGraph();
        Label infectedLabel = new Label("Infectés : " + region.getTotalInfected());
        infectedLabel.setStyle("-fx-text-fill: darkred;");

        VBox box = new VBox(5, rect, nameLabel, infectedLabel);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-cursor: hand;");

        // Clic → zoom sur la région
        box.setOnMouseClicked(e -> renderRegionalGrid(region.getName()));

        return box;
    }

    // ===================== VUE RÉGIONALE =====================

    /**
     * Affiche le graphe des villes d'une région
     * Les villes sont des cercles colorés reliés par des routes
     */
    public void renderRegionalGrid(String regionName) {
        Region region = nationalGraph.getRegions().get(regionName);
        if (region == null) return;

        RegionalGraph regionalGraph = region.getRegionalGraph();

        // Pane libre pour positionner les villes et routes
        Pane graphPane = new Pane();
        graphPane.setPrefSize(700, 500);

        // Positions des villes (réparties en cercle)
        java.util.List<City> cities = new java.util.ArrayList<>(regionalGraph.getCities().values());
        java.util.Map<String, double[]> positions = new java.util.HashMap<>();

        int n = cities.size();
        double cx = 350, cy = 250, r = 180;

        // Dessin des routes d'abord (en dessous des villes)
        for (Route route : regionalGraph.getRoutes()) {
            String nameA = route.getCityA().getName();
            String nameB = route.getCityB().getName();
            double[] posA = positions.get(nameA);
            double[] posB = positions.get(nameB);

            // On calcule les positions d'abord
            for (int i = 0; i < n; i++) {
                double angle = 2 * Math.PI * i / n;
                positions.put(cities.get(i).getName(), new double[]{
                    cx + r * Math.cos(angle),
                    cy + r * Math.sin(angle)
                });
            }

            posA = positions.get(nameA);
            posB = positions.get(nameB);

            if (posA != null && posB != null) {
                Line line = new Line(posA[0], posA[1], posB[0], posB[1]);
                line.setStroke(Color.GRAY);
                line.setStrokeWidth(2);
                graphPane.getChildren().add(line);
            }
        }

        // Dessin des villes par-dessus les routes
        for (int i = 0; i < n; i++) {
            City city = cities.get(i);
            double[] pos = positions.get(city.getName());
            if (pos == null) continue;

            Circle circle = new Circle(pos[0], pos[1], CITY_RADIUS);
            circle.setFill(convertColor(city.getRiskColor()));
            circle.setStroke(Color.BLACK);

            Label label = new Label(city.getName());
            label.setLayoutX(pos[0] - 25);
            label.setLayoutY(pos[1] + CITY_RADIUS + 3);
            label.setStyle("-fx-font-size: 11px;");

            // Tooltip avec les stats de la ville
            Tooltip tooltip = new Tooltip(
                city.getName() + "\n" +
                "Sains : " + city.getSafe() + "\n" +
                "Exposés : " + city.getExposed() + "\n" +
                "Infectés : " + city.getInfected() + "\n" +
                "Guéris : " + city.getRecovered()
            );
            Tooltip.install(circle, tooltip);

            graphPane.getChildren().addAll(circle, label);
        }

        // Bouton retour
        Button backBtn = new Button("← Carte Nationale");
        backBtn.setOnAction(e -> renderNationalMap());

        Label title = new Label("Région : " + regionName);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox topBar = new HBox(20, backBtn, title);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(10, topBar, graphPane);
        root.setCenter(layout);
    }

    // ===================== BARRE DE CONTRÔLE =====================

    private HBox buildControlBar() {
        Button playPauseBtn = new Button("▶ Play");
        Button stepBtn = new Button("⏭ Étape suivante");

        Slider speedSlider = new Slider(0.5, 3.0, 1.0);
        speedSlider.setShowTickLabels(true);
        Label speedLabel = new Label("Vitesse :");

        // Légende des couleurs
        HBox legend = new HBox(10,
            buildLegendItem(Color.GREEN, "Faible risque"),
            buildLegendItem(Color.ORANGE, "Risque moyen"),
            buildLegendItem(Color.RED, "Risque élevé")
        );
        legend.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(15, playPauseBtn, stepBtn, speedLabel, speedSlider, legend);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        return bar;
    }

    private HBox buildLegendItem(Color color, String text) {
        Rectangle rect = new Rectangle(15, 15, color);
        rect.setStroke(Color.BLACK);
        Label label = new Label(text);
        HBox item = new HBox(5, rect, label);
        item.setAlignment(Pos.CENTER);
        return item;
    }

    // ===================== UTILITAIRES =====================

    /**
     * Convertit models.types.Color en javafx.scene.paint.Color
     */
    private Color convertColor(models.types.Color color) {
        switch (color) {
            case RED:    return Color.RED;
            case ORANGE: return Color.ORANGE;
            default:     return Color.GREEN;
        }
    }

    /**
     * Méthode appelée par l'Observer quand le modèle change
     */
    public void update() {
        renderNationalMap();
    }

    // ===================== DONNÉES DE TEST =====================

    /**
     * Données fictives pour tester la vue
     * À remplacer par le vrai SimulationController
     */
    private NationalGraph buildTestData() {
        NationalGraph graph = new NationalGraph();

        // Région Île-de-France
        Region idf = new Region("Île-de-France");
        City paris = new City("Paris", 500, 50, 200, 100, models.types.Color.RED);
        City versailles = new City("Versailles", 300, 20, 80, 40, models.types.Color.ORANGE);
        City evry = new City("Évry", 200, 10, 30, 20, models.types.Color.GREEN);
        idf.getRegionalGraph().addCity(paris);
        idf.getRegionalGraph().addCity(versailles);
        idf.getRegionalGraph().addCity(evry);
        idf.getRegionalGraph().addBiRoute(paris, versailles, 1.0);
        idf.getRegionalGraph().addBiRoute(paris, evry, 1.5);
        graph.addRegion(idf);

        // Région Bretagne
        Region bretagne = new Region("Bretagne");
        City rennes = new City("Rennes", 400, 10, 20, 15, models.types.Color.GREEN);
        City brest = new City("Brest", 250, 5, 10, 8, models.types.Color.GREEN);
        bretagne.getRegionalGraph().addCity(rennes);
        bretagne.getRegionalGraph().addCity(brest);
        bretagne.getRegionalGraph().addBiRoute(rennes, brest, 2.0);
        graph.addRegion(bretagne);

        // Région PACA
        Region paca = new Region("PACA");
        City marseille = new City("Marseille", 600, 80, 300, 120, models.types.Color.RED);
        City nice = new City("Nice", 350, 40, 150, 60, models.types.Color.ORANGE);
        paca.getRegionalGraph().addCity(marseille);
        paca.getRegionalGraph().addCity(nice);
        paca.getRegionalGraph().addBiRoute(marseille, nice, 1.8);
        graph.addRegion(paca);

        return graph;
    }

    public static void main(String[] args) {
        launch(args);
    }
}