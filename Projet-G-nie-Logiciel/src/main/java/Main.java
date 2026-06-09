import java.util.List;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;
import models.types.AccessState;
import models.types.Color;

public class Main {
    public static void main(String[] args) {
        System.out.println("DEBUT DU TEST DE L'ARCHITECTURE DES GRAPHES");

        NationalGraph nationalGraph = new NationalGraph();
        Region idf = new Region("Île-de-France");
        nationalGraph.addRegion(idf);
        System.out.println("Region ajoutee : " + idf.getName());

        RegionalGraph idfGraph = idf.getRegionalGraph();
        City cergy = new City("Cergy", 10000, 0, 0, 0, Color.GREEN);
        City pontoise = new City("Pontoise", 4500, 0, 500, 0, Color.GREEN);
        City versailles = new City("Versailles", 20000, 0, 0, 0, Color.GREEN);

        idfGraph.addCity(cergy);
        idfGraph.addCity(pontoise);
        idfGraph.addCity(versailles);

        idfGraph.addBiRoute(cergy, pontoise, 0.9);
        idfGraph.addBiRoute(cergy, versailles, 0.4);

        List<Route> routesDeCergy = idfGraph.getRoutesForCity("Cergy");
        System.out.println("Routes pour Cergy : " + routesDeCergy.size());
        
        for (Route r : routesDeCergy) {
            System.out.println("Liaison : " + r.getCityA().getName() + " - " + r.getCityB().getName() + " | Etat : " + r.getAccess().getAccessState());
        }

        System.out.println("Taux infection Pontoise initial : " + (pontoise.getInfectionRate() * 100) + "%");
        pontoise.updateColor();
        System.out.println("Couleur Pontoise initiale : " + pontoise.getRiskColor().getColor());

        pontoise.setInfected(1500);
        pontoise.setSafe(3500);
        pontoise.updateColor();
        System.out.println("Nouveau taux infection Pontoise : " + (pontoise.getInfectionRate() * 100) + "%");
        System.out.println("Nouvelle couleur Pontoise : " + pontoise.getRiskColor().getColor());

        System.out.println("Couleur initiale IDF : " + idf.getRiskColor().getColor());
        idf.totalInfectedGraph();
        System.out.println("Total infectes IDF : " + idf.getTotalInfected());
        System.out.println("Nouvelle couleur IDF : " + idf.getRiskColor().getColor());

        Route axeCergyPontoise = routesDeCergy.get(0);
        System.out.println("Etat axe initial : " + axeCergyPontoise.getAccess().getAccessState());
        axeCergyPontoise.setAccess(AccessState.BARRICATED);
        System.out.println("Nouvel etat axe : " + axeCergyPontoise.getAccess().getAccessState());

        System.out.println("FIN DES TESTS DE STRUCTURE");
    }
}