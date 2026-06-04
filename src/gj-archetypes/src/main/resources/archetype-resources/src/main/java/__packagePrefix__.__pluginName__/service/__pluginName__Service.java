package ${packagePrefix}.${pluginName}.service;

import ${packagePrefix}.${pluginName}.response.${pluginName}Response;
import iotcenter.common.OperateResult;

import java.util.List;

public interface ${pluginName}Service {
    OperateResult<List<${pluginName}Response>> getList();
}
