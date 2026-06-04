package ${packagePrefix}.${pluginName}.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ${packagePrefix}.${pluginName}.dao.${pluginName}Mapper;
import ${packagePrefix}.${pluginName}.model.Test;
import ${packagePrefix}.${pluginName}.response.${pluginName}Response;
import ${packagePrefix}.${pluginName}.service.${pluginName}Service;
import iotcenter.common.OperateResult;
import iotcenter.pf4j.modelmapper.IoTModelMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
public class ${pluginName}ServiceImpl implements ${pluginName}Service {

    private final ${pluginName}Mapper pluginMapper;

    @Autowired
    public ${pluginName}ServiceImpl(${pluginName}Mapper pluginMapper) {
        this.pluginMapper = pluginMapper;
    }

    @Override
    public OperateResult<List<${pluginName}Response>> getList() {
        LambdaQueryWrapper<Test> queryWrapper = Wrappers.lambdaQuery();
        var pluginQuery = pluginMapper.selectList(queryWrapper);
        List<${pluginName}Response> plugnlist = pluginQuery.stream().map(${pluginName}ServiceImpl::apply).toList();
        return OperateResult.success(plugnlist);
    }

    private static ${pluginName}Response apply(Test d) {
        return IoTModelMapperUtils.mapTo(d, ${pluginName}Response.class);
    }
}
