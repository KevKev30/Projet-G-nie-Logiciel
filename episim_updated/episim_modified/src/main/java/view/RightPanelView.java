package view;

import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import models.entities.City;
import models.entities.Region;

/**
 * RightPanelView — panneau latéral droit de l'application.
 *
 * Découpé en 4 sections empilées verticalement :
 *   1. Info COVID-19        : rappel des paramètres SEIR du modèle
 *   2. Légende              : codes couleur Faible / Modéré / Élevé
 *   3. Région sélectionnée  : mise à jour dynamique au clic sur la carte
 *   4. Contrôles            : Play/Pause, Step, Reset, Événement, Vitesse (timer)
 *
 * Le panneau ne connaît pas le modèle directement : il reçoit le
 * contrôleur pour déclencher des actions, et expose des méthodes
 * de mise à jour appelées par SimulationView (pattern MVC).
 */
public class RightPanelView {

    // ── Références gardées pour mise à jour dynamique ────────────────────────
    private final VBox panel;               // conteneur racine retourné à SimulationView
    private final Button playPauseBtn;      // mis à jour par setPlayPauseButton()
    private final SimulationController ctrl; // référence pour les actions boutons

    public RightPanelView(SimulationController controller) {
        this.ctrl = controller;
        this.panel = new VBox(0);
        this.playPauseBtn = new Button("▶  Lancer");

        panel.setPrefWidth(270);
        panel.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #dee2e6;" +
            "-fx-border-width: 0 0 0 1;"
        );

