package com.ufis.config;

import datomic.Peer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatomicConfig {
    @Value("${datomic.uri}")
    private String datomicUri;

    @Bean
    public datomic.Connection datomicConnection() {
        Peer.createDatabase(datomicUri);
        return Peer.connect(datomicUri);
    }
}