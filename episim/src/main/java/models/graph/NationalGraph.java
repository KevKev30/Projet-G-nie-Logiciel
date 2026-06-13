package models.graph;

import java.util.HashMap;
import java.util.Map;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.types.AccessState;
import models.types.Color;

public class NationalGraph {
    private final Map<String, Region> regions;

    public NationalGraph() {
        this.regions = new HashMap<>();
    }

    public void addRegion(Region region) {
        this.regions.put(region.getName(), region);
    }

    public Map<String, Region> getRegions() {
        return this.regions;
    }

    /**
     * Creates a fully independent deep copy of this NationalGraph.
     *
     * Every City, Region and Route is a brand-new object — modifying
     * the copy has zero effect on the original, and vice-versa.
     * This is what lets us run the "real" simulation and the "sandbox"
     * simulation in parallel without them interfering.
     *
     * Deep copy order matters:
     *   1. Copy Cities first (Routes hold references to City objects)
     *   2. Copy Regions and add copied Cities
     *   3. Copy Routes using the copied City objects (not the originals)
     */
    public NationalGraph deepCopy() {
        NationalGraph copy = new NationalGraph();

        for (Region originalRegion : this.regions.values()) {
            Region copyRegion = new Region(originalRegion.getName());

            // Step 1 — copy every City into a fresh object
            Map<String, City> copiedCities = new HashMap<>();
            for (City originalCity : originalRegion.getRegionalGraph().getCities().values()) {
                City copyCity = new City(
                    originalCity.getName(),
                    originalCity.getSafe(),
                    originalCity.getExposed(),
                    originalCity.getInfected(),
                    originalCity.getRecovered(),
                    originalCity.getRiskColor()
                );
                copiedCities.put(copyCity.getName(), copyCity);
                copyRegion.getRegionalGraph().addCity(copyCity);
            }

            // Step 2 — copy every Route, linking to the COPIED cities
            for (Route originalRoute : originalRegion.getRegionalGraph().getRoutes()) {
                City copyA = copiedCities.get(originalRoute.getCityA().getName());
                City copyB = copiedCities.get(originalRoute.getCityB().getName());
                if (copyA != null && copyB != null) {
                    Route copyRoute = new Route(copyA, copyB,
                            originalRoute.getWeight(), originalRoute.getAccess());
                    copyRegion.getRegionalGraph().getRoutes().add(copyRoute);
                }
            }

            copyRegion.totalInfectedGraph();
            copy.addRegion(copyRegion);
        }

        return copy;
    }
}