package view;
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.types.AccessState;
 
/**
 * RegionalGridView — vue détaillée d'une région sous forme de grille de villes.
 *
 * Responsabilité unique : afficher les villes d'une région sous forme de
 * "cellules" colorées (Rectangle + Labels), reliées par des lignes (Route).
 *
 * Disposition en grille automatique : les villes sont placées en colonnes
 * calculées par ceil(sqrt(n)), ce qui donne une grille approximativement carrée.
 *
 * Chaque cellule affiche :
 *   - le nom de la ville
 *   - le nombre d'infectés (🦠)
 *   - le taux d'infection en %
 *   - une barre de progression (rectangle blanc en bas de la cellule)
 *
 * Chaque route affiche :
 *   - une ligne bleue si ouverte, rouge en tirets si barrée
 */
public class RegionalGridView {
 
    // ── Constantes de mise en page des cellules ───────────────────────────────
    /** Taille d'une cellule de ville (carré). */
    private static final int CELL_SIZE = 85;
    /** Écart entre cellules (utilisé × 4 comme "pas de grille"). */
    private static final int CELL_GAP  = 6;
 
    private final SimulationController ctrl;
 
    public RegionalGridView(SimulationController controller) {
        this.ctrl = controller;
    }
 
    // ────────────────────────────────────────────────────────────────────────
    //  MÉTHODE PRINCIPALE
    // ────────────────────────────────────────────────────────────────────────
 
    /**
     * Construit et retourne le VBox de la vue régionale.
     * Appelé par SimulationView.renderRegionalGrid() à chaque rafraîchissement.
     *
     * @param regionName    nom de la région à afficher
     * @param onBackClick   callback pour retourner à la carte nationale
     * @param onMessage     callback pour afficher un message bas de page
     */
    public VBox build(String regionName, Runnable onBackClick,
                      java.util.function.Consumer<String> onMessage) {
 
        Region region = ctrl.getNationalGraph().getRegions().get(regionName);
        if (region == null) return new VBox(); // sécurité : région introuvable
 
        List<City> cities = new ArrayList<>(region.getRegionalGraph().getCities().values());
 
        // ── Calcul de la disposition en grille ────────────────────────────────
        // On calcule le nombre de colonnes pour avoir une grille "carrée"
        int cols = (int) Math.ceil(Math.sqrt(cities.size()));
 
        // Pré-calcul des positions (x, y) en pixels pour chaque ville
        Map<String, double[]> positions = new HashMap<>();
        for (int i = 0; i < cities.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            positions.put(cities.get(i).getName(), new double[]{
                col * (CELL_SIZE + CELL_GAP * 4) + 20,
                row * (CELL_SIZE + CELL_GAP * 4) + 20
            });
        }
 
        // ── Canvas de la grille ───────────────────────────────────────────────
        Pane gridPane = new Pane();
        gridPane.setStyle("-fx-background-color: #f8f9fa;");
        gridPane.setPrefSize(
            cols * (CELL_SIZE + CELL_GAP * 4) + 40,
            (int) Math.ceil((double) cities.size() / cols) * (CELL_SIZE + CELL_GAP * 4) + 40
        );
 
        // Les routes sont dessinées EN PREMIER (sous les cellules)
        drawRoutes(gridPane, region, regionName, positions, onMessage);
 
        // Les cellules de villes sont dessinées PAR-DESSUS les routes
        drawCities(gridPane, cities, positions, regionName, onMessage);
 
        // ── Barre du haut (bouton retour + titre + timer) ─────────────────────
        Button backBtn = new Button("← Carte Nationale");
        styleBtn(backBtn, "#2c3e50");
        backBtn.setOnAction(e -> onBackClick.run());
 
        int totalDays = ctrl.getSimulationModel().getTotalDays();
        Label title = new Label(
            "📍 " + regionName +
            "   |   Jour " + totalDays +
            " · Sem. " + ctrl.getSimulationModel().getCurrentWeek()
        );
        title.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;"
        );
 
