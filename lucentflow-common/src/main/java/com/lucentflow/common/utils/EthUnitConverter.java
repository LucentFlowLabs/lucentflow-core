package com.lucentflow.common.utils;

import org.web3j.utils.Convert;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Precision Ethereum unit conversion utility with BigDecimal accuracy.
 * 
 * <p>Implementation Details:
 * Thread-safe static utility class using Web3j Convert for optimal performance.
 * All conversions maintain 18-decimal precision for financial accuracy.
 * Virtual thread compatible through stateless design and immutable operations.
 * Zero dependency on external services for unit conversions.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public class EthUnitConverter {

    /**
     * Converts Wei to Ether with 18 decimal precision using Web3j Convert.
     * 
     * @param wei the amount in Wei
     * @return the equivalent amount in Ether with 18 decimal places
     * @throws IllegalArgumentException if wei is null
     */
    public static BigDecimal weiToEther(BigInteger wei) {
        if (wei == null) {
            throw new IllegalArgumentException("Wei amount cannot be null");
        }
        return Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
    }

    /**
     * Converts Ether to Wei using Web3j Convert.
     * 
     * @param ether the amount in Ether
     * @return the equivalent amount in Wei
     * @throws IllegalArgumentException if ether is null
     */
    public static BigInteger etherToWei(BigDecimal ether) {
        if (ether == null) {
            throw new IllegalArgumentException("Ether amount cannot be null");
        }
        return Convert.toWei(ether, Convert.Unit.ETHER).toBigInteger();
    }

    /**
     * Converts a string Ether amount to Wei.
     * 
     * @param etherString the amount in Ether as string
     * @return the equivalent amount in Wei
     * @throws IllegalArgumentException if etherString is null or invalid
     */
    public static BigInteger etherStringToWei(String etherString) {
        if (etherString == null || etherString.trim().isEmpty()) {
            throw new IllegalArgumentException("Ether string cannot be null or empty");
        }
        try {
            BigDecimal ether = new BigDecimal(etherString);
            return etherToWei(ether);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Ether amount: " + etherString, e);
        }
    }

    /**
     * Converts Wei to Ether string with 18 decimal precision.
     * 
     * @param wei the amount in Wei
     * @return the equivalent amount in Ether as string with 18 decimal places
     * @throws IllegalArgumentException if wei is null
     */
    public static String weiToEtherString(BigInteger wei) {
        return weiToEther(wei).toString();
    }

    /**
     * Formats a Wei amount to a human-readable Ether string with proper decimal places.
     * Uses BigDecimal.stripTrailingZeros() for cleaner display without regex.
     * 
     * @param wei the amount in Wei
     * @return the formatted Ether string
     * @throws IllegalArgumentException if wei is null
     */
    public static String formatWeiToEther(BigInteger wei) {
        BigDecimal ether = weiToEther(wei);
        return ether.stripTrailingZeros().toPlainString();
    }
}
