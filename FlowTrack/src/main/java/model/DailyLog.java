package model;

import java.time.LocalDate;

public class DailyLog {
    private Long id;
    private LocalDate date;
    private String mood;
    private String energy;
    private String sleep;
    private String discharge;
    private String flow;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getEnergy() { return energy; }
    public void setEnergy(String energy) { this.energy = energy; }
    public String getSleep() { return sleep; }
    public void setSleep(String sleep) { this.sleep = sleep; }
    public String getDischarge() { return discharge; }
    public void setDischarge(String discharge) { this.discharge = discharge; }
    public String getFlow() { return flow; }
    public void setFlow(String flow) { this.flow = flow; }
}