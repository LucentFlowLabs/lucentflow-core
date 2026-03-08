package com.lucentflow.common.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Domain model representing blockchain transaction events for indexing.
 * 
 * <p>Implementation Details:
 * Immutable POJO for blockchain event data transfer between components.
 * Uses BigInteger for precision handling of block numbers and values.
 * Thread-safe through immutable design and proper equals/hashCode implementation.
 * Virtual thread compatible through stateless data transfer operations.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public class ChainEvent {
    
    private String hash;
    private String from;
    private String to;
    private BigInteger value;
    private BigInteger blockNumber;
    private String type;

    public ChainEvent() {
    }

    public ChainEvent(String hash, String from, String to, BigInteger value, BigInteger blockNumber, String type) {
        this.hash = hash;
        this.from = from;
        this.to = to;
        this.value = value;
        this.blockNumber = blockNumber;
        this.type = type;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public BigInteger getValue() {
        return value;
    }

    public void setValue(BigInteger value) {
        this.value = value;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainEvent chainEvent = (ChainEvent) o;
        return Objects.equals(hash, chainEvent.hash) &&
               Objects.equals(from, chainEvent.from) &&
               Objects.equals(to, chainEvent.to) &&
               Objects.equals(value, chainEvent.value) &&
               Objects.equals(blockNumber, chainEvent.blockNumber) &&
               Objects.equals(type, chainEvent.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, from, to, value, blockNumber, type);
    }

    @Override
    public String toString() {
        return "ChainEvent{" +
                "hash='" + hash + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", value=" + value +
                ", blockNumber=" + blockNumber +
                ", type='" + type + '\'' +
                '}';
    }
}
