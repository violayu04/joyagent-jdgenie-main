package com.jd.genie.agent.util;


import com.jd.genie.agent.dto.File;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
public class FileUtil {

    /**
     * 格式化文件信息
     *
     * @param files
     * @return
     */
    public static String formatFileInfo(List<File> files, Boolean filterInternalFile) {
        StringBuilder stringBuilder = new StringBuilder();
        for (File file : files) {
            if (filterInternalFile && file.getIsInternalFile()) {
                // log.info("filter file {}", file);
                continue;
            }
            stringBuilder.append(String.format("fileName:%s fileDesc:%s fileUrl:%s\n",
                    file.getFileName(), file.getDescription(),
                    Objects.nonNull(file.getOriginOssUrl()) && !file.getOriginOssUrl().isEmpty() ? file.getOriginOssUrl() : file.getOssUrl()));
        }
        return stringBuilder.toString();
    }
}
