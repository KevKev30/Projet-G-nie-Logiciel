package models;

public class SimulationConfig {

    // ── Paramètres épidémiologiques COVID-19 ──────────────────────────────────
    // β  : taux de transmission       → R0 ≈ 2.5–3.5 pour le SARS-CoV-2
    // σ  : taux d'incubation          → période d'incubation moyenne ~7 jours  (1/7 ≈ 0.143)
    // γ  : taux de guérison           → durée de la maladie ~14 jours          (1/14 ≈ 0.071)
    // μ  : taux de mortalité          → létalité COVID sans vaccination ≈ 2 %
    // α  : taux d'asymptomatiques     → ~40 % des infectés ne montrent pas de symptômes

    private double transmissionRate;    // β
    private double incubationRate;      // σ
    private double recoveryRate;        // γ
    private double mortalityRate;       // μ
    private double asymptomaticRate;    // α
    private double speedFactor;

    public SimulationConfig(double transmissionRate, double incubationRate, double recoveryRate) {
        this.transmissionRate   = transmissionRate;
        this.incubationRate     = incubationRate;
        this.recoveryRate       = recoveryRate;
        this.mortalityRate      = 0.02;   // 2 % de létalité
        this.asymptomaticRate   = 0.40;   // 40 % d'asymptomatiques
        this.speedFactor        = 1.0;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

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

    public double getMortalityRate() {
        return mortalityRate;
    }
    public void setMortalityRate(double mortalityRate) {
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

