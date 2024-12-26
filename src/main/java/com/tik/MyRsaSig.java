package com.tik;

import com.binance.connector.client.utils.ParameterChecker;
import com.binance.connector.client.utils.signaturegenerator.SignatureGenerator;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class MyRsaSig implements SignatureGenerator {
    private static final String RSA_SHA256 = "SHA256withRSA";
    private static final String RSA = "RSA";
    private RSAPrivateKey privateKey;

    public MyRsaSig(String privateKey) {
        this(privateKey, (String)null);
    }

    public MyRsaSig(String privateKey, String password) {
        ParameterChecker.checkParameterType(privateKey, String.class, "privateKey");

        try {
            String privateKeyPem = privateKey;
            if ((new File(privateKey)).exists()) {
                privateKeyPem = new String(Files.readAllBytes(Paths.get(privateKey)));
            }

            this.privateKey = this.parsePrivateKey(privateKeyPem, password);
        } catch (IOException e) {
            throw new RuntimeException("Unable to find/read private key at given file path", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RSA private key", e);
        }
    }

    public String getSignature(String data) {
        try {
            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(this.privateKey);
            privateSignature.update(data.getBytes());
            byte[] signature = privateSignature.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate rsa-sha256", e);
        }
    }

    private RSAPrivateKey parsePrivateKey(String privateKeyPem, String password) throws Exception {
        String parsedPem = privateKeyPem.replace("\n", "").trim();
        if (password != null) {
            parsedPem = parsedPem.replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "").replace("-----END ENCRYPTED PRIVATE KEY-----", "");
        } else {
            parsedPem = parsedPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");
        }

        byte[] encoded = Base64.getDecoder().decode(parsedPem);
        PKCS8EncodedKeySpec encodedKeySpec;
        if (password != null) {
            EncryptedPrivateKeyInfo pkInfo = new EncryptedPrivateKeyInfo(encoded);
            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray());
            SecretKeyFactory pbeKeyFactory = SecretKeyFactory.getInstance(pkInfo.getAlgName());
            encodedKeySpec = pkInfo.getKeySpec(pbeKeyFactory.generateSecret(keySpec));
        } else {
            encodedKeySpec = new PKCS8EncodedKeySpec(encoded);
        }

        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey)kf.generatePrivate(encodedKeySpec);
    }
}
