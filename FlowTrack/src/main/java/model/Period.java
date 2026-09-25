package model;

import java.time.LocalDate;

public class Period {
    private Long id;
    private LocalDate startDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
}