package models;

public class SimulationConfig {
    private double transmissionRate;
    private double incubationRate;
    private double recoveryRate;
    private double speedFactor;

    public SimulationConfig(double transmissionRate, double incubationRate, double recoveryRate) {
        this.transmissionRate = transmissionRate;
        this.incubationRate = incubationRate;
        this.recoveryRate = recoveryRate;
        this.speedFactor = 1.0;
    }

    public double getTransmissionRate() { 
        return transmissionRate; 
    }

    public void setTransmissionRate(double transmissionRate) { 
        this.transmissionRate = transmissionRate; 
    }



    public double getIncubationRate() { 
        return incubationRate; 
    
    }

    public void setIncubationRate(double incubationRate) { 
        this.incubationRate = incubationRate; 
    }

    public double getRecoveryRate() { 
        return recoveryRate; 
    
    }
    public void setRecoveryRate(double recoveryRate) { 
        this.recoveryRate = recoveryRate; 
    }

    public double getSpeedFactor() { 
        return speedFactor; 
    }

    public void setSpeedFactor(double speedFactor) { 
        this.speedFactor = speedFactor; 
    }
}