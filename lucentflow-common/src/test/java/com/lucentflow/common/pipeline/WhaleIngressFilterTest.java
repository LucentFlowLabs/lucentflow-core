package com.lucentflow.common.pipeline;

import com.lucentflow.common.utils.EthUnitConverter;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Indexer and analyzer must share these ingress rules.
 *
 * @author ArchLucent
 * @since 1.2
 */
class WhaleIngressFilterTest {

    private static final String EOA = "0x1111111111111111111111111111111111111111";
    private static final String USDC = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913";

    @Test
    void rejectsNull() {
        assertThat(WhaleIngressFilter.matches(null)).isFalse();
    }

    @Test
    void passesContractCreationRegardlessOfValue() {
        assertThat(WhaleIngressFilter.matches(tx(null, wei("0"), "0x"))).isTrue();
        assertThat(WhaleIngressFilter.matches(tx("  ", wei("0"), "0x"))).isTrue();
    }

    @Test
    void passesCoreTokenCallAtZeroValue() {
        assertThat(WhaleIngressFilter.matches(tx(USDC, wei("0"), "0xa9059cbb00"))).isTrue();
    }

    @Test
    void passesExactTenEthTransfer() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("10"), "0x"))).isTrue();
    }

    @Test
    void dropsTransferJustBelowTenEth() {
        BigInteger justBelow = wei("10").subtract(BigInteger.ONE);
        assertThat(WhaleIngressFilter.matches(tx(EOA, justBelow, "0x"))).isFalse();
    }

    @Test
    void passesExactFiveEthContractCall() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("5"), "0xa9059cbb"))).isTrue();
    }

    @Test
    void dropsContractCallBelowFiveEth() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("4.99"), "0xa9059cbb"))).isFalse();
    }

    @Test
    void passesZeroEthRenounceOwnership() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("0"), "0x715018a6"))).isTrue();
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("0"), "0x715018A6"))).isTrue();
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("0"), "715018a6"))).isTrue();
    }

    @Test
    void dropsZeroEthRandomSelector() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("0"), "0xdeadbeef"))).isFalse();
    }

    @Test
    void doesNotTreatSelectorInCalldataAsRenounce() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("0"), "0xa9059cbb715018a6"))).isFalse();
    }

    @Test
    void rejectsNullValueWhenNotCreationCoreOrRenounce() {
        Transaction tx = mock(Transaction.class);
        when(tx.getTo()).thenReturn(EOA);
        when(tx.getInput()).thenReturn("0xa9059cbb");
        when(tx.getValue()).thenReturn(null);
        assertThat(WhaleIngressFilter.matches(tx)).isFalse();
    }

    @Test
    void fiveEthNativeTransferWithoutSelectorDoesNotPass() {
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("5"), "0x"))).isFalse();
        assertThat(WhaleIngressFilter.matches(tx(EOA, wei("5"), null))).isFalse();
    }

    private static BigInteger wei(String eth) {
        return EthUnitConverter.etherStringToWei(eth);
    }

    private static Transaction tx(String to, BigInteger valueWei, String input) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getTo()).thenReturn(to);
        when(transaction.getValue()).thenReturn(valueWei);
        when(transaction.getInput()).thenReturn(input);
        return transaction;
    }
}
