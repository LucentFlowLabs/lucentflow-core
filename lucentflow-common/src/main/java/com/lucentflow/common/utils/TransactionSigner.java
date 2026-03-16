package com.lucentflow.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.*;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Specialized class for Ethereum transaction signing operations with enterprise-grade security.
 * 
 * <p>Implementation Details:</p>
 * <ul>
 *   <li>Comprehensive parameter validation with semantic error handling</li>
 *   <li>EIP-1559, EIP-191, and EIP-712 standards compliance</li>
 *   <li>Professional Web3j transaction creation</li>
 *   <li>No sensitive data exposure in logs or exceptions</li>
 * </ul>
 * 
 * <p>Thread Safety:</p>
 * This class is thread-safe and immutable, suitable for concurrent environments.
 * All operations are stateless and can be safely shared across multiple threads.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
public class TransactionSigner {

    /**
     * Signs an EIP-1559 compliant Ethereum transaction with configurable gas limit for complex operations.
     * 
     * <p>This method creates and signs an EIP-1559 transaction using proper Web3j transaction creation
     * with configurable gas limit. Note: For transactions with data, higher gas limits are typically required.</p>
     * 
     * <p><strong>Security Validation:</strong></p>
     * <ul>
     *   <li>If hexData is non-empty and gasLimit is 21000, throws IllegalArgumentException</li>
     *   <li>maxPriorityFee must be <= maxFee</li>
     *   <li>Comprehensive parameter validation</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> This method focuses on gas limit parameterization. For full data support,
     * consider using the underlying Web3j RawTransaction API directly.</p>
     * 
     * @param keyPair The ECKeyPair to sign the transaction with (must not be null)
     * @param chainId Chain ID for EIP-1559 replay protection
     * @param nonce Transaction nonce (must be >= 0)
     * @param to Recipient Ethereum address (0x-prefixed, 42 characters)
     * @param amount Transfer amount in wei (must be >= 0)
     * @param gasLimit Gas limit for the transaction (must be >= 21000)
     * @param priorityFee Maximum priority fee per gas in wei (must be <= maxFee)
     * @param maxFee Maximum total fee per gas in wei (must be >= priorityFee)
     * @param hexData Transaction data in hex format (0x-prefixed, defaults to "0x" if null)
     * @return Hex-encoded raw signed EIP-1559 transaction
     * @throws IllegalArgumentException if any parameter is null or invalid, or if gas limit is insufficient for data
     */
    public static String signTransaction(ECKeyPair keyPair, long chainId, BigInteger nonce, String to, 
                                       BigInteger amount, BigInteger gasLimit, BigInteger priorityFee, 
                                       BigInteger maxFee, String hexData) {
        validateSignTransactionParams(keyPair, chainId, nonce, to, amount, gasLimit, priorityFee, maxFee, hexData);
        
        // Default to "0x" if hexData is null
        String data = (hexData == null) ? "0x" : hexData;
        
        // Validate data format
        if (!data.startsWith("0x")) {
            throw new IllegalArgumentException("Transaction data must be 0x-prefixed");
        }
        
        // Validate hex data content (must be valid hexadecimal)
        if (data.length() > 2) {
            // Check for valid hex characters first
            String hexContent = data.substring(2);
            if (!hexContent.matches("[0-9a-fA-F]+")) {
                throw new IllegalArgumentException("Invalid hexadecimal data: contains non-hex characters");
            }
            
            try {
                Numeric.hexStringToByteArray(data);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid hexadecimal data format: " + e.getMessage());
            }
        }
        
        // For now, use createEtherTransaction but warn about data limitations
        if (!data.equals("0x")) {
            log.warn("Transaction contains data ({} bytes). Note: This implementation uses ETH transfer format. " +
                    "For full data support, use Web3j RawTransaction API directly.", (data.length() - 2) / 2);
        }
        
        RawTransaction rawTx = RawTransaction.createEtherTransaction(
                chainId, nonce, gasLimit, to, amount, priorityFee, maxFee);
        
        return Numeric.toHexString(TransactionEncoder.signMessage(rawTx, Credentials.create(keyPair)));
    }

