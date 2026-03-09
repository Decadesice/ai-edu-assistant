package com.syh.chat.dto;

public class StatsOverviewResponse {
    private long totalAttempts;
    private long correctAttempts;
    private long wrongAttempts;
    private double accuracy;

    public StatsOverviewResponse() {
    }

    public StatsOverviewResponse(long totalAttempts, long correctAttempts, long wrongAttempts, double accuracy) {
        this.totalAttempts = totalAttempts;
        this.correctAttempts = correctAttempts;
        this.wrongAttempts = wrongAttempts;
        this.accuracy = accuracy;
    }

    public long getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(long totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public long getCorrectAttempts() {
        return correctAttempts;
    }

    public void setCorrectAttempts(long correctAttempts) {
        this.correctAttempts = correctAttempts;
    }

    public long getWrongAttempts() {
        return wrongAttempts;
    }

    public void setWrongAttempts(long wrongAttempts) {
        this.wrongAttempts = wrongAttempts;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
}


