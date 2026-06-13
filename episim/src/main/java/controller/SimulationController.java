// MVC Controller for the epidemic simulation : model stores the data, view displays it, controller handles user actions and simulation steps
package controller;

import exceptions.CityStateException;
import exceptions.InvalidParameterException;
import exceptions.SimulationSaveException;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Main controller for the epidemic simulation (MVC pattern).
 *
 * Responsibilities:
 *   - drives the real simulation via a JavaFX Timeline
 *   - drives the sandbox simulation via a separate Timeline
 *   - handles admin actions (route toggle, city color, virus injection)
 *   - manages sandbox state (deep copy, history, before/after snapshot)
 *   - saves the simulation to a fixed directory
 */
public class SimulationController {

    // ── Core objects ──────────────────────────────────────────────────────────
    private SimulationModel  model;
    private SimulationEngine engine;
    private SimulationConfig config;
    private SimulationView   view;

    // ── Real simulation timeline ───────────────────────────────────────────────
    private Timeline timeline;
    private boolean  isRunning;
    private double   speedFactor;

    // ── Sandbox state ─────────────────────────────────────────────────────────
    private NationalGraph        sandboxGraph;
    private int                  sandboxStep    = 0;
    private boolean              sandboxRunning = false;
    private Timeline             sandboxTimeline;
    private final List<String>   sandboxHistory = new ArrayList<>(); // saved events for the Simulator tab, most recent first
    private Map<String, Integer> sandboxSnapshot = null; // regionName → totalInfected at first step, for before/after report

    // =========================================================================
    // Constructor : when we call new SimulationController(view), we create a new model, engine, and config, and build the initial NationalGraph with test data
    // =========================================================================

    public SimulationController(SimulationView view) {
        this.view        = view;
        this.isRunning   = false;
        this.speedFactor = 1.0;

        this.model  = new SimulationModel();
        this.engine = new SimulationEngine();
        this.config = model.getConfig();

        this.model.setNationalGraph(buildTestData()); // builds the initial NationalGraph with 13 French regions, 4 cities each, intra-regional routes, and 16 inter-regional routes following real French geography and inject them into the model
        buildTimeline(speedFactor); // builds the timeline for the real simulation with the given speed factor but does not start it yet, we can start it later with togglePlayPause()
    }

    // =========================================================================
    // Real simulation timeline
    // =========================================================================

