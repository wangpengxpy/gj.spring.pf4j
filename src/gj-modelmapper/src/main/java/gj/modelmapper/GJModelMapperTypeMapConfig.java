package gj.modelmapper;

import lombok.Getter;
import org.modelmapper.TypeMap;

import java.util.function.Consumer;

public class GJModelMapperTypeMapConfig {
    @Getter
    private final Class<?> sourceType;
    @Getter
    private final Class<?> destinationType;
    @Getter
    private final Consumer<TypeMap<?, ?>> mappingConfigurer;

    private GJModelMapperTypeMapConfig(Class<?> sourceType, Class<?> destinationType, Consumer<TypeMap<?, ?>> mappingConfigurer) {
        this.sourceType = sourceType;
        this.destinationType = destinationType;
        this.mappingConfigurer = mappingConfigurer;
    }

    public static <S, D> GJModelMapperTypeMapConfig of(Class<S> source, Class<D> destination) {
        return new GJModelMapperTypeMapConfig(source, destination, typeMap -> {});
    }

    public static <S, D> GJModelMapperTypeMapConfig of(Class<S> source, Class<D> destination, Consumer<TypeMap<S, D>> configurer) {
        @SuppressWarnings("unchecked")
        Consumer<TypeMap<?, ?>> unsafeConfigurer = (Consumer<TypeMap<?, ?>>) (Consumer<?>) configurer;
        return new GJModelMapperTypeMapConfig(source, destination, unsafeConfigurer);
    }
}
