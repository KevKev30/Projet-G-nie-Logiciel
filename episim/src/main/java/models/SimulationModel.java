package models;

import java.util.ArrayList;
import java.util.List;

import interfaces.Observer;
import interfaces.Subject;
import models.entities.User;
import models.graph.NationalGraph;

public class SimulationModel implements Subject {
    private NationalGraph nationalGraph;
    private SimulationEngine engine;
    private SimulationConfig config;
    private DataPersistenceManager persistenceManager;
    private ScenarioManager scenarioManager;
    private User currentUser;
    private int currentStep;
    private boolean isRunning;
    
    private final List<Observer> observers;

    /**
     * SimulationModel constructor
     * Initialize a new Simulation Model
     */
    public SimulationModel() {
        this.nationalGraph = new NationalGraph();
        this.engine = new SimulationEngine();
        this.config = new SimulationConfig(0.35, 0.2, 0.14); // Valeurs par défaut (ex: COVID)
        this.persistenceManager = new DataPersistenceManager();
        this.scenarioManager = new ScenarioManager();
        this.currentUser = new User("guest", User.Role.LAMBDA);
        this.currentStep = 0;
        this.isRunning = false;
        this.observers = new ArrayList<>();
    }

    /**
     * Advances the entire simulation by one time step.
     * Computes SEIR changes and city movements for all regions, 
     * applies automatic quarantines, and updates the user interface.
     */
    public void nextStep() {
        this.currentStep++;
        
        for (models.entities.Region region : nationalGraph.getRegions().values()) {
            engine.computeLocalSEIR(region, config);
            engine.computeInterCityFlux(region, config);
        }
        
        engine.checkAndApplyBarricades(nationalGraph);
        
        notifyObservers();
    }

    /**
     * Resets the simulation state to step zero and stops the timer.
     * Notifies the user interface to refresh the display.
     */
    public void initializeSimulation() {
        this.currentStep = 0;
        this.isRunning = false;
        notifyObservers();
    }

    /**
     * Triggers a random disease outbreak in a random city 
     * and immediately updates the user interface.
     */
    public void triggerRandomOutbreak() {
        this.scenarioManager.generateRandomEvent(this.nationalGraph);
        notifyObservers();
    }

    /**
     * Manually infects a specific city with a given number of cases 
     * and refreshes the user interface.
     * @param city the City object to infect
     * @param count the number of safe people to convert into infected
     */
    public void triggerManualOutbreak(models.entities.City city, int count) {
        this.scenarioManager.triggerManualInfection(city, count);
        notifyObservers();
    }

    // ==================== PATTERN OBSERVER ====================


    /**
     * Registers a new observer (like the View) to receive update alerts.
     * * @param o the Observer object to attach
     */
    @Override
    public void attach(Observer o) {
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    /**
     * Removes a registered observer from the notification list.
     * * @param o the Observer object to detach
     */
    @Override
    public void detach(Observer o) {
        observers.remove(o);
    }

    /**
     * Loops through all registered observers and triggers their refresh method.
     */
    @Override
    public void notifyObservers() {
        for (Observer observer : observers) {
            observer.update();
        }
    }


    // ==================== GETTERS & SETTERS ====================
    public NationalGraph getNationalGraph() { 
        return nationalGraph; 
    }

    public void setNationalGraph(NationalGraph graph) { 
        this.nationalGraph = graph; 
    }

    public SimulationEngine getEngine() { 
        return engine; 
    }


    public SimulationConfig getConfig() { 
        return config; 
    }
    
    public int getCurrentStep() { 
        return currentStep; 
    }

    public void setCurrentStep(int currentStep) { 
        this.currentStep = currentStep; 
    }

    public boolean isRunning() { 
        return isRunning; 
    }

    public void setRunning(boolean running) { 
        this.isRunning = running; 
    }

    public User getCurrentUser() { 
        return currentUser; 
    }

    public void setCurrentUser(User user) { 
        this.currentUser = user; 
    }

    public ScenarioManager getScenarioManager() { 
        return scenarioManager; 
    }

    public DataPersistenceManager getPersistenceManager() { 
        return persistenceManager; 
    }
}