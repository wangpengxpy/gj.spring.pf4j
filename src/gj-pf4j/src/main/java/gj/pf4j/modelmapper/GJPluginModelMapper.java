package gj.pf4j.modelmapper;

import org.modelmapper.ModelMapper;

public class GJPluginModelMapper {
    public ModelMapper build() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                // Disable direct field matching
                .setFieldMatchingEnabled(false)
                // Enable null value skipping
                .setSkipNullEnabled(true)
                // Disable collection merging, use replacement instead
                .setCollectionsMergeEnabled(false)
                // Require full type matching (including generics)
                .setFullTypeMatchingRequired(true)
                // Enable implicit mapping (automatic matching)
                .setImplicitMappingEnabled(true)
                // Prioritize nested property matching
                .setPreferNestedProperties(true);
        return modelMapper;
    }
}
