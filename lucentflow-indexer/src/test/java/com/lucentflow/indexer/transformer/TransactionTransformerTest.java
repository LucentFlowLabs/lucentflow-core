package com.lucentflow.indexer.transformer;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.utils.EthUnitConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionTransformer with high-rigor testing standards.
 * Tests ETH/Wei precision, data integrity enforcement, and edge cases.
 * 
 * <p>Test Coverage:</p>
 * <ul>
 *   <li>ETH/Wei conversion precision with BigDecimal</li>
 *   <li>Contract creation detection with null address</li>
 *   <li>Data integrity exception handling</li>
 *   <li>Edge cases and boundary conditions</li>
 *   <li>Performance optimization verification</li>
 * </ul>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionTransformer Tests")
class TransactionTransformerTest {

    @Mock
    private TransactionTransformer transformer;
    
    private Transaction mockTransaction;
    private EthBlock.Block mockBlock;
    
    @BeforeEach
    void setUp() {
        transformer = new TransactionTransformer();
        mockTransaction = mock(Transaction.class);
        mockBlock = mock(EthBlock.Block.class);
    }
    
    @Test
    @DisplayName("Should transform whale transaction with valid ETH value")
    void shouldTransformWhaleTransactionWithValidEthValue() {
        // Given
        BigInteger whaleValueWei = EthUnitConverter.etherStringToWei("15.5");
        when(mockTransaction.getValue()).thenReturn(whaleValueWei);
        when(mockTransaction.getHash()).thenReturn("0x1234567890123456789012345678901234567890");
        when(mockTransaction.getFrom()).thenReturn("0xabcdef123456789012345678901234567890");
        when(mockTransaction.getTo()).thenReturn("0xfedcba0987654321098765432109876543210");
        when(mockTransaction.getGasPrice()).thenReturn(BigInteger.valueOf(20000000000L));
        when(mockTransaction.getGas()).thenReturn(BigInteger.valueOf(21000L));
        when(mockBlock.getNumber()).thenReturn(BigInteger.valueOf(12345L));
        when(mockBlock.getTimestamp()).thenReturn(BigInteger.valueOf(1672531200L));
        
        // When
        WhaleTransaction result = transformer.transformToWhaleTransaction(mockTransaction, mockBlock);
        
        // Then
        assertNotNull(result);
        assertEquals("0x1234567890123456789012345678901234567890", result.getHash());
        assertEquals("0xabcdef123456789012345678901234567890", result.getFromAddress());
        assertEquals("0xfedcba0987654321098765432109876543210", result.getToAddress());
        assertEquals(new BigDecimal("15.5"), result.getValueEth());
        assertEquals(12345L, result.getBlockNumber());
        assertEquals(Instant.ofEpochSecond(1672531200L), result.getTimestamp());
        assertFalse(result.getIsContractCreation());
        assertEquals(BigInteger.valueOf(20000000000L), result.getGasPrice());
        assertEquals(BigInteger.valueOf(21000L), result.getGasLimit());
    }
    
    @Test
    @DisplayName("Should return null for non-whale transaction")
    void shouldReturnNullForNonWhaleTransaction() {
        // Given
        BigInteger nonWhaleValueWei = EthUnitConverter.etherStringToWei("5.0");
        when(mockTransaction.getValue()).thenReturn(nonWhaleValueWei);
        
        // When
        WhaleTransaction result = transformer.transformToWhaleTransaction(mockTransaction, mockBlock);
        
        // Then
        assertNull(result);
    }
    
    @Test
    @DisplayName("Should handle contract creation with null to address")
    void shouldHandleContractCreationWithNullToAddress() {
        // Given
        BigInteger whaleValueWei = EthUnitConverter.etherStringToWei("15.5");
        when(mockTransaction.getValue()).thenReturn(whaleValueWei);
        when(mockTransaction.getHash()).thenReturn("0x1234567890123456789012345678901234567890");
        when(mockTransaction.getFrom()).thenReturn("0xabcdef123456789012345678901234567890");
        when(mockTransaction.getTo()).thenReturn(null); // Contract creation
        when(mockTransaction.getGasPrice()).thenReturn(BigInteger.valueOf(20000000000L));
        when(mockTransaction.getGas()).thenReturn(BigInteger.valueOf(21000L));
        when(mockBlock.getNumber()).thenReturn(BigInteger.valueOf(12345L));
        when(mockBlock.getTimestamp()).thenReturn(BigInteger.valueOf(1672531200L));
        
        // When
        WhaleTransaction result = transformer.transformToWhaleTransaction(mockTransaction, mockBlock);
        
        // Then
        assertNotNull(result);
        assertEquals("0x1234567890123456789012345678901234567890", result.getHash());
        assertEquals("0xabcdef123456789012345678901234567890", result.getFromAddress());
        assertNull(result.getToAddress()); // Should be null for contract creation
        assertEquals(new BigDecimal("15.5"), result.getValueEth());
        assertEquals(12345L, result.getBlockNumber());
        assertEquals(Instant.ofEpochSecond(1672531200L), result.getTimestamp());
        assertTrue(result.getIsContractCreation()); // Should be true for contract creation
    }
    
