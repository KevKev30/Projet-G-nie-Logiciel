package view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import models.entities.Region;
import models.graph.NationalGraph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reusable JavaFX Canvas component that draws a France epidemic map.
 *
 * This class is intentionally data-agnostic: it receives a NationalGraph
 * and draws it, nothing more. This means the same MapCanvas class can be
 * used for the "Real" map and the "Sandbox" map — they just receive
 * different NationalGraph instances.
 *
 * Usage:
 *   MapCanvas realMap    = new MapCanvas("Temps Réel");
 *   MapCanvas sandboxMap = new MapCanvas("Simulateur");
 *   realMap.draw(realGraph);
 *   sandboxMap.draw(sandboxGraph);
 */
public class MapCanvas extends Canvas {

    // Canvas dimensions (fixed for both maps)
    public static final int W = 480;
    public static final int H = 560;

    // Radius of each region circle in pixels
    private static final double R = 24;

    // Title displayed at the top of this canvas
    private final String title;

    /**
     * Region positions as (cx%, cy%) fractions of W and H.
     * Using a LinkedHashMap so iteration order is always the same
     * (HashMap order is undefined, which would misalign circles).
     */
    private static final Map<String, double[]> POSITIONS = new LinkedHashMap<>();
    static {
        POSITIONS.put("Hauts-de-France",          new double[]{0.52, 0.10});
        POSITIONS.put("Normandie",                 new double[]{0.30, 0.18});
        POSITIONS.put("Île-de-France",             new double[]{0.54, 0.24});
        POSITIONS.put("Grand Est",                 new double[]{0.72, 0.22});
        POSITIONS.put("Bretagne",                  new double[]{0.13, 0.30});
        POSITIONS.put("Pays de la Loire",          new double[]{0.27, 0.40});
        POSITIONS.put("Centre-Val de Loire",       new double[]{0.47, 0.38});
        POSITIONS.put("Bourgogne-Franche-Comté",  new double[]{0.66, 0.40});
        POSITIONS.put("Nouvelle-Aquitaine",        new double[]{0.27, 0.60});
        POSITIONS.put("Auvergne-Rhône-Alpes",     new double[]{0.63, 0.56});
        POSITIONS.put("Occitanie",                 new double[]{0.44, 0.74});
        POSITIONS.put("PACA",                      new double[]{0.68, 0.76});
        POSITIONS.put("Corse",                     new double[]{0.82, 0.88});
    }

    /**
     * @param title label shown at the top of the canvas ("Temps Réel" or "Simulateur")
     */
    public MapCanvas(String title) {
        super(W, H);
        this.title = title;
    }

    /**
     * Draws the full map from the given graph data.
     * Call this every time the data changes (after each step, after an event…).
     *
     * @param graph the NationalGraph to render (real or sandbox)
     */
    public void draw(NationalGraph graph) {
        GraphicsContext gc = getGraphicsContext2D();

        // Background
        gc.setFill(Color.web("#0f3460"));
        gc.fillRect(0, 0, W, H);

        // Title bar
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, W, 28);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(13));
        gc.fillText("🗺  " + title + "  (clic = détails)", 8, 19);

        // Draw each region circle
        Map<String, Region> regions = graph.getRegions();
        for (Map.Entry<String, double[]> entry : POSITIONS.entrySet()) {
            String name   = entry.getKey();
            Region region = regions.get(name);
            if (region == null) continue;

            double cx = entry.getValue()[0] * W;
            double cy = entry.getValue()[1] * H;

            // Fill color = region risk level
            gc.setFill(riskColor(region.getRiskColor()));
            gc.fillOval(cx - R, cy - R, R * 2, R * 2);

            // Border
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - R, cy - R, R * 2, R * 2);

            // First word of region name + infected count
            String shortName = name.split("[\\s\\-–]")[0];
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(9));
            gc.fillText(shortName,                        cx - R + 2, cy - 3);
            gc.fillText("I:" + region.getTotalInfected(), cx - R + 2, cy + 9);
        }
    }

    /**
     * Returns the pixel position of a region circle.
     * Used by the click handler in the view to detect which region was clicked.
     *
     * @param regionName exact region name
     * @return double[]{cx, cy} in pixels, or null if region not found
     */
    public double[] getCircleCenter(String regionName) {
        double[] pct = POSITIONS.get(regionName);
        if (pct == null) return null;
        return new double[]{ pct[0] * W, pct[1] * H };
    }

    /** @return the click radius in pixels */
    public double getRadius() { return R; }

    // Converts model risk color to a JavaFX paint color.
    // Fully-qualified to avoid collision with javafx.scene.paint.Color.
    private Color riskColor(models.types.Color risk) {
        if (risk == models.types.Color.RED)    return Color.web("#c62828", 0.90);
        if (risk == models.types.Color.ORANGE) return Color.web("#e65100", 0.90);
        return Color.web("#2e7d32", 0.90);
    }
}
