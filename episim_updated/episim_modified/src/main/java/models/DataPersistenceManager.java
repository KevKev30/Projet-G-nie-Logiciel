package models;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.types.AccessState;
import models.types.Color;

public class DataPersistenceManager {

    /**
     * Serializes the current simulation state, regions, cities, and routes 
     * into a JSON file saved on the hard drive.
     * * @param model the current simulation data to backup
     * @param filePath the destination path on the disk (e.g., "save.json")
     */
    public void save(SimulationModel model, String filePath) {
        try {
            JSONObject jsonRoot = new JSONObject();
            jsonRoot.put("currentStep", model.getCurrentStep());
            
            JSONArray jsonRegions = new JSONArray();
            NationalGraph graph = model.getNationalGraph();
            
            for (Region region : graph.getRegions().values()) {
                JSONObject jsonRegion = new JSONObject();
                jsonRegion.put("name", region.getName());
                jsonRegion.put("riskColor", region.getRiskColor().name());
                
                JSONArray jsonCities = new JSONArray();
                for (City city : region.getRegionalGraph().getCities().values()) {
                    JSONObject jsonCity = new JSONObject();
                    jsonCity.put("name", city.getName());
                    jsonCity.put("safe", city.getSafe());
                    jsonCity.put("exposed", city.getExposed());
                    jsonCity.put("infected", city.getInfected());
                    jsonCity.put("recovered", city.getRecovered());
                    jsonCity.put("riskColor", city.getRiskColor().name());
                    jsonCities.put(jsonCity);
                }
                jsonRegion.put("cities", jsonCities);
                
                JSONArray jsonRoutes = new JSONArray();
                for (Route route : region.getRegionalGraph().getRoutes()) {
                    JSONObject jsonRoute = new JSONObject();
                    jsonRoute.put("cityA", route.getCityA().getName());
                    jsonRoute.put("cityB", route.getCityB().getName());
                    jsonRoute.put("weight", route.getWeight());
                    jsonRoute.put("access", route.getAccess().name());
                    jsonRoutes.put(jsonRoute);
                }
                jsonRegion.put("routes", jsonRoutes);
                
                jsonRegions.put(jsonRegion);
            }
            
            jsonRoot.put("regions", jsonRegions);
            
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(jsonRoot.toString(4));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a JSON backup file from the disk and reconstructs 
     * a complete SimulationModel object with its full history.
     * * @param filePath the file path to load from the disk
     * @return a restored SimulationModel, or null if an error occurs
     */
    public SimulationModel load(String filePath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject jsonRoot = new JSONObject(content);
            
            SimulationModel model = new SimulationModel();
            model.setCurrentStep(jsonRoot.getInt("currentStep"));
            
            JSONArray jsonRegions = jsonRoot.getJSONArray("regions");
            for (int i = 0; i < jsonRegions.length(); i++) {
                JSONObject jsonRegion = jsonRegions.getJSONObject(i);
                Region region = new Region(jsonRegion.getString("name"));
                region.setRiskColor(Color.valueOf(jsonRegion.getString("riskColor")));
                
                JSONArray jsonCities = jsonRegion.getJSONArray("cities");
                for (int j = 0; j < jsonCities.length(); j++) {
                    JSONObject jsonCity = jsonCities.getJSONObject(j);
                    City city = new City(
                        jsonCity.getString("name"),
                        jsonCity.getInt("safe"),
                        jsonCity.getInt("exposed"),
                        jsonCity.getInt("infected"),
                        jsonCity.getInt("recovered"),
                        Color.valueOf(jsonCity.getString("riskColor"))
                    );
                    region.getRegionalGraph().addCity(city);
                }
                
                JSONArray jsonRoutes = jsonRegion.getJSONArray("routes");
                for (int j = 0; j < jsonRoutes.length(); j++) {
                    JSONObject jsonRoute = jsonRoutes.getJSONObject(j);
                    City cA = region.getRegionalGraph().getCities().get(jsonRoute.getString("cityA"));
                    City cB = region.getRegionalGraph().getCities().get(jsonRoute.getString("cityB"));
                    
                    if (cA != null && cB != null) {
                        Route route = new Route(
                            cA, cB,
                            jsonRoute.getDouble("weight"),
                            AccessState.valueOf(jsonRoute.getString("access"))
                        );
                        region.getRegionalGraph().getRoutes().add(route);
                    }
                }
                
                model.getNationalGraph().addRegion(region);
            }
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}