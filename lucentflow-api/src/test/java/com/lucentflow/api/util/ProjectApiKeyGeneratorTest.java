package com.lucentflow.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for project API key generation and hashing.
 *
 * @author ArchLucent
 * @since 1.0
 */
class ProjectApiKeyGeneratorTest {

    @Test
    void generate_usesStablePrefix() {
        String key = ProjectApiKeyGenerator.generate();
        assertThat(key).startsWith("lfproj_");
        assertThat(key.length()).isGreaterThan(20);
    }

    @Test
    void hash_isDeterministicSha256Hex() {
        String key = "demo-project-key-2026";
        String hash1 = ProjectApiKeyGenerator.hash(key);
        String hash2 = ProjectApiKeyGenerator.hash("  " + key + " ");
        assertThat(hash1).hasSize(64).isEqualTo(hash2);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    void prefix_and_maskFromPrefix() {
        assertThat(ProjectApiKeyGenerator.prefix("demo-project-key-2026")).isEqualTo("demo-pro");
        assertThat(ProjectApiKeyGenerator.maskFromPrefix("demo-pro")).isEqualTo("demo-pro...****");
    }

    @Test
    void hash_rejectsBlank() {
        assertThatThrownBy(() -> ProjectApiKeyGenerator.hash("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
