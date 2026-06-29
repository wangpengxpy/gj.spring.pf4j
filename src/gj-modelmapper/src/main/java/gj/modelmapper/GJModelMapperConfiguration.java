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
                .setFieldMatchingEnabled(false)
                .setSkipNullEnabled(true)
                .setCollectionsMergeEnabled(false)
                .setFullTypeMatchingRequired(true)
                .setImplicitMappingEnabled(true)
                .setPreferNestedProperties(true);
        return modelMapper;
    }
}
