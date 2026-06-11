package models.entities;

import models.types.AccessState;

public class Route {
    private City cityA;
    private City cityB;
    private double weight;
    private AccessState access;

    /**
     * Route constructor
     * Initialize a new route
     * @param cityA the first city to be linked
     * @param cityB the city linked to cityA
     * @param weight the weight of the route
     * @param access the access of the route
     */
    public Route(City cityA, City cityB, double weight, AccessState access){
        this.cityA = cityA;
        this.cityB = cityB;
        this.weight = weight;
        this.access = access;
    }

    public City getCityA() { 
        return cityA; 
    }
    public City getCityB() { 
        return cityB; 
    }
    public double getWeight() {
        return weight; 
    }
    public AccessState getAccess() { 
        return access; 
    }
    public void setAccess(AccessState access) { 
        this.access = access; 
    }

}
