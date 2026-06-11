package view;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import models.entities.Region;

/**
 * MapView — rendu de la carte nationale à partir du fichier GeoJSON.
 *
 * Responsabilité unique : charger regions.geojson, projeter les coordonnées
 * géographiques (longitude/latitude) en pixels, et dessiner un Polygon JavaFX
 * coloré pour chaque région selon son niveau de risque COVID.
 *
 * Les interactions utilisateur (hover, clic) sont câblées ici car elles
 * sont visuellement liées aux polygones, mais délèguent au SimulationController
 * et à SimulationView pour la logique.
 *
 * Projection utilisée : projection plate-carrée (équirectangulaire) —
 * suffisante pour la France métropolitaine à cette échelle.
 */
public class MapView {

    // ── Constantes de rendu ───────────────────────────────────────────────────
    /** Largeur cible du canvas de la carte en pixels. */
    private static final double MAP_W   = 750.0;
    /** Hauteur cible du canvas de la carte en pixels. */
    private static final double MAP_H   = 580.0;
    /** Marge interne (padding) autour de la carte en pixels. */
    private static final double PADDING = 20.0;

    /**
     * Correspondance entre les noms de régions dans le GeoJSON
     * et les noms utilisés dans le modèle (NationalGraph).
     * Nécessaire car le GeoJSON utilise des noms officiels complets.
     */
    private static final Map<String, String> GEO_TO_REGION = new HashMap<>();
    static {
        GEO_TO_REGION.put("Île-de-France",             "Île-de-France");
        GEO_TO_REGION.put("Bretagne",                   "Bretagne");
        GEO_TO_REGION.put("Provence-Alpes-Côte d'Azur", "PACA");
        GEO_TO_REGION.put("Normandie",                  "Normandie");
    }

    // ── Références ────────────────────────────────────────────────────────────
    private final SimulationController ctrl;
    private Pane mapPane; // conservé pour ajouter les labels par-dessus les polygones

