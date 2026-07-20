package com.openadb.adb.repository;

import com.openadb.adb.entity.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
}
