package com.apiforge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndpointInfo {
    private String endpoint;
    private String method;
    private String description;
}
