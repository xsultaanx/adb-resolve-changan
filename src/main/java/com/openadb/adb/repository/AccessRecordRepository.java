package com.openadb.adb.repository;

import com.openadb.adb.entity.AccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccessRecordRepository extends JpaRepository<AccessRecord, Long> {

    Optional<AccessRecord> findByVin(String vin);

    Optional<AccessRecord> findByVinAndExpiresAtAfter(String vin, LocalDateTime now);

    boolean existsByVinAndExpiresAtAfter(String vin, LocalDateTime now);
}
