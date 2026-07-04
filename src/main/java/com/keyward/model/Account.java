package com.keyward.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Account {
    private String id;
    private String username;
    private String premiumUuid;
    private String passwordHash;
    private String accountType;
    private String status;
    private String totpSecret;
    private String backupCodeHashes;
    private long createdAt;
    private long lastLoginAt;
    private String fixedUniqueId;
}