    public MapView(SimulationController controller) {
        this.ctrl = controller;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  MÉTHODE PRINCIPALE
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Construit et retourne le VBox contenant la carte nationale.
     * Appelé par SimulationView.renderNationalMap() à chaque rafraîchissement.
     *
     * @param dayInfo      texte du timer à afficher dans le titre (ex. "Jour 14 · Semaine 2")
     * @param onRegionClic callback appelé quand l'utilisateur clique sur une région ;
     *                     reçoit le nom de la région (String)
     * @param onMessage    callback pour afficher un message dans la barre du bas
     */
    public VBox build(String dayInfo,
                      java.util.function.Consumer<String> onRegionClic,
                      java.util.function.Consumer<String> onMessage) {

        mapPane = new Pane();
        mapPane.setStyle("-fx-background-color: #eaf2f8;");

        try {
            // ── Chargement du GeoJSON depuis les ressources Maven ─────────────
            InputStream is = getClass().getResourceAsStream("/regions.geojson");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject geojson = new JSONObject(json);
            JSONArray features = geojson.getJSONArray("features");

            // ── Calcul des bornes géographiques (min/max lon/lat) ─────────────
            // Nécessaire pour normaliser les coordonnées en pixels.
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;

            for (int i = 0; i < features.length(); i++) {
                JSONObject geometry = features.getJSONObject(i).getJSONObject("geometry");
                String type = geometry.getString("type");

                if (type.equals("Polygon")) {
                    double[] bounds = updateBounds(
                        geometry.getJSONArray("coordinates").getJSONArray(0),
                        minLon, maxLon, minLat, maxLat
                    );
                    minLon = bounds[0]; maxLon = bounds[1];
                    minLat = bounds[2]; maxLat = bounds[3];

                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    for (int p = 0; p < polys.length(); p++) {
                        double[] bounds = updateBounds(
                            polys.getJSONArray(p).getJSONArray(0),
                            minLon, maxLon, minLat, maxLat
                        );
                        minLon = bounds[0]; maxLon = bounds[1];
                        minLat = bounds[2]; maxLat = bounds[3];
                    }
                }
            }

            // ── Variables finales pour les lambdas ────────────────────────────
            final double fMinLon = minLon, fMaxLon = maxLon;
            final double fMinLat = minLat, fMaxLat = maxLat;

            // ── Dessin de chaque feature GeoJSON ─────────────────────────────
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature  = features.getJSONObject(i);
                JSONObject props    = feature.getJSONObject("properties");
                JSONObject geometry = feature.getJSONObject("geometry");
                String geoName    = props.getString("nom");
                String type       = geometry.getString("type");

                // Cherche la Region correspondante dans le modèle
                String regionName = GEO_TO_REGION.getOrDefault(geoName, geoName);
                Region region = ctrl.getNationalGraph().getRegions().get(regionName);

                // Couleur : gris si la région n'est pas dans le modèle
                Color fillColor = (region != null)
                    ? riskToColor(region.getRiskColor())
                    : Color.valueOf("#bdc3c7");

                if (type.equals("Polygon")) {
                    JSONArray coords = geometry.getJSONArray("coordinates").getJSONArray(0);
                    mapPane.getChildren().add(buildPolygon(
                        coords, fMinLon, fMaxLon, fMinLat, fMaxLat,
                        fillColor, geoName, regionName, region,
                        onRegionClic, onMessage
                    ));
                    double[] center = centroid(coords, fMinLon, fMaxLon, fMinLat, fMaxLat);
                    addLabel(geoName, center[0], center[1], region);

                } else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    double[] firstCenter = null;
                    for (int p = 0; p < polys.length(); p++) {
                        JSONArray coords = polys.getJSONArray(p).getJSONArray(0);
                        mapPane.getChildren().add(buildPolygon(
                            coords, fMinLon, fMaxLon, fMinLat, fMaxLat,
                            fillColor, geoName, regionName, region,
                            onRegionClic, onMessage
                        ));
                        if (p == 0)
                            firstCenter = centroid(coords, fMinLon, fMaxLon, fMinLat, fMaxLat);
                    }
                    if (firstCenter != null)
                        addLabel(geoName, firstCenter[0], firstCenter[1], region);
                }
            }

        } catch (Exception e) {
            onMessage.accept("Erreur chargement carte : " + e.getMessage());
            e.printStackTrace();
        }

