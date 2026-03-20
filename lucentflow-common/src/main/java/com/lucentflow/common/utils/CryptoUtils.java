package com.lucentflow.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.List;

/**
 * Facade class for cryptographic operations providing backward compatibility while delegating to specialized components.
 * 
 * <p>This class serves as a lightweight entry point that maintains the existing API while internally
 * delegating to the newly modularized components:</p>
 * <ul>
 *   <li>{@link MnemonicVault} - BIP-39 mnemonic operations</li>
 *   <li>{@link TransactionSigner} - Transaction signing operations</li>
 *   <li>{@link BaseGasOracle} - Base L2 gas fee calculations</li>
 * </ul>
 * 
 * <p>Migration Path:</p>
 * For new code, consider using the specialized classes directly for better type safety
 * and clearer intent. This facade exists primarily for backward compatibility.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
public class CryptoUtils {

    // --- MnemonicVault Delegation ---

    /**
     * @see MnemonicVault#generateMnemonic(int)
     */
    public static String generateMnemonic(int wordCount) {
        return MnemonicVault.generateMnemonic(wordCount);
    }
    
    /**
     * @see MnemonicVault#generateMnemonic(int) - char[] version for enhanced security
     */
    public static char[] generateMnemonicChars(int wordCount) {
        return MnemonicVault.generateMnemonic(wordCount).toCharArray();
    }

    /**
     * @see MnemonicVault#validateMnemonic(String)
     */
    public static boolean validateMnemonic(String mnemonic) {
        return MnemonicVault.validateMnemonic(mnemonic);
    }
    
    /**
     * @see MnemonicVault#validateMnemonic(String) - char[] version for enhanced security
     */
    public static boolean validateMnemonic(char[] mnemonic) {
        return MnemonicVault.validateMnemonic(new String(mnemonic));
    }

    /**
     * @see MnemonicVault#deriveBatch(char[], char[], int, int)
     */
    public static List<ECKeyPair> deriveBatch(char[] mnemonic, char[] passphrase, int start, int count) {
        return MnemonicVault.deriveBatch(mnemonic, passphrase, start, count);
    }

    /**
     * @deprecated Use {@link #deriveBatch(char[], char[], int, int)} for enhanced security
     * @see MnemonicVault#deriveBatch(char[], char[], int, int)
     */
    @Deprecated
    public static List<ECKeyPair> deriveBatch(String mnemonic, int start, int count) {
        return MnemonicVault.deriveBatch(mnemonic.toCharArray(), new char[0], start, count);
    }

    /**
     * @see MnemonicVault#mnemonicToNumericIndices(String)
     */
    public static String mnemonicToNumericIndices(String mnemonic) {
        return MnemonicVault.mnemonicToNumericIndices(mnemonic);
    }
    
    /**
     * @see MnemonicVault#mnemonicToNumericIndices(String) - char[] version for enhanced security
     */
    public static String mnemonicToNumericIndices(char[] mnemonic) {
        return MnemonicVault.mnemonicToNumericIndices(new String(mnemonic));
    }

    /**
     * @see MnemonicVault#numericIndicesToMnemonic(String)
     */
    public static char[] numericIndicesToMnemonic(String indices) {
        return MnemonicVault.numericIndicesToMnemonic(indices);
    }

    /**
     * @see MnemonicVault#clearArray(char[])
     */
    public static void clearArray(char[] array) {
        MnemonicVault.clearArray(array);
    }

    // --- TransactionSigner Delegation ---

    /**
     * @see TransactionSigner#signTransaction(ECKeyPair, long, BigInteger, String, BigInteger, BigInteger, BigInteger, BigInteger, String)
     */
    public static String signTransaction(ECKeyPair keyPair, long chainId, BigInteger nonce, String to, 
                                       BigInteger amount, BigInteger gasLimit, BigInteger priorityFee, 
                                       BigInteger maxFee, String hexData) {
        return TransactionSigner.signTransaction(keyPair, chainId, nonce, to, amount, gasLimit, priorityFee, maxFee, hexData);
    }

    /**
     * @see TransactionSigner#signEtherTransaction(ECKeyPair, long, BigInteger, String, BigInteger, BigInteger, BigInteger)
     */
    public static String signEtherTransaction(ECKeyPair keyPair, long chainId, BigInteger nonce, String to, 
                                            BigInteger amount, BigInteger priorityFee, BigInteger maxFee) {
        return TransactionSigner.signEtherTransaction(keyPair, chainId, nonce, to, amount, priorityFee, maxFee);
    }

    /**
     * @see TransactionSigner#signPersonalMessage(String, ECKeyPair)
     */
    public static String signPersonalMessage(String message, ECKeyPair keyPair) {
        return TransactionSigner.signPersonalMessage(message, keyPair);
    }

    /**
     * @see TransactionSigner#verifySignature(String, String, String)
     */
    public static boolean verifySignature(String message, String signature, String expectedAddress) {
        return TransactionSigner.verifySignature(message, signature, expectedAddress);
    }

    /**
     * @see TransactionSigner#signTypedData(String, ECKeyPair)
     */
    public static String signTypedData(String jsonPayload, ECKeyPair keyPair) {
        return TransactionSigner.signTypedData(jsonPayload, keyPair);
    }

    /**
     * @see TransactionSigner#getAddress(ECKeyPair)
     */
    public static String getAddress(ECKeyPair keyPair) {
        return TransactionSigner.getAddress(keyPair);
    }

    /**
     * @see TransactionSigner#getAddressFromPublicKey(String)
     */
    public static String getAddressFromPublicKey(String publicKeyHex) {
        return TransactionSigner.getAddressFromPublicKey(publicKeyHex);
    }

    // --- BaseGasOracle Delegation ---

    /**
     * @see BaseGasOracle#getL1Fee(Web3j, String)
     */
    public static BigInteger getL1Fee(Web3j web3j, String rawTxHex) throws Exception {
        return BaseGasOracle.getL1Fee(web3j, rawTxHex);
    }

    /**
     * @see BaseGasOracle#calculateBaseTotalCost(BigInteger, BigInteger, BigInteger)
     */
    public static BigInteger calculateBaseTotalCost(BigInteger l2GasLimit, BigInteger l2GasPrice, BigInteger l1Fee) {
        return BaseGasOracle.calculateBaseTotalCost(l2GasLimit, l2GasPrice, l1Fee);
    }

    /**
     * @see BaseGasOracle#clearCache()
     */
    public static void clearL1FeeCache() {
        BaseGasOracle.clearCache();
    }

    /**
     * @see BaseGasOracle#getCacheStats()
     */
    public static BaseGasOracle.CacheStats getL1FeeCacheStats() {
        return BaseGasOracle.getCacheStats();
    }
}
