package com.archlens.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("test-secret-key-for-unit-testing-32bytes");
    }

    @Test
    void testEncryptAndDecryptRoundtrip() {
        String token = "ghp_1234567890abcdefghijklmnopqrstuvwxyz";
        byte[] encrypted = encryptionService.encrypt(token);

        assertNotNull(encrypted);
        assertNotEquals(0, encrypted.length);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(token, decrypted);
    }

    @Test
    void testTamperedCiphertextThrowsException() {
        String token = "ghp_secure_github_token";
        byte[] encrypted = encryptionService.encrypt(token);

        // Tamper with last byte (part of authentication tag)
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(encrypted));
    }

    @Test
    void testNullHandling() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
        assertNull(encryptionService.decrypt(new byte[5])); // shorter than IV
    }
}
