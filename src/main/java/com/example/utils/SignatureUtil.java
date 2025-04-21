package com.example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SignatureUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String CER_FILE_A  = "075192000178_1.cer";
    private static final String CER_FILE_B  = "075192000178_2.cer";
    private static final String KEY_FILE    = "075192000178.key";
    private static final String KEY_PASSWORD = "12345678";

    private static final String AUTH_ID_A   = "1526fa6a-4388-4234-99e5-846ba2c6e328";
    private static final String AUTH_ID_B   = "72d9c963-4a20-40f3-bef1-9b773369826b";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String generateAuthorizationString(Object requestBody, String authorizationId) throws Exception {
        if (requestBody == null) {
            throw new IllegalArgumentException("requestBody cannot be null");
        }
        JsonNode rootNode = mapper.valueToTree(requestBody);
        JsonNode requestParameters = rootNode.path("requestParameters");
        if (requestParameters.isMissingNode()) {
            throw new IllegalArgumentException("requestParameters is missing in requestBody");
        }
        JsonNode data = requestParameters.path("data");
        if (data.isMissingNode()) {
            throw new IllegalArgumentException("data is missing in requestParameters");
        }
        String dataString = data.toString();
        if (dataString == null || dataString.trim().isEmpty()) {
            throw new IllegalArgumentException("data.toString() is null or empty");
        }
        String plainText = hashDataWithSha256(dataString);

        if (AUTH_ID_A.equals(authorizationId)) {
            return signDataUsingCertAndKey(plainText, CER_FILE_A, KEY_FILE, KEY_PASSWORD);
        } else if (AUTH_ID_B.equals(authorizationId)) {
            return signDataUsingCertAndKey(plainText, CER_FILE_B, KEY_FILE, KEY_PASSWORD);
        } else {
            throw new IllegalArgumentException("Unknown authorizationId: " + authorizationId);
        }
    }

    private static String hashDataWithSha256(String input) {
        try {
            if (input == null) {
                throw new IllegalArgumentException("Input for hashDataWithSha256 cannot be null");
            }
            String encBase64 = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
            LoggerUtil.info("Base64 Data: {}", encBase64);
            String hexData = computeSHA256(encBase64);
            LoggerUtil.info("SHA-256 Hex Data: {}", hexData);
            return hexData;
        } catch (Exception ex) {
            LoggerUtil.error("Error in hashDataWithSha256: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private static String computeSHA256(String input) {
        try {
            if (input == null) {
                throw new IllegalArgumentException("Input for computeSHA256 cannot be null");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            LoggerUtil.error("Error in computeSHA256: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    /**
     * Sinh chữ ký số sử dụng certificate từ file .cer và private key từ file .key dùng chung.
     */
    private static String signDataUsingCertAndKey(String plainText, String certFilePath, String keyFilePath, String keyPassword) {
        try {
            if (plainText == null) {
                throw new IllegalArgumentException("plainText cannot be null");
            }
            // Load certificate từ file .cer
            X509Certificate certificate = loadCertificate(certFilePath);
            // Load private key từ file .key dùng chung
            PrivateKey privateKey = loadPrivateKey(keyFilePath, keyPassword);
            String signature = sign(plainText, privateKey, certificate);
            LoggerUtil.info("Generated Signature for {}: {}", certificate.getSubjectDN(), signature);
            return signature;
        } catch (Exception ex) {
            LoggerUtil.error("Error in signDataUsingCertAndKey: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    /**
     * Load certificate từ file .cer sử dụng CertificateFactory.
     */
    private static X509Certificate loadCertificate(String certFilePath) throws Exception {
        InputStream certStream = SignatureUtil.class.getClassLoader().getResourceAsStream(certFilePath);
        if (certStream == null) {
            throw new IllegalArgumentException("Cannot find certificate file at: " + certFilePath);
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(certStream);
        } finally {
            certStream.close();
        }
    }

    /**
     * Load private key từ file .key (hoặc .pem) sử dụng PEMParser của BouncyCastle.
     */
    private static PrivateKey loadPrivateKey(String keyFilePath, String keyPassword) throws Exception {
        InputStream keyStream = SignatureUtil.class.getClassLoader().getResourceAsStream(keyFilePath);
        if (keyStream == null) {
            throw new IllegalArgumentException("Cannot find private key file at: " + keyFilePath);
        }
        try (Reader reader = new InputStreamReader(keyStream);
             PEMParser pemParser = new PEMParser(reader)) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (object instanceof PEMEncryptedKeyPair) {
                PEMEncryptedKeyPair encKeyPair = (PEMEncryptedKeyPair) object;
                PEMKeyPair keyPair = encKeyPair.decryptKeyPair(
                        new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray()));
                return converter.getKeyPair(keyPair).getPrivate();
            } else if (object instanceof PEMKeyPair) {
                PEMKeyPair keyPair = (PEMKeyPair) object;
                return converter.getKeyPair(keyPair).getPrivate();
            } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Unsupported private key format in file: " + keyFilePath);
            }
        }
    }

    /**
     * Ký dữ liệu sử dụng BouncyCastle CMS (PKCS#7) với thuật toán SHA1withRSA.
     */
    private static String sign(String plainText, PrivateKey privateKey, X509Certificate certificate) {
        try {
            if (plainText == null) {
                throw new IllegalArgumentException("plainText cannot be null in sign method");
            }
            byte[] dataToSign = plainText.getBytes(StandardCharsets.UTF_16LE);
            List<X509Certificate> certList = new ArrayList<>();
            certList.add(certificate);
            Store certs = new JcaCertStore(certList);
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA")
                    .setProvider("BC")
                    .build(privateKey);
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .build(sha1Signer, certificate));
            gen.addCertificates(certs);
            CMSTypedData msg = new CMSProcessableByteArray(dataToSign);
            CMSSignedData sigData = gen.generate(msg, true);
            byte[] encoded = Base64.getEncoder().encode(sigData.getEncoded());
            return new String(encoded);
        } catch (Exception ex) {
            LoggerUtil.error("Error in sign: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
