package com.lucentflow.common.pipeline;

import com.lucentflow.common.constant.BaseChainConstants;
import com.lucentflow.common.utils.Erc20Decoder;
import com.lucentflow.common.utils.EthUnitConverter;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Canonical whale ingress rules shared by indexer pipe push and analyzer drain.
 *
 * <p>Pass through: contract creation, core-token contract calls, {@code renounceOwnership},
 * contract calls of at least {@link #CONTRACT_CALL_THRESHOLD_ETH} ETH, and native transfers of
 * at least {@link #TRANSFER_THRESHOLD_ETH} ETH (inclusive).</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
public final class WhaleIngressFilter {

    public static final BigDecimal TRANSFER_THRESHOLD_ETH = BaseChainConstants.WHALE_THRESHOLD;
    public static final BigDecimal CONTRACT_CALL_THRESHOLD_ETH = new BigDecimal("5.0");

    /** First 4 bytes of {@code renounceOwnership()} — compared case-insensitively after optional {@code 0x}. */
    public static final String RENOUNCE_OWNERSHIP_SELECTOR = "715018a6";

    private static final BigInteger TRANSFER_THRESHOLD_WEI = EthUnitConverter.etherStringToWei("10");
    private static final BigInteger CONTRACT_CALL_THRESHOLD_WEI = EthUnitConverter.etherStringToWei("5");

    private WhaleIngressFilter() {
    }

    /**
     * @param tx raw chain transaction, may be {@code null}
     * @return {@code true} when the tx must enter {@link TransactionPipe} / analyzer scoring
     */
    public static boolean matches(Transaction tx) {
        if (tx == null) {
            return false;
        }
        String to = tx.getTo();
        if (to == null || to.isBlank()) {
            return true;
        }
        if (Erc20Decoder.isCoreTokenContract(to)) {
            return true;
        }
        String input = tx.getInput();
        if (isRenounceOwnership(input)) {
            return true;
        }
        if (tx.getValue() == null) {
            return false;
        }
        if (isContractCall(input) && tx.getValue().compareTo(CONTRACT_CALL_THRESHOLD_WEI) >= 0) {
            return true;
        }
        return tx.getValue().compareTo(TRANSFER_THRESHOLD_WEI) >= 0;
    }

    static boolean isContractCall(String input) {
        return selectorStart(input) >= 0;
    }

    /**
     * {@code renounceOwnership()} selector at the start of calldata (not a substring match).
     *
     * @param input transaction input hex, optional {@code 0x} prefix
     * @return true when the 4-byte selector is {@link #RENOUNCE_OWNERSHIP_SELECTOR}
     */
    public static boolean isRenounceOwnership(String input) {
        int start = selectorStart(input);
        if (start < 0) {
            return false;
        }
        return input.regionMatches(true, start, RENOUNCE_OWNERSHIP_SELECTOR, 0, 8);
    }

    /**
     * @return index of the 4-byte selector in {@code input}, or {@code -1} if none
     */
    private static int selectorStart(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        int start = startsWith0x(input) ? 2 : 0;
        if (input.length() - start < 8) {
            return -1;
        }
        return start;
    }

    private static boolean startsWith0x(String input) {
        return input.length() >= 2
                && input.charAt(0) == '0'
                && (input.charAt(1) == 'x' || input.charAt(1) == 'X');
    }
}
