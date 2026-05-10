package com.example.upload_service.testsupport;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class UploadedFileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String secureUrl;

    private String publicId;

    protected UploadedFileRecord() {
    }

    public UploadedFileRecord(String secureUrl, String publicId) {
        this.secureUrl = secureUrl;
        this.publicId = publicId;
    }

    public Long getId() {
        return id;
    }

    public String getSecureUrl() {
        return secureUrl;
    }

    public String getPublicId() {
        return publicId;
    }
}
