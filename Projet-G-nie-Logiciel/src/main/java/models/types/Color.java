package models.types;

public enum Color {
    GREEN("Green"), 
    ORANGE("Orange"), 
    RED("Red");

    private final String color;

    Color(String color){
        this.color = color;
    }

    public String getColor(){
        return this.color;
    }
}
