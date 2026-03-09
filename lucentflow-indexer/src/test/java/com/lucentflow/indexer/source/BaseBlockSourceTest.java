package com.lucentflow.indexer.source;

import com.lucentflow.common.entity.SyncStatus;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.exceptions.ClientConnectionException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Enterprise-grade unit tests for BaseBlockSource blockchain data extraction.
 * Tests Web3j integration, retry logic, block range calculation, and error handling.
 * 
 * <p>Test Coverage Matrix:</p>
 * <ul>
 *   <li>Web3j ethBlockNumber() integration with proper response handling</li>
 *   <li>Resilience4j retry logic for HTTP 429 rate limiting scenarios</li>
 *   <li>Block range calculation between lastScannedBlock and chain head</li>
 *   <li>Error handling for network failures and invalid responses</li>
 *   <li>Zero-loss pipeline integration with TransactionPipe</li>
 * </ul>
 * 
 * <p>Performance Characteristics:</p>
 * <ul>
 *   <li>O(1) block number fetching with automatic retry</li>
 *   <li>O(n) block range processing where n = block count in range</li>
 *   <li>Fault-tolerant with exponential backoff retry strategy</li>
 *   <li>Zero-loss guarantee through blocking queue semantics</li>
 * </ul>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BaseBlockSource Blockchain Integration Tests")
class BaseBlockSourceTest {

    @Mock
    private Web3j web3j;
    
    @Mock
    private SyncStatusRepository syncStatusRepository;
    
    @Mock
    private TransactionPipe transactionPipe;
    
    @Mock
    private Request<?, EthBlockNumber> mockBlockNumberRequest;
    
    @Mock
    private Request<?, EthBlock> mockBlockRequest;
    
    @Mock
    private EthBlockNumber mockEthBlockNumber;
    
    @Mock
    private EthBlock mockEthBlock;
    
    private BaseBlockSource baseBlockSource;
    
    @BeforeEach
    void setUp() {
        baseBlockSource = new BaseBlockSource(web3j, syncStatusRepository, transactionPipe);
    }
    
