package com.lucentflow.indexer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ArchLucent
 * @since 1.0
 */
class OfficialBaseRpcPolicyTest {

    @Test
    void mainnetBaseOrg_isOfficial() {
        assertTrue(OfficialBaseRpcPolicy.isOfficialBasePublicRpc("https://mainnet.base.org"));
        assertTrue(OfficialBaseRpcPolicy.isOfficialBasePublicRpc("https://mainnet.base.org/v1/"));
    }

    @Test
    void blank_isTreatedAsOfficialSafeDefault() {
        assertTrue(OfficialBaseRpcPolicy.isOfficialBasePublicRpc(""));
        assertTrue(OfficialBaseRpcPolicy.isOfficialBasePublicRpc("   "));
    }

    @Test
    void alchemy_isNotOfficial() {
        assertFalse(OfficialBaseRpcPolicy.isOfficialBasePublicRpc(
                "https://base-mainnet.g.alchemy.com/v2/demo"));
    }
}