    /**
     * Creates a fresh Timeline at the given speed.
     * We never mutate an existing Timeline's KeyFrames — JavaFX doesn't
     * support that reliably on a running timeline, so we always stop and rebuild a new one with the valid interval.
     *
     * @param factor steps per second
     */
    private void buildTimeline(double factor) {
        double safe = Math.max(0.1, factor); // every 0.1s at least, to avoid divide-by-zero and too-fast updates
        timeline = new Timeline(
            new KeyFrame(Duration.seconds(1.0 / safe), e -> stepForward()) // e is the ActionEvent, we call stepForward() to advance the simulation by one step (equivalent to a button with setOnAction(e -> stepForward())
        );
        timeline.setCycleCount(Timeline.INDEFINITE); // repeat forever until paused
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    /** Logs in as admin and notifies the view. */
    public void loginAsAdmin() {
        model.setCurrentUser(new User("admin", User.Role.ADMIN)); // controller creates a new User object with username "admin" and role ADMIN, and sets it as the current user in the model
        view.showMessage("Connecté en tant qu'Admin");
        view.updateUserLabel("Admin ✓");
        view.update();
    }

    /** Logs in as a standard user and notifies the view. */
    public void loginAsLambda() {
        model.setCurrentUser(new User("guest", User.Role.LAMBDA));
        view.showMessage("Connecté en tant qu'Utilisateur");
        view.updateUserLabel("Utilisateur");
        view.update();
    }

    /** @return true if the current user has admin privileges */
    public boolean isAdmin() { // used by the view to enable/disable admin-only buttons, and by the controller to check before performing admin actions
        return model.getCurrentUser().isAdmin();
    }

    // =========================================================================
    // Real simulation control
    // =========================================================================

    /** Toggles the real simulation between play and pause. */
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

    /**
     * Advances the real simulation by one step.
     * Order: local SEIR → intra-regional flux → inter-regional flux → barricades.
     * automatically called by the Timeline, but can also be triggered manually by the user with a button.
     */
    public void stepForward() {
        model.setCurrentStep(model.getCurrentStep() + 1);
        for (Region region : model.getNationalGraph().getRegions().values()) {
            engine.computeLocalSEIR(region, config); // calculates the SEIR dynamics for each city in the region, updating their S, E, I, R counts based on the current state and the config parameters
            engine.computeInterCityFlux(region, config); // propagates the infection between cities in the same region based on the routes and their access state, updating the cities' S, E, I, R counts accordingly
            region.totalInfectedGraph(); // calculates the total number of infected people in the region and updates the risk color based on the infection rate, using the method defined in Region.java
        }
        engine.computeInterRegionalFlux(model.getNationalGraph(), config); // propagates the infection between regions based on the inter-regional routes and their access state, made after the local SEIR to not double-count the fluxes, updating the cities' S, E, I, R counts accordingly
        // checkAndApplyBarricades returns routes newly barricaded this step.
        // If any region triggered a quarantine, we show it in the UI.
        java.util.List<String> blocked = engine.checkAndApplyBarricades(model.getNationalGraph());
        if (!blocked.isEmpty()) {
            view.showMessage("🚨 Quarantaine : " + String.join(", ", blocked));
        }
        view.update(); // notify the view to refresh the map and the popup if it is open, reflecting the new state of the simulation after this step
    }

    /**
     * Changes the real simulation speed.
     * Stops the old timeline, builds a fresh one, resumes if it was playing.
     *
     * @param factor steps per second — must be in [0.1, 60]
     * @throws InvalidParameterException if factor is out of range
     */
    public void changeSpeed(double factor) {
        if (factor < 0.1 || factor > 60)
            throw new InvalidParameterException("speedFactor", String.valueOf(factor),
                "Speed factor must be between 0.1 and 60, got: " + factor);
        this.speedFactor = factor;
        config.setSpeedFactor(factor);
        boolean wasRunning = isRunning; // if the simulation was running, we stop it, rebuild the timeline with the new speed, and resume it
        timeline.stop();
        buildTimeline(factor);
        if (wasRunning) timeline.play(); // avoid a pause of the timeline if the user changed the speed while it was running
    }

    // =========================================================================
    // Admin actions : all verify that the current user is admin before performing the action, otherwise show a message and return
    // =========================================================================

    /** Sets manually the risk color of a city (admin only), we can admin test here. */
    public void setCityColor(String regionName, String cityName, Color color) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        City city = getCity(regionName, cityName);
        if (city != null) {
            city.setRiskColor(color);
            view.renderRegionalGrid(regionName);
            view.showMessage("Couleur de " + cityName + " modifiée");
        }
    }

