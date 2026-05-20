package com.orgasm.backend.config;

import com.orgasm.backend.tenant.CurrentTenantResolver;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.orgasm.backend",
        entityManagerFactoryRef = "appEntityManagerFactory",
        transactionManagerRef = "appTransactionManager"
)
public class AppJpaConfig {

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean appEntityManagerFactory(
            @Qualifier("appDataSource") DataSource dataSource,
            EntityManagerFactoryBuilder builder,
            @Autowired(required = false) @Qualifier("appFlyway") Flyway appFlyway,
            CurrentTenantResolver currentTenantResolver) {
        return builder
                .dataSource(dataSource)
                .packages("com.orgasm.backend")
                .persistenceUnit("app")
                .properties(Map.of("hibernate.tenant_identifier_resolver", currentTenantResolver))
                .build();
    }

    @Bean
    @Primary
    PlatformTransactionManager appTransactionManager(
            @Qualifier("appEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
