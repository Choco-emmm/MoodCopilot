package com.moodcopilot.dto;


import lombok.Data;

@Data
public class UpdateDiarySortRequest {
    private Double prevSortOrder;
    private Double nextSortOrder;
}