        // ── Titre avec info jour ──────────────────────────────────────────────
        Label title = new Label("🗺  Carte Épidémiologique COVID-19 — " + dayInfo);
        title.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50; -fx-padding: 10 15 8 15;"
        );

        ScrollPane scroll = new ScrollPane(mapPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #eaf2f8; -fx-background: #eaf2f8;");

        VBox container = new VBox(title, new Separator(), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return container;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  CONSTRUCTION D'UN POLYGONE
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Crée un Polygon JavaFX à partir d'un tableau de coordonnées GeoJSON.
     *
     * La projection consiste à normaliser lon/lat entre 0 et 1 par rapport aux
     * bornes globales, puis à multiplier par les dimensions du canvas.
     * L'axe Y est inversé (lat max → y=0) car en JavaFX y croît vers le bas.
     *
     * @param coords       tableau JSON de [lon, lat]
     * @param onRegionClic callback de clic sur la région
     * @param onMessage    callback pour affichage de messages d'erreur
     */
    private Polygon buildPolygon(
            JSONArray coords,
            double minLon, double maxLon, double minLat, double maxLat,
            Color fillColor, String geoName, String regionName, Region region,
            java.util.function.Consumer<String> onRegionClic,
            java.util.function.Consumer<String> onMessage) {

        Polygon poly = new Polygon();

        // Conversion coordonnées géographiques → pixels
        for (int j = 0; j < coords.length(); j++) {
            double lon = coords.getJSONArray(j).getDouble(0);
            double lat = coords.getJSONArray(j).getDouble(1);
            double x = PADDING + (lon - minLon) / (maxLon - minLon) * (MAP_W - 2 * PADDING);
            // Y inversé : la latitude augmente vers le nord (haut écran)
            double y = PADDING + (maxLat - lat) / (maxLat - minLat) * (MAP_H - 2 * PADDING);
            poly.getPoints().addAll(x, y);
        }

        poly.setFill(fillColor);
        poly.setStroke(Color.WHITE);
        poly.setStrokeWidth(1.5);

        // ── Effet hover : surbrillance au survol ──────────────────────────────
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

        // ── Clic : navigation vers la vue régionale ───────────────────────────
        poly.setOnMouseClicked(e -> {
            if (region != null) {
                onRegionClic.accept(regionName);
            } else {
                onMessage.accept("ℹ Région '" + geoName + "' non configurée dans la simulation");
            }
        });
        poly.setStyle("-fx-cursor: hand;");

        // ── Tooltip : info rapide au survol ───────────────────────────────────
        String tipText = region != null
            ? geoName + "\nInfectés : " + region.getTotalInfected()
            : geoName + "\n(non configurée)";
        Tooltip.install(poly, new Tooltip(tipText));

        return poly;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  LABELS SUR LA CARTE
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ajoute le nom de la région et le nombre d'infectés sous forme de Labels
     * positionnés au centroïde du polygone.
     * mouseTransparent=true évite que ces labels captent les événements souris
     * à la place du polygone sous-jacent.
     */
    private void addLabel(String name, double x, double y, Region region) {
        Label nameLbl = new Label(name);
        nameLbl.setStyle(
            "-fx-font-size: 9px; -fx-font-weight: bold;" +
            "-fx-text-fill: white;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0, 0, 1);"
        );
        nameLbl.setLayoutX(x - 30);
        nameLbl.setLayoutY(y - 8);
        nameLbl.setMouseTransparent(true);

        if (region != null) {
            Label infLbl = new Label("🦠 " + region.getTotalInfected());
            infLbl.setStyle(
                "-fx-font-size: 8px; -fx-text-fill: white;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0, 0, 1);"
            );
            infLbl.setLayoutX(x - 20);
            infLbl.setLayoutY(y + 4);
            infLbl.setMouseTransparent(true);
            mapPane.getChildren().add(infLbl);
        }
        mapPane.getChildren().add(nameLbl);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  HELPERS GÉOMÉTRIQUES
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Calcule le centroïde (barycentre) d'un polygone exprimé en pixels.
     * Utilisé pour positionner les labels au centre de chaque région.
     *
     * @return tableau [x, y] en pixels
     */
    private double[] centroid(JSONArray coords,
            double minLon, double maxLon, double minLat, double maxLat) {
        double sumX = 0, sumY = 0;
        for (int j = 0; j < coords.length(); j++) {
            double lon = coords.getJSONArray(j).getDouble(0);
            double lat = coords.getJSONArray(j).getDouble(1);
            sumX += PADDING + (lon - minLon) / (maxLon - minLon) * (MAP_W - 2 * PADDING);
            sumY += PADDING + (maxLat - lat) / (maxLat - minLat) * (MAP_H - 2 * PADDING);
        }
        return new double[]{sumX / coords.length(), sumY / coords.length()};
    }

    /**
     * Met à jour les 4 bornes géographiques (min/max lon/lat) en parcourant
     * un tableau de coordonnées. Retourne un tableau [minLon, maxLon, minLat, maxLat].
     */
    private double[] updateBounds(JSONArray coords,
            double minLon, double maxLon, double minLat, double maxLat) {
        for (int j = 0; j < coords.length(); j++) {
            double lon = coords.getJSONArray(j).getDouble(0);
            double lat = coords.getJSONArray(j).getDouble(1);
            minLon = Math.min(minLon, lon); maxLon = Math.max(maxLon, lon);
            minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat);
        }
        return new double[]{minLon, maxLon, minLat, maxLat};
    }

    /**
     * Convertit un niveau de risque (enum models.types.Color) en Color JavaFX.
     * Rouge = élevé, Orange = modéré, Vert = faible.
     */
    private Color riskToColor(models.types.Color riskColor) {
        switch (riskColor) {
            case RED:    return Color.valueOf("#e74c3c");
            case ORANGE: return Color.valueOf("#e67e22");
            default:     return Color.valueOf("#27ae60");
        }
    }
}