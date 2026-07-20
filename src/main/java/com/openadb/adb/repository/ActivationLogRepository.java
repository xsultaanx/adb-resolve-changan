package com.openadb.adb.repository;

import com.openadb.adb.entity.ActivationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivationLogRepository extends JpaRepository<ActivationLog, Long> {

    List<ActivationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
