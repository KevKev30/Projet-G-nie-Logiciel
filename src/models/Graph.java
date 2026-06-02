package models;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Graph{
    private Map<String, Grid> zones;
    private Map<String, List<String>> adjacency;

    public Graph(){
        this.zones = new HashMap<>();
        this.adjacency = new HashMap<>();
    }

    /**
     * Adds an area to the graph
     * @param name name of the area
     * @param grid the grid associated to this area
     */
    public void addZone(String name, Grid grid){
        zones.put(name, grid);
        adjacency.put(name, new ArrayList<>());
    }

    /**
     * Links two areas (bidirectional connexion)
     */
    public void addEdge(String zone1, String zone2) {
        adjacency.get(zone1).add(zone2);
        adjacency.get(zone2).add(zone1);
    }

    public List<String> getNeighborZones(String zone) {
        return adjacency.getOrDefault(zone, new ArrayList<>());
    }

    public Grid getGrid(String zone) {
        return zones.get(zone);
    }

    public Map<String, Grid> getZones() { 
        return zones;
    }
}