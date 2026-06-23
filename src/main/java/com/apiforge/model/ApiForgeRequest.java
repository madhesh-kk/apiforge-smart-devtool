package com.apiforge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiForgeRequest {
    private String docsUrl;
    private String useCase;
    private String language;
}
