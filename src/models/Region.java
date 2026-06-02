package models;

public class Region{
    private final String name;
    private final Grid cellGrid;
    private Color riskColor;
    private double localInfectionRate;

    /**
     * Constructor to initialize a region with a blank grid.
     */
    public Region(String name, int gridWidth, int gridHeight) {
        this.name = name;
        this.cellGrid = new Grid(gridWidth, gridHeight);
        this.riskColor = Color.GREEN; //default color
        this.localInfectionRate = 0.0;
    }

    /**
     * Recalculates the local infection rate and updates the region's risk color.
     * This fulfills the requirement from the Citizen/Expert UI presentation thresholds.
     */
    public void updateMacroMetrics() {
        int totalCells = cellGrid.getWidth() * cellGrid.getHeight();
        int infectedCount = 0;

        for (int x = 0; x < cellGrid.getWidth(); x++) {
            for (int y = 0; y < cellGrid.getHeight(); y++) {
                if (cellGrid.getCell(x, y).getState() == State.INFECTED) {
                    infectedCount++;
                }
            }
        }

        // Calculate percentage of infected cells
        this.localInfectionRate = totalCells > 0 ? (double) infectedCount / totalCells : 0.0;

        // Determine risk color based on infected population thresholds
        if (localInfectionRate > 0.15) {
            this.riskColor = Color.RED;
        } else if (localInfectionRate > 0.05) {
            this.riskColor = Color.ORANGE;
        } else {
            this.riskColor = Color.GREEN;
        }
    }

    // Getters
    public String getName() {
        return name;
    }

    public Grid getCellGrid() {
        return cellGrid;
    }

    public Color getRiskColor() {
        return riskColor;
    }

    public double getLocalInfectionRate() {
        return localInfectionRate;
    }
}