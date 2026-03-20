package com.lucentflow.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite to verify the modular components work independently and maintain backward compatibility.
 */
class ModularComponentsTest {

    @Test
    @DisplayName("MnemonicVault: Direct usage of mnemonic operations")
    void testMnemonicVaultDirectUsage() {
        // Test mnemonic generation
        String mnemonic = MnemonicVault.generateMnemonic(12);
        assertNotNull(mnemonic);
        assertEquals(12, mnemonic.split(" ").length);
        assertTrue(MnemonicVault.validateMnemonic(mnemonic));

        // Test numeric indexing
        String indices = MnemonicVault.mnemonicToNumericIndices(mnemonic);
        assertNotNull(indices);
        
        char[] recovered = MnemonicVault.numericIndicesToMnemonic(indices);
        assertEquals(mnemonic, new String(recovered));
        
        // Clean up
        MnemonicVault.clearArray(recovered);
    }

    @Test
    @DisplayName("TransactionSigner: Direct usage of signing operations")
    void testTransactionSignerDirectUsage() {
        // Generate a test key pair
        String mnemonic = MnemonicVault.generateMnemonic(12);
        var keys = MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);

        // Test address extraction
        String address = TransactionSigner.getAddress(keyPair);
        assertNotNull(address);
        assertTrue(address.startsWith("0x"));
        assertEquals(42, address.length());

        // Test message signing
        String message = "Test message for modular components";
        String signature = TransactionSigner.signPersonalMessage(message, keyPair);
        assertNotNull(signature);
        assertTrue(signature.startsWith("0x"));

        // Test signature verification
        boolean isValid = TransactionSigner.verifySignature(message, signature, address);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("BaseGasOracle: Direct usage of gas fee calculations")
    void testBaseGasOracleDirectUsage() {
        // Test cost calculation
        BigInteger l2GasLimit = BigInteger.valueOf(100000L);
        BigInteger l2GasPrice = new BigInteger("1000000000"); // 1 gwei
        BigInteger l1Fee = new BigInteger("5000000000000000"); // 0.005 ETH

        BigInteger totalCost = BaseGasOracle.calculateBaseTotalCost(l2GasLimit, l2GasPrice, l1Fee);
        
        // Expected: (100000 * 1000000000) + 5000000000000000 = 5100000000000000
        BigInteger expected = new BigInteger("5100000000000000");
        assertEquals(expected, totalCost);

        // Test cache operations
        BaseGasOracle.clearCache();
        var stats = BaseGasOracle.getCacheStats();
        assertEquals(0, stats.getActiveEntries());
        assertEquals(0, stats.getTotalEntries());
    }

    @Test
    @DisplayName("CryptoUtils Facade: Backward compatibility maintained")
    void testCryptoUtilsBackwardCompatibility() {
        // Test that all original methods still work through the facade
        String mnemonic = CryptoUtils.generateMnemonic(12);
        assertTrue(CryptoUtils.validateMnemonic(mnemonic));

        var keys = CryptoUtils.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        String address = CryptoUtils.getAddress(keyPair);
        assertNotNull(address);

        String signature = CryptoUtils.signPersonalMessage("Test message", keyPair);
        assertTrue(CryptoUtils.verifySignature("Test message", signature, address));

        // Test deprecated method still works
        var legacyKeys = CryptoUtils.deriveBatch(mnemonic, 0, 1);
        assertEquals(1, legacyKeys.size());
    }

    @Test
    @DisplayName("Component Separation: Each component has single responsibility")
    void testComponentSeparation() {
        // Verify each component focuses on its specific domain
        
        // MnemonicVault should only handle mnemonic-related operations
        assertTrue(hasMnemonicMethods(MnemonicVault.class));
        assertFalse(hasTransactionMethods(MnemonicVault.class));
        assertFalse(hasGasOracleMethods(MnemonicVault.class));

        // TransactionSigner should only handle signing operations
        assertFalse(hasMnemonicMethods(TransactionSigner.class));
        assertTrue(hasTransactionMethods(TransactionSigner.class));
        assertFalse(hasGasOracleMethods(TransactionSigner.class));

        // BaseGasOracle should only handle gas fee operations
        assertFalse(hasMnemonicMethods(BaseGasOracle.class));
        assertFalse(hasTransactionMethods(BaseGasOracle.class));
        assertTrue(hasGasOracleMethods(BaseGasOracle.class));
    }

    private boolean hasMnemonicMethods(Class<?> clazz) {
        var methods = clazz.getDeclaredMethods();
        return java.util.Arrays.stream(methods)
                .anyMatch(m -> m.getName().contains("Mnemonic") || 
                           m.getName().contains("mnemonic") ||
                           m.getName().equals("generateMnemonic") ||
                           m.getName().equals("validateMnemonic") ||
                           m.getName().equals("deriveBatch"));
    }

    private boolean hasTransactionMethods(Class<?> clazz) {
        var methods = clazz.getDeclaredMethods();
        return java.util.Arrays.stream(methods)
                .anyMatch(m -> m.getName().contains("sign") ||
                           m.getName().contains("verify") ||
                           m.getName().contains("getAddress"));
    }

    private boolean hasGasOracleMethods(Class<?> clazz) {
        var methods = clazz.getDeclaredMethods();
        return java.util.Arrays.stream(methods)
                .anyMatch(m -> m.getName().contains("Fee") ||
                           m.getName().contains("Cost") ||
                           m.getName().contains("Cache"));
    }
}
