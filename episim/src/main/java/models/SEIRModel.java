package models;

import models.entities.City;
import models.entities.Region;
import models.entities.Route;
import models.graph.NationalGraph;
import models.graph.RegionalGraph;
import models.types.AccessState;


public class SEIRPropagation {
    private double beta;
    private double sigma;
    private double gamma;
    private double mu;
    private double barricadeReductionFactor;
    private double autoQuarantineThreshold;

    public SEIRPropagation() {
        this(0.3, 0.2, 0.143, 0.001, 0.0, 0.6);
    }

    /**
     * @param beta                     taux de transmission
     * @param sigma                    taux d'incubation (1 / jours_incubation)
     * @param gamma                    taux de guérison  (1 / jours_infection)
     * @param mu                       taux de mortalité (0 pour désactiver)
     * @param barricadeReductionFactor réduction de β sur route BARRICATED (0.0 = blocage total)
     * @param autoQuarantineThreshold  seuil d'infection pour quarantaine auto (0.0 à 1.0)
     */
  
    public SEIRPropagation(double beta, double sigma, double gamma, double mu,
                           double barricadeReductionFactor, double autoQuarantineThreshold) {
        this.beta = beta;
        this.sigma = sigma;
        this.gamma = gamma;
        this.mu = mu;
        this.barricadeReductionFactor = barricadeReductionFactor;
        this.autoQuarantineThreshold = autoQuarantineThreshold;
    }
  public void step(NationalGraph graph, SimulationView view, SimulationController controller) {
        for (Region region : graph.getRegions().values()) {
            RegionalGraph rg = region.getRegionalGraph();
            java.util.List<City> cities = new java.util.ArrayList<>(rg.getCities().values());


            java.util.Map<String, int[]> deltas = new java.util.HashMap<>();
            for (City city : cities) {
                deltas.put(city.getName(), computeCityDelta(city));
            }

            propagateViaRoutes(rg, cities, deltas, barricadeReductionFactor);

            for (City city : cities) {
                int[] d = deltas.get(city.getName());
                applyDelta(city, d);
                city.updateColor();

                // 4. Quarantaine automatique si le seuil est dépassé
                if (city.getInfectionRate() > autoQuarantineThreshold) {
                    controller.blockRoutesForCity(region, city.getName());
                    view.showMessage("⚠ Quarantaine SEIR automatique : " + city.getName()
                            + " (" + String.format("%.0f", city.getInfectionRate() * 100) + "% infectés)");
                }
            }

            region.totalInfectedGraph();
        }
        view.update();
    }

    // ===================== CALCUL SEIR PAR VILLE =====================

    public int[] computeCityDelta(City city) {
        int S = city.getSafe();
        int E = city.getExposed();
        int I = city.getInfected();
        int N = Math.max(1, S + E + I); // population totale non décédée (guéris exclus si non stockés)

        // Équations SEIR discrètes
        double newExposures  = beta  * S * I / (double) N;   // S → E
        double newInfections = sigma * E;                      // E → I
        double newRecovered  = gamma * I;                      // I → R
        double newDeaths     = mu    * I;                      // I → décès

        // Arrondi déterministe (plancher + tirage de la partie fractionnaire)
        int dE       = roundStochastic(newExposures);
        int dI       = roundStochastic(newInfections);
        int dR       = roundStochastic(newRecovered);
        int deaths   = roundStochastic(newDeaths);

        // Contraintes de conservation (on ne peut pas infecter plus que disponible)
        dE     = Math.min(dE, S);
        dI     = Math.min(dI, E);
        dR     = Math.min(dR, I);
        deaths = Math.min(deaths, I - dR);  // les décès viennent des infectés restants

        int dS = -dE;                // les nouveaux exposés quittent S
        int dInet = dI - dR - deaths; // variation nette de I

        return new int[]{dS, dE - dI, dInet, dR, deaths};
        //               ΔS   ΔE         ΔI     ΔR  morts
    }

    // ===================== PROPAGATION INTER-CITÉS =====================

