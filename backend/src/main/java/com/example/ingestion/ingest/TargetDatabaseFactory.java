package com.example.ingestion.ingest;

import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.DataSourceConfig;
import com.example.ingestion.security.CryptoService;
import org.springframework.stereotype.Component;

@Component
public class TargetDatabaseFactory {
    private final CryptoService crypto;
    private final Jsons jsons;

    public TargetDatabaseFactory(CryptoService crypto, Jsons jsons) {
        this.crypto = crypto;
        this.jsons = jsons;
    }

    public TargetDatabase create(DataSourceConfig profile) {
        return new TargetDatabase(profile, crypto, jsons);
    }
}
