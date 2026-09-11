package com.lucentflow.ops;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Maven-side deploy gate mirroring {@code lucentflow-deployment/k8s/assert_single_writer.py}.
 * Prevents accidental multi-writer regressions (sync_status ID=1) from landing via {@code mvn verify}.
 *
 * @author ArchLucent
 * @since 1.2
 */
class SingleWriterK8sGateTest {

    private static Path k8sDir;
    private static Path workerProfile;
    private static Path apiProfile;

    @BeforeAll
    static void resolvePaths() {
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = moduleDir.getFileName().toString().equals("lucentflow-api")
                ? moduleDir.getParent()
                : moduleDir;
        k8sDir = repoRoot.resolve("lucentflow-deployment").resolve("k8s");
        workerProfile = repoRoot.resolve("lucentflow-api")
                .resolve("src/main/resources/application-worker.yml");
        apiProfile = repoRoot.resolve("lucentflow-api")
                .resolve("src/main/resources/application-api.yml");
        assertTrue(Files.isDirectory(k8sDir), "k8s dir missing: " + k8sDir);
        assertTrue(Files.isRegularFile(workerProfile), "worker profile missing: " + workerProfile);
        assertTrue(Files.isRegularFile(apiProfile), "api profile missing: " + apiProfile);
    }

    @Test
    void workerDeploymentIsSingleWriter() throws IOException {
        String deployment = read("deployment.yaml");
        String workerDoc = findDoc(deployment, "Deployment", "lucentflow-worker");
        assertTrue(workerDoc != null, "Missing Deployment lucentflow-worker");

        assertMatches(workerDoc, "(?m)^  replicas:\\s*1\\s*$",
                "worker replicas must be exactly 1");
        assertMatches(workerDoc, "(?m)^    type:\\s*Recreate\\s*$",
                "worker strategy must be Recreate");
        assertMatches(workerDoc, "(?m)^[ \\t]+lucentflow\\.io/single-writer:\\s*\"?true\"?\\s*$",
                "missing lucentflow.io/single-writer annotation");
        assertMatches(workerDoc, "(?m)^[ \\t]+lucentflow\\.io/max-replicas:\\s*\"?1\"?\\s*$",
                "missing lucentflow.io/max-replicas annotation");
        assertTrue(workerDoc.contains("--spring.profiles.active=worker"),
                "worker must use spring.profiles.active=worker");
        assertMatches(workerDoc,
                "(?m)^[ \\t]+path:\\s*/actuator/health/readiness\\s*$",
                "worker readinessProbe must use /actuator/health/readiness (db only; not jsonRpc)");
        assertMatches(workerDoc,
                "(?m)^[ \\t]+path:\\s*/actuator/health/liveness\\s*$",
                "worker livenessProbe must use /actuator/health/liveness");
        assertMatches(workerDoc,
                "(?m)^[ \\t]+terminationGracePeriodSeconds:\\s*180\\s*$",
                "worker terminationGracePeriodSeconds must be 180 (pipe drain window)");
        assertMatches(workerDoc,
                "(?m)^[ \\t]+- name:\\s*LUCENTFLOW_RUNTIME_ENABLE_INDEXER\\s*$\\s+[ \\t]+value:\\s*\"true\"",
                "worker must set LUCENTFLOW_RUNTIME_ENABLE_INDEXER=true");
        assertMatches(workerDoc,
                "(?m)^[ \\t]+- name:\\s*LUCENTFLOW_RUNTIME_ENABLE_ANALYZER\\s*$\\s+[ \\t]+value:\\s*\"true\"",
                "worker must set LUCENTFLOW_RUNTIME_ENABLE_ANALYZER=true");
    }

    @Test
    void noServiceOrHpaTargetsWorker() throws IOException {
        for (Path yaml : listYaml()) {
            String text = Files.readString(yaml);
            for (String doc : splitDocs(text)) {
                String kind = kindOf(doc);
                String name = metaName(doc);
                if ("Service".equals(kind)) {
                    assertFalse(hasLabel(doc, "component", "worker"),
                            yaml.getFileName() + ": Service must not select component=worker");
                    assertFalse("lucentflow-worker".equals(name),
                            yaml.getFileName() + ": Service named lucentflow-worker is forbidden");
                }
                if ("HorizontalPodAutoscaler".equals(kind)) {
                    boolean targetsWorker = name != null && name.toLowerCase().contains("worker")
                            || Pattern.compile("(?m)^[ \\t]+name:\\s*lucentflow-worker\\s*$")
                            .matcher(doc).find();
                    assertFalse(targetsWorker,
                            yaml.getFileName() + ": HPA must not target worker until multi-replica failover is validated");
                }
            }
        }
    }