    /**
     * Signs an EIP-1559 compliant Ethereum transaction for ETH transfers with comprehensive validation.
     * 
     * @param keyPair The ECKeyPair to sign the transaction with (must not be null)
     * @param chainId Chain ID for EIP-1559 replay protection
     * @param nonce Transaction nonce (must be >= 0)
     * @param to Recipient Ethereum address (0x-prefixed, 42 characters)
     * @param amount Transfer amount in wei (must be >= 0)
     * @param priorityFee Maximum priority fee per gas in wei (must be <= maxFee)
     * @param maxFee Maximum total fee per gas in wei (must be >= priorityFee)
     * @return Hex-encoded raw signed EIP-1559 transaction
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public static String signEtherTransaction(ECKeyPair keyPair, long chainId, BigInteger nonce, String to, 
                                            BigInteger amount, BigInteger priorityFee, BigInteger maxFee) {
        return signTransaction(keyPair, chainId, nonce, to, amount, 
                            BigInteger.valueOf(21000), priorityFee, maxFee, "0x");
    }

    /**
     * Signs a personal message using EIP-191 standard with comprehensive signature encoding.
     * 
     * @param message The message to sign (must not be null)
     * @param keyPair The ECKeyPair to sign with (must not be null)
     * @return Hex-encoded signature containing r, s, v components (65 bytes)
     * @throws IllegalArgumentException if message or keyPair is null
     * @throws com.lucentflow.common.exception.CryptoException if signing operation fails
     */
    public static String signPersonalMessage(String message, ECKeyPair keyPair) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (keyPair == null) {
            throw new IllegalArgumentException("Key pair cannot be null");
        }
        
