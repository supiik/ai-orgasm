package com.etactics.proxima.service.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.etactics.proxima.service.backend.repository.billing",
        entityManagerFactoryRef = "billingEntityManagerFactory",
        transactionManagerRef = "billingTransactionManager"
)
public class BillingJpaConfig {

    @Bean
    LocalContainerEntityManagerFactoryBean billingEntityManagerFactory(
            @Qualifier("billingDataSource") DataSource dataSource,
            EntityManagerFactoryBuilder builder,
            @Autowired(required = false) @Qualifier("billingFlyway") Flyway billingFlyway) {
        return builder
                .dataSource(dataSource)
                .packages("com.etactics.proxima.service.backend.domain.billing")
                .persistenceUnit("billing")
                .build();
    }

    @Bean
    PlatformTransactionManager billingTransactionManager(
            @Qualifier("billingEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
