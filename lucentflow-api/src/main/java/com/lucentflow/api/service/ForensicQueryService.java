package com.lucentflow.api.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.api.dto.ForensicEventDTO;
import com.lucentflow.api.spec.WhaleTransactionSpecifications;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.WatchlistRepository;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Read-only forensic query service backed by JPA Specifications.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class ForensicQueryService {

    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.DESC, "timestamp");
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final WhaleTransactionRepository whaleTransactionRepository;
    private final WatchlistRepository watchlistRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ForensicEventDTO> queryEvents(
            Integer minRiskScore,
            Integer maxRiskScore,
            String address,
            String bytecodeHash,
            String reason,
            Long projectId,
            Pageable pageable
    ) {
        Specification<WhaleTransaction> spec = buildSpecification(minRiskScore, maxRiskScore, address, bytecodeHash, reason, projectId);
        return whaleTransactionRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public void exportJson(
            Integer minRiskScore,
            Integer maxRiskScore,
            String address,
            String bytecodeHash,
            String reason,
            Long projectId,
            OutputStream outputStream
    ) throws IOException {
        Specification<WhaleTransaction> spec = buildSpecification(minRiskScore, maxRiskScore, address, bytecodeHash, reason, projectId);
        try (Stream<WhaleTransaction> stream = streamBySpecification(spec);
             JsonGenerator generator = objectMapper.getFactory().createGenerator(outputStream)) {
            generator.writeStartArray();
            stream.map(this::toDto).forEach(dto -> {
                try {
                    generator.writeObject(dto);
                } catch (IOException e) {
                    throw new UncheckedExportIOException(e);
                }
            });
            generator.writeEndArray();
            generator.flush();
        } catch (UncheckedExportIOException e) {
            throw e.getCause();
        }
    }

    @Transactional(readOnly = true)
    public void exportCsv(
            Integer minRiskScore,
            Integer maxRiskScore,
            String address,
            String bytecodeHash,
            String reason,
            Long projectId,
            OutputStream outputStream
    ) throws IOException {
        Specification<WhaleTransaction> spec = buildSpecification(minRiskScore, maxRiskScore, address, bytecodeHash, reason, projectId);
        outputStream.write(UTF8_BOM);
        try (Stream<WhaleTransaction> stream = streamBySpecification(spec);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.write("hash,blockNumber,timestamp,fromAddress,toAddress,valueEth,riskScore,rugRiskLevel,executionStatus,isContractCreation,bytecodeHash,riskReasons");
            writer.newLine();
            stream.map(this::toDto).forEach(dto -> {
                try {
                    writer.write(toCsvLine(dto));
                    writer.newLine();
                } catch (IOException e) {
                    throw new UncheckedExportIOException(e);
                }
            });
            writer.flush();
        } catch (UncheckedExportIOException e) {
            throw e.getCause();
        }
    }

    private Specification<WhaleTransaction> buildSpecification(
            Integer minRiskScore,
            Integer maxRiskScore,
            String address,
            String bytecodeHash,
            String reason,
            Long projectId
    ) {
        Specification<WhaleTransaction> base = Specification.where(WhaleTransactionSpecifications.minRiskScore(minRiskScore))
                .and(WhaleTransactionSpecifications.maxRiskScore(maxRiskScore))
                .and(WhaleTransactionSpecifications.address(address))
                .and(WhaleTransactionSpecifications.bytecodeHash(bytecodeHash))
                .and(WhaleTransactionSpecifications.reasonContains(reason));
        if (projectId == null) {
            return base;
        }
        Set<String> addresses = watchlistRepository.findAllByProjectId(projectId).stream()
                .map(Watchlist::getAddress)
                .filter(a -> a != null && !a.isBlank())
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        // Empty watchlist ⇒ empty result set (tenant isolation). addressInSet([]) is a disjunction.
        return base.and(WhaleTransactionSpecifications.addressInSet(addresses));
    }

    private Stream<WhaleTransaction> streamBySpecification(Specification<WhaleTransaction> spec) {
        return whaleTransactionRepository.findBy(spec, (FluentQuery.FetchableFluentQuery<WhaleTransaction> q) ->
                q.sortBy(EXPORT_SORT).stream()
        );
    }

    private ForensicEventDTO toDto(WhaleTransaction tx) {
        return new ForensicEventDTO(
                tx.getHash(),
                tx.getBlockNumber(),
                tx.getTimestamp(),
                tx.getFromAddress(),
                tx.getToAddress(),
                tx.getValueEth(),
                tx.getRiskScore(),
                tx.getRugRiskLevel(),
                tx.getExecutionStatus(),
                tx.getIsContractCreation(),
                tx.getBytecodeHash(),
                tx.getRiskReasons() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tx.getRiskReasons())
        );
    }

    private String toCsvLine(ForensicEventDTO dto) {
        return String.join(",",
                csv(dto.hash()),
                csv(valueOf(dto.blockNumber())),
                csv(valueOf(dto.timestamp())),
                csv(dto.fromAddress()),
                csv(dto.toAddress()),
                csv(valueOf(dto.valueEth())),
                csv(valueOf(dto.riskScore())),
                csv(dto.rugRiskLevel()),
                csv(dto.executionStatus()),
                csv(valueOf(dto.isContractCreation())),
                csv(dto.bytecodeHash()),
                csv(flattenReasons(dto.riskReasons()))
        );
    }

    private String flattenReasons(Map<String, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "";
        }
        return reasons.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class UncheckedExportIOException extends RuntimeException {
        private final IOException cause;

        private UncheckedExportIOException(IOException cause) {
            super(cause);
            this.cause = cause;
        }

        @Override
        public IOException getCause() {
            return cause;
        }
    }
}