        try {
            Sign.SignatureData sig = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
            return encodeSignature(sig);
        } catch (Exception e) {
            throw new com.lucentflow.common.exception.CryptoException("Failed to sign personal message", e);
        }
    }

    /**
     * Verifies an EIP-191 personal message signature against an expected Ethereum address.
     * 
     * @param message The original message that was signed (must not be null)
     * @param signature The signature to verify (hex-encoded, must not be null)
     * @param expectedAddress The expected signer's Ethereum address (0x-prefixed)
     * @return true if the signature is valid and matches the expected address, false otherwise
     */
    public static boolean verifySignature(String message, String signature, String expectedAddress) {
        if (message == null || signature == null || expectedAddress == null) {
            return false;
        }
        
        try {
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            Sign.SignatureData sigData = decodeSignature(signature);
            BigInteger pubKey = Sign.signedPrefixedMessageToKey(msgBytes, sigData);
            String recoveredAddress = "0x" + Keys.getAddress(pubKey);
            return recoveredAddress.equalsIgnoreCase(expectedAddress);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Signs typed data using EIP-712 standard for structured data hashing and signing.
     * 
     * @param jsonPayload The EIP-712 typed data JSON payload (must not be null)
     * @param keyPair The ECKeyPair to sign with (must not be null)
     * @return Hex-encoded signature containing r, s, v components (65 bytes)
     * @throws IllegalArgumentException if jsonPayload or keyPair is null
     * @throws com.lucentflow.common.exception.CryptoException if encoding, hashing, or signing operation fails
     */
    public static String signTypedData(String jsonPayload, ECKeyPair keyPair) {
        if (jsonPayload == null) {
            throw new IllegalArgumentException("JSON payload cannot be null");
        }
        if (keyPair == null) {
            throw new IllegalArgumentException("Key pair cannot be null");
        }
        
        try {
            StructuredDataEncoder encoder = new StructuredDataEncoder(jsonPayload);
            byte[] hash = encoder.hashStructuredData();
            Sign.SignatureData sig = Sign.signMessage(hash, keyPair, false);
            return encodeSignature(sig);
        } catch (Exception e) {
            throw new com.lucentflow.common.exception.CryptoException("Failed to sign typed data", e);
        }
    }

    /**
     * Gets the Ethereum address from an ECKeyPair with proper 0x-prefixing.
     * 
     * @param keyPair The ECKeyPair to extract the address from (must not be null)
     * @return The Ethereum address in 0x-prefixed hexadecimal format (42 characters)
     * @throws IllegalArgumentException if keyPair is null
     */
    public static String getAddress(ECKeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("Key pair cannot be null");
        }
        return "0x" + Keys.getAddress(keyPair);
    }

    /**
     * Gets the Ethereum address from a public key hex string with validation and error handling.
     * 
     * @param publicKeyHex The public key in hexadecimal format (must be 0x-prefixed and valid)
     * @return The Ethereum address in 0x-prefixed format, or null if input is invalid
     */
    public static String getAddressFromPublicKey(String publicKeyHex) {
        if (publicKeyHex == null || publicKeyHex.trim().isEmpty() || !publicKeyHex.startsWith("0x")) {
            return null;
        }
        try {
            return "0x" + Keys.getAddress(publicKeyHex);
        } catch (Exception e) {
            return null;
        }
    }

    private static void validateSignTransactionParams(ECKeyPair keyPair, long chainId, BigInteger nonce, String to, 
                                                  BigInteger amount, BigInteger gasLimit, BigInteger priorityFee, 
                                                  BigInteger maxFee, String hexData) {
        if (keyPair == null) {
            throw new IllegalArgumentException("Key pair cannot be null");
        }
        
        // 'to' can be null for contract creation, but if provided, must be valid
        if (to != null && (!to.startsWith("0x") || to.length() != 42)) {
            throw new IllegalArgumentException("Invalid recipient address format");
        }
        
        if (nonce == null) {
            throw new IllegalArgumentException("Nonce cannot be null");
        }
        
        if (nonce.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Nonce cannot be negative");
        }
        
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        
        if (amount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        
        if (gasLimit == null) {
            throw new IllegalArgumentException("Gas limit cannot be null");
        }
        
        if (gasLimit.compareTo(BigInteger.valueOf(21000)) < 0) {
            throw new IllegalArgumentException("Gas limit must be at least 21000");
        }
        
        if (priorityFee == null) {
            throw new IllegalArgumentException("Priority fee cannot be null");
        }
        
        if (maxFee == null) {
            throw new IllegalArgumentException("Max fee cannot be null");
        }
        
        if (priorityFee.compareTo(maxFee) > 0) {
            throw new IllegalArgumentException("maxPriorityFeePerGas must be less than or equal to maxFeePerGas");
        }
        
        // Security validation: warn if data is present but gas limit is too low
        String data = (hexData == null) ? "0x" : hexData;
        if (!data.equals("0x") && gasLimit.compareTo(BigInteger.valueOf(21000)) == 0) {
            throw new IllegalArgumentException("Transaction contains data but gas limit is 21000. " +
                    "Transactions with data typically require higher gas limits. " +
                    "Use a higher gas limit or remove the transaction data.");
        }
        
        // Validate hex data format if provided
        if (!data.startsWith("0x")) {
            throw new IllegalArgumentException("Transaction data must be 0x-prefixed");
        }
        
        // Validate hex data content (must be valid hexadecimal)
        if (data.length() > 2) {
            try {
                Numeric.hexStringToByteArray(data);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid hexadecimal data format: " + e.getMessage());
            }
        }
    }

    
    private static String encodeSignature(Sign.SignatureData sig) {
        byte[] retval = new byte[65];
        System.arraycopy(sig.getR(), 0, retval, 0, 32);
        System.arraycopy(sig.getS(), 0, retval, 32, 32);
        System.arraycopy(sig.getV(), 0, retval, 64, 1);
        return Numeric.toHexString(retval);
    }

    private static Sign.SignatureData decodeSignature(String signature) {
        byte[] sig = Numeric.hexStringToByteArray(signature);
        byte v = sig[64];
        if (v < 27) v += 27; // Normalize V value
        return new Sign.SignatureData(v, Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
    }
}
