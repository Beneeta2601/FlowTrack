package model;

import java.time.LocalDate;
import java.util.List;

public class User {
    private Long id;
    private String username;
    private String password;
    private Integer age;
    private String goal;
    private LocalDate lastPeriodStart;
    private Integer periodLength;
    private Integer cycleLength;
    private Boolean cycleLengthKnown;
    private Boolean cycleLengthVaries;
    private List<Period> periods;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public LocalDate getLastPeriodStart() { return lastPeriodStart; }
    public void setLastPeriodStart(LocalDate lastPeriodStart) { this.lastPeriodStart = lastPeriodStart; }
    public Integer getPeriodLength() { return periodLength; }
    public void setPeriodLength(Integer periodLength) { this.periodLength = periodLength; }
    public Integer getCycleLength() { return cycleLength; }
    public void setCycleLength(Integer cycleLength) { this.cycleLength = cycleLength; }
    public Boolean getCycleLengthKnown() { return cycleLengthKnown; }
    public void setCycleLengthKnown(Boolean cycleLengthKnown) { this.cycleLengthKnown = cycleLengthKnown; }
    public Boolean getCycleLengthVaries() { return cycleLengthVaries; }
    public void setCycleLengthVaries(Boolean cycleLengthVaries) { this.cycleLengthVaries = cycleLengthVaries; }
    public List<Period> getPeriods() { return periods; }
    public void setPeriods(List<Period> periods) { this.periods = periods; }
}