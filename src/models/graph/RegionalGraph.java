package models.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.entities.City;
import models.entities.Route;

public class RegionalGraph {
    private Map<String, City> cities;
    private List<Route> routes;

    public RegionalGraph() {
        this.cities = new HashMap<>();
        this.routes = new ArrayList<>();
    }

    public void addCity(City city) {
        this.cities.put(city.getName(), city);
    }

    public void addBiRoute(City cityA, City cityB, double weight) {
        Route newRoute = new Route(cityA, cityB, weight, models.types.AccessState.OPEN);
        this.routes.add(newRoute);
    }

    public Map<String, City> getCities() { 
        return this.cities; 
    }
    public List<Route> getRoutes() { 
        return this.routes; 
    }

    /**
     * Gets all routes from a specific city
     * @param cityName the name of the city
     * @return a list with all the routes connected to the city
     */
    public List<Route> getRoutesForCity(String cityName) {
        List<Route> cityRoutes = new ArrayList<>();
        for (Route r : routes) {
            if (r.getCityA().getName().equals(cityName) || r.getCityB().getName().equals(cityName)) {
                cityRoutes.add(r);
            }
        }
        return cityRoutes;
    }
}