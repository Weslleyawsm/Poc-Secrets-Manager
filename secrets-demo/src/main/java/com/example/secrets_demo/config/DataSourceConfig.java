package com.example.secrets_demo.config;

import com.example.secrets_demo.config.utils.DeserializaJson;
import com.example.secrets_demo.config.utils.SetHikariCP;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Value("${aws.secretsmanager.secret-name}")
    private String secretName;

    @Value("${aws.secretsmanager.region}")
    private String region;

    @Bean
    public DataSource dataSource() throws Exception {

        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse response = client.getSecretValue(request);

        Map<String, String> dados = DeserializaJson.getDadosAcesso(response.secretString());

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                dados.get("host"), dados.get("port"), dados.get("dbname")
        );

        HikariDataSource ds = SetHikariCP.setHikariCP(jdbcUrl, dados);
        return ds;
    }
}