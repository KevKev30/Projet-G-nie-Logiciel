package view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * NavBarView — barre de navigation en haut de la fenêtre.
 *
 * Responsabilité unique : construire la HBox du haut avec :
 *   - le logo "EpiSim" et le badge "COVID-19"
 *   - les boutons de navigation (Carte, Simulation, Données)
 *   - le label utilisateur connecté
 *   - le bouton de connexion Expert
 *
 * Elle ne contient aucune logique métier : elle délègue les actions
 * aux lambdas (Runnable) passés en paramètre depuis SimulationView.
 */
public class NavBarView {

    // Label mis à jour quand l'utilisateur se connecte (ex. "👤 Admin ✓")
    private final Label userLabel;

    public NavBarView() {
        this.userLabel = new Label("👤 Visiteur");
        this.userLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
    }

    /**
     * Construit et retourne la HBox complète de la navbar.
     *
     * @param onCarteClic  action déclenchée par le bouton "Carte"
     * @param onLoginClic  action déclenchée par le bouton "Connexion Expert"
     */
    public HBox build(Runnable onCarteClic, Runnable onLoginClic) {

        // ── Logo ─────────────────────────────────────────────────────────────
        Label logo = new Label("🦠 EpiSim");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // ── Badge COVID-19 (toujours visible) ────────────────────────────────
        Label covidBadge = new Label("COVID-19");
        covidBadge.setStyle(
            "-fx-background-color: #c0392b;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 3 7 3 7;" +
            "-fx-background-radius: 10;"
        );

        // ── Bouton Carte (actif = souligné en blanc) ─────────────────────────
        Button carteBtn = buildNavBtn("Carte", true);
        carteBtn.setOnAction(e -> onCarteClic.run());

        // ── Spacer pour pousser user/login à droite ───────────────────────────
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Bouton de connexion ───────────────────────────────────────────────
        Button loginBtn = new Button("🔑 Connexion Expert");
        loginBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: white;" +
            "-fx-border-color: white;" +
            "-fx-border-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 12 5 12;"
        );
        loginBtn.setOnAction(e -> onLoginClic.run());

        // ── Assemblage ────────────────────────────────────────────────────────
        HBox nav = new HBox(12,
            logo, covidBadge,
            carteBtn,
            buildNavBtn("Simulation", false),
            buildNavBtn("Données", false),
            spacer, userLabel, loginBtn
        );
        nav.setPadding(new Insets(12, 20, 12, 20));
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle("-fx-background-color: #1a252f;");
        return nav;
    }

    /**
     * Met à jour le label utilisateur affiché dans la navbar.
     * Appelé par SimulationView.updateUserLabel().
     *
     * @param text texte à afficher (ex. "Admin ✓")
     */
    public void setUserLabel(String text) {
        userLabel.setText("👤 " + text);
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    /**
     * Crée un bouton de navigation stylisé.
     * Si active=true, un soulignement blanc indique la page courante.
     */
    private Button buildNavBtn(String text, boolean active) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + (active ? "white" : "#aaa") + ";" +
            "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 5 10 5 10;" +
            (active
                ? "-fx-border-color: transparent transparent white transparent;" +
                  "-fx-border-width: 0 0 2 0;"
                : "")
        );
        return btn;
    }
}