package gj.demo.config;

import org.springdoc.core.customizers.SpringDocCustomizers;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.SpringDocProviders;
import org.springdoc.core.service.AbstractRequestService;
import org.springdoc.core.service.GenericResponseService;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.OperationService;
import org.springdoc.webflux.api.MultipleOpenApiWebFluxResource;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

@Configuration
public class SpringDocOpenApiCfg {

    @Bean
    @Lazy
    MultipleOpenApiWebFluxResource multipleOpenApiResource(List<GroupedOpenApi> groupedOpenApis,
                                                          ObjectFactory<OpenAPIService> defaultOpenAPIBuilder,
                                                          AbstractRequestService requestBuilder,
                                                          GenericResponseService responseBuilder,
                                                          OperationService operationParser,
                                                          SpringDocConfigProperties springDocConfigProperties,
                                                          SpringDocProviders springDocProviders,
                                                          SpringDocCustomizers springDocCustomizers) {
        return new MultipleOpenApiWebFluxResource(groupedOpenApis,
                defaultOpenAPIBuilder, requestBuilder,
                responseBuilder, operationParser,
                springDocConfigProperties,
                springDocProviders,
                springDocCustomizers);
    }
}