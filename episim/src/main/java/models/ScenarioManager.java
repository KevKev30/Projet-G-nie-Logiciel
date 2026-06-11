package models;

import java.util.ArrayList;
import java.util.List;

import models.entities.City;
import models.entities.Region;
import models.graph.NationalGraph;

public class ScenarioManager {

    /**
     * Directly injects a virus load into a specific city.
     * Converts healthy people into infected people if enough population is safe.
     * * @param city the City target to infect
     * @param count the number of new cases to inject
     */
    public void triggerManualInfection(City city, int count) {
        if (city.getSafe() >= count) {
            city.setSafe(city.getSafe() - count);
            city.setInfected(city.getInfected() + count);
            city.updateColor();
        }
    }

    /**
     * Randomly selects a region and a city across the map, 
     * then infects 25% of its remaining healthy population.
     * * @param nationalGraph the global network used to pick a random target
     */
    public void generateRandomEvent(NationalGraph nationalGraph) {
        List<Region> regions = new ArrayList<>(nationalGraph.getRegions().values());
        if (regions.isEmpty()) {
            return;
        }

        Region randomRegion = regions.get((int) (Math.random() * regions.size()));
        List<City> cities = new ArrayList<>(randomRegion.getRegionalGraph().getCities().values());
        if (cities.isEmpty()) {
            return;
        }
        
        City randomCity = cities.get((int) (Math.random() * cities.size()));

        int outbreak = (int) (randomCity.getSafe() * 0.25);
        if (outbreak <= 0) {
            outbreak = 1;
        }

        if (randomCity.getSafe() >= outbreak) {
            randomCity.setSafe(randomCity.getSafe() - outbreak);
            randomCity.setInfected(randomCity.getInfected() + outbreak);
            randomCity.updateColor();
            randomRegion.totalInfectedGraph();
        }
    }
}