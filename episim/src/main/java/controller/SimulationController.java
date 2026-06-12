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

/*package controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.entities.User;
import models.graph.NationalGraph;
import models.math.SEIRModel;
import models.types.AccessState;
import models.types.Color;
import view.SimulationView;

public class SimulationController{
    private NationalGraph nationalGraph;
    private SimulationView view;
    private User currentUser;
    private Timeline timeline;
    private boolean isRunning;
    private double speedFactor;
    private SEIRModel seirModel;
 
    public SimulationController(SimulationView view) {
        this.view = view;
        this.isRunning = false;
        this.speedFactor = 1.0;
        this.nationalGraph = buildTestData();
        this.currentUser = new User("guest", User.Role.LAMBDA);
        this.seirModel = SEIRModel.covid(); // préréglage par défaut
        setupTimeline();
    }

    // ===================== TIMELINE =====================

    private void setupTimeline() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0 / speedFactor), e -> {
            stepForward();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    // ===================== AUTHENTIFICATION =====================

    public void loginAsAdmin() {
        this.currentUser = new User("admin", User.Role.ADMIN);
        view.showMessage("Connecté en tant qu'Admin");
        view.getNavBarView().updateUserLabel("Admin ✓");
        view.update();
    }

    public void loginAsLambda() {
        this.currentUser = new User("guest", User.Role.LAMBDA);
        view.showMessage("Connecté en tant qu'Utilisateur");
        view.updateUserLabel("Utilisateur");
        view.update();
    }

    public boolean isAdmin() {
        return currentUser.isAdmin();
    }

    // ===================== SIMULATION =====================

    public void togglePlayPause() {
        isRunning = !isRunning;
        if (isRunning) {
            timeline.play();
            view.getRightPanelView().updatePlayPauseButton("⏸ Pause");
            view.showMessage("Simulation démarrée — " + seirModel);
        } else {
            timeline.pause();
            view.getRightPanelView().updatePlayPauseButton("▶ Play");
            view.showMessage("Simulation en pause");
        }
    }

    public void stepForward() {
      seirModel.step(nationalGraph, view, this);
    }

    public void changeSpeed(double factor) {
        this.speedFactor = factor;
        boolean wasRunning = isRunning;
        if (wasRunning) timeline.pause();
        timeline.getKeyFrames().clear();
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1.0 / factor), e -> stepForward()));
        if (wasRunning) timeline.play();
    }

    // ===================== MODÈLE SEIR =====================
 
    public void setSeirPreset(String preset) {
        if (!isAdmin()) {
            view.showMessage("Action réservée à l'Admin !");
            return;
        }
        switch (preset.toLowerCase()) {
            case "grippe"   -> seirModel = SEIRModel.flu();
            case "covid"    -> seirModel = SEIRModel.covid();
            case "rougeole" -> seirModel = SEIRModel.measles();
            case "ebola"    -> seirModel = SEIRModel.ebola();
            default -> {
                view.showMessage("Préréglage inconnu : " + preset);
                return;
            }
        }
        view.showMessage("🦠 Modèle changé : " + seirModel);
        view.update();
    }
 
    /**
     * Permet à l'admin de définir des paramètres SEIR personnalisés.
     *
     * @param beta  taux de transmission
     * @param sigma taux d'incubation (1 / durée_incubation en jours)
     * @param gamma taux de guérison  (1 / durée_infection en jours)
     * @param mu    taux de mortalité (0 pour désactiver)
     */
    /*public void setSeirParameters(double beta, double sigma, double gamma, double mu) {
        if (!isAdmin()) {
            view.showMessage("Action réservée à l'Admin !");
            return;
        }
        seirModel.setBeta(beta);
        seirModel.setSigma(sigma);
        seirModel.setGamma(gamma);
        seirModel.setMu(mu);
        view.showMessage("⚙ Paramètres SEIR mis à jour — " + seirModel);
        view.update();
    }
    
    public SEIRModel getSeirModel() {
        return seirModel;
    }
 
    // ===================== ACTIONS ADMIN =====================

    public void setCityColor(String regionName, String cityName, Color color) {
        if (!isAdmin()) {
            view.showMessage("Action réservée à l'Admin !");
            return;
        }
        City city = getCity(regionName, cityName);
        if (city != null) {
            city.setRiskColor(color);
            view.renderRegionalGrid(regionName);
            view.showMessage("Couleur de " + cityName + " modifiée");
        }
    }

    public void toggleRoute(String regionName, String cityA, String cityB) {
        if (!isAdmin()) {
            view.showMessage("Action réservée à l'Admin !");
            return;
        }
        Region region = nationalGraph.getRegions().get(regionName);
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
        java.util.List<Region> regions = new java.util.ArrayList<>(nationalGraph.getRegions().values());
        if (regions.isEmpty()) return;
        Region randomRegion = regions.get((int)(Math.random() * regions.size()));
        java.util.List<City> cities = new java.util.ArrayList<>(randomRegion.getRegionalGraph().getCities().values());
        if (cities.isEmpty()) return;
        City randomCity = cities.get((int)(Math.random() * cities.size()));

        int outbreak = (int)(randomCity.getSafe() * 0.25);
        if (outbreak <= 0) outbreak = 1;
        if (outbreak > randomCity.getSafe()) outbreak = randomCity.getSafe();

        randomCity.setSafe(randomCity.getSafe() - outbreak);
        randomCity.setInfected(randomCity.getInfected() + outbreak);
        randomCity.updateColor();
        randomRegion.totalInfectedGraph();

        view.showMessage("⚡ Événement aléatoire : foyer à " + randomCity.getName() + " (" + outbreak + " nouveaux infectés)");
        view.update();
    }

    // ===================== GETTERS =====================

    public NationalGraph getNationalGraph() { return nationalGraph; }

    public City getCity(String regionName, String cityName) {
        Region region = nationalGraph.getRegions().get(regionName);
        if (region == null) return null;
        return region.getRegionalGraph().getCities().get(cityName);
    }

    // ===================== DONNÉES DE TEST =====================

    private NationalGraph buildTestData() {
        NationalGraph graph = new NationalGraph();

        // Île-de-France
        Region idf = new Region("Île-de-France");
        City paris     = new City("Paris",      300, 50, 200, 100, Color.RED);
        City versailles= new City("Versailles", 400, 20,  80,  40, Color.ORANGE);
        City evry      = new City("Évry",       500, 10,  10,  20, Color.GREEN);
        City marne     = new City("Marne",      350, 15,  20,  10, Color.GREEN);
        idf.getRegionalGraph().addCity(paris);
        idf.getRegionalGraph().addCity(versailles);
        idf.getRegionalGraph().addCity(evry);
        idf.getRegionalGraph().addCity(marne);
        idf.getRegionalGraph().addBiRoute(paris, versailles, 1.0);
        idf.getRegionalGraph().addBiRoute(paris, marne, 1.2);
        idf.getRegionalGraph().addBiRoute(versailles, evry, 1.5);
        idf.getRegionalGraph().addBiRoute(marne, evry, 1.3);
        graph.addRegion(idf);

        // Bretagne
        Region bretagne = new Region("Bretagne");
        City rennes  = new City("Rennes",  400, 10, 20, 15, Color.GREEN);
        City brest   = new City("Brest",   250,  5, 10,  8, Color.GREEN);
        City lorient = new City("Lorient", 180,  3,  5,  2, Color.GREEN);
        bretagne.getRegionalGraph().addCity(rennes);
        bretagne.getRegionalGraph().addCity(brest);
        bretagne.getRegionalGraph().addCity(lorient);
        bretagne.getRegionalGraph().addBiRoute(rennes, brest, 2.0);
        bretagne.getRegionalGraph().addBiRoute(brest, lorient, 1.5);
        graph.addRegion(bretagne);

        // PACA
        Region paca = new Region("PACA");
        City marseille = new City("Marseille", 200, 80, 300, 120, Color.RED);
        City nice      = new City("Nice",      350, 40, 150,  60, Color.ORANGE);
        City toulon    = new City("Toulon",    280, 30,  80,  40, Color.ORANGE);
        paca.getRegionalGraph().addCity(marseille);
        paca.getRegionalGraph().addCity(nice);
        paca.getRegionalGraph().addCity(toulon);
        paca.getRegionalGraph().addBiRoute(marseille, toulon, 1.2);
        paca.getRegionalGraph().addBiRoute(toulon, nice, 1.8);
        graph.addRegion(paca);

        // Normandie
        Region normandie = new Region("Normandie");
        City rouen  = new City("Rouen",  300, 20, 40, 20, Color.GREEN);
        City caen   = new City("Caen",   250, 10, 15, 10, Color.GREEN);
        normandie.getRegionalGraph().addCity(rouen);
        normandie.getRegionalGraph().addCity(caen);
        normandie.getRegionalGraph().addBiRoute(rouen, caen, 1.5);
        graph.addRegion(normandie);

        return graph;
    }
}

*/