package models.entities;

import models.graph.RegionalGraph;
import models.types.Color;

public class Region {
    private String name;
    private RegionalGraph regionalGraph;
    private Color riskColor;
    private int totalInfected;

    public Region(String name) {
        this.name = name;
        this.regionalGraph = new RegionalGraph();
        this.riskColor = Color.GREEN;
        this.totalInfected = 0;
    }

    public String getName() {
        return name; 
    }

    public RegionalGraph getRegionalGraph() {
        return regionalGraph; 
    }

    public Color getRiskColor() { 
        return riskColor; 
    }

    public int getTotalInfected() { 
        return totalInfected; 
    }

    /**
     * Calculate the total of persons infected in all the graph
     * In addition, give a color representing the rate of the virus depending on the total population ans the total infected
     */
    public void totalInfectedGraph() {
        int totalPop = 0;
        this.totalInfected = 0;

        for (City city : regionalGraph.getCities().values()) {
            totalPop += city.getTotalPopulation();
            this.totalInfected += city.getInfected();
        }

        if (totalPop == 0) {
            this.riskColor = Color.GREEN;
            return;
        }

        double macroRate = (double) this.totalInfected / totalPop;
        if (macroRate < 0.3) {
            this.riskColor = Color.GREEN;
        } else if (macroRate < 0.6) {
            this.riskColor = Color.ORANGE;
        } else {
            this.riskColor = Color.RED;
        }
    }
}