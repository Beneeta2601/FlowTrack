package model;

import java.time.LocalDate;

public class Symptom {
    private Long id;
    private LocalDate date;
    private String symptomName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getSymptomName() { return symptomName; }
    public void setSymptomName(String symptomName) { this.symptomName = symptomName; }
}