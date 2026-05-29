package com.moodcopilot.dto;

public class UpdateDiarySortRequest {
    private Double prevSortOrder;
    private Double nextSortOrder;

    public Double getPrevSortOrder() { return prevSortOrder; }
    public void setPrevSortOrder(Double prevSortOrder) { this.prevSortOrder = prevSortOrder; }

    public Double getNextSortOrder() { return nextSortOrder; }
    public void setNextSortOrder(Double nextSortOrder) { this.nextSortOrder = nextSortOrder; }
}