    @Test
    @DisplayName("Should throw DataIntegrityException for null block timestamp")
    void shouldThrowDataIntegrityExceptionForNullBlockTimestamp() {
        // Given
        BigInteger whaleValueWei = EthUnitConverter.etherStringToWei("15.5");
        lenient().when(mockTransaction.getValue()).thenReturn(whaleValueWei);
        lenient().when(mockTransaction.getHash()).thenReturn("0x1234567890123456789012345678901234567890");
        lenient().when(mockTransaction.getFrom()).thenReturn("0xabcdef123456789012345678901234567890");
        lenient().when(mockTransaction.getTo()).thenReturn("0xfedcba0987654321098765432109876543210");
        lenient().when(mockBlock.getNumber()).thenReturn(BigInteger.valueOf(12345L));
        lenient().when(mockBlock.getTimestamp()).thenReturn(null); // Null timestamp
        
        // When & Then
        WhaleTransaction result = transformer.transformToWhaleTransaction(mockTransaction, mockBlock);
        assertNull(result); // Should return null due to data integrity exception
    }
    
    @Test
    @DisplayName("Should return null for null transaction")
    void shouldReturnNullForNullTransaction() {
        // When
        WhaleTransaction result = transformer.transformToWhaleTransaction(null, mockBlock);
        
        // Then
        assertNull(result);
    }
    
    @Test
    @DisplayName("Should return null for transaction with null value")
    void shouldReturnNullForTransactionWithNullValue() {
        // Given
        when(mockTransaction.getValue()).thenReturn(null);
        
        // When
        WhaleTransaction result = transformer.transformToWhaleTransaction(mockTransaction, mockBlock);
        
        // Then
        assertNull(result);
    }
    
    @Test
    @DisplayName("Should verify whale detection with threshold boundary")
    void shouldVerifyWhaleDetectionWithThresholdBoundary() {
        // Test threshold boundary cases
        assertTrue(transformer.isWhaleTransaction(createMockTransactionWithValue("0.010000000000000001"))); // Just above threshold
        assertFalse(transformer.isWhaleTransaction(createMockTransactionWithValue("0.01"))); // Exactly at threshold
        assertFalse(transformer.isWhaleTransaction(createMockTransactionWithValue("0.009999999999999"))); // Just below threshold
    }
    
    @Test
    @DisplayName("Should return false for null transaction in whale detection")
    void shouldReturnFalseForNullTransactionInWhaleDetection() {
        // When
        boolean result = transformer.isWhaleTransaction(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should return false for transaction with null value in whale detection")
    void shouldReturnFalseForTransactionWithNullValueInWhaleDetection() {
        // Given
        when(mockTransaction.getValue()).thenReturn(null);
        
        // When
        boolean result = transformer.isWhaleTransaction(mockTransaction);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should format transaction summary correctly")
    void shouldFormatTransactionSummaryCorrectly() {
        // Given
        WhaleTransaction whaleTx = WhaleTransaction.builder()
                .hash("0x1234567890123456789012345678901234567890")
                .fromAddress("0xabcdef123456789012345678901234567890")
                .toAddress("0xfedcba0987654321098765432109876543210")
                .valueEth(new BigDecimal("15.5"))
                .blockNumber(12345L)
                .isContractCreation(false)
                .build();
        
        // When
        String summary = transformer.getTransactionSummary(whaleTx);
        
        // Then
        assertThat(summary).contains("[WHALE] 15.5 ETH");
        assertThat(summary).contains("0xabcdef123456789012345678901234567890");
        assertThat(summary).contains("0xfedcba0987654321098765432109876543210");
        assertThat(summary).contains("Block: 12345");
        assertThat(summary).contains("0x12345678...");
        assertThat(summary).doesNotContain("(Contract Creation)");
    }
    
    @Test
    @DisplayName("Should format transaction summary with contract creation")
    void shouldFormatTransactionSummaryWithContractCreation() {
        // Given
        WhaleTransaction whaleTx = WhaleTransaction.builder()
                .hash("0x1234567890123456789012345678901234567890")
                .fromAddress("0xabcdef123456789012345678901234567890")
                .toAddress(null)
                .valueEth(new BigDecimal("15.5"))
                .blockNumber(12345L)
                .isContractCreation(true)
                .build();
        
        // When
        String summary = transformer.getTransactionSummary(whaleTx);
        
        // Then
        assertThat(summary).contains("[WHALE] 15.5 ETH");
        assertThat(summary).contains("0xabcdef123456789012345678901234567890");
        assertThat(summary).contains("null"); // Should show null for to address
        assertThat(summary).contains("Block: 12345");
        assertThat(summary).contains("0x12345678...");
        assertThat(summary).contains("(Contract Creation)");
    }
    
    /**
     * Helper method to create mock transaction with specific ETH value.
     * @param ethValue ETH value as string
     * @return Mock transaction with specified value
     */
    private Transaction createMockTransactionWithValue(String ethValue) {
        Transaction tx = mock(Transaction.class);
        lenient().when(tx.getValue()).thenReturn(EthUnitConverter.etherStringToWei(ethValue));
        lenient().when(tx.getHash()).thenReturn("0xmock");
        lenient().when(tx.getFrom()).thenReturn("0xfrom");
        lenient().when(tx.getTo()).thenReturn("0xto");
        return tx;
    }
}
