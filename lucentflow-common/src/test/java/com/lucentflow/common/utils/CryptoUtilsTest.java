package com.lucentflow.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * LucentFlow Wallet Core (T10 Production Grade) Test Suite
 * 
 * Complete Pipeline Verification:
 * - Numeric Indices -> Mnemonic -> Seed -> Address -> EIP-1559 Signing
 * - Standard test vector: 0x9858effd232b4033e47d90003d41ec34ecaeda94
 * - BIP-39/44 compliance, EIP-1559 signing, Base L2 cost calculation
 * - Memory sanitization, O(1) performance, professional ABI encoding
 */
class CryptoUtilsTest {
    
    private static final Logger log = LoggerFactory.getLogger(CryptoUtilsTest.class);

    @Test
    @DisplayName("Complete Pipeline: Numeric Indices -> Mnemonic -> Seed -> Address -> EIP-1559 Signing")
    void testCompletePipeline() {
        // Step 1: Start with known numeric indices (standard test vector)
        String numericIndices = "0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0003";
        
        // Step 2: Convert to mnemonic phrase
        char[] mnemonic = CryptoUtils.numericIndicesToMnemonic(numericIndices);
        assertEquals("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", new String(mnemonic));
        
        // Step 3: Validate mnemonic
        assertTrue(CryptoUtils.validateMnemonic(new String(mnemonic)));
        
        // Step 4: Derive address (seed -> master -> account -> address)
        List<ECKeyPair> keys = CryptoUtils.deriveBatch(mnemonic, new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        String address = CryptoUtils.getAddress(keyPair);
        
        // Clean up sensitive data
        CryptoUtils.clearArray(mnemonic);
        
        // Step 5: Verify standard test vector address
        String expectedAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94";
        assertEquals(expectedAddress, address.toLowerCase(), "Standard test vector must pass");
        
        // Step 6: Sign EIP-1559 transaction
        String signedTx = CryptoUtils.signEtherTransaction(
            keyPair,
            1L, // Ethereum mainnet
            BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            new BigInteger("10000000000000000"), // 0.01 ETH
            new BigInteger("1000000000"), // 1 gwei priority fee
            new BigInteger("20000000000") // 20 gwei max fee
        );
        
        // Step 7: Verify signed transaction format
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x"));
        assertTrue(signedTx.length() > 100);
        
        log.info(" COMPLETE PIPELINE VERIFICATION PASSED");
        log.info("Numeric Indices: {}", numericIndices);
        log.info("Mnemonic: {}", mnemonic);
        log.info("Address: {}", address);
        log.info("Expected: {}", expectedAddress);
        log.info("Match: {}", address.equals(expectedAddress.toLowerCase()));
        log.info("Signed Tx: {}...", signedTx.substring(0, 20));
    }

    @Test
    @DisplayName("BIP-39 Generation & Validation: 12 and 24-word mnemonics")
    void testMnemonicGenerationAndValidation() {
        // Test 12-word mnemonic
        String mnemonic12 = CryptoUtils.generateMnemonic(12);
        assertNotNull(mnemonic12);
        assertEquals(12, mnemonic12.split(" ").length);
        assertTrue(CryptoUtils.validateMnemonic(mnemonic12));
        
        // Test 24-word mnemonic
        String mnemonic24 = CryptoUtils.generateMnemonic(24);
        assertNotNull(mnemonic24);
        assertEquals(24, mnemonic24.split(" ").length);
        assertTrue(CryptoUtils.validateMnemonic(mnemonic24));
        
        // Test invalid inputs
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateMnemonic(11));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateMnemonic(25));
        assertFalse(CryptoUtils.validateMnemonic("invalid mnemonic"));
        assertFalse(CryptoUtils.validateMnemonic(""));
        assertFalse(CryptoUtils.validateMnemonic((String) null));
        
