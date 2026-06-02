import models.Graph;
import models.Grid;

public class Main {
    public static void main(String[] args){
        // Test Grid
        Grid grid = new Grid(10, 10);
        grid.infectCell(5, 5);
        System.out.println("Cellule (5,5) : " + grid.getCell(5, 5).getState());
        System.out.println("Cellule (0,0) : " + grid.getCell(0, 0).getState());
        System.out.println("Voisins de (5,5) : " + grid.getCell(5, 5).getNeighbors().size());

        // Test Graph
        Graph graph = new Graph();
        graph.addZone("Nord", new Grid(10, 10));
        graph.addZone("Sud", new Grid(10, 10));
        graph.addZone("Centre", new Grid(10, 10));
        graph.addEdge("Nord", "Centre");
        graph.addEdge("Sud", "Centre");

        System.out.println("Voisins de Centre : " + graph.getNeighborZones("Centre"));
        System.out.println("Voisins de Nord : " + graph.getNeighborZones("Nord"));
    }
}
