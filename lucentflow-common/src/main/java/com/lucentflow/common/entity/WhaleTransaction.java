package com.lucentflow.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * JPA Entity for storing whale transaction records.
 * Tracks large Ethereum transactions (>10 ETH) with comprehensive details.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "whale_transactions", 
       indexes = {
           @Index(name = "idx_from_address", columnList = "from_address"),
           @Index(name = "idx_to_address", columnList = "to_address"),
           @Index(name = "idx_block_number", columnList = "block_number"),
           @Index(name = "idx_timestamp", columnList = "timestamp"),
           @Index(name = "idx_value_eth", columnList = "value_eth")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash", unique = true, nullable = false, length = 66)
    private String hash;

    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    @Column(name = "to_address", length = 42)
    private String toAddress;

    @Column(name = "value_eth", nullable = false, precision = 38, scale = 18)
    private BigDecimal valueEth;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "is_contract_creation", nullable = false)
    private Boolean isContractCreation;

    @Column(name = "gas_price", precision = 78, scale = 0)
    private BigInteger gasPrice;

    @Column(name = "gas_limit", precision = 78, scale = 0)
    private BigInteger gasLimit;

    @Column(name = "gas_cost_eth", precision = 38, scale = 18)
    private BigDecimal gasCostEth;
    
    @Column(name = "transaction_type", nullable = false, length = 20)
    @Builder.Default
    private String transactionType = "UNKNOWN";
    
    @Column(name = "from_address_tag", length = 50)
    private String fromAddressTag;
    
    @Column(name = "to_address_tag", length = 20)
    private String toAddressTag;
    
    @Column(name = "whale_category", length = 30)
    private String whaleCategory;
    
    @Column(name = "address_tag", length = 50)
    private String addressTag;
    
    @Column(name = "transaction_category", length = 30)
    private String transactionCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        
        // Auto-detect contract creation if toAddress is null
        if (toAddress == null || toAddress.trim().isEmpty()) {
            isContractCreation = true;
            // Keep toAddress null for contract creations
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhaleTransaction)) return false;
        return hash != null && hash.equals(((WhaleTransaction) o).hash);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "WhaleTransaction{" +
                "id=" + id +
                ", hash='" + hash + '\'' +
                ", fromAddress='" + fromAddress + '\'' +
                ", toAddress='" + toAddress + '\'' +
                ", valueEth=" + valueEth +
                ", blockNumber=" + blockNumber +
                ", timestamp=" + timestamp +
                ", isContractCreation=" + isContractCreation +
                ", transactionType='" + transactionType + '\'' +
                ", fromAddressTag='" + fromAddressTag + '\'' +
                ", toAddressTag='" + toAddressTag + '\'' +
                ", whaleCategory='" + whaleCategory + '\'' +
                ", addressTag='" + addressTag + '\'' +
                ", transactionCategory='" + transactionCategory + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
