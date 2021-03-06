/*
 * Copyright 2014-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.cryptools.dynamodbencryptionclientsdk2.encryption.providers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.cryptools.dynamodbencryptionclientsdk2.encryption.EncryptionContext;
import software.amazon.cryptools.dynamodbencryptionclientsdk2.encryption.materials.DecryptionMaterials;
import software.amazon.cryptools.dynamodbencryptionclientsdk2.encryption.materials.EncryptionMaterials;
import software.amazon.cryptools.dynamodbencryptionclientsdk2.encryption.materials.WrappedRawMaterials;
import software.amazon.cryptools.dynamodbencryptionclientsdk2.internal.Utils;

public class AsymmetricStaticProviderTest {
    private static KeyPair encryptionPair;
    private static SecretKey macKey;
    private static KeyPair sigPair;
    private Map<String, String> description;
    private EncryptionContext ctx;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048, Utils.getRng());
        sigPair = rsaGen.generateKeyPair();
        encryptionPair = rsaGen.generateKeyPair();
        
        KeyGenerator macGen = KeyGenerator.getInstance("HmacSHA256");
        macGen.init(256, Utils.getRng());
        macKey = macGen.generateKey();
    }
    
    @BeforeMethod
    public void setUp() {
        description = new HashMap<>();
        description.put("TestKey", "test value");
        description = Collections.unmodifiableMap(description);
        ctx = EncryptionContext.builder().build();
    }

    @Test
    public void simpleMac() {
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, macKey, Collections.emptyMap());

        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(macKey, eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(macKey, dMat.getVerificationKey());
    }
    
    @Test
    public void simpleSig() {
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, Collections.emptyMap());

        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    @Test
    public void randomEnvelopeKeys() {
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, macKey, Collections.emptyMap());

        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(macKey, eMat.getSigningKey());
        
        EncryptionMaterials eMat2 = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey2 = eMat2.getEncryptionKey();
        assertEquals(macKey, eMat.getSigningKey());

        assertFalse("Envelope keys must be different", encryptionKey.equals(encryptionKey2));
    }
    
    @Test
    public void testRefresh() {
        // This does nothing, make sure we don't throw and exception.
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, macKey, description);
        prov.refresh();
    }
    
    // Following tests should be moved the WrappedRawMaterialsTests when that is created
    @Test
    public void explicitWrappingAlgorithmPkcs1() throws GeneralSecurityException {
        Map<String, String> desc = new HashMap<>();
        desc.put(WrappedRawMaterials.KEY_WRAPPING_ALGORITHM, "RSA/ECB/PKCS1Padding");
        
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, desc);
        
        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals("RSA/ECB/PKCS1Padding", eMat.getMaterialDescription().get(WrappedRawMaterials.KEY_WRAPPING_ALGORITHM));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    @Test
    public void explicitWrappingAlgorithmPkcs2() throws GeneralSecurityException {
        Map<String, String> desc = new HashMap<>();
        desc.put(WrappedRawMaterials.KEY_WRAPPING_ALGORITHM, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, desc);
        
        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", eMat.getMaterialDescription().get(WrappedRawMaterials.KEY_WRAPPING_ALGORITHM));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    @Test
    public void explicitContentKeyAlgorithm() throws GeneralSecurityException {
        Map<String, String> desc = new HashMap<>();
        desc.put(WrappedRawMaterials.CONTENT_KEY_ALGORITHM, "AES");
        
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, desc);
        
        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals("AES", eMat.getMaterialDescription().get(WrappedRawMaterials.CONTENT_KEY_ALGORITHM));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    @Test
    public void explicitContentKeyLength128() throws GeneralSecurityException {
        Map<String, String> desc = new HashMap<>();
        desc.put(WrappedRawMaterials.CONTENT_KEY_ALGORITHM, "AES/128");
        
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, desc);
        
        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(16, encryptionKey.getEncoded().length); // 128 Bits
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals("AES", eMat.getMaterialDescription().get(WrappedRawMaterials.CONTENT_KEY_ALGORITHM));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    @Test
    public void explicitContentKeyLength256() throws GeneralSecurityException {
        Map<String, String> desc = new HashMap<>();
        desc.put(WrappedRawMaterials.CONTENT_KEY_ALGORITHM, "AES/256");
        
        AsymmetricStaticProvider prov = new AsymmetricStaticProvider(encryptionPair, sigPair, desc);
        
        EncryptionMaterials eMat = prov.getEncryptionMaterials(ctx);
        SecretKey encryptionKey = eMat.getEncryptionKey();
        assertThat(encryptionKey, is(not(nullValue())));
        assertEquals(32, encryptionKey.getEncoded().length); // 256 Bits
        assertEquals(sigPair.getPrivate(), eMat.getSigningKey());
        
        DecryptionMaterials dMat = prov.getDecryptionMaterials(ctx(eMat));
        assertEquals("AES", eMat.getMaterialDescription().get(WrappedRawMaterials.CONTENT_KEY_ALGORITHM));
        assertEquals(encryptionKey, dMat.getDecryptionKey());
        assertEquals(sigPair.getPublic(), dMat.getVerificationKey());
    }
    
    private static EncryptionContext ctx(EncryptionMaterials mat) {
        return EncryptionContext.builder()
            .materialDescription(mat.getMaterialDescription()).build();
    }
}
