package models;

import exceptions.CityStateException;
import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;
import models.types.AccessState;

/**
 * Core simulation engine.
 *
 * Handles all epidemic computations:
 *   - computeLocalSEIR()          : S→E→I→R transitions inside a region
 *   - computeInterCityFlux()      : virus spread between cities via intra-regional routes
 *   - computeInterRegionalFlux()  : virus spread between regions via national routes
 *   - checkAndApplyBarricades()   : automatic route closure when a city hits 60% infection
 */
public class SimulationEngine {

    /**
     * Computes local S→E→I→R transitions for every city inside a region.
     *
     * Each city is updated independently.  If a city reaches an inconsistent
     * state, a CityStateException is caught and logged so the rest of the
     * simulation is never interrupted by one bad city.
     *
     * @param region the region whose cities to update
     * @param config epidemic parameters (β, σ, γ, μ…)
     */
    public void computeLocalSEIR(Region region, SimulationConfig config) {
        RegionalGraph rg = region.getRegionalGraph();
        for (City city : rg.getCities().values()) {
            int total = city.getTotalPopulation();
            if (total == 0) continue;

            int S = city.getSafe();
            int E = city.getExposed();
            int I = city.getInfected();
            int R = city.getRecovered();

            try {
                double lambda = config.getTransmissionRate() * I / total;

                // Math.min ensures no value ever goes negative before reaching the setter.
                // City setters throw CityStateException on negative values — but
                // these Math.min guards make that path unreachable under normal operation.
                int newExposed   = Math.min((int)(S * lambda), S);
                int newInfected  = Math.min((int)(E * config.getIncubationRate()), E);
                int newRecovered = Math.min((int)(I * config.getRecoveryRate()), I);
                int newDeaths    = Math.min((int)(I * config.getMortalityRate()),
                                            Math.max(0, I - newRecovered));

                city.setSafe    (S - newExposed);
                city.setExposed (E + newExposed  - newInfected);
                city.setInfected(I + newInfected - newRecovered - newDeaths);
                city.setRecovered(R + newRecovered);
                city.updateColor();

            } catch (CityStateException e) {
                // One bad city must not crash the whole simulation step.
                System.err.println("[SEIR] " + e.getMessage());
            }
        }
    }

    /**
     * Computes virus propagation between cities connected by intra-regional routes.
     *
     * For each open route, a fraction of the safe population in each city
     * becomes exposed based on the infected proportion of the neighbouring city.
     * The flux factor depends on route weight and transmission rate.
     *
     * @param region the region containing the routes to process
     * @param config epidemic parameters
     */
    public void computeInterCityFlux(Region region, SimulationConfig config) {
        RegionalGraph rg = region.getRegionalGraph();
        for (Route route : rg.getRoutes()) {
            if (route.getAccess() == AccessState.BARRICATED) continue;

            City cityA = route.getCityA();
            City cityB = route.getCityB();

            // RESTRICTED = partial closure (e.g. checkpoint), 20% of normal flux
            double modifier   = (route.getAccess() == AccessState.RESTRICTED) ? 0.2 : 1.0;
            double fluxFactor = route.getWeight() * modifier * config.getTransmissionRate() * 0.12;

            applyFlux(cityA, cityB, fluxFactor);
            applyFlux(cityB, cityA, fluxFactor);
        }
    }

    /**
     * Computes virus propagation across inter-regional routes.
     *
     * Same logic as computeInterCityFlux() but with a lower base flux (0.05 vs 0.12)
     * because cross-region travel is less frequent than local movement.
     *
     * Called once per step AFTER all intra-regional flux has been computed.
     *
     * @param graph  the national graph containing all inter-regional routes
     * @param config epidemic parameters
     */
    public void computeInterRegionalFlux(NationalGraph graph, SimulationConfig config) {
        for (Route route : graph.getInterRegionalRoutes()) {
            if (route.getAccess() == AccessState.BARRICATED) continue;

            City cityA = route.getCityA();
            City cityB = route.getCityB();

            double modifier   = (route.getAccess() == AccessState.RESTRICTED) ? 0.2 : 1.0;
            double fluxFactor = route.getWeight() * modifier * config.getTransmissionRate() * 0.05;

            applyFlux(cityA, cityB, fluxFactor);
            applyFlux(cityB, cityA, fluxFactor);
        }
    }

    /**
     * Transfers infected pressure from source city to destination city.
     *
     * The number of newly exposed people in dest = dest.safe
     *   × (source.infected / source.total) × fluxFactor.
     *
     * Private helper shared by both flux methods to avoid duplication.
     *
     * @param source      city generating infected pressure
     * @param dest        city receiving the exposure
     * @param fluxFactor  combined weight × modifier × transmissionRate × baseFlux
     */
    private void applyFlux(City source, City dest, double fluxFactor) {
        if (source.getInfected() <= 0 || dest.getSafe() <= 0) return;
        int inf = (int)(dest.getSafe()
            * ((double) source.getInfected() / source.getTotalPopulation())
            * fluxFactor);
        inf = Math.min(inf, dest.getSafe());
        if (inf <= 0) return;
        dest.setSafe(dest.getSafe() - inf);
        dest.setExposed(dest.getExposed() + inf);
    }

    /**
     * Automatically barricades any route (intra or inter-regional) whose
     * endpoint cities exceed 60% infection rate.
     *
     * Checks both route collections so no hotspot city keeps its connections open.
     *
     * @param graph the national graph to inspect
     */
    public void checkAndApplyBarricades(NationalGraph graph) {
        // Intra-regional routes
        for (Region region : graph.getRegions().values()) {
            for (Route route : region.getRegionalGraph().getRoutes()) {
                autoBarricade(route);
            }
        }
        // Inter-regional routes
        for (Route route : graph.getInterRegionalRoutes()) {
            autoBarricade(route);
        }
    }

    /**
     * Barricades a route if either endpoint exceeds the 60% threshold.
     * Private helper shared by checkAndApplyBarricades().
     */
    private void autoBarricade(Route route) {
        if (route.getAccess() == AccessState.BARRICATED) return;
        if (route.getCityA().getInfectionRate() > 0.6
                || route.getCityB().getInfectionRate() > 0.6) {
            route.setAccess(AccessState.BARRICATED);
        }
    }
}