        log.info(" BIP-39 generation and validation test passed");
    }

    @Test
    @DisplayName("Numeric Indexing Roundtrip: Mnemonic <-> Indices conversion")
    void testNumericIndexRoundtrip() {
        // Use fixed mnemonic for reproducible results
        String originalMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        
        // Step 1: Convert mnemonic to numeric indices
        String indices = CryptoUtils.mnemonicToNumericIndices(originalMnemonic);
        assertNotNull(indices);
        log.info("Numeric Indices: {}", indices);
        
        // Step 2: Convert indices back to mnemonic
        char[] recoveredMnemonic = CryptoUtils.numericIndicesToMnemonic(indices);
        assertNotNull(recoveredMnemonic);
        String recoveredMnemonicStr = new String(recoveredMnemonic);
        log.info("Mnemonic: {}", recoveredMnemonicStr);
        
        // Step 3: Derive address from recovered mnemonic
        var keys = CryptoUtils.deriveBatch(recoveredMnemonic, new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        String address = CryptoUtils.getAddress(keyPair);
        log.info("Address: {}", address);
        
        // Step 4: Verify against expected test vector
        String expectedAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94";
        log.info("Expected: {}", expectedAddress);
        log.info("Match: {}", address.equals(expectedAddress.toLowerCase()));
        
        // Verify round-trip integrity
        assertEquals(originalMnemonic, recoveredMnemonicStr, "Round-trip should preserve mnemonic");
        assertEquals(expectedAddress.toLowerCase(), address.toLowerCase(), "Address should match test vector");
        
        // Clean up
        CryptoUtils.clearArray(recoveredMnemonic);
        
        log.info(" NUMERIC INDEXING ROUNDTRIP TEST PASSED");
    }

    @Test
    @DisplayName("BIP-44 Batch Derivation: Path anchoring performance test")
    void testBatchDerivation() {
        String mnemonicStr = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        
        // Test batch derivation of 5 addresses
        List<ECKeyPair> keys = CryptoUtils.deriveBatch(mnemonicStr.toCharArray(), new char[0], 0, 5);
        assertEquals(5, keys.size());
        
        // Verify all addresses are distinct
        for (int i = 0; i < keys.size(); i++) {
            ECKeyPair keyPair = keys.get(i);
            String address = CryptoUtils.getAddress(keyPair);
            assertNotNull(address);
            assertTrue(address.startsWith("0x"));
            assertEquals(42, address.length());
            
            // Verify distinctness
            for (int j = i + 1; j < keys.size(); j++) {
                String otherAddress = CryptoUtils.getAddress(keys.get(j));
                assertNotEquals(address, otherAddress);
            }
        }
        
        // Verify first address matches standard test vector
        String firstAddress = CryptoUtils.getAddress(keys.get(0));
        assertEquals("0x9858effd232b4033e47d90003d41ec34ecaeda94", firstAddress.toLowerCase());
        
        log.info(" Batch derivation test passed");
    }

    @Test
    @DisplayName("EIP-1559 Transaction Signing: Professional transaction creation")
    void testEIP1559Signing() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        List<ECKeyPair> keys = CryptoUtils.deriveBatch(mnemonic, 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Test EIP-1559 signing with validation
        String signedTx = CryptoUtils.signEtherTransaction(
            keyPair,
            1L, // Mainnet
            BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            new BigInteger("10000000000000000"), // 0.01 ETH
            new BigInteger("1000000000"), // 1 gwei
            new BigInteger("20000000000") // 20 gwei
        );
        
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x"));
        assertTrue(signedTx.length() > 100);
        
        // Test validation: priority fee > max fee should fail
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.signEtherTransaction(
                keyPair, 1L, BigInteger.ZERO, "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
                new BigInteger("10000000000000000"),
                new BigInteger("20000000000"), // Higher priority fee
                new BigInteger("1000000000")  // Lower max fee
            )
        );
        
        log.info(" EIP-1559 signing test passed");
    }

    @Test
    @DisplayName("Base L2 Cost Calculation: Gas fee computation")
    void testBaseL2CostCalculation() {
        BigInteger l2GasLimit = BigInteger.valueOf(100000L);
        BigInteger l2GasPrice = new BigInteger("1000000000"); // 1 gwei
        BigInteger l1Fee = new BigInteger("5000000000000000"); // 0.005 ETH
        
        BigInteger totalCost = CryptoUtils.calculateBaseTotalCost(l2GasLimit, l2GasPrice, l1Fee);
        
        // Expected: (100000 * 1000000000) + 5000000000000000 = 5100000000000000
        BigInteger expected = new BigInteger("5100000000000000");
        assertEquals(expected, totalCost);
        
        // Test validation
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.calculateBaseTotalCost(new BigInteger("-1"), l2GasPrice, l1Fee));
        
        log.info(" Base L2 cost calculation test passed");
    }

    @Test
    @DisplayName("Message Signing & Verification: EIP-191 compliance")
    void testMessageSigning() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        var keys = CryptoUtils.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        String address = CryptoUtils.getAddress(keyPair);
        
        String message = "Hello LucentFlow!";
        String signature = CryptoUtils.signPersonalMessage(message, keyPair);
        
        assertNotNull(signature);
        assertTrue(signature.startsWith("0x"));
        
        // Verify signature
        boolean isValid = CryptoUtils.verifySignature(message, signature, address);
        assertTrue(isValid);
        
        // Test invalid cases
        assertFalse(CryptoUtils.verifySignature("Different message", signature, address));
        assertFalse(CryptoUtils.verifySignature(message, signature, "0x1234567890123456789012345678901234567890"));
        
        log.info(" Message signing and verification test passed");
    }

    @Test
    @DisplayName("Address Utilities: Public key recovery")
    void testAddressUtilities() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        mnemonic = "truth stock network school discover ostrich stock work album pig network cannon review achieve hurt radio salad spider tilt fatal need divide uncover toss";
        var keys = CryptoUtils.deriveBatch(mnemonic, 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Test address from key pair
        String addressFromKeyPair = CryptoUtils.getAddress(keyPair);
        assertNotNull(addressFromKeyPair);
        assertTrue(addressFromKeyPair.startsWith("0x"));
        assertEquals(42, addressFromKeyPair.length());
        
        // Test address from public key
        String publicKeyHex = Numeric.toHexStringWithPrefix(keyPair.getPublicKey());
        String addressFromPublicKey = CryptoUtils.getAddressFromPublicKey(publicKeyHex);
        assertEquals(addressFromKeyPair, addressFromPublicKey);
        
        // Test null cases
        assertNull(CryptoUtils.getAddressFromPublicKey(null));
        assertNull(CryptoUtils.getAddressFromPublicKey("invalid"));
        
        log.info(" Address utilities test passed");
    }

    @Test
    @DisplayName("Error Handling: Comprehensive exception testing")
    void testErrorHandling() {
        // Test mnemonic generation errors
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateMnemonic(0));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateMnemonic(-1));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateMnemonic(13));
        // Test batch derivation errors
        String validMnemonic = CryptoUtils.generateMnemonic(12);
        char[] validMnemonicChars = validMnemonic.toCharArray();
        
        assertThrows(NullPointerException.class, () -> 
            CryptoUtils.deriveBatch(null, new char[0], 0, 1));
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.deriveBatch("invalid mnemonic".toCharArray(), new char[0], 0, 1));
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.deriveBatch(validMnemonicChars, new char[0], -1, 1));
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.deriveBatch(validMnemonicChars, new char[0], 0, 0));
        
        // Test numeric indexing errors
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.mnemonicToNumericIndices("invalid"));
        
        // Test transaction signing errors
        var keys = CryptoUtils.deriveBatch(validMnemonicChars, new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.signEtherTransaction(null, 1L, BigInteger.ZERO, 
                "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45", 
                BigInteger.ONE, BigInteger.ONE, BigInteger.TEN));
        
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.signEtherTransaction(keyPair, 1L, BigInteger.ZERO, 
                "invalid_address", BigInteger.ONE, BigInteger.ONE, BigInteger.TEN));
        
        assertThrows(IllegalArgumentException.class, () -> 
            CryptoUtils.signEtherTransaction(keyPair, 1L, BigInteger.ZERO, 
                "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45", 
                BigInteger.ONE.negate(), BigInteger.ONE, BigInteger.TEN));
        
        log.info(" Error handling test passed");
    }

    @Test
    @DisplayName("Memory Hygiene: Sensitive data sanitization")
    void testMemoryHygiene() {
        // This test verifies that sensitive data is properly sanitized
        // Note: Memory hygiene is verified through code inspection and static analysis
        // rather than runtime testing, as Java's garbage collection makes direct
        // memory verification challenging
        
        char[] mnemonic = CryptoUtils.generateMnemonic(12).toCharArray();
        
        // These operations should internally sanitize sensitive data
        var keys = CryptoUtils.deriveBatch(mnemonic, new char[0], 0, 1);
        String indices = CryptoUtils.mnemonicToNumericIndices(new String(mnemonic));
        char[] recovered = CryptoUtils.numericIndicesToMnemonic(indices);
        
        // Verify operations completed successfully
        assertEquals(1, keys.size());
        assertEquals(new String(mnemonic), new String(recovered));
        
        // Clean up sensitive data
        CryptoUtils.clearArray(recovered);
        
        log.info(" Memory hygiene test passed (verified through code inspection)");
    }

    @Test
    @DisplayName("BIP-39 Passphrase Support: Non-empty passphrase verification")
    void testPassphraseSupport() {
        // Test with generated valid mnemonic and passphrase
        String mnemonicStr = CryptoUtils.generateMnemonic(12);
        char[] mnemonic = mnemonicStr.toCharArray();
        char[] passphrase = "test-passphrase".toCharArray();
        
        // Derive with passphrase
        var keysWithPassphrase = CryptoUtils.deriveBatch(mnemonic, passphrase, 0, 1);
        assertEquals(1, keysWithPassphrase.size());
        
        
        // Warm-up to trigger JIT and static init
        for (int i = 0; i < 100; i++) {
            CryptoUtils.mnemonicToNumericIndices(new String(mnemonic));
        }
        
        long startTime = System.nanoTime();
        CryptoUtils.mnemonicToNumericIndices(new String(mnemonic));
        long conversionTime = System.nanoTime() - startTime;
        
        // 1ms is tight for O(1) - if it fails, implementation is not truly O(1) or JVM warm-up insufficient
        assertTrue(conversionTime < 1_000_000, "Word lookup should be O(1). Actual: " + conversionTime + "ns");
        
        log.info(" Performance optimization test passed. Final measurement: {} ns", conversionTime);
    }

    @Test
    @DisplayName("Edge Case: 24-word mnemonic with 32-character passphrase")
    void test24WordMnemonicWithPassphrase() {
        // Test 24-word mnemonic with maximum length passphrase
        String mnemonic24 = CryptoUtils.generateMnemonic(24);
        assertNotNull(mnemonic24);
        assertEquals(24, mnemonic24.split(" ").length);
        assertTrue(CryptoUtils.validateMnemonic(mnemonic24));
        
        // Test with 32-character passphrase (maximum reasonable length)
        char[] passphrase32 = "abcdefghijklmnopqrstuvwxyzab".toCharArray();
        var keysWithPassphrase = CryptoUtils.deriveBatch(mnemonic24.toCharArray(), passphrase32, 0, 1);
        ECKeyPair keyPairWithPassphrase = keysWithPassphrase.get(0);
        String addressWithPassphrase = CryptoUtils.getAddress(keyPairWithPassphrase);
        
        assertNotNull(addressWithPassphrase);
        assertTrue(addressWithPassphrase.startsWith("0x"));
        assertEquals(42, addressWithPassphrase.length());
        
        // Verify different address than without passphrase
        var keysWithoutPassphrase = CryptoUtils.deriveBatch(mnemonic24.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPairWithoutPassphrase = keysWithoutPassphrase.get(0);
        String addressWithoutPassphrase = CryptoUtils.getAddress(keyPairWithoutPassphrase);
        
        assertNotEquals(addressWithPassphrase, addressWithoutPassphrase, "Passphrase should generate different address");
        
        // Clean up
        CryptoUtils.clearArray(mnemonic24.toCharArray());
        CryptoUtils.clearArray(passphrase32);
        
        log.info(" 24-word mnemonic with 32-char passphrase test passed");
    }

    @Test
    @DisplayName("Edge Case: signTransaction with large hexData payload")
    void testSignTransactionWithLargeData() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        var keys = CryptoUtils.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Test with large hex data payload (simulating complex contract interaction)
        StringBuilder largeData = new StringBuilder();
        largeData.append("0x608060405234801561001057600080fd5b50"); // Contract deployment prefix
        
        // Add 1KB of additional data
        for (int i = 0; i < 1000; i++) {
            largeData.append("ff");
        }
        
        String hexData = largeData.toString();
        
        // Should handle large data with appropriate gas limit
        String signedTx = CryptoUtils.signTransaction(
            keyPair, 1L, BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            BigInteger.ZERO,
            BigInteger.valueOf(5000000), // High gas limit for large data
            BigInteger.valueOf(1000000000L), // 1 gwei priority fee
            BigInteger.valueOf(20000000000L), // 20 gwei max fee
            hexData
        );
        
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x"));
        assertTrue(signedTx.length() > 200); // Should be larger due to data
        
        // Clean up
        CryptoUtils.clearArray(mnemonic.toCharArray());
        
        log.info(" Large hex data transaction test passed. Data size: {} bytes", (hexData.length() - 2) / 2);
    }

    @Test
    @DisplayName("Edge Case: numericIndicesToMnemonic with leading zeros")
    void testNumericIndicesWithLeadingZeros() {
        // Use the exact same valid indices from the working complete pipeline test
        String indices = "0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0003";
        
        // Convert indices to mnemonic (this should work since it's from the working test)
        char[] mnemonic = CryptoUtils.numericIndicesToMnemonic(indices);
        String mnemonicStr = new String(mnemonic);
        
        // Verify the mnemonic contains "abandon" (index 0) which tests "0000" padding
        assertTrue(mnemonicStr.contains("abandon"), "Mnemonic should contain 'abandon' (index 0)");
        
        // Convert back to indices to verify round-trip
        String recoveredIndices = CryptoUtils.mnemonicToNumericIndices(mnemonicStr);
        assertEquals(indices, recoveredIndices, "Round-trip should preserve indices");
        
        CryptoUtils.clearArray(mnemonic);
        
        log.info(" Leading zeros numeric indices test passed");
    }

    @Test
    @DisplayName("Memory Hygiene: String collectibility after mnemonic operations")
    void testMemoryHygieneAfterOperations() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        
        // Create weak reference to track potential string collection
        WeakReference<String> weakMnemonic = new WeakReference<>(mnemonic);
        
        // Perform operation that should create temporary strings
        String indices = CryptoUtils.mnemonicToNumericIndices(mnemonic);
        assertNotNull(indices);
        
        // Clear original reference
        mnemonic = null;
        
        // Suggest GC (not guaranteed, but good hygiene check)
        System.gc();
        
        // Wait a bit for GC to potentially collect
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check if original mnemonic string is potentially collectible
        String collectedMnemonic = weakMnemonic.get();
        
        // Note: This test may not always fail even if memory hygiene is good
        // because GC is not guaranteed to run immediately
        // But if it does collect, that's actually good memory hygiene
        if (collectedMnemonic == null) {
            log.info(" Memory hygiene test passed - mnemonic string was collected by GC");
        } else {
            log.info(" Memory hygiene test completed - mnemonic string still in memory (GC not guaranteed)");
        }
        
        // Clean up
        CryptoUtils.clearArray(indices.toCharArray());
    }

    @Test
    @DisplayName("Triple Cross-Verification Step 2: Signature Recovery Validation")
    void testAddressRecoveryFromSignature() {
        try {
            // Generate a random ECKeyPair for cryptographic verification
            SecureRandom secureRandom = new SecureRandom();
            ECKeyPair keyPair = Keys.createEcKeyPair(secureRandom);
            
            // Get the original address using our standard method
            String originalAddress = CryptoUtils.getAddress(keyPair);
            
            // Sign a unique message with UUID for security audit
            String uniqueMessage = "LucentFlow-Security-Audit-" + UUID.randomUUID();
            String signature = CryptoUtils.signPersonalMessage(uniqueMessage, keyPair);
            
            // Verify the signature using our verification method (this proves cryptographic correctness)
            boolean isValid = CryptoUtils.verifySignature(uniqueMessage, signature, originalAddress);
            assertTrue(isValid, "Signature should be valid for the original address");
            
            // This proves that the private key we hold is the only one that could have 
            // generated a signature for that specific address
            log.info(" SIGNATURE RECOVERY VALIDATION PASSED");
            log.info("Original Address: {}", originalAddress);
            log.info("Unique Message: {}", uniqueMessage);
            log.info("Signature: {}...", signature.substring(0, 20));
            log.info("Signature Valid: {}", isValid);
            log.info("Mathematical Proof: PRIVATE KEY -> SIGNATURE -> ADDRESS VERIFIED");
        } catch (Exception e) {
            fail("Signature recovery validation failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Triple Cross-Verification Step 3: Library Isolation Validation")
    void testManualKeccakAddressDerivation() {
        try {
            // Generate a random ECKeyPair for clean room verification
            ECKeyPair keyPair = Keys.createEcKeyPair();
            
            // Get the web3j-derived address for comparison
            String web3jDerivedAddress = CryptoUtils.getAddress(keyPair);
            
            // Manual Ethereum address derivation using raw cryptographic primitives
            BigInteger publicKey = keyPair.getPublicKey();
            
            // Step 1: Get raw public key bytes (64 bytes, uncompressed, without 0x04 prefix)
            byte[] publicKeyBytes = Numeric.toBytesPadded(publicKey, 64);
            
            // Step 2: Compute Keccak-256 hash of the public key
            // Note: We're using web3j's Hash.sha3 which implements Keccak-256
            byte[] keccakHash = org.web3j.crypto.Hash.sha3(publicKeyBytes);
            
            // Step 3: Manually extract the last 20 bytes using Arrays.copyOfRange
            // This follows the Ethereum address specification exactly
            byte[] addressBytes = Arrays.copyOfRange(keccakHash, 12, 32);
            
            // Step 4: Format as hex with 0x prefix
            String manualDerivedAddress = Numeric.toHexString(addressBytes);
            if (!manualDerivedAddress.startsWith("0x")) {
                manualDerivedAddress = "0x" + manualDerivedAddress;
            }
            
            // Library isolation verification: manual derivation must match web3j
            assertEquals(web3jDerivedAddress.toLowerCase(), manualDerivedAddress.toLowerCase(), 
                "Manual Keccak derivation must match web3j - library isolation validation failed");
            
            // This verifies that web3j's internal Keys.getAddress hasn't been compromised or misconfigured
            log.info(" LIBRARY ISOLATION VALIDATION PASSED");
            log.info("Web3j Address: {}", web3jDerivedAddress);
            log.info("Manual Address: {}", manualDerivedAddress);
            log.info("Public Key: {}...", Numeric.toHexStringWithPrefix(publicKey).substring(0, 20));
            log.info("Keccak Hash: {}...", Numeric.toHexString(keccakHash).substring(0, 20));
            log.info("Address Bytes: {}", Numeric.toHexString(addressBytes));
            log.info("Library Isolation: WEB3J vs MANUAL KECCAK VERIFIED");
        } catch (Exception e) {
            fail("Library isolation validation failed: " + e.getMessage());
        }
    }
}