        // Assemblage des 4 sections
        panel.getChildren().addAll(
            buildCovidSection(),
            new Separator(),
            buildLegendSection(),
            new Separator(),
            buildRegionSection(),   // id="regionSection" → lookup() depuis updateRegion()
            new Separator(),
            buildControlsSection()
        );
    }

    /** Retourne le VBox racine à placer dans BorderPane.right. */
    public VBox getPanel() {
        return panel;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  SECTION 1 — Rappel des paramètres COVID
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Affiche les paramètres épidémiologiques du modèle SEIR utilisé.
     * Purement informatif, aucun élément interactif.
     */
    private VBox buildCovidSection() {
        VBox section = buildSection("Simulation — COVID-19", "#c0392b");

        Label info = new Label(
            "Modèle SEIR · β=0.30 · σ=0.14 · γ=0.07\n" +
            "Létalité : 2 % · Asymptomatiques : 40 %\n" +
            "Simulation à vocation préventive"
        );
        info.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-wrap-text: true;");
        section.getChildren().add(info);
        return section;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  SECTION 2 — Légende couleurs
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Légende des 3 niveaux de risque (vert / orange / rouge).
     * Les seuils correspondent au taux d'infection d'une ville.
     */
    private VBox buildLegendSection() {
        VBox section = buildSection("Niveau de risque", "#2c3e50");
        section.getChildren().addAll(
            buildRiskItem("🔴", "Élevé   (> 60%)",  "#e74c3c"),
            buildRiskItem("🟠", "Modéré (30-60%)", "#e67e22"),
            buildRiskItem("🟢", "Faible  (< 30%)",  "#27ae60")
        );
        return section;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  SECTION 3 — Région sélectionnée (mise à jour dynamique)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Construit la section "région" avec l'état par défaut (aucune sélection).
     * L'id "regionSection" permet de la retrouver via panel.lookup("#regionSection").
     */
    private VBox buildRegionSection() {
        VBox section = buildSection("Sélectionnez une région", "#2c3e50");
        section.setId("regionSection");

        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        section.getChildren().add(hint);
        return section;
    }

    /**
     * Réinitialise la section région à son état "aucune sélection".
     * Appelé depuis SimulationView quand on revient à la carte nationale.
     */
    public void showDefaultRegion() {
        VBox section = (VBox) panel.lookup("#regionSection");
        if (section == null) return;
        section.getChildren().clear();

        Label title = new Label("Sélectionnez une région");
        title.setStyle(
            "-fx-text-fill: #2c3e50; -fx-font-size: 13px; -fx-font-weight: bold;"
        );
        Label hint = new Label("Cliquez sur une région\npour voir ses détails");
        hint.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        section.getChildren().addAll(title, hint);
    }

    /**
     * Remplace le contenu de la section région par les détails d'une région cliquée.
     * Affiche : nom, badge risque, stats (cas actifs, taux, population),
     * et 3 boutons d'actions rapides.
     *
     * @param region        la Region sélectionnée sur la carte
     * @param onSimuClick   action du bouton "Voir simulation" (→ vue régionale)
     */
    public void showRegionDetails(Region region, Runnable onSimuClick) {
        VBox section = (VBox) panel.lookup("#regionSection");
        if (section == null) return;
        section.getChildren().clear();

        // Couleur dépend du niveau de risque de la région
        String colorHex = riskToHex(region.getRiskColor());

        Label title = new Label(region.getName());
        title.setStyle(
            "-fx-text-fill: " + colorHex + ";" +
            "-fx-font-size: 14px; -fx-font-weight: bold;"
        );

        // Badge textuel (Risque élevé / modéré / faible)
        String riskText = region.getRiskColor() == models.types.Color.RED   ? "Risque élevé"
                        : region.getRiskColor() == models.types.Color.ORANGE ? "Risque modéré"
                        : "Faible risque";
        Label badge = new Label(riskText);
        badge.setStyle(
            "-fx-background-color: " + colorHex + ";" +
            "-fx-text-fill: white; -fx-padding: 3 8 3 8;" +
            "-fx-background-radius: 10; -fx-font-size: 11px;"
        );

        // Calcul des stats de la région (somme sur toutes ses villes)
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

        // ── Actions rapides ───────────────────────────────────────────────────
        Label actTitle = new Label("Actions rapides");
        actTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Button simuBtn = new Button("▶  Voir simulation");
        simuBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(simuBtn, "#2980b9");
        simuBtn.setOnAction(e -> onSimuClick.run());

        Button exportBtn = new Button("↓  Exporter données");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(exportBtn, "#27ae60");
        // TODO : implémenter l'export CSV/JSON via DataPersistenceManager
        exportBtn.setOnAction(e ->
            ctrl.getSimulationModel().showMessage("📥 Export : " + region.getName())
        );

        Button alertBtn = new Button("🔔  Activer alertes");
        alertBtn.setMaxWidth(Double.MAX_VALUE);
        styleBtn(alertBtn, "#e67e22");
        // TODO : implémenter les alertes (seuil configurable)

        section.getChildren().addAll(actTitle, simuBtn, exportBtn, alertBtn);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  SECTION 4 — Contrôles de simulation + timer modulable
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Construit la section "Contrôles" avec :
     *   - Play/Pause
     *   - Étape suivante (+1 jour)
     *   - Événement aléatoire
     *   - Revenir au départ (reset)
     *   - Slider de vitesse (timer modulable : 0.2 → 5 jours/seconde)
     */
    private VBox buildControlsSection() {
        VBox section = buildSection("Contrôles", "#2c3e50");

        // ── Play / Pause ──────────────────────────────────────────────────────
        styleBtn(playPauseBtn, "#27ae60");
        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setOnAction(e -> ctrl.togglePlayPause());

        // ── Étape manuelle (+1 jour) ──────────────────────────────────────────
        Button stepBtn = new Button("⏭  Étape suivante (+1 jour)");
        styleBtn(stepBtn, "#8e44ad");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> ctrl.stepForward());

        // ── Événement aléatoire (foyer épidémique) ────────────────────────────
        Button randomBtn = new Button("⚡  Événement aléatoire");
        styleBtn(randomBtn, "#e67e22");
        randomBtn.setMaxWidth(Double.MAX_VALUE);
        randomBtn.setOnAction(e -> ctrl.generateRandomEvent());

        // ── Reset → Jour 0 ────────────────────────────────────────────────────
        Button resetBtn = new Button("⏮  Revenir au départ");
        styleBtn(resetBtn, "#7f8c8d");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setOnAction(e -> {
            ctrl.resetSimulation();
            ctrl.getSimulationModel().showMessage("↩ Simulation réinitialisée au Jour 0");
        });

        // ── Timer modulable : slider de vitesse ───────────────────────────────
        // Chaque "step" correspond à 1 jour simulé.
        // Le slider contrôle combien de jours avancent par seconde réelle.
        Separator sep = new Separator();

        Label timerTitle = new Label("⏱  Vitesse (jours / seconde)");
        timerTitle.setStyle(
            "-fx-text-fill: #555; -fx-font-size: 11px; -fx-font-weight: bold;"
        );

        Slider speedSlider = new Slider(0.2, 5.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.setMinorTickCount(1);
        speedSlider.setSnapToTicks(false);
        speedSlider.setMaxWidth(Double.MAX_VALUE);

        // Label live affichant la valeur courante du slider
        Label speedValueLabel = new Label("1.0 j/s");
        speedValueLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

        // Listener : met à jour l'affichage ET informe le contrôleur
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double v = newVal.doubleValue();
            speedValueLabel.setText(String.format("%.1f j/s", v));
            ctrl.changeSpeed(v);   // recrée la KeyFrame de la Timeline
        });

        section.getChildren().addAll(
            playPauseBtn, stepBtn, randomBtn, resetBtn,
            sep, timerTitle, speedSlider, speedValueLabel
        );
        return section;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  API publique — appelée depuis SimulationView
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Change le texte et la couleur du bouton Play/Pause.
     * Vert quand la simulation est arrêtée, rouge quand elle tourne.
     *
     * @param text le nouveau texte du bouton (ex. "⏸ Pause" ou "▶ Play")
     */
    public void setPlayPauseButton(String text) {
        playPauseBtn.setText(text);
        boolean isPlaying = !text.contains("Lancer") && !text.contains("Play");
        styleBtn(playPauseBtn, isPlaying ? "#e74c3c" : "#27ae60");
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helpers privés de construction UI
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Crée un conteneur de section avec un titre coloré et un padding uniforme.
     * Toutes les sections du panneau droit utilisent ce même gabarit.
     */
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

    /**
     * Crée une ligne "emoji + texte coloré" pour la légende des risques.
     */
    private HBox buildRiskItem(String emoji, String text, String color) {
        Label dot = new Label(emoji);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        HBox item = new HBox(8, dot, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    /**
     * Crée une ligne "clé : valeur" pour les stats d'une région.
     */
    private Label buildInfoLine(String key, String val, String color) {
        Label lbl = new Label(key + " : " + val);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        return lbl;
    }

    /**
     * Applique le style standard des boutons d'action (fond coloré, texte blanc).
     */
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

    /**
     * Convertit un niveau de risque (enum Color) en code hexadécimal CSS.
     * Utilisé pour colorier dynamiquement les badges et textes.
     */
    private String riskToHex(models.types.Color riskColor) {
        switch (riskColor) {
            case RED:    return "#e74c3c";
            case ORANGE: return "#e67e22";
            default:     return "#27ae60";
        }
    }
}