package com.ufis.repository;

import datomic.Connection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatomicSchemaLoader {
    private final Connection connection;

    @PostConstruct
    public void loadSchema() throws ExecutionException, InterruptedException {
        log.info("Transacting Datomic schema...");
        connection.transact(DatomicSchema.allSchema()).get();
        log.info("Transacting Datomic enum idents...");
        connection.transact(DatomicSchema.allEnums()).get();
        log.info("Datomic schema loaded successfully.");
    }

    public Connection getConnection() {
        return connection;
    }
}