    @Test
    @DisplayName("Should fetch latest block number successfully")
    void shouldFetchLatestBlockNumberSuccessfully() throws Exception {
        // Given
        BigInteger expectedBlockNumber = BigInteger.valueOf(12345678);
        
        // Use doReturn pattern to avoid generic capture issues
        doReturn(mockBlockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(mockEthBlockNumber).when(mockBlockNumberRequest).send();
        when(mockEthBlockNumber.getBlockNumber()).thenReturn(expectedBlockNumber);
        
        // When
        long actualBlockNumber = baseBlockSource.getLatestBlockNumber();
        
        // Then
        assertThat(actualBlockNumber).isEqualTo(expectedBlockNumber.longValue());
        verify(web3j, times(1)).ethBlockNumber();
        verify(mockBlockNumberRequest, times(1)).send();
        verify(mockEthBlockNumber, times(1)).getBlockNumber();
    }
    
    @Test
    @DisplayName("Should retry HTTP 429 errors and eventually succeed")
    void shouldRetryHttp429ErrorsAndEventuallySucceed() throws Exception {
        // Given
        BigInteger expectedBlockNumber = BigInteger.valueOf(87654321);
        
        // Use doReturn pattern for proper mocking
        doReturn(mockBlockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(mockEthBlockNumber).when(mockBlockNumberRequest).send();
        when(mockEthBlockNumber.getBlockNumber()).thenReturn(expectedBlockNumber);
        
        // Configure request to fail twice with HTTP 429, then succeed
        when(mockBlockNumberRequest.send())
            .thenThrow(new ClientConnectionException("HTTP 429 Too Many Requests"))
            .thenThrow(new ClientConnectionException("HTTP 429 Too Many Requests"))
            .thenReturn(mockEthBlockNumber);
        
        // When
        long actualBlockNumber = baseBlockSource.getLatestBlockNumber();
        
        // Then
        assertThat(actualBlockNumber).isEqualTo(expectedBlockNumber.longValue());
        verify(mockBlockNumberRequest, times(3)).send(); // 1 initial + 2 retries
        verify(mockEthBlockNumber, times(1)).getBlockNumber();
    }
    
    @Test
    @DisplayName("Should calculate block range to process correctly")
    void shouldCalculateBlockRangeToProcessCorrectly() throws Exception {
        // Given
        BigInteger currentBlockNumber = BigInteger.valueOf(1000000);
        BigInteger lastScannedBlock = BigInteger.valueOf(999950);
        
        // Use doReturn pattern
        doReturn(mockBlockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(mockEthBlockNumber).when(mockBlockNumberRequest).send();
        when(mockEthBlockNumber.getBlockNumber()).thenReturn(currentBlockNumber);
        
        SyncStatus syncStatus = new SyncStatus();
        syncStatus.setLastScannedBlock(lastScannedBlock.longValue());
        
        when(syncStatusRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(syncStatus));
        
        // Initialize last scanned block
        baseBlockSource.initializeLastScannedBlock();
        
        // When
        long[] blockRange = baseBlockSource.getBlockRangeToProcess();
        
        // Then
        assertThat(blockRange).hasSize(2);
        assertThat(blockRange[0]).isEqualTo(999951); // First unscanned block
        assertThat(blockRange[1]).isEqualTo(1000000); // Current block
        verify(web3j, times(2)).ethBlockNumber(); // Once for init, once for range
        verify(syncStatusRepository, times(2)).findFirstByOrderByIdDesc();
    }
    
    @Test
    @DisplayName("Should handle empty block range when fully synced")
    void shouldHandleEmptyBlockRangeWhenFullySynced() throws Exception {
        // Given
        BigInteger currentBlockNumber = BigInteger.valueOf(1000000);
        BigInteger lastScannedBlock = BigInteger.valueOf(1000000); // Fully synced
        
        // Use doReturn pattern
        doReturn(mockBlockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(mockEthBlockNumber).when(mockBlockNumberRequest).send();
        when(mockEthBlockNumber.getBlockNumber()).thenReturn(currentBlockNumber);
        
        SyncStatus syncStatus = new SyncStatus();
        syncStatus.setLastScannedBlock(lastScannedBlock.longValue());
        
        when(syncStatusRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(syncStatus));
        
        // Initialize last scanned block
        baseBlockSource.initializeLastScannedBlock();
        
        // When
        long[] blockRange = baseBlockSource.getBlockRangeToProcess();
        
        // Then
        assertThat(blockRange).isEmpty(); // No blocks to process
        verify(web3j, times(2)).ethBlockNumber(); // Once for init, once for range
        verify(syncStatusRepository, times(2)).findFirstByOrderByIdDesc();
    }
    
    @Test
    @DisplayName("Should fetch block by number with retry logic")
    void shouldFetchBlockByNumberWithRetryLogic() throws Exception {
        // Given
        BigInteger blockNumber = BigInteger.valueOf(123456);
        EthBlock.Block mockBlock = mock(EthBlock.Block.class);
        
        // Use doReturn pattern for ethGetBlockByNumber
        doReturn(mockBlockRequest).when(web3j).ethGetBlockByNumber(any(), anyBoolean());
        doReturn(mockEthBlock).when(mockBlockRequest).sendAsync();
        when(mockEthBlock.getBlock()).thenReturn(mockBlock);
        when(mockBlock.getNumber()).thenReturn(blockNumber);
        when(mockBlockRequest.sendAsync()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mockEthBlock));
        
        // When
        EthBlock.Block actualBlock = baseBlockSource.fetchBlock(blockNumber.longValue());
        
        // Then
        assertThat(actualBlock).isNotNull();
        assertThat(actualBlock.getNumber()).isEqualTo(blockNumber);
        verify(web3j, times(1)).ethGetBlockByNumber(any(), anyBoolean());
        verify(mockBlockRequest, times(1)).sendAsync();
        verify(mockEthBlock, times(1)).getBlock();
        verify(mockBlock, times(1)).getNumber();
    }
    
    @Test
    @DisplayName("Should return null when block fetch fails after retries")
    void shouldReturnNullWhenBlockFetchFailsAfterRetries() throws Exception {
        // Given
        BigInteger blockNumber = BigInteger.valueOf(123456);
        
        // Use doReturn pattern
        doReturn(mockBlockRequest).when(web3j).ethGetBlockByNumber(any(), anyBoolean());
        doReturn(mockEthBlock).when(mockBlockRequest).sendAsync();
        
        // Configure request to fail 3 times (exhaust retries)
        when(mockBlockRequest.sendAsync())
            .thenThrow(new ClientConnectionException("HTTP 429 Too Many Requests"))
            .thenThrow(new ClientConnectionException("HTTP 429 Too Many Requests"))
            .thenThrow(new ClientConnectionException("HTTP 429 Too Many Requests"));
        
        // When
        EthBlock.Block actualBlock = baseBlockSource.fetchBlock(blockNumber.longValue());
        
        // Then
        assertThat(actualBlock).isNull(); // Should return null on failure
        verify(web3j, times(3)).ethGetBlockByNumber(any(), anyBoolean()); // Exhaust all retries
        verify(mockBlockRequest, times(3)).sendAsync();
    }
    
    @Test
    @DisplayName("Should extract transactions from block successfully")
    void shouldExtractTransactionsFromBlockSuccessfully() throws Exception {
        // Given
        BigInteger blockNumber = BigInteger.valueOf(123456);
        EthBlock.Block mockBlock = mock(EthBlock.Block.class);
        
        // Create mock transaction list
        List<EthBlock.TransactionResult> transactionResults = new ArrayList<>();
        EthBlock.TransactionResult txResult1 = mock(EthBlock.TransactionResult.class);
        EthBlock.TransactionResult txResult2 = mock(EthBlock.TransactionResult.class);
        EthBlock.TransactionResult txResult3 = mock(EthBlock.TransactionResult.class);
        
        Transaction tx1 = createMockTransaction("0x123");
        Transaction tx2 = createMockTransaction("0x456");
        Transaction tx3 = createMockTransaction("0x789");
        
        // Configure transaction mocks
        when(txResult1.get()).thenReturn(tx1);
        when(txResult2.get()).thenReturn(tx2);
        when(txResult3.get()).thenReturn(tx3);
        
        transactionResults.add(txResult1);
        transactionResults.add(txResult2);
        transactionResults.add(txResult3);
        
        // Configure block mock
        when(mockBlock.getTransactions()).thenReturn(transactionResults);
        when(mockBlock.getNumber()).thenReturn(blockNumber);
        
        // When
        List<Transaction> transactions = baseBlockSource.getTransactionsFromBlock(mockBlock);
        
        // Then
        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getHash()).isEqualTo("0x123");
        assertThat(transactions.get(1).getHash()).isEqualTo("0x456");
        assertThat(transactions.get(2).getHash()).isEqualTo("0x789");
        
        verify(mockBlock, times(1)).getTransactions();
        verify(txResult1, times(1)).get();
        verify(txResult2, times(1)).get();
        verify(txResult3, times(1)).get();
    }
    
    @Test
    @DisplayName("Should handle block with no transactions")
    void shouldHandleBlockWithNoTransactions() throws Exception {
        // Given
        BigInteger blockNumber = BigInteger.valueOf(123456);
        EthBlock.Block mockBlock = mock(EthBlock.Block.class);
        
        // Configure empty transaction list
        List<EthBlock.TransactionResult> emptyTransactionResults = new ArrayList<>();
        when(mockBlock.getTransactions()).thenReturn(emptyTransactionResults);
        when(mockBlock.getNumber()).thenReturn(blockNumber);
        
        // When
        List<Transaction> transactions = baseBlockSource.getTransactionsFromBlock(mockBlock);
        
        // Then
        assertThat(transactions).isEmpty();
        verify(mockBlock, times(1)).getTransactions();
    }
    
    @Test
    @DisplayName("Should push whale transactions to pipe")
    void shouldPushWhaleTransactionsToPipe() throws Exception {
        // Given
        BigInteger blockNumber = BigInteger.valueOf(123456);
        EthBlock.Block mockBlock = mock(EthBlock.Block.class);
        
        // Create mock transaction list with whale transactions
        List<EthBlock.TransactionResult> transactionResults = new ArrayList<>();
        EthBlock.TransactionResult whaleTxResult1 = mock(EthBlock.TransactionResult.class);
        EthBlock.TransactionResult smallTxResult = mock(EthBlock.TransactionResult.class);
        EthBlock.TransactionResult whaleTxResult2 = mock(EthBlock.TransactionResult.class);
        
        Transaction whaleTx1 = createMockWhaleTransaction("0xwhale1"); // > 10 ETH
        Transaction smallTx = createMockTransaction("0xsmall1");      // < 10 ETH
        Transaction whaleTx2 = createMockWhaleTransaction("0xwhale2");  // > 10 ETH
        
        // Configure transaction mocks
        when(whaleTxResult1.get()).thenReturn(whaleTx1);
        when(smallTxResult.get()).thenReturn(smallTx);
        when(whaleTxResult2.get()).thenReturn(whaleTx2);
        
        transactionResults.add(whaleTxResult1);
        transactionResults.add(smallTxResult);
        transactionResults.add(whaleTxResult2);
        
        // Configure block mock
        when(mockBlock.getTransactions()).thenReturn(transactionResults);
        when(mockBlock.getNumber()).thenReturn(blockNumber);
        
        // When
        List<Transaction> transactions = baseBlockSource.getTransactionsFromBlock(mockBlock);
        
        // Then
        assertThat(transactions).hasSize(3);
        verify(transactionPipe, times(2)).push(any(Transaction.class)); // Only whale transactions pushed
        verify(mockBlock, times(1)).getTransactions();
    }
    
    @Test
    @DisplayName("Should detect new blocks correctly")
    void shouldDetectNewBlocksCorrectly() throws Exception {
        // Given
        BigInteger currentBlockNumber = BigInteger.valueOf(1000000);
        BigInteger lastScannedBlock = BigInteger.valueOf(999950);
        
        // Use doReturn pattern
        doReturn(mockBlockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(mockEthBlockNumber).when(mockBlockNumberRequest).send();
        when(mockEthBlockNumber.getBlockNumber()).thenReturn(currentBlockNumber);
        
        SyncStatus syncStatus = new SyncStatus();
        syncStatus.setLastScannedBlock(lastScannedBlock.longValue());
        
        when(syncStatusRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(syncStatus));
        
        // Initialize last scanned block
        baseBlockSource.initializeLastScannedBlock();
        
        // When
        boolean hasNewBlocks = baseBlockSource.hasNewBlocks();
        
        // Then
        assertThat(hasNewBlocks).isTrue();
        verify(web3j, times(2)).ethBlockNumber(); // Once for init, once for check
        verify(syncStatusRepository, times(2)).findFirstByOrderByIdDesc();
    }
    
    /**
     * Helper method to create mock Transaction for testing.
     * 
     * @param hash Transaction hash
     * @return Configured Transaction instance
     */
    private Transaction createMockTransaction(String hash) {
        Transaction tx = new Transaction();
        tx.setHash(hash);
        tx.setFrom("0xfrom1234567890123456789012345678901234567890");
        tx.setTo("0xto1234567890123456789012345678901234567890");
        tx.setValue("500000000000000000"); // 0.5 ETH (not whale)
        tx.setGas("21000");
        tx.setGasPrice("20000000000");
        tx.setNonce("1");
        tx.setBlockNumber("12345");
        tx.setTransactionIndex("0");
        return tx;
    }
    
    /**
     * Helper method to create mock whale Transaction (>10 ETH) for testing.
     * 
     * @param hash Transaction hash
     * @return Configured Transaction instance with whale value
     */
    private Transaction createMockWhaleTransaction(String hash) {
        Transaction tx = new Transaction();
        tx.setHash(hash);
        tx.setFrom("0xfrom1234567890123456789012345678901234567890");
        tx.setTo("0xto1234567890123456789012345678901234567890");
        tx.setValue("20000000000000000000"); // 20 ETH (whale)
        tx.setGas("21000");
        tx.setGasPrice("20000000000");
        tx.setNonce("1");
        tx.setBlockNumber("12345");
        tx.setTransactionIndex("0");
        return tx;
    }
}
