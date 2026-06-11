package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import models.entities.City;
import models.entities.Region;
import models.graph.NationalGraph;

public class ScenarioManager {

    private static final Random random = new Random();

    /**
     * Directly injects a virus load into a specific city.
     * Converts healthy people into infected people if enough population is safe.
     *
     * @param city  the City target to infect
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
     * Randomly selects ANY city across ALL regions of the map and starts
     * a new COVID outbreak there — even if the city had zero infected before.
     * The outbreak seeds between 0.05% and 0.3% of the city's safe population,
     * with a minimum of 1 case so the event always has a visible effect.
     *
     * @param nationalGraph the global network used to pick a random target
     * @return the name of the city where the outbreak was triggered, or null
     */
    public String generateRandomEvent(NationalGraph nationalGraph) {
        // Collect every city across all regions
        List<City>   allCities  = new ArrayList<>();
        List<Region> allRegions = new ArrayList<>();

        for (Region region : nationalGraph.getRegions().values()) {
            for (City city : region.getRegionalGraph().getCities().values()) {
                allCities.add(city);
                allRegions.add(region);
            }
        }

        if (allCities.isEmpty()) {
            return null;
        }

        // Prefer cities with no (or few) infected — more realistic for a new outbreak
        List<Integer> preferredIndices = new ArrayList<>();
        for (int i = 0; i < allCities.size(); i++) {
            City c = allCities.get(i);
            if (c.getSafe() > 0 && c.getInfected() == 0) {
                preferredIndices.add(i);
            }
        }

        int chosenIndex;
        if (!preferredIndices.isEmpty() && random.nextDouble() < 0.75) {
            // 75% chance: pick a city that has NO infected yet (new outbreak)
            chosenIndex = preferredIndices.get(random.nextInt(preferredIndices.size()));
        } else {
            // 25% chance: pick any city (amplify an existing zone)
            List<Integer> safeIndices = new ArrayList<>();
            for (int i = 0; i < allCities.size(); i++) {
                if (allCities.get(i).getSafe() > 0) safeIndices.add(i);
            }
            if (safeIndices.isEmpty()) return null;
            chosenIndex = safeIndices.get(random.nextInt(safeIndices.size()));
        }

        City  targetCity   = allCities.get(chosenIndex);
        Region targetRegion = allRegions.get(chosenIndex);

        // Seed: between 0.05% and 0.3% of safe population, minimum 1
        double fraction = 0.0005 + random.nextDouble() * 0.0025;
        int outbreak = Math.max(1, (int) (targetCity.getSafe() * fraction));
        if (outbreak > targetCity.getSafe()) outbreak = targetCity.getSafe();

        targetCity.setSafe(targetCity.getSafe() - outbreak);
        targetCity.setInfected(targetCity.getInfected() + outbreak);
        targetCity.updateColor();
        targetRegion.totalInfectedGraph();

        return targetCity.getName();
    }
}
