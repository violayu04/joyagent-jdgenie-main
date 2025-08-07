package com.jd.genie.dto.knowledgebase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SearchRequest {
    
    @NotBlank(message = "查询内容不能为空")
    private String query;
    
    @Min(value = 1, message = "返回结果数量至少为1")
    @Max(value = 50, message = "返回结果数量不能超过50")
    private Integer topK = 10;
}