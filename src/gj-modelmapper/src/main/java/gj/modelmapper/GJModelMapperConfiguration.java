package gj.modelmapper;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GJModelMapperConfiguration {
    @Bean
    @Qualifier("mainModelMapper")
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                //禁用字段直接匹配
                .setFieldMatchingEnabled(false)
                //启用跳过 null 值
                .setSkipNullEnabled(true)
                //禁用集合合并，改为集合替换
                .setCollectionsMergeEnabled(false)
                //要求完全的类型匹配（包括泛型）
                .setFullTypeMatchingRequired(true)
                //启用隐式映射（自动匹配）
                .setImplicitMappingEnabled(true)
                //优先匹配嵌套属性
                .setPreferNestedProperties(true);
        return modelMapper;
    }
}
