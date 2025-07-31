package com.jd.genie.agent.dto.tool;

import lombok.Data;

/**
 * 工具类
 */
@Data
public class Tool {
    private String name;           // 工具名称
    private String description;    // 工具描述
    private Object parameters;     // 工具参数
}