package models.entities;

import models.types.Color;

public class City{
    private String name;
    private int populationSafe;
    private int populationExposed;
    private int populationInfected;
    private int populationRecovered;
    private Color riskColor;

    /**
     * City constrctor
     * Initialize a new city
     * @param name the name of the city
     * @param populationSafe the number of safe people
     * @param populationExposed the number of people who got exposed to the virus 
     * @param populationInfected the number of people who are currently infected
     * @param populationRecovered the number of people who got recovered of the virus
     * @param riskColor the color of the city based on the virus spread rate
     */
    public City(String name, int popSafe, int popExposed, int popInfected, int popRecovered, Color risk){
        this.name = name;
        this.populationSafe = popSafe;
        this.populationExposed = popExposed;
        this.populationInfected = popInfected;
        this.populationRecovered = popRecovered;
        this.riskColor = risk;
    }

    public String getName(){
        return this.name;
    }

    public int getSafe(){
        return this.populationSafe;
    }

    public int getExposed(){
        return this.populationExposed;
    }

    public int getInfected(){
        return this.populationInfected;
    }

    public int getRecovered(){
        return this.populationRecovered;
    }

    public Color getRiskColor(){
        return this.riskColor;
    }

    /**
     * Calculates the total population of the city
     * @return the total number of people without considering their state
     */
    public int getTotalPopulation() {
        return this.populationSafe + this.populationExposed + this.populationInfected + this.populationRecovered;
    }

    /**
     * Calculates the proportion of infected people relative to the total population.
     * @return a percentage value between 0.0 and 1.0 representing the infection rate
     */
    public double getInfectionRate() {
        int total = getTotalPopulation();
        if (total == 0) return 0.0;
        return (double) this.populationInfected / total;
    }

    public void setSafe(int count) { 
        this.populationSafe = count; 
    }

    public void setExposed(int count) { 
        this.populationExposed = count; 
    }

    public void setInfected(int count) { 
        this.populationInfected = count; 
    }

    public void setRecovered(int count) { 
        this.populationRecovered = count; 
    }

    public void setRiskColor(Color color) { 
        this.riskColor = color; 
    }

    /**
     * Updates the risk color of the city dynamically based on its infection rate.
     * Green means low risk (<30%), Orange is moderate (<60%), and Red is high risk.
     */
    public void updateColor() {
        double rate = getInfectionRate();
        if (rate < 0.3) {
            this.riskColor = Color.GREEN;
        } else if (rate < 0.6) {
            this.riskColor = Color.ORANGE;
        } else {
            this.riskColor = Color.RED;
        }
    }
}