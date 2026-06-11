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
     * * @param region the region containing the cities to update
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

            double lambda = config.getTransmissionRate() * I / total;
            int newExposed = (int) (S * lambda);
            if (newExposed > S) newExposed = S;

            int newInfected = (int) (E * config.getIncubationRate());
            if (newInfected > E) newInfected = E;

            int newRecovered = (int) (I * config.getRecoveryRate());
            if (newRecovered > I) newRecovered = I;

            city.setSafe(S - newExposed);
            city.setExposed(E + newExposed - newInfected);
            city.setInfected(I + newInfected - newRecovered);
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
            double fluxFactor = route.getWeight() * modifier * config.getTransmissionRate() * 0.05;

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
    public void checkAndApplyBarricades(NationalGraph nationalGraph) {
        for (Region region : nationalGraph.getRegions().values()) {
            for (Route route : region.getRegionalGraph().getRoutes()) {
                City cityA = route.getCityA();
                City cityB = route.getCityB();
                
                if (cityA.getInfectionRate() > 0.6 && route.getAccess() != AccessState.BARRICATED) {
                    route.setAccess(AccessState.BARRICATED);
                }
                if (cityB.getInfectionRate() > 0.6 && route.getAccess() != AccessState.BARRICATED) {
                    route.setAccess(AccessState.BARRICATED);
                }
            }
        }
    }
}