    /**
     * Enrichit les deltas en tenant compte de la propagation entre villes voisines
     * via les routes (ouvertes ou partiellement barriquées).
     *
     * La transmission via une route suit le même modèle β*S_voisin*I_source/N_source,
     * pondérée par un facteur de distance et par l'état de la route.
     */
    private void propagateViaRoutes(RegionalGraph rg, java.util.List<City> cities,
                                    java.util.Map<String, int[]> deltas,
                                    double barricadeFactor) {
        for (City source : cities) {
            if (source.getInfected() == 0) continue;

            for (Route route : rg.getRoutesForCity(source.getName())) {
                // Facteur de réduction selon l'état de la route
                double routeFactor;
                if (route.getAccess() == AccessState.BARRICATED) {
                    routeFactor = barricadeFactor; // transmission résiduelle (0 par défaut = blocage total)
                } else {
                    routeFactor = 1.0;
                }
                if (routeFactor == 0.0) continue;

                City neighbor = route.getCityA().getName().equals(source.getName())
                        ? route.getCityB() : route.getCityA();

                int S = neighbor.getSafe();
                int I = source.getInfected();
                int N = Math.max(1, source.getSafe() + source.getExposed() + source.getInfected());

                // β est réduit par la distance de la route
                double distance = Math.max(0.5, route.getDistance());
                double effectiveBeta = beta * routeFactor / distance;

                double newExposures = effectiveBeta * S * I / (double) N;
                int dE = roundStochastic(newExposures);
                dE = Math.min(dE, S);
                if (dE <= 0) continue;

                // Ajouter au delta du voisin : dS -= dE, dE += dE
                int[] d = deltas.get(neighbor.getName());
                d[0] -= dE;  // ΔS
                d[1] += dE;  // ΔE
            }
        }
    }

    // ===================== APPLICATION DES DELTAS =====================

    private void applyDelta(City city, int[] d) {
        int dS      = d[0];
        int dE      = d[1];
        int dI      = d[2];

        // Garantir que les valeurs restent >= 0
        int newSafe     = Math.max(0, city.getSafe()     + dS);
        int newExposed  = Math.max(0, city.getExposed()  + dE);
        int newInfected = Math.max(0, city.getInfected() + dI);

        city.setSafe(newSafe);
        city.setExposed(newExposed);
        city.setInfected(newInfected);
    }

    // ===================== UTILITAIRES =====================

    /**
     * Arrondi stochastique : arrondit à l'entier inférieur avec une probabilité
     * égale à la partie fractionnaire, garantissant une espérance correcte.
     */
    private int roundStochastic(double value) {
        int floor = (int) value;
        double frac = value - floor;
        return floor + (Math.random() < frac ? 1 : 0);
    }

    // ===================== PRÉRÉGLAGES DE DIFFERENTES MALADIES =====================

    /** Paramètres représentatifs d'une grippe saisonnière (R0 ≈ 1.5, incubation 2j). */
    public static SEIRPropagation flu() {
        return new SEIRPropagation(0.30, 0.50, 0.20, 0.0005, 0.0, 0.6);
    }

    /** Paramètres représentatifs d'une rougeole (R0 ≈ 15, très contagieuse). */
    public static SEIRPropagation measles() {
        return new SEIRPropagation(0.90, 0.25, 0.10, 0.001, 0.0, 0.5);
    }

    /** Paramètres représentatifs d'un pathogène modéré type COVID-19 (R0 ≈ 2.5). */
    public static SEIRPropagation covid() {
        return new SEIRPropagation(0.35, 0.196, 0.143, 0.003, 0.0, 0.6);
    }

    /** Pathogène très létal mais peu contagieux (type Ebola). */
    public static SEIRPropagation ebola() {
        return new SEIRPropagation(0.15, 0.125, 0.10, 0.05, 0.0, 0.4);
    }

    // ===================== GETTERS / SETTERS =====================

    public double getBeta()  { return beta;  }
    public double getSigma() { return sigma; }
    public double getGamma() { return gamma; }
    public double getMu()    { return mu;    }

    public void setBeta(double beta)   { this.beta  = beta;  }
    public void setSigma(double sigma) { this.sigma = sigma; }
    public void setGamma(double gamma) { this.gamma = gamma; }
    public void setMu(double mu)       { this.mu    = mu;    }

    public void setBarricadeReductionFactor(double f)  { this.barricadeReductionFactor = f; }
    public void setAutoQuarantineThreshold(double t)   { this.autoQuarantineThreshold  = t; }

    public double getR0() { return beta / gamma; }

    @Override
    public String toString() {
        return String.format("SEIRPropagation[β=%.3f, σ=%.3f, γ=%.3f, μ=%.4f, R0=%.2f]",
                beta, sigma, gamma, mu, getR0());
    }
}