    @Test
    void workerNetworkPolicyDeniesIngress() throws IOException {
        String text = read("networkpolicy.yaml");
        String policy = findDoc(text, "NetworkPolicy", "lucentflow-worker-deny-ingress");
        assertTrue(policy != null, "Missing NetworkPolicy lucentflow-worker-deny-ingress");
        assertTrue(hasLabel(policy, "component", "worker"),
                "deny-ingress policy must select component=worker");
        assertMatches(policy, "(?m)^[ \\t]+-\\s*Ingress\\s*$",
                "policyTypes must include Ingress");
        assertMatches(policy, "(?m)^  ingress:\\s*\\[\\s*]\\s*$",
                "deny-ingress must set ingress: []");
    }

    @Test
    void apiServiceSelectsApiOnly() throws IOException {
        String text = read("service.yaml");
        String svc = findDoc(text, "Service", "lucentflow-api");
        assertTrue(svc != null, "Missing Service lucentflow-api");
        assertTrue(hasLabel(svc, "component", "api"),
                "lucentflow-api Service must select component=api");
        assertFalse(hasLabel(svc, "component", "worker"),
                "lucentflow-api Service must not select component=worker");
    }

    @Test
    void apiAndWorkerProbesSplitLivenessFromReadiness() throws IOException {
        String deployment = read("deployment.yaml");
        String apiDoc = findDoc(deployment, "Deployment", "lucentflow-api");
        assertTrue(apiDoc != null, "Missing Deployment lucentflow-api");
        assertMatches(apiDoc,
                "(?m)^[ \\t]+path:\\s*/actuator/health/readiness\\s*$",
                "api readinessProbe must use /actuator/health/readiness");
        assertMatches(apiDoc,
                "(?m)^[ \\t]+path:\\s*/actuator/health/liveness\\s*$",
                "api livenessProbe must use /actuator/health/liveness");
    }

    @Test
    void apiSpringProfileDisablesIndexer() throws IOException {
        String text = Files.readString(apiProfile);
        assertMatches(text, "(?m)^[ \\t]*enable-indexer:\\s*false\\s*$",
                "application-api.yml must set enable-indexer: false");
        assertMatches(text, "(?m)^[ \\t]*enable-analyzer:\\s*false\\s*$",
                "application-api.yml must set enable-analyzer: false");
        assertMatches(text, "(?m)^[ \\t]*enable-api:\\s*true\\s*$",
                "application-api.yml must set enable-api: true");
    }

    @Test
    void workerSpringProfileDisablesApi() throws IOException {
        String text = Files.readString(workerProfile);
        assertMatches(text, "(?m)^[ \\t]*enable-api:\\s*false\\s*$",
                "application-worker.yml must set enable-api: false");
        assertMatches(text, "(?m)^[ \\t]*enable-indexer:\\s*true\\s*$",
                "application-worker.yml must set enable-indexer: true");
        assertMatches(text, "(?m)^[ \\t]*enable-analyzer:\\s*true\\s*$",
                "application-worker.yml must set enable-analyzer: true");
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(k8sDir.resolve(fileName));
    }

    private static List<Path> listYaml() throws IOException {
        try (Stream<Path> stream = Files.list(k8sDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<String> splitDocs(String text) {
        return Arrays.stream(text.split("(?m)^---\\s*$"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String findDoc(String text, String kind, String name) {
        for (String doc : splitDocs(text)) {
            if (kind.equals(kindOf(doc)) && name.equals(metaName(doc))) {
                return doc;
            }
        }
        return null;
    }

    private static String kindOf(String doc) {
        var m = Pattern.compile("(?m)^kind:\\s*(\\S+)").matcher(doc);
        return m.find() ? m.group(1) : null;
    }

    private static String metaName(String doc) {
        var meta = Pattern.compile("(?m)^metadata:\\s*$").matcher(doc);
        if (!meta.find()) {
            return null;
        }
        String tail = doc.substring(meta.end());
        var name = Pattern.compile("(?m)^  name:\\s*[\"']?([^\\s\"'#]+)[\"']?").matcher(tail);
        return name.find() ? name.group(1) : null;
    }

    private static boolean hasLabel(String doc, String key, String value) {
        return Pattern.compile(
                        "(?m)^[ \\t]+" + Pattern.quote(key) + ":\\s*[\"']?" + Pattern.quote(value) + "[\"']?\\s*$")
                .matcher(doc)
                .find();
    }

    private static void assertMatches(String text, String regex, String message) {
        if (!Pattern.compile(regex).matcher(text).find()) {
            fail(message);
        }
    }
}
