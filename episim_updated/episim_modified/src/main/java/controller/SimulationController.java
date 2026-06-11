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
        this.view = view;  
        this.isRunning = false;  
        this.speedFactor = 1.0;  
        
        this.model = new SimulationModel();
        this.engine = new SimulationEngine();
        this.config = model.getConfig();

        this.model.setNationalGraph(buildTestData());  
        
        setupTimeline();  
    }

    private void setupTimeline() {  
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0 / speedFactor), e -> {  
            stepForward();  
        }));  
        timeline.setCycleCount(Timeline.INDEFINITE);  
    }

    // ===================== AUTHENTIFICATION =====================

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

    // ===================== SIMULATION =====================

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

    public void changeSpeed(double factor) {  
        this.speedFactor = factor;  
        config.setSpeedFactor(factor);
        boolean wasRunning = isRunning;  
        if (wasRunning) timeline.pause();  
        timeline.getKeyFrames().clear();  
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1.0 / factor), e -> stepForward()));  
        if (wasRunning) timeline.play();  
    } 
    
   
    // ===================== ACTIONS EXPERT / ADMIN =====================

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
        model.triggerRandomOutbreak();  
        view.showMessage("⚡ Événement aléatoire : Foyer épidémique détecté !");  
    }

    /**
     * Resets the simulation to its initial state (day 0).
     * Stops the timeline if running, rebuilds the test data,
     * and resets the step counter.
     */
    public void resetSimulation() {
        if (isRunning) {
            isRunning = false;
            timeline.pause();
            view.setPlayPauseButton("▶  Lancer");
        }
        model.setNationalGraph(buildTestData());
        model.setCurrentStep(0);
        model.setRunning(false);
        view.update();
    }

    public void injectVirus(String regionName, String cityName, int count) {
    if (!isAdmin()) {
        view.showMessage("Action réservée à l'Admin !");
        return;
    }
    
    City city = getCity(regionName, cityName);
        if (city != null) {
            model.triggerManualOutbreak(city, count);
            view.showMessage("🦠 Injection manuelle : " + count + " nouveaux cas à " + cityName);
        }
    }

    // ===================== GETTERS =====================

    public NationalGraph getNationalGraph() { 
        return model.getNationalGraph(); 
    }  

    public City getCity(String regionName, String cityName) {  
        Region region = model.getNationalGraph().getRegions().get(regionName);  
        if (region == null) return null;  
        return region.getRegionalGraph().getCities().get(cityName);  
    }

    public SimulationModel getSimulationModel() {
        return this.model;
    }

    // ===================== TEST =====================

    private NationalGraph buildTestData() {  
        NationalGraph graph = new NationalGraph();  

        // Île-de-France  
        Region idf = new Region("Île-de-France");  
        City paris = new City("Paris", 300, 50, 200, 100, Color.RED);  
        City versailles = new City("Versailles", 400, 20,  80,  40, Color.ORANGE);  
        City evry = new City("Évry", 500, 10,  10,  20, Color.GREEN);  
        City marne = new City("Marne", 350, 15,  20,  10, Color.GREEN);  
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
        City rouen = new City("Rouen",  300, 20, 40, 20, Color.GREEN);  
        City caen  = new City("Caen",   250, 10, 15, 10, Color.GREEN);  
        normandie.getRegionalGraph().addCity(rouen);  
        normandie.getRegionalGraph().addCity(caen);  
        normandie.getRegionalGraph().addBiRoute(rouen, caen, 1.5);  
        graph.addRegion(normandie);  

        return graph;  
    }  
}