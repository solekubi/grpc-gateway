package com.esquel.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author zhangjikai
 * Created on 2018-12-16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrpcMethodDefinition {
    private String packageName;
    private String serviceName;
    private String methodName;

    public String getFullServiceName() {
        if (isNotBlank(packageName)) {
            return packageName + "." + serviceName;
        }
        return serviceName;
    }

    public String getFullMethodName() {
        if (isNotBlank(packageName)) {
            return packageName + "." + serviceName + "/" + methodName;
        }
        return serviceName + "/" + methodName;
    }
}
