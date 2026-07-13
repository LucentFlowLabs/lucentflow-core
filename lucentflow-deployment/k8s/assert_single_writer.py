#!/usr/bin/env python3
"""
CI / deploy gate: enforce single-writer worker posture in K8s manifests.

HARD CONSTRAINT — until lease-based leader election exists:
  - lucentflow-worker.spec.replicas MUST be 1
  - strategy MUST be Recreate
  - no Service may select component=worker
  - NetworkPolicy must deny worker pod ingress
  - no HPA may target the worker

Exit 0 on pass; exit 1 with actionable errors on failure.

@author ArchLucent
@since 1.2
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

K8S_DIR = Path(__file__).resolve().parent
ERRORS: list[str] = []


def fail(msg: str) -> None:
    ERRORS.append(msg)


def split_docs(text: str) -> list[str]:
    """Split multi-document YAML on --- lines (manifests are indentation-controlled)."""
    parts: list[str] = []
    buf: list[str] = []
    for line in text.splitlines():
        if line.strip() == "---":
            if buf:
                parts.append("\n".join(buf))
                buf = []
            continue
        buf.append(line)
    if buf:
        parts.append("\n".join(buf))
    return [p for p in parts if p.strip()]


def meta_name(doc: str) -> str | None:
    m = re.search(r"(?m)^metadata:\s*$", doc)
    if not m:
        return None
    # name under metadata (first occurrence after metadata:)
    tail = doc[m.end() :]
    nm = re.search(r"(?m)^  name:\s*[\"']?([^\s\"'#]+)[\"']?", tail)
    return nm.group(1) if nm else None


def kind_of(doc: str) -> str | None:
    m = re.search(r"(?m)^kind:\s*(\S+)", doc)
    return m.group(1) if m else None


def has_label(doc: str, key: str, value: str) -> bool:
    # Matches "  key: value" under labels blocks (2–6 space indent typical).
    return bool(
        re.search(
            rf"(?m)^[ \t]+{re.escape(key)}:\s*[\"']?{re.escape(value)}[\"']?\s*$",
            doc,
        )
    )


def load_all_docs() -> list[tuple[Path, str]]:
    docs: list[tuple[Path, str]] = []
    for path in sorted(K8S_DIR.glob("*.yaml")):
        text = path.read_text(encoding="utf-8")
        for doc in split_docs(text):
            docs.append((path, doc))
    return docs


def assert_worker_deployment(docs: list[tuple[Path, str]]) -> None:
    workers = [
        (path, doc)
        for path, doc in docs
        if kind_of(doc) == "Deployment" and meta_name(doc) == "lucentflow-worker"
    ]
    if not workers:
        fail("Missing Deployment metadata.name=lucentflow-worker")
        return
    if len(workers) > 1:
        fail(f"Expected exactly one lucentflow-worker Deployment, found {len(workers)}")

    path, doc = workers[0]

    if not re.search(r"(?m)^  replicas:\s*1\s*$", doc):
        fail(f"{path.name}: lucentflow-worker.spec.replicas must be exactly 1")

    if not re.search(r"(?m)^  strategy:\s*$", doc) or not re.search(
        r"(?m)^    type:\s*Recreate\s*$", doc
    ):
        fail(f"{path.name}: lucentflow-worker.spec.strategy.type must be Recreate")

    if not re.search(
        r'(?m)^[ \t]+lucentflow\.io/single-writer:\s*"?true"?\s*$', doc
    ):
        fail(f"{path.name}: missing annotation lucentflow.io/single-writer: true")

    if not re.search(
        r'(?m)^[ \t]+lucentflow\.io/max-replicas:\s*"?1"?\s*$', doc
    ):
        fail(f"{path.name}: missing annotation lucentflow.io/max-replicas: \"1\"")

    if not has_label(doc, "component", "worker"):
        fail(f"{path.name}: worker Deployment must label component=worker")

    if "--spring.profiles.active=worker" not in doc:
        fail(f"{path.name}: worker container must use --spring.profiles.active=worker")

    if not re.search(
        r'(?m)^[ \t]+- name:\s*LUCENTFLOW_RUNTIME_ENABLE_INDEXER\s*$'
        r'\s+[ \t]+value:\s*"true"',
        doc,
    ):
        fail(f"{path.name}: worker must set LUCENTFLOW_RUNTIME_ENABLE_INDEXER=true")

    if not re.search(
        r'(?m)^[ \t]+- name:\s*LUCENTFLOW_RUNTIME_ENABLE_ANALYZER\s*$'
        r'\s+[ \t]+value:\s*"true"',
        doc,
    ):
        fail(f"{path.name}: worker must set LUCENTFLOW_RUNTIME_ENABLE_ANALYZER=true")


def assert_no_worker_service(docs: list[tuple[Path, str]]) -> None:
    for path, doc in docs:
        if kind_of(doc) != "Service":
            continue
        name = meta_name(doc) or path.name
        # Selector block contains component: worker
        if re.search(r"(?m)^[ \t]+component:\s*worker\s*$", doc):
            fail(
                f"{path.name}: Service '{name}' selects component=worker — "
                "worker must not be exposed via Service"
            )
        if name == "lucentflow-worker" or "worker" in name.lower() and "api" not in name.lower():
            # Explicit name guard for accidental lucentflow-worker Service
            if meta_name(doc) == "lucentflow-worker":
                fail(f"{path.name}: Service named lucentflow-worker is forbidden")


def assert_api_service_targets_api_only(docs: list[tuple[Path, str]]) -> None:
    for path, doc in docs:
        if kind_of(doc) != "Service" or meta_name(doc) != "lucentflow-api":
            continue
        if not has_label(doc, "component", "api"):
            fail(f"{path.name}: lucentflow-api Service must select component=api")
        if has_label(doc, "component", "worker"):
            fail(f"{path.name}: lucentflow-api Service must not select component=worker")


def assert_worker_network_policy(docs: list[tuple[Path, str]]) -> None:
    policies = [
        (path, doc)
        for path, doc in docs
        if kind_of(doc) == "NetworkPolicy"
        and meta_name(doc) == "lucentflow-worker-deny-ingress"
    ]
    if not policies:
        fail("Missing NetworkPolicy lucentflow-worker-deny-ingress")
        return

    path, doc = policies[0]
    if not has_label(doc, "component", "worker"):
        fail(f"{path.name}: deny-ingress NetworkPolicy must select component=worker")

    if not re.search(r"(?m)^[ \t]+-\s*Ingress\s*$", doc):
        fail(f"{path.name}: NetworkPolicy must declare policyTypes Ingress")

    # Deny-all: require explicit empty allow-list.
    if not re.search(r"(?m)^  ingress:\s*\[\s*\]\s*$", doc):
        fail(f"{path.name}: lucentflow-worker-deny-ingress must set ingress: []")


def assert_no_worker_hpa(docs: list[tuple[Path, str]]) -> None:
    for path, doc in docs:
        if kind_of(doc) != "HorizontalPodAutoscaler":
            continue
        name = meta_name(doc) or path.name
        if "worker" in name.lower() or re.search(
            r"(?m)^[ \t]+name:\s*lucentflow-worker\s*$", doc
        ):
            fail(
                f"{path.name}: HorizontalPodAutoscaler '{name}' targets worker — "
                "forbidden until leader election"
            )


def assert_worker_profile_docs() -> None:
    """Belt-and-suspenders: Spring worker profile must disable API MVC."""
    # Resolve repo root: .../lucentflow-deployment/k8s -> repo
    repo = K8S_DIR.parent.parent
    profile = (
        repo
        / "lucentflow-api"
        / "src"
        / "main"
        / "resources"
        / "application-worker.yml"
    )
    if not profile.is_file():
        fail(f"Missing worker Spring profile: {profile}")
        return
    text = profile.read_text(encoding="utf-8")
    if not re.search(r"(?m)^[ \t]*enable-api:\s*false\s*$", text):
        fail("application-worker.yml must set lucentflow.runtime.enable-api: false")
    if not re.search(r"(?m)^[ \t]*enable-indexer:\s*true\s*$", text):
        fail("application-worker.yml must set lucentflow.runtime.enable-indexer: true")
    if not re.search(r"(?m)^[ \t]*enable-analyzer:\s*true\s*$", text):
        fail("application-worker.yml must set lucentflow.runtime.enable-analyzer: true")


def main() -> int:
    if not K8S_DIR.is_dir():
        print(f"ERROR: k8s dir not found: {K8S_DIR}", file=sys.stderr)
        return 1

    docs = load_all_docs()
    if not docs:
        fail(f"No *.yaml manifests under {K8S_DIR}")
    else:
        assert_worker_deployment(docs)
        assert_no_worker_service(docs)
        assert_api_service_targets_api_only(docs)
        assert_worker_network_policy(docs)
        assert_no_worker_hpa(docs)
        assert_worker_profile_docs()

    if ERRORS:
        print("FAIL - single-writer gate", file=sys.stderr)
        for err in ERRORS:
            print(f"  - {err}", file=sys.stderr)
        print(
            "\nScaling workers without leader election races sync_status ID=1.",
            file=sys.stderr,
        )
        return 1

    print("PASS - single-writer gate")
    print("  worker replicas=1, strategy=Recreate")
    print("  no worker Service / HPA")
    print("  NetworkPolicy deny-ingress present")
    print("  application-worker.yml enable-api=false")
    return 0


if __name__ == "__main__":
    sys.exit(main())
