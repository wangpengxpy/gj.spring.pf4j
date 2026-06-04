package gj.modelmapper;

import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(GJModelMapperScannerRegistrar.class)
public @interface GJModelMapperScan {
    @AliasFor("basePackages")
    String[] value() default {};
    @AliasFor("value")
    String[] basePackages() default {};
    Class<?>[] basePackageClasses() default {};
    Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;
    Class<? extends Annotation> annotationClass() default Annotation.class;
    Class<?> markerInterface() default Class.class;
    String defaultScope() default "";
}
