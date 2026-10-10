<!--
Copyright (c) 2026 Innovar Healthcare
Licensed under the MPL-2.0.
-->

# BridgeLink RPM Packaging

This directory holds `bridgelink.spec`, the RPM spec for BridgeLink. It closes
issue [#93](https://github.com/Innovar-Healthcare/BridgeLink/issues/93): on
FIPS-mode RHEL 8/9 and AlmaLinux 9 hosts, RPMs built with the historical RPM
default (MD5 cpio file digests) are rejected by the kernel with `cpio: Digest
mismatch`. The spec declares `%global _binary_filedigest_algorithm 8` and
`%global _source_filedigest_algorithm 8` in the spec body itself (so the
build is reproducible regardless of `~/.rpmmacros` on the build host), which
selects SHA-256 (algorithm `8`) for both binary and source payload digests.

Maintainer: Innovar Healthcare.

Wiring `rpmbuild` into `.github/workflows/build_bridgelink.yml` is out of
scope for this milestone — see the "Out of scope" section below.

## Build recipe (Docker, AlmaLinux 9)

```bash
# 1. Produce the source tarball expected by Source0. CI emits bridgelink.tar.gz
#    without a version suffix; rename it so rpmbuild's %setup macro finds it.
cp bridgelink.tar.gz bridgelink-26.3.1.tar.gz

# 2. Build the RPM inside a clean AlmaLinux 9 container.
docker run --rm -v "$PWD":/work -w /work almalinux:9 bash -c '
  dnf install -y rpm-build rpmdevtools tar gzip && \
  rpmdev-setuptree && \
  cp bridgelink-26.3.1.tar.gz ~/rpmbuild/SOURCES/ && \
  cp packaging/bridgelink.spec ~/rpmbuild/SPECS/ && \
  rpmbuild -bb ~/rpmbuild/SPECS/bridgelink.spec && \
  cp ~/rpmbuild/RPMS/noarch/bridgelink-*.rpm /work/
'
```

The output artifact is `bridgelink-26.3.1-1.el9.noarch.rpm` (or similar
distribution tag) in the repo root.

## FIPS digest verification

```bash
rpm -qp --qf "FILEDIGESTALGO=%{FILEDIGESTALGO}\n" bridgelink-26.3.1-1.*.noarch.rpm
```

Expected output: `FILEDIGESTALGO=8` (SHA-256). Reject any other value
(`1` = MD5). If you see `FILEDIGESTALGO=1` or no output, the `%global`
directives did not apply — inspect the `rpmbuild` log for `warning:
%global ignored` and confirm the spec at `packaging/bridgelink.spec` is
the one rpmbuild consumed.

## GPG signing key audit

```bash
gpg --list-packets /path/to/innovar-public-signing-key.asc | grep "hash algorithm"
```

Every line MUST show `hash algorithm: SHA-256` or stronger. Any `SHA-1`
or `MD5` line means the key has a SHA-1 subkey binding and the key MUST
be regenerated before signing FIPS-compatible RPMs — RHEL 9's GPG
verifier rejects signatures whose subkey bindings use SHA-1. This audit
is a hard pre-merge gate for issue #93. Coordinate with the Innovar
Healthcare release/security team to obtain the public key for inspection.

## FIPS-mode install test

```bash
docker run --rm --privileged -v "$PWD":/work almalinux:9 bash -c '
  fips-mode-setup --enable && \
  dnf install -y /work/bridgelink-26.3.1-1.noarch.rpm
'
```

Expected: install completes with no `cpio: Digest mismatch` error.
A `Requires: java-17-openjdk-headless` resolution warning in the default
container repo configuration is acceptable for this digest-only smoke
test; production installs should run against a repo that provides the
Java runtime.

The one-shot RHEL 8 hardware install verification is performed
out-of-band per the milestone's accepted verification matrix.

## Out of scope for this milestone

- Wiring `rpmbuild` into `.github/workflows/` — follow-up.
- Service definition (systemd unit) — separate issue.
- WebDAV / commons-httpclient removal — tracked as SEC-V2-01.
