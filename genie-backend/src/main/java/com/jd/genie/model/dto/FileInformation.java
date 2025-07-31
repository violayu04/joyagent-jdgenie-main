package com.jd.genie.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInformation {
    private String fileName;
    private String fileDesc;
    private String ossUrl;
    private String domainUrl;
    private Integer fileSize;
    private String fileType;
    private String originFileName;
    private String originFileUrl;
    private String originOssUrl;
    private String originDomainUrl;
}