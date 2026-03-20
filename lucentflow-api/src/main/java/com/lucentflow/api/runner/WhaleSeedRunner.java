package com.lucentflow.api.runner;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WhaleSeedRunner - Injects real-time (2026) mock data for dashboard visualization.
 * Only runs in local development profile to prevent database bloat.
 * 
 * <p>This runner creates sample whale transactions with realistic 2026 data
 * to demonstrate dashboard capabilities during local development.</p>
 * 
 * @author LucentFlow Core Development Team
 * @since 1.0.0-SEEDER
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class WhaleSeedRunner implements CommandLineRunner {

    private final WhaleTransactionRepository whaleTransactionRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if database already has sufficient data to prevent bloat
        long existingCount = whaleTransactionRepository.count();
        
        if (existingCount >= 100) {
            log.info("📊 Database already contains {} whale transactions. Skipping seeding to prevent bloat.", existingCount);
            return;
        }

        log.info("🚀 Seeding fresh 2026 Whale data for local dashboard visualization...");

        // Use Java 21 Virtual Threads for asynchronous seeding
        try (ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<WhaleTransaction> seedTransactions = createSeedTransactions();
            
            // Submit all transactions as virtual tasks for concurrent processing
            seedTransactions.parallelStream().forEach(transaction -> {
                virtualThreadExecutor.submit(() -> {
                    whaleTransactionRepository.save(transaction);
                    log.debug("💾 Seeded transaction: {} ETH from {} to {}", 
                            transaction.getValueEth(), transaction.getFromAddress(), transaction.getToAddress());
                });
            });
            
            // Wait for all tasks to complete
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            log.info("✅ Seeding complete: {} whale transactions added to database", seedTransactions.size());
        } catch (Exception e) {
            log.error("❌ Error during whale data seeding", e);
            throw e;
        }
    }

    /**
     * Creates realistic 2026 whale transaction data for dashboard demonstration.
     * Uses BigDecimal for 18-decimal precision and LocalDateTime for current timestamp.
     * 
     * @return List of whale transactions with varied data
     */
    private List<WhaleTransaction> createSeedTransactions() {
        Instant now = Instant.now();
        
        return List.of(
            // Binance Hot Wallet - Large transfer
            WhaleTransaction.builder()
                    .hash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                    .fromAddress("0xBinanceHotWallet1234567890abcdef1234567890abcdef12")
                    .toAddress("0xDefiProtocol1234567890abcdef1234567890abcdef12")
                    .toAddressTag("DefiProtocol")
                    .valueEth(new BigDecimal("550.5"))
                    .blockNumber(18500000L)
                    .timestamp(now.minusSeconds(7200))
                    .isContractCreation(false)
                    .transactionType("REGULAR_TRANSFER")
                    .whaleCategory("WHALE")
                    .fromAddressTag("Binance-Hot")
                    .transactionCategory("DeFi")
                    .gasPrice(java.math.BigInteger.valueOf(20000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(21000L))
                    .gasCostEth(new BigDecimal("0.00042"))
                    .build(),
            
            // Vitalik Bridge - Large ETH transfer to Base
            WhaleTransaction.builder()
                    .hash("0xfedcba0987654321fedcba0987654321fedcba0987654321fed")
                    .fromAddress("0xVitalikEthAddress1234567890abcdef1234567890abcdef")
                    .toAddress("0xBaseBridgeProtocol1234567890abcdef1234567890abcdef")
                    .toAddressTag("BaseBridge")
                    .valueEth(new BigDecimal("1200.0"))
                    .blockNumber(18500050L)
                    .timestamp(now.minusSeconds(14400))
                    .isContractCreation(false)
                    .transactionType("BRIDGE_TRANSFER")
                    .whaleCategory("MEGA_WHALE")
                    .fromAddressTag("Vitalik")
                    .transactionCategory("Bridge")
                    .gasPrice(java.math.BigInteger.valueOf(25000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(100000L))
                    .gasCostEth(new BigDecimal("0.0025"))
                    .build(),
            
            // LucentFlow Test Vault - Contract creation
            WhaleTransaction.builder()
                    .hash("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                    .fromAddress("0xDeployerWallet1234567890abcdef1234567890abcdef")
                    .toAddress(null) // Contract creation
                    .toAddressTag("LucentFlowVault")
                    .valueEth(new BigDecimal("88.8"))
                    .blockNumber(18500100L)
                    .timestamp(now.minusSeconds(21600))
                    .isContractCreation(true)
                    .transactionType("CONTRACT_CREATION")
                    .whaleCategory("WHALE")
                    .fromAddressTag("LucentFlow-Deployer")
                    .transactionCategory("DeFi")
                    .gasPrice(java.math.BigInteger.valueOf(30000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(2500000L))
                    .gasCostEth(new BigDecimal("0.075"))
                    .build(),
            
            // Fresh Whale entities - varied transactions
            WhaleTransaction.builder()
                    .hash("0x7890abcdef1234567890abcdef1234567890abcdef1234567890ab")
                    .fromAddress("0xFreshWhale1234567890abcdef1234567890abcdef")
                    .toAddress("0xNewDeFiProtocol1234567890abcdef1234567890abcdef")
                    .toAddressTag("NewDeFi")
                    .valueEth(new BigDecimal("25.3"))
                    .blockNumber(18500150L)
                    .timestamp(now.minusSeconds(28800))
                    .isContractCreation(false)
                    .transactionType("REGULAR_TRANSFER")
                    .whaleCategory("WHALE")
                    .fromAddressTag("Fresh-Whale")
                    .transactionCategory("DeFi")
                    .gasPrice(java.math.BigInteger.valueOf(22000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(50000L))
                    .gasCostEth(new BigDecimal("0.0011"))
                    .build(),
            
            WhaleTransaction.builder()
                    .hash("0x34567890abcdef1234567890abcdef1234567890abcdef1234567890cd")
                    .fromAddress("0xInstitutionalWallet1234567890abcdef1234567890abcdef")
                    .toAddress("0xCexDepositWallet1234567890abcdef1234567890abcdef")
                    .toAddressTag("CentralizedExchange")
                    .valueEth(new BigDecimal("450.0"))
                    .blockNumber(18500200L)
                    .timestamp(now.minusSeconds(43200))
                    .isContractCreation(false)
                    .transactionType("EXCHANGE")
                    .whaleCategory("MEGA_WHALE")
                    .fromAddressTag("Institutional")
                    .transactionCategory("CeFi")
                    .gasPrice(java.math.BigInteger.valueOf(18000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(80000L))
                    .gasCostEth(new BigDecimal("0.00144"))
                    .build(),
            
            WhaleTransaction.builder()
                    .hash("0xcdef1234567890abcdef1234567890abcdef1234567890abcdef123456")
                    .fromAddress("0xMegaWhale1234567890abcdef1234567890abcdef")
                    .toAddress("0xYieldAggregator1234567890abcdef1234567890abcdef")
                    .toAddressTag("YieldAggregator")
                    .valueEth(new BigDecimal("888.0"))
                    .blockNumber(18500250L)
                    .timestamp(now.minusSeconds(64800))
                    .isContractCreation(false)
                    .transactionType("DEFI")
                    .whaleCategory("GIGA_WHALE")
                    .fromAddressTag("Mega-Whale")
                    .transactionCategory("Yield")
                    .gasPrice(java.math.BigInteger.valueOf(15000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(120000L))
                    .gasCostEth(new BigDecimal("0.0018"))
                    .build(),
            
            WhaleTransaction.builder()
                    .hash("0x567890abcdef1234567890abcdef1234567890abcdef1234567890de")
                    .fromAddress("0xProtocolTreasury1234567890abcdef1234567890abcdef")
                    .toAddress("0xLiquidityPool1234567890abcdef1234567890abcdef")
                    .toAddressTag("LiquidityPool")
                    .valueEth(new BigDecimal("156.7"))
                    .blockNumber(18500300L)
                    .timestamp(now.minusSeconds(86400))
                    .isContractCreation(false)
                    .transactionType("DEFI")
                    .whaleCategory("MEGA_WHALE")
                    .fromAddressTag("Protocol-Treasury")
                    .transactionCategory("Liquidity")
                    .gasPrice(java.math.BigInteger.valueOf(16000000000L))
                    .gasLimit(java.math.BigInteger.valueOf(150000L))
                    .gasCostEth(new BigDecimal("0.0024"))
                    .build()
        );
    }
}
