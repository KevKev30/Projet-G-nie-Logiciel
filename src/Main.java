import models.Graph;
import models.Grid;
import models.Region;

public class Main {
    public static void main(String[] args) {

        Graph franceMap = new Graph();

        Region idf = new Region("Ile-de-France", 10, 10);
        Region normandie = new Region("Normandie", 10, 10);
        Region hautsDeFrance = new Region("Hauts-de-France", 10, 10);

        franceMap.addRegion(idf);
        franceMap.addRegion(normandie);
        franceMap.addRegion(hautsDeFrance);

        franceMap.addEdge("Ile-de-France", "Normandie");
        franceMap.addEdge("Ile-de-France", "Hauts-de-France");

        Grid idfGrid = idf.getCellGrid();
        for (int i = 0; i < 20; i++) {
            idfGrid.infectCell(i % 10, i / 10);
        }

        for (Region r : franceMap.getRegions().values()) {
            r.updateMacroMetrics();
        }

        System.out.println("\n--- Region Dashboard View ---");
        for (Region r : franceMap.getRegions().values()) {
            System.out.println("Region Name: " + r.getName());
            System.out.println("  > Infection Rate: " + String.format("%.2f", r.getLocalInfectionRate() * 100) + "%");
            System.out.println("  > Risk Color Code: " + r.getRiskColor());
        }

        System.out.println("\nTopology Check:");
        System.out.println("Neighbors of Ile-de-France: " + franceMap.getNeighborRegions("Ile-de-France"));
    }
}