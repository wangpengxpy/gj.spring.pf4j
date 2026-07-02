package ${packagePrefix}.${pluginName}.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ${packagePrefix}.${pluginName}.dao.${pluginName}Mapper;
import ${packagePrefix}.${pluginName}.model.Test;
import ${packagePrefix}.${pluginName}.response.${pluginName}Response;
import ${packagePrefix}.${pluginName}.service.${pluginName}Service;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
public class ${pluginName}ServiceImpl implements ${pluginName}Service {

    private final ${pluginName}Mapper pluginMapper;
    private final ModelMapper modelMapper;

    @Autowired
    public ${pluginName}ServiceImpl(${pluginName}Mapper pluginMapper, ModelMapper modelMapper) {
        this.pluginMapper = pluginMapper;
        this.modelMapper = modelMapper;
    }

    @Override
    public List<${pluginName}Response> getList() {
        LambdaQueryWrapper<Test> queryWrapper = Wrappers.lambdaQuery();
        var pluginQuery = pluginMapper.selectList(queryWrapper);
        List<${pluginName}Response> plugnlist = pluginQuery.stream().map(this::apply).toList();
        return plugnlist;
    }

    private ${pluginName}Response apply(Test d) {
        return modelMapper.map(d, ${pluginName}Response.class);
    }
}
