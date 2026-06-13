package models;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;
import models.types.AccessState;

public class SimulationEngine {

    /**
     * Computes local biological virus transitions (S -> E -> I -> R) 
     * for every city inside a specific region.
     *
     * Ajustements COVID par rapport au modèle générique :
     *  - La mortalité est soustraite des infectés à chaque pas (μ = 2 %)
     *  - Le taux d'asymptomatiques réduit la pression effective de transmission
     *    (les asymptomatiques transmettent moins → facteur 0.5 appliqué sur leur part)
     *
     * @param region the region containing the cities to update
     * @param config the configuration containing the infection rates
     */
    public void computeLocalSEIR(Region region, SimulationConfig config) {
        RegionalGraph rg = region.getRegionalGraph();
        for (City city : rg.getCities().values()) {
            int total = city.getTotalPopulation();
            if (total == 0) {
                continue;
            }

            int S = city.getSafe();
            int E = city.getExposed();
            int I = city.getInfected();
            int R = city.getRecovered();
            
            double asymp  = config.getAsymptomaticRate();
            double effectiveI = I * ((1 - asymp) + asymp * 0.5);
            double lambda = config.getTransmissionRate() * I / total;
            
            int newExposed = (int) (S * lambda);
            if (newExposed > S) newExposed = S;

            int newInfected = (int) (E * config.getIncubationRate());
            if (newInfected > E) newInfected = E;

            int newRecovered = (int) (I * config.getRecoveryRate());
            if (newRecovered > I) newRecovered = I;

            int newDeaths = (int) (I * config.getMortalityRate());
            if (newDeaths > I - newRecovered) newDeaths = Math.max(0, I - newRecovered);

            city.setSafe(S - newExposed);
            city.setExposed(E + newExposed - newInfected);
            city.setInfected(I + newInfected - newRecovered - newDeaths);
            city.setRecovered(R + newRecovered);

            city.updateColor();
        }
    }

    /**
     * Computes virus propagation between neighboring cities connected by routes.
     * Contamination speed depends on the route weight and road state.
     * * @param region the region containing the routes to analyze
     * @param config the configuration containing transmission parameters
     */
    public void computeInterCityFlux(Region region, SimulationConfig config) {
        RegionalGraph rg = region.getRegionalGraph();
        for (Route route : rg.getRoutes()) {
            if (route.getAccess() == AccessState.BARRICATED) {
                continue;
            }

            City cityA = route.getCityA();
            City cityB = route.getCityB();

            double modifier = (route.getAccess() == AccessState.RESTRICTED) ? 0.2 : 1.0;
            double fluxFactor = route.getWeight() * modifier * config.getTransmissionRate() * 0.12;

            if (cityA.getInfected() > 0 && cityB.getSafe() > 0) {
                int inf = (int) (cityB.getSafe() * ((double) cityA.getInfected() / cityA.getTotalPopulation()) * fluxFactor);
                if (inf > cityB.getSafe()) {
                    inf = cityB.getSafe();
                }
                cityB.setSafe(cityB.getSafe() - inf);
                cityB.setExposed(cityB.getExposed() + inf);
            }

            if (cityB.getInfected() > 0 && cityA.getSafe() > 0) {
                int inf = (int) (cityA.getSafe() * ((double) cityB.getInfected() / cityB.getTotalPopulation()) * fluxFactor);
                if (inf > cityA.getSafe()) {
                    inf = cityA.getSafe();
                }
                cityA.setSafe(cityA.getSafe() - inf);
                cityA.setExposed(cityA.getExposed() + inf);
            }
        }
    }

    /**
     * Automatically barricades routes connected to cities 
     * that have exceeded the critical threshold of 60% infection rate.
     * * @param nationalGraph the global map containing all regions and cities
     */
    /**
     * Automatically barricades intra AND inter-regional routes when a city
     * exceeds the 60% infection threshold.
     *
     * We check both route collections:
     *   1. Intra-regional routes (inside each Region's RegionalGraph)
     *   2. Inter-regional routes (stored directly on the NationalGraph)
     *
     * This ensures that a city that becomes a hotspot also cuts off its
     * inter-regional connections automatically, preventing the epidemic
     * from spreading nationally via that city.
     */
    public void checkAndApplyBarricades(NationalGraph nationalGraph) {
        // Intra-regional routes
        for (Region region : nationalGraph.getRegions().values()) {
            for (Route route : region.getRegionalGraph().getRoutes()) {
                autoBarricade(route);
            }
        }
        // Inter-regional routes
        for (Route route : nationalGraph.getInterRegionalRoutes()) {
            autoBarricade(route);
        }
    }

    /**
     * Barricades a route if either of its endpoint cities exceeds 60% infection.
     * Private helper to avoid code duplication between intra/inter checks.
     */
    private void autoBarricade(Route route) {
        if (route.getCityA().getInfectionRate() > 0.6
                && route.getAccess() != AccessState.BARRICATED) {
            route.setAccess(AccessState.BARRICATED);
        }
        if (route.getCityB().getInfectionRate() > 0.6
                && route.getAccess() != AccessState.BARRICATED) {
            route.setAccess(AccessState.BARRICATED);
        }
    }

    /**
     * Computes virus propagation across inter-regional routes.
     *
     * Called once per simulation step AFTER computeInterCityFlux() has
     * already handled intra-regional spread.  The logic is identical to
     * computeInterCityFlux() — infection probability depends on the number
     * of infected in the source city, the route weight, and the config rate
     * — but the two cities now belong to different regions.
     *
     * Inter-regional routes have a lower base flux (×0.05 vs ×0.12 intra)
     * because travelling between regions is less frequent than moving within
     * a region, so the spread is naturally slower.
     *
     * Barricaded routes are skipped entirely (same as intra-regional logic).
     *
     * @param graph  the national graph containing all inter-regional routes
     * @param config simulation configuration (transmission rate, etc.)
     */
    public void computeInterRegionalFlux(models.graph.NationalGraph graph, SimulationConfig config) {
        for (models.entities.Route route : graph.getInterRegionalRoutes()) {

            // Barricaded = no movement at all between the two cities
            if (route.getAccess() == models.types.AccessState.BARRICATED) continue;

            models.entities.City cityA = route.getCityA();
            models.entities.City cityB = route.getCityB();

            // RESTRICTED reduces flux to 20% (e.g. border checkpoint)
            double modifier = (route.getAccess() == models.types.AccessState.RESTRICTED) ? 0.2 : 1.0;

            // Inter-regional flux factor: lower than intra (0.05 vs 0.12)
            // because cross-region travel is less frequent
            double fluxFactor = route.getWeight() * modifier * config.getTransmissionRate() * 0.05;

            // A → B direction
            if (cityA.getInfected() > 0 && cityB.getSafe() > 0) {
                int inf = (int)(cityB.getSafe()
                    * ((double) cityA.getInfected() / cityA.getTotalPopulation())
                    * fluxFactor);
                inf = Math.min(inf, cityB.getSafe());
                cityB.setSafe(cityB.getSafe() - inf);
                cityB.setExposed(cityB.getExposed() + inf);
            }

            // B → A direction (bidirectional route)
            if (cityB.getInfected() > 0 && cityA.getSafe() > 0) {
                int inf = (int)(cityA.getSafe()
                    * ((double) cityB.getInfected() / cityB.getTotalPopulation())
                    * fluxFactor);
                inf = Math.min(inf, cityA.getSafe());
                cityA.setSafe(cityA.getSafe() - inf);
                cityA.setExposed(cityA.getExposed() + inf);
            }
        }
    }}