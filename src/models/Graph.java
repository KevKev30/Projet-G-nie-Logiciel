package models;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Graph{
    private Map<String, Region> regions;
    private Map<String, List<String>> adjacency;

    public Graph() {
        this.regions = new HashMap<>();
        this.adjacency = new HashMap<>();
    }

    /**
     * Adds a complete region node to the graph.
     */
    public void addRegion(Region region) {
        regions.put(region.getName(), region);
        adjacency.put(region.getName(), new ArrayList<>());
    }

    /**
     * Links two regions together (bidirectional/undirected edge).
     */
    public void addEdge(String region1, String region2) {
        if (adjacency.containsKey(region1) && adjacency.containsKey(region2)) {
            if (!adjacency.get(region1).contains(region2)) {
                adjacency.get(region1).add(region2);
            }
            if (!adjacency.get(region2).contains(region1)) {
                adjacency.get(region2).add(region1);
            }
        }
    }

    public List<String> getNeighborRegions(String regionName) {
        return adjacency.getOrDefault(regionName, new ArrayList<>());
    }

    public Region getRegion(String regionName) {
        return regions.get(regionName);
    }

    public Map<String, Region> getRegions() {
        return regions;
    }
}