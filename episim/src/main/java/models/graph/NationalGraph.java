package models.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.types.AccessState;
import models.types.Color;

/**
 * The national-level graph of regions and inter-regional routes.
 *
 * Two distinct route collections exist:
 *   - intra-regional routes  : stored inside each Region's RegionalGraph
 *                              (city A ↔ city B within the same region)
 *   - inter-regional routes  : stored here, in interRegionalRoutes
 *                              (city A in region X ↔ city B in region Y)
 *
 * This separation lets the engine process them in two distinct steps:
 *   1. computeInterCityFlux(region)   — intra only
 *   2. computeInterRegionalFlux(graph) — inter only  ← new
 */
public class NationalGraph {

    private final Map<String, Region> regions;

    /**
     * Routes that cross regional boundaries.
     * Each Route here links a city in one region to a city in another.
     * They carry an AccessState so they can be blocked individually
     * (by an Admin action, or automatically when a city hits 60% infection).
     */
    private final List<Route> interRegionalRoutes;

    public NationalGraph() {
        this.regions = new HashMap<>();
        this.interRegionalRoutes = new ArrayList<>();
    }

    public void addRegion(Region region) {
        this.regions.put(region.getName(), region);
    }

    /**
     * Adds a bidirectional inter-regional route between two cities
     * that belong to different regions.
     *
     * The route is stored once (A→B direction only) but the engine
     * treats it as bidirectional when computing flux.
     *
     * @param cityA  city in region X
     * @param cityB  city in region Y
     * @param weight traffic intensity (higher = more people travel = faster spread)
     */
    public void addInterRegionalRoute(City cityA, City cityB, double weight) {
        interRegionalRoutes.add(new Route(cityA, cityB, weight, AccessState.OPEN));
    }

    /** @return all inter-regional routes */
    public List<Route> getInterRegionalRoutes() {
        return interRegionalRoutes;
    }

    public Map<String, Region> getRegions() {
        return this.regions;
    }

    /**
     * Deep copy — creates fully independent Region, City and Route objects.
     *
     * Copy order is critical:
     *   1. Copy all Cities across all Regions first (Routes reference Cities)
     *   2. Copy intra-regional Routes per Region
     *   3. Copy inter-regional Routes using the copied City objects
     *
     * Step 3 is the new addition: without it, the sandbox's inter-regional
     * routes would still point at the real simulation's City objects,
     * meaning an injection in the sandbox would bleed into the real map.
     */
    public NationalGraph deepCopy() {
        NationalGraph copy = new NationalGraph();

        // Pass 1 — copy every City and Region; build a global name→copy map
        // so we can resolve inter-regional route endpoints in pass 3.
        Map<String, City> allCopiedCities = new HashMap<>();

        for (Region originalRegion : this.regions.values()) {
            Region copyRegion = new Region(originalRegion.getName());

            // Copy cities
            Map<String, City> regionCopiedCities = new HashMap<>();
            for (City originalCity : originalRegion.getRegionalGraph().getCities().values()) {
                City copyCity = new City(
                    originalCity.getName(),
                    originalCity.getSafe(),
                    originalCity.getExposed(),
                    originalCity.getInfected(),
                    originalCity.getRecovered(),
                    originalCity.getRiskColor()
                );
                regionCopiedCities.put(copyCity.getName(), copyCity);
                allCopiedCities.put(copyCity.getName(), copyCity);
                copyRegion.getRegionalGraph().addCity(copyCity);
            }

            // Copy intra-regional routes (link to copied cities)
            for (Route originalRoute : originalRegion.getRegionalGraph().getRoutes()) {
                City copyA = regionCopiedCities.get(originalRoute.getCityA().getName());
                City copyB = regionCopiedCities.get(originalRoute.getCityB().getName());
                if (copyA != null && copyB != null) {
                    copyRegion.getRegionalGraph().getRoutes().add(
                        new Route(copyA, copyB, originalRoute.getWeight(), originalRoute.getAccess())
                    );
                }
            }

            copyRegion.totalInfectedGraph();
            copy.addRegion(copyRegion);
        }

        // Pass 2 — copy inter-regional routes using the global city map
        for (Route original : this.interRegionalRoutes) {
            City copyA = allCopiedCities.get(original.getCityA().getName());
            City copyB = allCopiedCities.get(original.getCityB().getName());
            if (copyA != null && copyB != null) {
                copy.interRegionalRoutes.add(
                    new Route(copyA, copyB, original.getWeight(), original.getAccess())
                );
            }
        }

        return copy;
    }
}