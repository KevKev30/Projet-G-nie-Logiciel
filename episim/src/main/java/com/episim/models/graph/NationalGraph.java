package com.episim.models.graph;

import java.util.HashMap;
import java.util.Map;

import com.episim.models.entities.Region;

public class NationalGraph {
    private Map<String, Region> regions;

    public NationalGraph() {
        this.regions = new HashMap<>();
    }

    public void addRegion(Region region) {
        this.regions.put(region.getName(), region);
    }

    public Map<String, Region> getRegions() {
        return this.regions;
    }
}