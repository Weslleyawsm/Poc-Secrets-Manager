package com.example.secrets_demo.config.utils;

import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

public class SetHikariCP {
    public SetHikariCP() {
    }

    public static HikariDataSource setHikariCP(String jdbcUrl, Map<String,String> dados) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(dados.get("username"));
        ds.setPassword(dados.get("password"));
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return ds;
    }
}
