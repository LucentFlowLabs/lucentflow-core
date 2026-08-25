package com.lucentflow.api.controller;

import com.lucentflow.api.dto.RiskScoreRequest;
import com.lucentflow.api.dto.RiskScoreResponse;
import com.lucentflow.api.service.RiskScoreBusyException;
import com.lucentflow.api.service.RiskScoreService;
import com.lucentflow.api.service.RiskScoreTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Risk Score error responses include a JSON body (400 / 503 / 504).
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class RiskScoreControllerTest {

    @Mock
    private RiskScoreService riskScoreService;

    @InjectMocks
    private RiskScoreController controller;

    @Test
    void score_missingAddress_returns400Json() {
        when(riskScoreService.score(any())).thenThrow(new IllegalArgumentException("address or txHash is required"));

        ResponseEntity<?> response = controller.score(new RiskScoreRequest(null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", 400)
                .containsEntry("error", "Bad Request")
                .containsEntry("message", "address or txHash is required");
    }

    @Test
    void score_timeout_returns504Json() {
        when(riskScoreService.score(any())).thenThrow(new RiskScoreTimeoutException("Risk score lookup timed out"));

        ResponseEntity<?> response = controller.score(new RiskScoreRequest("0xabc", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", 504)
                .containsEntry("error", "Gateway Timeout")
                .containsEntry("message", "Risk score lookup timed out");
    }

    @Test
    void score_busy_returns503Json() {
        when(riskScoreService.score(any())).thenThrow(new RiskScoreBusyException("Risk score RPC permit pool exhausted"));

        ResponseEntity<?> response = controller.score(new RiskScoreRequest("0xabc", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", 503)
                .containsEntry("error", "Service Unavailable")
                .containsEntry("message", "Risk score RPC permit pool exhausted");
    }

    @Test
    void score_ok_returnsBody() {
        RiskScoreResponse payload = mock(RiskScoreResponse.class);
        when(riskScoreService.score(any())).thenReturn(payload);

        ResponseEntity<?> response = controller.score(new RiskScoreRequest("0xabc", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(payload);
    }
}
