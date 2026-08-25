package com.lucentflow.analyzer.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-address maps must store and look up lowercase keys ({@link Locale#ROOT}).
 *
 * @author ArchLucent
 * @since 1.0
 */
class AddressLabelerTest {

    private static final String COINBASE_CHECKSUM = "0x49048044D57e1C23A120ab3913D2258d96af6E56";
    private static final String WETH_CHECKSUM = "0x4200000000000000000000000000000000000006";
    private static final String USDC_CHECKSUM = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913";
    private static final String UNISWAP_V3_ROUTER = "0x26213694093010b985442A2338BCe7E690558133";
    private static final String AERODROME_ROUTER = "0x327Df1E6bcbf968d84a78cE91f97FAbDc9d267cb";
    private static final String UNKNOWN_EOA = "0x1111111111111111111111111111111111111111";

    @Test
    void getAddressLabel_checksumAndLowercase_hitTheSameKnownLabel() {
        AddressLabeler labeler = new AddressLabeler();

        assertThat(labeler.getAddressLabel(COINBASE_CHECKSUM)).isEqualTo("Coinbase Proxy");
        assertThat(labeler.getAddressLabel(COINBASE_CHECKSUM.toLowerCase(Locale.ROOT)))
                .isEqualTo("Coinbase Proxy");
        assertThat(labeler.getAddressLabel(WETH_CHECKSUM)).isEqualTo("WETH");
        assertThat(labeler.getAddressLabel(WETH_CHECKSUM.toUpperCase(Locale.ROOT))).isEqualTo("WETH");
        assertThat(labeler.getAddressLabel(USDC_CHECKSUM)).isEqualTo("USDC");
        assertThat(labeler.getAddressLabel(USDC_CHECKSUM.toLowerCase(Locale.ROOT))).isEqualTo("USDC");
    }

    @Test
    void knownAddresses_areStoredLowercaseRoot() {
        AddressLabeler labeler = new AddressLabeler();

        assertThat(labeler.getKnownAddresses())
                .containsKey(COINBASE_CHECKSUM.toLowerCase(Locale.ROOT))
                .doesNotContainKey(COINBASE_CHECKSUM);
    }

    @Test
    void getTransactionCategory_coinbaseIsExchange_routersAndUsdcAreDefi() {
        AddressLabeler labeler = new AddressLabeler();
        BigDecimal oneEth = BigDecimal.ONE;

        assertThat(labeler.getTransactionCategory(UNKNOWN_EOA, COINBASE_CHECKSUM, oneEth))
                .isEqualTo("EXCHANGE");
        assertThat(labeler.getTransactionCategory(UNKNOWN_EOA, UNISWAP_V3_ROUTER, oneEth))
                .isEqualTo("DEFI");
        assertThat(labeler.getTransactionCategory(UNKNOWN_EOA, AERODROME_ROUTER, oneEth))
                .isEqualTo("DEFI");
        assertThat(labeler.getTransactionCategory(UNKNOWN_EOA, USDC_CHECKSUM, oneEth))
                .isEqualTo("DEFI");
        assertThat(labeler.getTransactionCategory(UNKNOWN_EOA, WETH_CHECKSUM, oneEth))
                .isEqualTo("DEFI");
    }
}
