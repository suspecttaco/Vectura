package com.vectura.server.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class HashUtils {
    // Calculo de SHA-256 de cualquier archivo
    public static String calculateSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Leer archivo en bloques
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192]; // Bloques de 8KB
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        // Convertir bytes a hexadecimal
        return HexFormat.of().formatHex(hashBytes);
    }
}
