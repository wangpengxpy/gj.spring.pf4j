package gj.demo;

import gj.modelmapper.GJModelMapperConfig;
import gj.modelmapper.GJModelMapperScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
})
@ComponentScan("gj")
@MapperScan("gj.data.dao")
@GJModelMapperScan(basePackages = {"gj.data.test"}, markerInterface = GJModelMapperConfig.class)
@EnableConfigurationProperties
@EnableCaching
public final class GJApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(GJApplication.class)
//                .web(WebApplicationType.REACTIVE)
                .run(args);
    }
}
