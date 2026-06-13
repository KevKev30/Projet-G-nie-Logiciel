package models;

import exceptions.InvalidParameterException;

public class SimulationConfig {

    // ── COVID-19 Epidemiological Parameters ───────────────────────────────────
    // β  : transmission rate          → R0 ≈ 2.5–3.5 for SARS-CoV-2
    // σ  : incubation rate            → average incubation period ~7 days  (1/7 ≈ 0.143)
    // γ  : recovery rate              → disease duration ~14 days          (1/14 ≈ 0.071)
    // μ  : mortality rate             → COVID fatality rate without vaccination ≈ 2%
    // α  : asymptomatic rate          → ~40% of infected show no symptoms

    private double transmissionRate;    // β
    private double incubationRate;      // σ
    private double recoveryRate;        // γ
    private double mortalityRate;       // μ
    private double asymptomaticRate;    // α
    private double speedFactor;

    public SimulationConfig(double transmissionRate, double incubationRate, double recoveryRate) { //here to change the default values of SEIR
        this.transmissionRate   = transmissionRate;
        this.incubationRate     = incubationRate;
        this.recoveryRate       = recoveryRate;
        this.mortalityRate      = 0.02;   // μ  : mortality rate 
        this.asymptomaticRate   = 0.40;   // α  : asymptomatic rate  
        this.speedFactor        = 1.0;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public double getTransmissionRate() {
        return transmissionRate;
    }
    /**
     * @throws IllegalArgumentException if value is not in [0.0, 1.0]
     */
    /**
     * @throws InvalidParameterException if value is not in [0.0, 1.0]
     */
    public void setTransmissionRate(double transmissionRate) {
        if (transmissionRate < 0.0 || transmissionRate > 1.0)
            throw new InvalidParameterException("transmissionRate", String.valueOf(transmissionRate),
                "Transmission rate must be in [0.0, 1.0], got: " + transmissionRate);
        this.transmissionRate = transmissionRate;
    }

    public double getIncubationRate() {
        return incubationRate;
    }
    /**
     * @throws IllegalArgumentException if value is not in [0.0, 1.0]
     */
    /**
     * @throws InvalidParameterException if value is not in [0.0, 1.0]
     */
    public void setIncubationRate(double incubationRate) {
        if (incubationRate < 0.0 || incubationRate > 1.0)
            throw new InvalidParameterException("incubationRate", String.valueOf(incubationRate),
                "Incubation rate must be in [0.0, 1.0], got: " + incubationRate);
        this.incubationRate = incubationRate;
    }

    public double getRecoveryRate() {
        return recoveryRate;
    }
    /**
     * @throws IllegalArgumentException if value is not in [0.0, 1.0]
     */
    /**
     * @throws InvalidParameterException if value is not in [0.0, 1.0]
     */
    public void setRecoveryRate(double recoveryRate) {
        if (recoveryRate < 0.0 || recoveryRate > 1.0)
            throw new InvalidParameterException("recoveryRate", String.valueOf(recoveryRate),
                "Recovery rate must be in [0.0, 1.0], got: " + recoveryRate);
        this.recoveryRate = recoveryRate;
    }

    public double getMortalityRate() {
        return mortalityRate;
    }
    /**
     * @throws IllegalArgumentException if value is not in [0.0, 1.0]
     */
    /**
     * @throws InvalidParameterException if value is not in [0.0, 1.0]
     */
    public void setMortalityRate(double mortalityRate) {
        if (mortalityRate < 0.0 || mortalityRate > 1.0)
            throw new InvalidParameterException("mortalityRate", String.valueOf(mortalityRate),
                "Mortality rate must be in [0.0, 1.0], got: " + mortalityRate);
        this.mortalityRate = mortalityRate;
    }

    public double getAsymptomaticRate() {
        return asymptomaticRate;
    }
    public void setAsymptomaticRate(double asymptomaticRate) {
        this.asymptomaticRate = asymptomaticRate;
    }

    public double getSpeedFactor() {
        return speedFactor;
    }
    public void setSpeedFactor(double speedFactor) {
        this.speedFactor = speedFactor;
    }
}
