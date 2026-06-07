package src;

public class SimulationController {

    private SimulationView view;

    public SimulationController(SimulationView view) {
        this.view = view;
    }

    public void togglePlayPause() {
        System.out.println("Play/Pause");
    }

    public void stepForward() {
        System.out.println("Step forward");
    }

    public void changeSpeed(double factor) {
        System.out.println("Speed: " + factor);
    }

    public void onCellClicked(String regionName, int x, int y, String actionType) {
        System.out.println("Cellule cliquée : " + regionName + " (" + x + "," + y + ") - " + actionType);
    }
}