        HBox topBar = new HBox(15, backBtn, title);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;"
        );
 
        // Bandeau admin si l'utilisateur est connecté en mode Expert
        if (ctrl.isAdmin()) {
            topBar.getChildren().add(buildAdminBanner());
        }
 
        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setStyle("-fx-background-color: #f8f9fa; -fx-background: #f8f9fa;");
 
        VBox container = new VBox(topBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return container;
    }
 
    // ────────────────────────────────────────────────────────────────────────
    //  DESSIN DES ROUTES
    // ────────────────────────────────────────────────────────────────────────
 
    /**
     * Dessine les lignes représentant les routes entre villes.
     *
     * Une route ouverte → ligne bleue pleine (épaisseur 3).
     * Une route barrée  → ligne rouge en tirets (épaisseur 2).
     *
     * En mode Expert, un clic sur une ligne bascule l'état OPEN ↔ BARRICATED.
     */
    private void drawRoutes(Pane gridPane, Region region, String regionName,
                             Map<String, double[]> positions,
                             java.util.function.Consumer<String> onMessage) {
 
        for (Route route : region.getRegionalGraph().getRoutes()) {
            double[] posA = positions.get(route.getCityA().getName());
            double[] posB = positions.get(route.getCityB().getName());
            if (posA == null || posB == null) continue;
 
            // Coordonnées du centre de chaque cellule
            double x1 = posA[0] + CELL_SIZE / 2.0, y1 = posA[1] + CELL_SIZE / 2.0;
            double x2 = posB[0] + CELL_SIZE / 2.0, y2 = posB[1] + CELL_SIZE / 2.0;
 
            boolean blocked = route.getAccess() == AccessState.BARRICATED;
            Line line = new Line(x1, y1, x2, y2);
            line.setStroke(blocked ? Color.valueOf("#e74c3c") : Color.valueOf("#3498db"));
            line.setStrokeWidth(blocked ? 2 : 3);
            if (blocked) line.getStrokeDashArray().addAll(8.0, 4.0);
 
            // Noms capturés en final pour le lambda du clic
            final String cA = route.getCityA().getName();
            final String cB = route.getCityB().getName();
            line.setOnMouseClicked(e -> {
                if (ctrl.isAdmin()) {
                    ctrl.toggleRoute(regionName, cA, cB);
                } else {
                    onMessage.accept("⛔ Réservé à l'Expert !");
                }
            });
            line.setStyle("-fx-cursor: hand;");
            Tooltip.install(line, new Tooltip(
                (blocked ? "🚧 Bloquée" : "✅ Ouverte") + " — " + cA + " ↔ " + cB
            ));
            gridPane.getChildren().add(line);
        }
    }
 
    // ────────────────────────────────────────────────────────────────────────
    //  DESSIN DES CELLULES DE VILLES
    // ────────────────────────────────────────────────────────────────────────
 
    /**
     * Dessine une cellule par ville (Rectangle coloré + Labels superposés).
     *
     * Chaque cellule contient :
     *   - [haut gauche]  nom de la ville
     *   - [bas gauche]   🦠 + nombre d'infectés
     *   - [bas droit]    taux en %
     *   - [tout en bas]  barre de progression (fond semi-transparent + remplissage blanc)
     *
     * En mode Expert, un clic ouvre le panneau de modification du risque.
     */
    private void drawCities(Pane gridPane, List<City> cities,
                             Map<String, double[]> positions,
                             String regionName,
                             java.util.function.Consumer<String> onMessage) {
 
        for (City city : cities) {
            double[] pos = positions.get(city.getName());
            if (pos == null) continue;
 
            Color bg = riskToColor(city.getRiskColor());
 
            // ── Rectangle principal ───────────────────────────────────────────
            Rectangle cell = new Rectangle(pos[0], pos[1], CELL_SIZE, CELL_SIZE);
            cell.setFill(bg);
            cell.setArcWidth(10); cell.setArcHeight(10);
            cell.setStroke(Color.WHITE); cell.setStrokeWidth(2);
 
            cell.setOnMouseEntered(e -> { cell.setFill(bg.brighter()); cell.setStrokeWidth(3); });
            cell.setOnMouseExited(e  -> { cell.setFill(bg); cell.setStrokeWidth(2); });
 
            // ── Nom de la ville (haut gauche) ─────────────────────────────────
            Label nameLbl = new Label(city.getName());
            nameLbl.setLayoutX(pos[0] + 5); nameLbl.setLayoutY(pos[1] + 5);
            nameLbl.setStyle(
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;"
            );
            nameLbl.setMaxWidth(CELL_SIZE - 10);
            nameLbl.setMouseTransparent(true); // ne capte pas les clics
 
            // ── Compteur infectés (bas gauche) ────────────────────────────────
            Label infLbl = new Label("🦠 " + city.getInfected());
            infLbl.setLayoutX(pos[0] + 5); infLbl.setLayoutY(pos[1] + CELL_SIZE - 30);
            infLbl.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
            infLbl.setMouseTransparent(true);
 
            // ── Taux d'infection en % (bas droit) ────────────────────────────
            double rate = city.getInfectionRate() * 100;
            Label rateLbl = new Label(String.format("%.0f%%", rate));
            rateLbl.setLayoutX(pos[0] + CELL_SIZE - 32);
            rateLbl.setLayoutY(pos[1] + CELL_SIZE - 18);
            rateLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;"
            );
            rateLbl.setMouseTransparent(true);
 
            // ── Barre de progression (rectangle en bas) ───────────────────────
            // Fond semi-transparent
            Rectangle pbBg = new Rectangle(
                pos[0] + 4, pos[1] + CELL_SIZE - 8, CELL_SIZE - 8, 5
            );
            pbBg.setFill(Color.valueOf("rgba(255,255,255,0.3)"));
            pbBg.setArcWidth(3); pbBg.setArcHeight(3);
            pbBg.setMouseTransparent(true);
 
            // Remplissage proportionnel au taux
            double progressWidth = (CELL_SIZE - 8) * Math.min(rate / 100.0, 1.0);
            Rectangle pb = new Rectangle(
                pos[0] + 4, pos[1] + CELL_SIZE - 8, progressWidth, 5
            );
            pb.setFill(Color.WHITE);
            pb.setArcWidth(3); pb.setArcHeight(3);
            pb.setMouseTransparent(true);
 
            // ── Tooltip détaillé (survol) ─────────────────────────────────────
            Tooltip.install(cell, new Tooltip(
                "📍 " + city.getName() + "\n─────────────────\n" +
                "Sains     : " + city.getSafe()      + "\n" +
                "Exposés   : " + city.getExposed()   + "\n" +
                "Infectés  : " + city.getInfected()  + "\n" +
                "Guéris    : " + city.getRecovered() + "\n" +
                String.format("Taux      : %.1f%%", rate)
            ));
 
            // ── Clic : panneau admin (Expert uniquement) ──────────────────────
            cell.setOnMouseClicked(e -> {
                if (ctrl.isAdmin()) {
                    showAdminCityPanel(regionName, city.getName());
                } else {
                    onMessage.accept("🔒 Connexion Expert requise");
                }
            });
            cell.setStyle("-fx-cursor: hand;");
 
            // Ordre d'ajout : cell d'abord, puis les labels par-dessus
            gridPane.getChildren().addAll(cell, nameLbl, infLbl, rateLbl, pbBg, pb);
        }
    }
 
    // ────────────────────────────────────────────────────────────────────────
    //  POPUP ADMIN — modification du risque d'une ville
    // ────────────────────────────────────────────────────────────────────────
 
    /**
     * Ouvre une petite fenêtre permettant à l'Expert de changer manuellement
     * le niveau de risque (couleur) d'une ville.
     * Accessible uniquement en mode Admin (vérifié côté appelant).
     */
    private void showAdminCityPanel(String regionName, String cityName) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.setTitle("Modifier : " + cityName);
 
        Label title = new Label("📍 " + cityName);
        title.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;"
        );
        Label sub = new Label("Modifier le niveau de risque");
        sub.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
 
        // 3 boutons : un par niveau de risque
        Button g = buildColorBtn("🟢 Faible risque", "#27ae60",
            models.types.Color.GREEN,  regionName, cityName, popup);
        Button o = buildColorBtn("🟠 Risque modéré", "#e67e22",
            models.types.Color.ORANGE, regionName, cityName, popup);
        Button r = buildColorBtn("🔴 Risque élevé",  "#e74c3c",
            models.types.Color.RED,    regionName, cityName, popup);
 
        VBox layout = new VBox(10, title, sub, g, o, r);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");
        popup.setScene(new javafx.scene.Scene(layout, 260, 220));
        popup.show();
    }
 
    /**
     * Crée un bouton coloré pour le panneau admin.
     * Au clic : envoie la nouvelle couleur au contrôleur et ferme la popup.
     */
    private Button buildColorBtn(String text, String color,
            models.types.Color modelColor,
            String regionName, String cityName,
            javafx.stage.Stage popup) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(btn, color);
        btn.setOnAction(e -> {
            ctrl.setCityColor(regionName, cityName, modelColor);
            popup.close();
        });
        return btn;
    }
 
    // ────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────────────────
 
    /**
     * Bandeau affiché dans la barre du haut quand l'Expert est connecté.
     * Rappelle les raccourcis disponibles.
     */
    private HBox buildAdminBanner() {
        Label lbl = new Label(
            "🔧 Mode Expert — clic sur ville : modifier | clic sur route : bloquer/ouvrir"
        );
        lbl.setStyle(
            "-fx-text-fill: #e67e22; -fx-font-size: 11px; -fx-font-style: italic;"
        );
        HBox bar = new HBox(lbl);
        bar.setPadding(new Insets(0, 0, 0, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }
 
    /** Convertit un niveau de risque (enum) en Color JavaFX. */
    private Color riskToColor(models.types.Color riskColor) {
        switch (riskColor) {
            case RED:    return Color.valueOf("#e74c3c");
            case ORANGE: return Color.valueOf("#e67e22");
            default:     return Color.valueOf("#27ae60");
        }
    }
 
    /** Style uniforme pour tous les boutons d'action. */
    private void styleBtn(Button btn, String color) {
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-cursor: hand; -fx-padding: 8; -fx-background-radius: 4;"
        );
    }
}
 
