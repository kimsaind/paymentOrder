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
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

public class SignatureUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String P12_FILE_A = "CA_a10.p12";
    private static final String P12_PASSWORD_A = "";
    private static final String AUTH_ID_A = "1526fa6a-4388-4234-99e5-846ba2c6e328";

    private static final String P12_FILE_B = "CA_b10.p12";
    private static final String P12_PASSWORD_B = "";
    private static final String AUTH_ID_B = "72d9c963-4a20-40f3-bef1-9b773369826b";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String generateAuthorizationString(Object requestBody, String authorizationId) throws Exception {
        JsonNode rootNode = mapper.valueToTree(requestBody);
        JsonNode requestParameters = rootNode.path("requestParameters");
        JsonNode data = requestParameters.path("data");
        String plainText = hashDataWithSha256(data.toString());

        if (AUTH_ID_A.equals(authorizationId)) {
            return signData(plainText, P12_FILE_A, P12_PASSWORD_A);
        } else if (AUTH_ID_B.equals(authorizationId)) {
            return signData(plainText, P12_FILE_B, P12_PASSWORD_B);
        } else {
            throw new IllegalArgumentException("Unknown authorizationId: " + authorizationId);
        }
    }

    private static String hashDataWithSha256(String input) {
        try {
            String encBase64 = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
            LoggerUtil.info("Base64 Data: {}", encBase64);
            String hexData = SHA256(encBase64);
            LoggerUtil.info("SHA-256 Hex Data: {}", hexData);
            return hexData;
        } catch (Exception ex) {
            LoggerUtil.error("Error in hashDataWithSha256: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private static String SHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
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
            LoggerUtil.error("Error in SHA256: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private static String signData(String plainText, String p12FilePath, String password) {
        try {
            InputStream p12Stream = SignatureUtil.class.getClassLoader().getResourceAsStream(p12FilePath);
            if (p12Stream == null) {
                throw new IllegalArgumentException("Cannot find P12 file at: " + p12FilePath);
            }
            var keyPair = getCertKeys(p12Stream, password);
            p12Stream.close();
            String signature = sign(plainText, (PrivateKey) keyPair.get("PrivateKey"), (X509Certificate) keyPair.get("X509Certificate"));
            LoggerUtil.info("Generated Signature for {}: {}", keyPair.get("Alias"), signature);
            return signature;
        } catch (Exception ex) {
            LoggerUtil.error("Error in signData: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private static HashMap<String, Object> getCertKeys(InputStream cerFileStream, String password) throws Exception {
        String newPassword = password != null ? password : "";
        HashMap<String, Object> keyPair = new HashMap<>();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(cerFileStream, newPassword.toCharArray());
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, newPassword.toCharArray());
        X509Certificate x509Certificate = (X509Certificate) keyStore.getCertificate(alias);
        PublicKey publicKey = x509Certificate.getPublicKey();
        keyPair.put("Alias", alias);
        keyPair.put("PublicKey", publicKey);
        keyPair.put("PrivateKey", privateKey);
        keyPair.put("X509Certificate", x509Certificate);
        return keyPair;
    }

    private static String sign(String plainText, PrivateKey privateKey, X509Certificate certificate) {
        try {
            byte[] dataToSign = plainText.getBytes(StandardCharsets.UTF_16LE);
            List<Object> certList = new ArrayList<>();
            certList.add(certificate);
            Store certs = new JcaCertStore(certList);
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA")
                    .build(privateKey);
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .build(sha1Signer, certificate));
            gen.addCertificates(certs);
            CMSTypedData msg = new CMSProcessableByteArray(dataToSign);
            CMSSignedData sigData = gen.generate(msg, true);
            byte[] envelopedData = Base64.getEncoder().encode(sigData.getEncoded());
            return new String(envelopedData);
        } catch (Exception ex) {
            LoggerUtil.error("Error in sign: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}