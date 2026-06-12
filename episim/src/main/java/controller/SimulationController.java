package controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import models.SimulationConfig;
import models.SimulationEngine;
import models.SimulationModel;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.entities.User;
import models.graph.NationalGraph;
import models.types.AccessState;
import models.types.Color;
import view.SimulationView;

public class SimulationController {

    private SimulationModel model;
    private SimulationEngine engine;
    private SimulationConfig config;
    private SimulationView view;
    private Timeline timeline;
    private boolean isRunning;
    private double speedFactor;

    public SimulationController(SimulationView view) {
        this.view        = view;
        this.isRunning   = false;
        this.speedFactor = 1.0;

        this.model  = new SimulationModel();
        this.engine = new SimulationEngine();
        this.config = model.getConfig();

        this.model.setNationalGraph(buildTestData());

        buildTimeline(speedFactor); // create the initial timeline
    }

    // ── Timeline ──────────────────────────────────────────────────────────────

    /**
     * Creates a brand-new Timeline at the given speed and stores it.
     * We NEVER mutate an existing Timeline's KeyFrames — JavaFX doesn't
     * support that reliably on a running timeline.  Instead we always
     * stop the old one, throw it away, and create a fresh one.
     *
     * @param factor steps per second (e.g. 3.0 = 3 steps/second)
     */
    private void buildTimeline(double factor) {
        // Guard: factor must be > 0 to avoid Division-by-zero / infinite delay
        double safeFactor = Math.max(0.1, factor);

        timeline = new Timeline(
            new KeyFrame(Duration.seconds(1.0 / safeFactor), e -> stepForward())
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    public void loginAsAdmin() {
        model.setCurrentUser(new User("admin", User.Role.ADMIN));
        view.showMessage("Connecté en tant qu'Admin");
        view.updateUserLabel("Admin ✓");
        view.update();
    }

    public void loginAsLambda() {
        model.setCurrentUser(new User("guest", User.Role.LAMBDA));
        view.showMessage("Connecté en tant qu'Utilisateur");
        view.updateUserLabel("Utilisateur");
        view.update();
    }

    public boolean isAdmin() {
        return model.getCurrentUser().isAdmin();
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    public void togglePlayPause() {
        isRunning = !isRunning;
        model.setRunning(isRunning);
        if (isRunning) {
            timeline.play();
            view.setPlayPauseButton("⏸ Pause");
            view.showMessage("Simulation démarrée");
        } else {
            timeline.pause();
            view.setPlayPauseButton("▶ Play");
            view.showMessage("Simulation en pause");
        }
    }

    public void stepForward() {
        model.setCurrentStep(model.getCurrentStep() + 1);

        for (Region region : model.getNationalGraph().getRegions().values()) {
            engine.computeLocalSEIR(region, config);
            engine.computeInterCityFlux(region, config);
            region.totalInfectedGraph();
        }

        engine.checkAndApplyBarricades(model.getNationalGraph());
        view.update();
    }

    /**
     * Changes the simulation speed.
     *
     * FIX: instead of mutating the existing Timeline's KeyFrames (unreliable),
     * we stop it, discard it, and build a fresh one at the new speed.
     * If the simulation was running we restart it immediately.
     *
     * @param factor steps per second
     */
    public void changeSpeed(double factor) {
        this.speedFactor = factor;
        config.setSpeedFactor(factor);

        boolean wasRunning = isRunning;

        // 1. Stop the old timeline completely
        timeline.stop();

        // 2. Build a brand-new one at the requested speed
        buildTimeline(factor);

        // 3. Resume if it was playing before
        if (wasRunning) {
            timeline.play();
        }
    }

    // ── Admin actions ─────────────────────────────────────────────────────────

    public void setCityColor(String regionName, String cityName, Color color) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        City city = getCity(regionName, cityName);
        if (city != null) {
            city.setRiskColor(color);
            view.renderRegionalGrid(regionName);
            view.showMessage("Couleur de " + cityName + " modifiée");
        }
    }

    public void toggleRoute(String regionName, String cityA, String cityB) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        Region region = model.getNationalGraph().getRegions().get(regionName);
        if (region == null) return;

        for (Route route : region.getRegionalGraph().getRoutes()) {
            boolean match =
                (route.getCityA().getName().equals(cityA) && route.getCityB().getName().equals(cityB)) ||
                (route.getCityA().getName().equals(cityB) && route.getCityB().getName().equals(cityA));
            if (match) {
                if (route.getAccess() == AccessState.OPEN) {
                    route.setAccess(AccessState.BARRICATED);
                    view.showMessage("🚧 Route bloquée : " + cityA + " ↔ " + cityB);
                } else {
                    route.setAccess(AccessState.OPEN);
                    view.showMessage("✅ Route ouverte : " + cityA + " ↔ " + cityB);
                }
                view.renderRegionalGrid(regionName);
                return;
            }
        }
    }

    public void blockRoutesForCity(Region region, String cityName) {
        for (Route route : region.getRegionalGraph().getRoutesForCity(cityName)) {
            route.setAccess(AccessState.BARRICATED);
        }
    }

    public void generateRandomEvent() {
        String cityName = model.triggerRandomOutbreak();
        if (cityName != null) {
            view.showMessage("⚡ Nouveau foyer COVID détecté à " + cityName + " !");
        } else {
            view.showMessage("⚡ Aucune ville disponible pour un nouveau foyer.");
        }
    }

    public void injectVirus(String regionName, String cityName, int count) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        City city = getCity(regionName, cityName);
        if (city != null) {
            model.triggerManualOutbreak(city, count);
            view.showMessage("🦠 Injection : " + count + " cas à " + cityName);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public NationalGraph getNationalGraph() { return model.getNationalGraph(); }

    public City getCity(String regionName, String cityName) {
        Region region = model.getNationalGraph().getRegions().get(regionName);
        if (region == null) return null;
        return region.getRegionalGraph().getCities().get(cityName);
    }

    public SimulationModel getSimulationModel() { return this.model; }

    // ── Test data ─────────────────────────────────────────────────────────────

    private NationalGraph buildTestData() {
        NationalGraph graph = new NationalGraph();
        java.util.Random rand = new java.util.Random();

        String[] regionNames = {
            "Île-de-France", "Bretagne", "PACA", "Normandie",
            "Nouvelle-Aquitaine", "Occitanie", "Auvergne-Rhône-Alpes",
            "Grand Est", "Hauts-de-France", "Centre-Val de Loire",
            "Pays de la Loire", "Bourgogne-Franche-Comté", "Corse"
        };

        java.util.Map<String, String[]> regionalCities = new java.util.HashMap<>();
        regionalCities.put("Île-de-France",           new String[]{"Paris", "Versailles", "Évry", "Marne"});
        regionalCities.put("Bretagne",                new String[]{"Rennes", "Brest", "Lorient", "Vannes"});
        regionalCities.put("PACA",                    new String[]{"Marseille", "Nice", "Toulon", "Avignon"});
        regionalCities.put("Normandie",               new String[]{"Rouen", "Caen", "Le Havre", "Cherbourg"});
        regionalCities.put("Nouvelle-Aquitaine",      new String[]{"Bordeaux", "Limoges", "Poitiers", "Pau"});
        regionalCities.put("Occitanie",               new String[]{"Toulouse", "Montpellier", "Nîmes", "Perpignan"});
        regionalCities.put("Auvergne-Rhône-Alpes",   new String[]{"Lyon", "Saint-Étienne", "Grenoble", "Clermont"});
        regionalCities.put("Grand Est",               new String[]{"Strasbourg", "Reims", "Metz", "Nancy"});
        regionalCities.put("Hauts-de-France",         new String[]{"Lille", "Amiens", "Roubaix", "Tourcoing"});
        regionalCities.put("Centre-Val de Loire",     new String[]{"Orléans", "Tours", "Bourges", "Blois"});
        regionalCities.put("Pays de la Loire",        new String[]{"Nantes", "Angers", "Le Mans", "Saint-Nazaire"});
        regionalCities.put("Bourgogne-Franche-Comté", new String[]{"Dijon", "Besançon", "Belfort", "Chalon"});
        regionalCities.put("Corse",                   new String[]{"Ajaccio", "Bastia", "Calvi", "Corte"});

        for (String rName : regionNames) {
            Region region = new Region(rName);
            String[] cities = regionalCities.get(rName);
            City[] cityObjects = new City[cities.length];

            for (int i = 0; i < cities.length; i++) {
                int safe      = 300 + rand.nextInt(500);
                int exposed   = 5   + rand.nextInt(45);
                int infected  = 10  + rand.nextInt(190);
                int recovered = 10  + rand.nextInt(90);
                cityObjects[i] = new City(cities[i], safe, exposed, infected, recovered, Color.GREEN);
                cityObjects[i].updateColor();
                region.getRegionalGraph().addCity(cityObjects[i]);
            }

            for (int i = 0; i < cityObjects.length; i++) {
                City current = cityObjects[i];
                City next    = cityObjects[(i + 1) % cityObjects.length];
                double weight = 1.0 + rand.nextDouble();
                region.getRegionalGraph().addBiRoute(current, next, weight);
            }

            region.totalInfectedGraph();
            graph.addRegion(region);
        }
        return graph;
    }
}

