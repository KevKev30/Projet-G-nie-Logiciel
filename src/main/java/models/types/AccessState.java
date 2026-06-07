package models.types;

public enum AccessState {
    OPEN("Open"),
    RESTRICTED("Restricted"),
    BARRICATED("Barricated");

    private final String accessState;

    AccessState(String state){
        this.accessState = state;
    }

    public String getAccessState(){
        return this.accessState;
    }
}
