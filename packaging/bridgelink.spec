# Copyright (c) 2026 Innovar Healthcare
# Licensed under the MPL-1.1.
# BridgeLink RPM spec — FIPS-compatible (SHA-256 file digests). Closes #93. See packaging/README.md.

%global _binary_filedigest_algorithm 8
%global _source_filedigest_algorithm 8
%global _binary_payload_flags 0x1

Name:           bridgelink
Version:        26.3.1
Release:        1%{?dist}
Summary:        BridgeLink healthcare integration engine
License:        MPL-1.1
URL:            https://github.com/Innovar-Healthcare/BridgeLink
Source0:        bridgelink-%{version}.tar.gz
BuildArch:      noarch
BuildRequires:  tar
BuildRequires:  gzip
Requires:       java-17-openjdk-headless

%description
BridgeLink is a fork of NextGen Connect (Mirth Connect) — a healthcare
integration engine maintained by Innovar Healthcare. This RPM lays down
the server installation tree under /opt/bridgelink. Service management
(systemd unit, start scripts) is intentionally out of scope for this
initial FIPS-digest packaging fix.

%prep
%setup -q -n bridgelink

%install
mkdir -p %{buildroot}/opt/bridgelink
cp -r * %{buildroot}/opt/bridgelink/

%files
/opt/bridgelink

%changelog
* Wed May 13 2026 Innovar Healthcare <ossteam@innovarhealthcare.com> - 26.3.1-1
- Initial RPM packaging with SHA-256 file digests for FIPS compatibility (closes #93)
