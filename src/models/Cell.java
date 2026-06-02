package models;
import java.util.ArrayList;
import java.util.List;

public class Cell{
    private int age;
    private State state;
    private double speed;
    private int radius;
    private double weakness;
    private final int x;
    private final int y;
    private List<Cell> neighbors;

    /**
     * Cell constrctor
     * Initialize a new cell
     * @param age the inital age of the cell
     * @param state the state of the cell
     * @param speed the movement speed of the cell
     * @param radius the radius of the cell
     * @param weakness a multiplier that increase or decrease the speed of the cell
     * @param x the position x of the cell
     * @param y the position y of the cell
     * @param neighbors the neighbors of the cell
     */
    public Cell(int age, State state, double speed, int radius, double weakness, int x, int y){
        this.age = age;
        this.state = state;
        this.speed = speed;
        this.radius = radius;
        this.weakness = weakness;
        this.x = x;
        this.y = y;
        this.neighbors = new ArrayList<>();
    }

    /**
     * Verify if this cell can infect a neighbor cell
     * An infected cell can infect its SAFE neighbors
     */
    public boolean canInfect(Cell other){
        return this.state == State.INFECTED && other.state == State.SAFE;
    }

    /**
     * Tries to infect this cell according to a probability rate
     * weakness increases or decreases the infection probability
     * @param infectionRate baseline infection rate (between 0 and 1)
     */
    public void tryInfect(double infectionRate){
        double probability = infectionRate * this.weakness;
        if (Math.random() < probability){
            this.state = State.INFECTED;
        }
    }

    /**
     * Tries to heal this cell according to a recovery rate
     * @param recoveryRate recovery rate (between 0 an 1)
     */
    public void tryRecover(double recoveryRate){
        if (this.state == State.INFECTED && Math.random() < recoveryRate){
            this.state = State.RECOVERED;
        }
    }

    public void addNeighbors(Cell neighbor){
        this.neighbors.add(neighbor);
    }

    public int getAge(){
        return age;
    }
    public void setAge(int age){
        this.age = age;
    }

    public State getState(){
        return state;
    }
    public void setState(State state){
        this.state = state;
    }

    public double getSpeed(){
        return speed;
    }
    public void setSpeed(double speed){
        this.speed = speed;
    }

    public int getRadius(){
        return radius;
    }
    public void setRadius(int radius){
        this.radius = radius;
    }

    public double getWeakness(){
        return weakness;
    }
    public void setWeakness(double weakness){
        this.weakness = weakness;
    }

    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }

    public List<Cell> getNeighbors(){
        return neighbors;
    }
}