package com.lucentflow.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.web3j.crypto.ECKeyPair;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for enhanced transaction signing with gas limit and data parameters.
 */
class EnhancedTransactionSigningTest {

    @Test
    @DisplayName("Enhanced Transaction Signing: Gas limit and data parameterization")
    void testEnhancedTransactionSigning() {
        // Generate test key pair
        String mnemonic = MnemonicVault.generateMnemonic(12);
        var keys = MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Test 1: Simple ETH transfer with custom gas limit
        String signedTx1 = TransactionSigner.signTransaction(
            keyPair, 1L, BigInteger.ZERO, 
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            BigInteger.valueOf(10000000000000000L), // 0.01 ETH
            BigInteger.valueOf(25000), // Custom gas limit
            BigInteger.valueOf(1000000000L), // 1 gwei priority fee
            BigInteger.valueOf(20000000000L), // 20 gwei max fee
            "0x" // No data
        );
        
        assertNotNull(signedTx1);
        assertTrue(signedTx1.startsWith("0x"));
        assertTrue(signedTx1.length() > 100);
        
        // Test 2: Transaction with data (e.g., token transfer)
        String tokenTransferData = "0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4db45000000000000000000000000000000000000000000000000000000000000000000a"; // transfer 10 tokens
        
        String signedTx2 = TransactionSigner.signTransaction(
            keyPair, 1L, BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            BigInteger.ZERO, // 0 ETH
            BigInteger.valueOf(100000), // Higher gas limit for data
            BigInteger.valueOf(1000000000L), // 1 gwei priority fee
            BigInteger.valueOf(20000000000L), // 20 gwei max fee
            tokenTransferData
        );
        
        assertNotNull(signedTx2);
        assertTrue(signedTx2.startsWith("0x"));
        assertTrue(signedTx2.length() > 100);
        
        // Test 3: Contract creation (null recipient)
        String contractCreationTx = TransactionSigner.signTransaction(
            keyPair, 1L, BigInteger.ZERO,
            null, // No recipient for contract creation
            BigInteger.ZERO, // 0 ETH
            BigInteger.valueOf(2000000), // High gas limit for contract deployment
            BigInteger.valueOf(1000000000L), // 1 gwei priority fee
            BigInteger.valueOf(20000000000L), // 20 gwei max fee
            "0x608060405234801561001057600080fd5b50" // Contract bytecode
        );
        
        assertNotNull(contractCreationTx);
        assertTrue(contractCreationTx.startsWith("0x"));
        
        // Clean up
        MnemonicVault.clearArray(mnemonic.toCharArray());
    }

    @Test
    @DisplayName("Security Validation: Gas limit warnings for transactions with data")
    void testGasLimitSecurityValidation() {
        String mnemonic = MnemonicVault.generateMnemonic(12);
        var keys = MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Should throw exception when data is present but gas limit is 21000
        String tokenData = "0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4db45000000000000000000000000000000000000000000000000000000000000000000a";
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TransactionSigner.signTransaction(
                keyPair, 1L, BigInteger.ZERO,
                "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
                BigInteger.ZERO,
                BigInteger.valueOf(21000), // Too low for data
                BigInteger.valueOf(1000000000L),
                BigInteger.valueOf(20000000000L),
                tokenData
            )
        );
        
        assertTrue(exception.getMessage().contains("Transaction contains data but gas limit is 21000"));
        
        // Clean up
        MnemonicVault.clearArray(mnemonic.toCharArray());
    }

    @Test
    @DisplayName("Parameter Validation: Invalid hex data format")
    void testInvalidHexDataValidation() {
        String mnemonic = MnemonicVault.generateMnemonic(12);
        var keys = MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Should throw exception for non-0x-prefixed data
        assertThrows(IllegalArgumentException.class, () ->
            TransactionSigner.signTransaction(
                keyPair, 1L, BigInteger.ZERO,
                "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
                BigInteger.ZERO,
                BigInteger.valueOf(50000),
                BigInteger.valueOf(1000000000L),
                BigInteger.valueOf(20000000000L),
                "invalid_hex_data" // Not 0x-prefixed
            )
        );
        
        // Should throw exception for invalid hex characters
        assertThrows(IllegalArgumentException.class, () ->
            TransactionSigner.signTransaction(
                keyPair, 1L, BigInteger.ZERO,
                "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
                BigInteger.ZERO,
                BigInteger.valueOf(50000),
                BigInteger.valueOf(1000000000L),
                BigInteger.valueOf(20000000000L),
                "0xZZZ" // Invalid hex characters
            )
        );
        
        // Clean up
        MnemonicVault.clearArray(mnemonic.toCharArray());
    }

    @Test
    @DisplayName("Backward Compatibility: Original signEtherTransaction still works")
    void testBackwardCompatibility() {
        String mnemonic = MnemonicVault.generateMnemonic(12);
        var keys = MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Original method should still work exactly as before
        String signedTx = TransactionSigner.signEtherTransaction(
            keyPair, 1L, BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            BigInteger.valueOf(10000000000000000L), // 0.01 ETH
            BigInteger.valueOf(1000000000L), // 1 gwei priority fee
            BigInteger.valueOf(20000000000L) // 20 gwei max fee
        );
        
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x"));
        assertTrue(signedTx.length() > 100);
        
        // Clean up
        MnemonicVault.clearArray(mnemonic.toCharArray());
    }

    @Test
    @DisplayName("CryptoUtils Facade: New signTransaction method available")
    void testCryptoUtilsFacadeIntegration() {
        String mnemonic = CryptoUtils.generateMnemonic(12);
        var keys = CryptoUtils.deriveBatch(mnemonic.toCharArray(), new char[0], 0, 1);
        ECKeyPair keyPair = keys.get(0);
        
        // Test that new method is available through facade
        String signedTx = CryptoUtils.signTransaction(
            keyPair, 1L, BigInteger.ZERO,
            "0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45",
            BigInteger.valueOf(10000000000000000L),
            BigInteger.valueOf(25000), // Custom gas limit
            BigInteger.valueOf(1000000000L),
            BigInteger.valueOf(20000000000L),
            "0x" // No data
        );
        
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x"));
        
        // Clean up
        CryptoUtils.clearArray(mnemonic.toCharArray());
    }
}
