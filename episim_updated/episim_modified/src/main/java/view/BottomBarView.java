package view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * BottomBarView — barre de statistiques épidémiques en bas de la fenêtre.
 *
 * Responsabilité unique : afficher en temps réel 4 métriques :
 *   1. Progression (jour total · semaine · jour de la semaine)
 *   2. Cas actifs en France
 *   3. Taux national d'infection
 *   4. Nombre de zones surveillées
 *
 * Les Labels sont exposés via des setters pour que SimulationView
 * puisse les mettre à jour à chaque step de simulation.
 */
public class BottomBarView {

    // Labels mis à jour à chaque appel de refresh()
    private final Label statJour;          // "J14 · S2 · J7"
    private final Label statCasActifs;     // nombre brut d'infectés
    private final Label statTauxNational;  // pourcentage national
    private final Label statZones;         // nb de villes suivies
    private final Label messageLabel;      // message flottant (bas droite)

    public BottomBarView() {
        this.statJour         = new Label("Jour 0 — Sem. 1");
        this.statCasActifs    = new Label("0");
        this.statTauxNational = new Label("0.00%");
        this.statZones        = new Label("0");
        this.messageLabel     = new Label("Simulation préventive COVID-19 · Modèle SEIR");
    }

    /**
     * Construit et retourne la HBox complète de la barre du bas.
     * À appeler une seule fois depuis SimulationView.start().
     */
    public HBox build() {
        HBox bar = new HBox(0,
            buildStatCell(statJour,         "Progression",       "#9b59b6"),
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

    /**
     * Met à jour toutes les statistiques affichées dans la barre.
     * Appelé à chaque step depuis SimulationView.updateBottomStats().
     *
     * @param totalDays   nombre de jours écoulés depuis le début
     * @param week        numéro de la semaine courante
     * @param dayOfWeek   jour dans la semaine (1-7)
     * @param totalInf    nombre total d'infectés actifs en France
     * @param rate        taux d'infection national (en %)
     * @param zones       nombre de villes surveillées
     */
    public void refresh(int totalDays, int week, int dayOfWeek,
                        int totalInf, double rate, int zones) {
        statJour.setText("J" + totalDays + " · S" + week + " · J" + dayOfWeek);
        statCasActifs.setText("" + totalInf);
        statTauxNational.setText(String.format("%.2f%%", rate));
        statZones.setText("" + zones);
    }

    /**
     * Affiche un message temporaire dans la zone de texte libre (bas droite).
     * Ex : "⚡ Événement aléatoire détecté !"
     */
    public void showMessage(String msg) {
        messageLabel.setText(msg);
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    /**
     * Crée une cellule de statistique : grande valeur colorée au-dessus
     * d'un petit titre gris, séparée des autres par une bordure droite.
     */
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

    /**
     * Crée la cellule de message flottant (occupe tout l'espace restant).
     */
    private HBox buildMsgCell() {
        messageLabel.setStyle(
            "-fx-text-fill: #aaa; -fx-font-style: italic; -fx-font-size: 12px;"
        );
        HBox cell = new HBox(messageLabel);
        cell.setPadding(new Insets(10, 20, 10, 20));
        cell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }
}