package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Transaction;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Risk scoring must not rewrite funding-origin {@code rug_risk_level}.
 *
 * @author ArchLucent
 * @since 1.0
 */
class RiskEngineTest {

    @Test
    void calculateRisk_thenSetRiskScore_preservesFundingOriginRugRiskLevel() {
        WhaleTransaction whaleTx = new WhaleTransaction();
        whaleTx.setRugRiskLevel("CRITICAL");
        whaleTx.setIsContractCreation(false);

        RiskEngine.RiskAssessment assessment = new RiskEngine().calculateRisk(whaleTx, null, 0, 0);
        whaleTx.setRiskScore(assessment.rawScore());

        assertThat(assessment.rawScore()).isEqualTo(40);
        assertThat(whaleTx.getRiskScore()).isEqualTo(40);
        assertThat(whaleTx.getRugRiskLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void clampScore_capsAtOneHundred() {
        assertThat(RiskEngine.clampScore(165)).isEqualTo(100);
        assertThat(RiskEngine.clampScore(-4)).isEqualTo(0);
        assertThat(RiskEngine.clampScore(70)).isEqualTo(70);
    }

    @Test
    void complete_addsBlacklistThenRevertAndClamps() {
        RiskEngine engine = new RiskEngine();
        RiskEngine.RiskAssessment base = new RiskEngine.RiskAssessment(90, Map.of("CONTRACT_CREATION", 30));

        RiskEngine.CompletedScore scored = engine.complete(base, true, true);

        assertThat(scored.rawScore()).isEqualTo(90 + 35 + 40);
        assertThat(scored.score()).isEqualTo(100);
        assertThat(scored.reasons())
                .containsEntry(RiskEngine.REASON_BLACKLISTED_FUNDING, 35)
                .containsEntry(RiskEngine.REASON_REVERT_PROBE, 40)
                .containsEntry("CONTRACT_CREATION", 30);
    }

    @Test
    void shouldTraceGenesis_matchesIngestGate() {
        assertThat(RiskEngine.shouldTraceGenesis(40)).isFalse();
        assertThat(RiskEngine.shouldTraceGenesis(41)).isTrue();
        assertThat(RiskEngine.rawAfterRevert(
                new RiskEngine.RiskAssessment(10, Map.of()), true)).isEqualTo(50);
    }

    @Test
    void calculateRisk_renounceUsesSelectorPrefixNotSubstring() {
        WhaleTransaction whaleTx = new WhaleTransaction();
        whaleTx.setRugRiskLevel("LOW");
        whaleTx.setIsContractCreation(false);
        Transaction tx = mock(Transaction.class);
        when(tx.getInput()).thenReturn("0xa9059cbb715018a6");

        RiskEngine.RiskAssessment assessment = new RiskEngine().calculateRisk(whaleTx, tx, 0, 0);

        assertThat(assessment.reasons()).doesNotContainKey("RENOUNCE_OWNERSHIP");
    }
}
