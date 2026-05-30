public class Cell{
    private int age;
    private State state;
    private double speed;
    private int radius;
    private double weakness;
    // ajouter les modes d'intéractions entre les cellules

    /**
     * Cell constrctor
     * Initialize a new cell
     * @param age the inital age of the cell
     * @param state the state of the cell
     * @param speed the movement speed of the cell
     * @param radius the radius of the cell
     * @param weakness a multiplier that increase or decrease the speed of the cell
     */
    public Cell(int age, State state, double speed, int radius, double weakness){
        this.age = age;
        this.state = state;
        this.speed = speed;
        this.radius = radius;
        this.weakness = weakness;
    }
}