    /** basculate a local route between OPEN and BARRICATED (admin only). */
    public void toggleRoute(String regionName, String cityA, String cityB) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        Region region = model.getNationalGraph().getRegions().get(regionName);
        if (region == null) return;
        for (Route route : region.getRegionalGraph().getRoutes()) {
            boolean match =
                (route.getCityA().getName().equals(cityA) && route.getCityB().getName().equals(cityB)) ||
                (route.getCityA().getName().equals(cityB) && route.getCityB().getName().equals(cityA));
            if (match) { // we compare the names of the cities in the route to find the correct route object, then we toggle its access state and notify the view to refresh the regional grid (A->B is the same as B->A)
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

    /** Barricades all routes connected to a given city. */
    public void blockRoutesForCity(Region region, String cityName) {
        for (Route route : region.getRegionalGraph().getRoutesForCity(cityName)) {
            route.setAccess(AccessState.BARRICATED);
        }
    }

    /** Triggers a random outbreak (foyer) in the real simulation. */
    public void generateRandomEvent() {
        String cityName = model.triggerRandomOutbreak(); // lets ScenarioManager generate a random event in the real simulation, returns the name of the city where the outbreak was triggered, or null if no city was available
        if (cityName != null)
            view.showMessage("⚡ Nouveau foyer détecté à " + cityName + " !");
        else
            view.showMessage("⚡ Aucune ville disponible pour un nouveau foyer.");
    }

    /** Injects virus cases into a real city (admin only). */
    public void injectVirus(String regionName, String cityName, int count) {
        if (!isAdmin()) { view.showMessage("Action réservée à l'Admin !"); return; }
        City city = getCity(regionName, cityName);
        if (city != null) {
            model.triggerManualOutbreak(city, count);
            view.showMessage("🦠 Injection : " + count + " cas à " + cityName);
        }
    }

    // =========================================================================
    // Save
    // =========================================================================

    /**
     * Saves the simulation to the saves/ directory (auto-created if needed).
     * Filename: episim_YYYY-MM-DD_HH-mm-ss.json — unique per second, no overwrite.
     *
     * @throws SimulationSaveException if the directory or file cannot be written
     */
    public void saveSimulation() throws SimulationSaveException {
        Path saveDir = Paths.get("saves"); // create the saves/ directory if it doesn't exist, using java.nio.file.Files.createDirectories() which creates all non-existent parent directories as well
        try {
            Files.createDirectories(saveDir); // if the directory already exists, this does nothing and does not throw an exception
        } catch (java.io.IOException e) {
            throw new SimulationSaveException(saveDir.toString(),
                "Cannot create save directory", e);
        }
        String timestamp = LocalDateTime.now() // LocalDateTime.now() returns the current date and time, then we format it as a string using DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss") to create a unique filename for the save file
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path filePath = saveDir.resolve("episim_" + timestamp + ".json");
        try {
            model.getPersistenceManager().save(model, filePath.toString());
        } catch (Exception e) {
            throw new SimulationSaveException(filePath.toString(),
                "Failed to write simulation file", e);
        }
    }

    /**
     * Lists all save files available in the saves/ directory.
     * Returns file names only (not full paths), sorted most recent first.
     * The view uses this list to let the user pick which save to load.
     *
     * @return list of .json filenames, empty if the directory doesn't exist yet
     */
    public java.util.List<String> listSaveFiles() { // returns a list of all .json files in the saves/ directory, sorted by last modified time descending (most recent first), using java.nio.file.Files.list() to get a stream of Path objects, filtering for .json files, sorting by last modified time, and collecting the file names into a List<String>
        Path saveDir = Paths.get("saves");
        if (!Files.exists(saveDir)) return new ArrayList<>();
        try {
            return Files.list(saveDir)
                .filter(p -> p.toString().endsWith(".json"))
                // Sort by last-modified descending → most recent first
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b)
                                    .compareTo(Files.getLastModifiedTime(a));
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                })
                .map(p -> p.getFileName().toString())
                .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Loads a simulation from the saves/ directory by filename.
     * The loaded model replaces the current real simulation.
     * The sandbox is reset so it starts from the newly loaded state.
     *
     * @param filename the .json filename inside saves/ (not the full path)
     * @throws SimulationSaveException if the file cannot be read or parsed
     */
    public void loadSimulation(String filename) throws SimulationSaveException { // loads a simulation from the saves/ directory by filename, replacing the current model with the loaded one, and resetting the sandbox to start from the newly loaded state. If the file does not exist or cannot be parsed, it throws a SimulationSaveException with an appropriate message.
        Path filePath = Paths.get("saves", filename);
        if (!Files.exists(filePath))
            throw new SimulationSaveException(filePath.toString(),
                "Save file not found: " + filename);
        try {
            SimulationModel loaded = model.getPersistenceManager()
                                         .load(filePath.toString());
            if (loaded == null)
                throw new SimulationSaveException(filePath.toString(),
                    "File could not be parsed: " + filename);
            this.model = loaded;
            this.config = model.getConfig();
            // Reset sandbox so it re-copies the newly loaded graph
            this.sandboxGraph    = null;
            this.sandboxStep     = 0;
            this.sandboxSnapshot = null;
            this.sandboxHistory.clear();
        } catch (SimulationSaveException e) {
            throw e;
        } catch (Exception e) {
            throw new SimulationSaveException(filePath.toString(),
                "Unexpected error while loading", e);
        }
    }

    // =========================================================================
    // Getters
    // =========================================================================

    /** @return the real NationalGraph */
    public NationalGraph getNationalGraph() { return model.getNationalGraph(); }

    /**
     * Returns a city by region + city name from the REAL graph.
     * @return the City, or null if not found
     */
    public City getCity(String regionName, String cityName) { // returns a City object from the real NationalGraph by looking up the Region by name, then looking up the City by name within that Region's RegionalGraph. If either the Region or City is not found, it returns null.
        Region region = model.getNationalGraph().getRegions().get(regionName);
        if (region == null) return null;
        return region.getRegionalGraph().getCities().get(cityName);
    }

    /** @return the simulation model (for timer, persistence, etc.) */
    public SimulationModel getSimulationModel() { return model; }

    // =========================================================================
    // Sandbox — independent simulation for the Simulator tab
    // =========================================================================

    /**
     * Returns the sandbox NationalGraph, creating it on first call.
     *
     * The sandbox is a deep copy of the real graph taken at the moment the
     * user first opens the Simulator tab.  sandboxStep is synced to the real
     * simulation's current day so the two timelines are comparable.
     */
    /**
     * Returns the sandbox graph, creating it on first call.
     *
     * FIX: we capture the "before" snapshot HERE at creation time,
     * not at the first step.  This means:
     *   - inject 5000 cases → "after" goes up, "before" stays at creation values
     *   - the delta is always meaningful regardless of when you generate the report
     */
    public NationalGraph getSandboxGraph() { // lazy creation - the sandbox is created only when the user first opens the Simulator tab, and it is a deep copy of the real NationalGraph at that moment. The sandboxStep is synced to the real simulation's current day so the two timelines are comparable. We also capture the "before" snapshot at creation time so that any injections or changes made before the first step are reflected in the "after" values.
        if (sandboxGraph == null) {
            sandboxGraph = model.getNationalGraph().deepCopy();
            sandboxStep  = model.getTotalDays(); // same logic as stepForward() for the real simulation, we sync the sandboxStep to the real simulation's current day so that the two timelines are comparable
            captureSnapshotIfNeeded();
        }
        return sandboxGraph;
    }

    /** @return number of steps run in the sandbox */
    public int getSandboxStep() { return sandboxStep; }

    /**
     * Advances the sandbox by one step using the same engine and config as
     * the real simulation.  Only the NationalGraph differs.
     */
    public void sandboxStep() {
        captureSnapshotIfNeeded();
        sandboxStep++;
        for (Region region : getSandboxGraph().getRegions().values()) {
            engine.computeLocalSEIR(region, config);
            engine.computeInterCityFlux(region, config);
            region.totalInfectedGraph();
        }
        engine.computeInterRegionalFlux(getSandboxGraph(), config);
        java.util.List<String> blocked = engine.checkAndApplyBarricades(getSandboxGraph());
        if (!blocked.isEmpty()) {
            view.showSandboxMessage("🚨 Quarantaine : " + String.join(", ", blocked));
        }
    }

    /**
     * Triggers a random outbreak in the sandbox only.
     * Records the event in the sandbox history.
     *
     * @return name of the city where the outbreak was triggered
     */
    public String sandboxRandomEvent() {
        String city = model.getScenarioManager().generateRandomEvent(getSandboxGraph());
        String name = (city != null) ? city : "inconnue";
        sandboxHistory.add(0, "Jour " + sandboxStep + " — ⚡ Foyer : " + name);
        return name;
    }

    /**
     * Manually injects cases into a sandbox city.
     * Moves people from safe → infected, updates colors, logs the event.
     *
     * @param regionName target region
     * @param cityName   target city
     * @param count      number of cases to inject — must be > 0
     * @throws InvalidParameterException if regionName, cityName, or count is invalid
     * @throws CityStateException        if the city has no safe population left
     */
    public void sandboxInject(String regionName, String cityName, int count) { // 3 validations: regionName and cityName must not be blank, count must be > 0, and the city must have safe population left to infect. If any validation fails, we throw an exception with a descriptive message. If all validations pass, we trigger the manual infection in the sandbox graph, update the city's color, recalculate the region's total infected, and log the event in the sandbox history.
        if (regionName == null || regionName.isBlank())
            throw new InvalidParameterException("regionName", regionName,
                "Region name must not be blank.");
        if (cityName == null || cityName.isBlank())
            throw new InvalidParameterException("cityName", cityName,
                "City name must not be blank.");
        if (count <= 0)
            throw new InvalidParameterException("count", String.valueOf(count),
                "Injection count must be > 0, got: " + count);

        Region region = getSandboxGraph().getRegions().get(regionName);
        if (region == null) return;
        City city = region.getRegionalGraph().getCities().get(cityName);
        if (city == null) return;

        if (city.getSafe() <= 0)
            throw new CityStateException(cityName, "No safe population left to infect.");

        model.getScenarioManager().triggerManualInfection(city, count);
        city.updateColor();
        region.totalInfectedGraph();
        sandboxHistory.add(0, "Jour " + sandboxStep + " — 💉 "
            + count + " cas injectés à " + cityName);
    }

    /**
     * Resets the sandbox: fresh deep copy of the current real graph,
     * step counter re-synced, history and snapshot cleared.
     */
    /**
     * Resets sandbox and immediately captures a fresh baseline snapshot
     * so the before/after comparison is always relative to the reset point.
     */
    public void resetSandbox() { 
        sandboxGraph    = model.getNationalGraph().deepCopy();
        sandboxStep     = model.getTotalDays();
        sandboxSnapshot = null;
        captureSnapshotIfNeeded(); // called at the creation and reset of the sandbox : saves in a map {regionName → totalInfected} the total infected count for each region at the moment of creation/reset, so that we can later compare the "before" and "after" counts for the report chart. This ensures that any injections or changes made before the first step are reflected in the "after" values.
        sandboxHistory.clear();
        sandboxHistory.add(0, "↺ Sandbox réinitialisée au jour " + sandboxStep);
    }

    /** @return unmodifiable sandbox event history, most recent first */
    public List<String> getSandboxHistory() {
        return Collections.unmodifiableList(sandboxHistory);
    }

    // ── Sandbox timeline ──────────────────────────────────────────────────────

    /**
     * Builds a fresh sandbox Timeline at the given speed.
     * Same pattern as buildTimeline() — never mutate, always recreate.
     */
    private void buildSandboxTimeline(double factor) {
        double safe = Math.max(0.1, factor);
        sandboxTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1.0 / safe), e -> {
                sandboxStep();
                view.refreshSandboxMap();
            })
        );
        sandboxTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * Toggles the sandbox timeline between play and pause.
     *
     * @return true if the sandbox is now running
     */
    public boolean toggleSandboxPlayPause() {
        sandboxRunning = !sandboxRunning;
        if (sandboxRunning) {
            if (sandboxTimeline == null) buildSandboxTimeline(1.0);
            sandboxTimeline.play();
        } else {
            if (sandboxTimeline != null) sandboxTimeline.stop();
        }
        return sandboxRunning;
    }

    /**
     * Changes the sandbox speed.
     *
     * @param factor steps per second — must be in [0.1, 60]
     * @throws InvalidParameterException if factor is out of range
     */
    public void sandboxChangeSpeed(double factor) {
        if (factor < 0.1 || factor > 60)
            throw new InvalidParameterException("speedFactor", String.valueOf(factor),
                "Speed factor must be between 0.1 and 60, got: " + factor);
        boolean wasRunning = sandboxRunning;
        if (sandboxTimeline != null) sandboxTimeline.stop();
        buildSandboxTimeline(factor);
        if (wasRunning) sandboxTimeline.play();
    }

    // ── Sandbox snapshot (before/after report) ────────────────────────────────

    /**
     * Takes the "before" snapshot on the first sandboxStep() call.
     * Records infected counts per region at that moment.
     */
    private void captureSnapshotIfNeeded() {
        if (sandboxSnapshot != null) return;
        sandboxSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Region> entry : getSandboxGraph().getRegions().entrySet()) {
            sandboxSnapshot.put(entry.getKey(), entry.getValue().getTotalInfected());
        }
    }

    /**
     * Returns before/after infected counts per region for the report chart.
     * "Before" = snapshot at first step. "After" = current state.
     *
     * @return map of regionName → [infectésBefore, infectésAfter], empty if no step run yet
     */
    /**
     * Returns before/after infected counts per region.
     *
     * FIX: forces totalInfectedGraph() on every region before reading
     * "after" values.  Without this, a manual injection that didn't trigger
     * a step would not be reflected in getTotalInfected() (which caches the
     * last computed value).
     */

    public Map<String, int[]> getSandboxBeforeAfter() { // forces totalInfectedGraph() on every region before reading "after" values, so that any manual injection that didn't trigger a step is reflected in getTotalInfected(). Returns a map of regionName → [infectésBefore, infectésAfter], empty if no step run yet. If the sandboxSnapshot is null (no step run yet), it returns an empty map.
        Map<String, int[]> result = new LinkedHashMap<>();
        if (sandboxSnapshot == null) return result;

        // Force recompute so injections without a step are included
        for (Region region : getSandboxGraph().getRegions().values()) {
            region.totalInfectedGraph();
        }

        for (Map.Entry<String, Region> entry : getSandboxGraph().getRegions().entrySet()) {
            String name  = entry.getKey();
            int before   = sandboxSnapshot.getOrDefault(name, 0);
            int after    = entry.getValue().getTotalInfected();
            result.put(name, new int[]{before, after});
        }
        return result;
    }

    // ── Inter-regional route helpers (used by the region popup) ───────────────

    /**
     * Returns inter-regional routes that have at least one endpoint in the given region.
     * Used by the view to populate the "Routes inter-régionales" section of the popup.
     *
     * @param regionName region to filter on
     * @param graph      the NationalGraph to query (real or sandbox)
     */
    public List<Route> getInterRegionalRoutesFor(String regionName, NationalGraph graph) {
        List<Route> result = new ArrayList<>();
        Region region = graph.getRegions().get(regionName);
        if (region == null) return result;
        Set<String> cityNames = region.getRegionalGraph().getCities().keySet();
        for (Route route : graph.getInterRegionalRoutes()) {
            if (cityNames.contains(route.getCityA().getName())
                    || cityNames.contains(route.getCityB().getName())) {
                result.add(route);
            }
        }
        return result;
    }

    /**
     * Returns the name of the region that contains the given city.
     * Used by the popup to label routes as "Paris (Île-de-France) ↔ Amiens (Hauts-de-France)".
     *
     * @param city  city to look up
     * @param graph the NationalGraph to search
     * @return region name, or "?" if not found
     */
    public String getRegionOf(City city, NationalGraph graph) {
        for (Map.Entry<String, Region> entry : graph.getRegions().entrySet()) {
            if (entry.getValue().getRegionalGraph().getCities().containsKey(city.getName()))
                return entry.getKey();
        }
        return "?";
    }

    // =========================================================================
    // Test data
    // =========================================================================

    /**
     * Builds the initial NationalGraph with 13 French regions, 4 cities each,
     * intra-regional routes, and 16 inter-regional routes following real French geography.
     *
     * Population scale: 20 000–120 000 per city so SEIR dynamics are visible.
     * With only hundreds, the (int) truncation in SEIR kills fractional transitions
     * and infection rates never reach the RED threshold.
     */
    private NationalGraph buildTestData() {
        NationalGraph graph = new NationalGraph();
        java.util.Random rand = new java.util.Random();

        String[] regionNames = {
            "Île-de-France", "Bretagne", "PACA", "Normandie",
            "Nouvelle-Aquitaine", "Occitanie", "Auvergne-Rhône-Alpes",
            "Grand Est", "Hauts-de-France", "Centre-Val de Loire",
            "Pays de la Loire", "Bourgogne-Franche-Comté", "Corse"
        };

        Map<String, String[]> regionalCities = new java.util.HashMap<>();
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
            // here to modify the initial values of SEIR
            for (int i = 0; i < cities.length; i++) {
                int safe      = 20_000 + rand.nextInt(100_000);
                int exposed   = 100    + rand.nextInt(900);
                int infected  = 500    + rand.nextInt(2_000);
                int recovered = 200    + rand.nextInt(800);
                cityObjects[i] = new City(cities[i], safe, exposed, infected, recovered, Color.GREEN);
                cityObjects[i].updateColor();
                region.getRegionalGraph().addCity(cityObjects[i]);
            }
            // local routes: each city connected to the next in a loop, weight = 1.0 + random(0.0, 1.0)
            for (int i = 0; i < cityObjects.length; i++) {
                City current = cityObjects[i];
                City next    = cityObjects[(i + 1) % cityObjects.length];
                region.getRegionalGraph().addBiRoute(current, next, 1.0 + rand.nextDouble());
            }
            region.totalInfectedGraph();
            graph.addRegion(region);
        }

        // Inter-regional routes: gateway cities following real French road network.
        // All routes start OPEN. Weight = relative traffic intensity.
        BiFunction<String, String, City> gc =
            (r, c) -> graph.getRegions().get(r).getRegionalGraph().getCities().get(c);

        graph.addInterRegionalRoute(gc.apply("Hauts-de-France",  "Amiens"),
                                    gc.apply("Île-de-France",    "Paris"),       2.0);
        graph.addInterRegionalRoute(gc.apply("Normandie",        "Rouen"),
                                    gc.apply("Île-de-France",    "Paris"),       1.5);
        graph.addInterRegionalRoute(gc.apply("Normandie",        "Caen"),
                                    gc.apply("Bretagne",         "Rennes"),      1.2);
        graph.addInterRegionalRoute(gc.apply("Bretagne",         "Rennes"),
                                    gc.apply("Pays de la Loire", "Nantes"),      1.8);
        graph.addInterRegionalRoute(gc.apply("Pays de la Loire",    "Angers"),
                                    gc.apply("Centre-Val de Loire", "Tours"),    1.3);
        graph.addInterRegionalRoute(gc.apply("Centre-Val de Loire", "Orléans"),
                                    gc.apply("Île-de-France",       "Paris"),    1.8);
        graph.addInterRegionalRoute(gc.apply("Île-de-France",       "Marne"),
                                    gc.apply("Grand Est",           "Reims"),    1.6);
        graph.addInterRegionalRoute(gc.apply("Grand Est",                "Strasbourg"),
                                    gc.apply("Bourgogne-Franche-Comté", "Belfort"), 1.1);
        graph.addInterRegionalRoute(gc.apply("Bourgogne-Franche-Comté", "Dijon"),
                                    gc.apply("Auvergne-Rhône-Alpes",    "Lyon"),    1.7);
        graph.addInterRegionalRoute(gc.apply("Auvergne-Rhône-Alpes", "Grenoble"),
                                    gc.apply("PACA",                 "Marseille"), 1.4);
        graph.addInterRegionalRoute(gc.apply("PACA",       "Avignon"),
                                    gc.apply("Occitanie",  "Nîmes"),              1.5);
        graph.addInterRegionalRoute(gc.apply("Occitanie",           "Toulouse"),
                                    gc.apply("Nouvelle-Aquitaine",  "Bordeaux"),  1.6);
        graph.addInterRegionalRoute(gc.apply("Nouvelle-Aquitaine",   "Poitiers"),
                                    gc.apply("Centre-Val de Loire",  "Tours"),    1.3);
        graph.addInterRegionalRoute(gc.apply("Nouvelle-Aquitaine",    "Limoges"),
                                    gc.apply("Auvergne-Rhône-Alpes", "Clermont"), 1.0);
        graph.addInterRegionalRoute(gc.apply("Centre-Val de Loire",    "Bourges"),
                                    gc.apply("Bourgogne-Franche-Comté", "Dijon"), 1.0);
        graph.addInterRegionalRoute(gc.apply("Auvergne-Rhône-Alpes", "Clermont"),
                                    gc.apply("Occitanie",            "Montpellier"), 1.2);
        // Corse: island — no inter-regional routes.
        // here to add inter-regional routes
        return graph;
    }
}