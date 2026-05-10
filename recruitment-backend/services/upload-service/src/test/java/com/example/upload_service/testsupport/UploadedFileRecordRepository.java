package com.example.upload_service.testsupport;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRecordRepository extends JpaRepository<UploadedFileRecord, Long> {

    Optional<UploadedFileRecord> findBySecureUrl(String secureUrl);
}
