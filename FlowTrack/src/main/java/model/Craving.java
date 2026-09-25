package model;

import java.time.LocalDate;

public class Craving {
    private Long id;
    private String text;
    private Boolean satisfied;
    private LocalDate createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Boolean getSatisfied() { return satisfied; }
    public void setSatisfied(Boolean satisfied) { this.satisfied = satisfied; }
    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}