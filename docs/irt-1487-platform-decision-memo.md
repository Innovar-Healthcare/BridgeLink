# Decision memo: two dependency questions in the 26.9 platform epic (IRT-1487)

**From:** Zi-Min Weng
**To:** Daniel Svanstedt
**Date:** 2026-07-21
**Needed by:** before CVE implementation starts (~2 weeks) — these gate IRT-1489 items

While planning the 26.9 milestone I fact-checked the dependency research against
Apache's docs, LibrePDF's published jars, and our own shipped config. One risk from
the epic turns out to be already mitigated (good news, one confirmation needed);
one replacement turns out to be harder than the ticket assumes and needs a real
decision from you.

---

## Item 1 — Derby 10.17: the irreversible-upgrade risk is already mitigated; confirming our stance

Derby 10.17.1.0 requires Java 21+ at runtime (per Apache's release notes), which
the epic already handles with the startup preflight. The follow-on worry was the
10.16→10.17 on-disk format upgrade, which has no downgrade path.

**What verification showed:** Derby only performs that irreversible full format
upgrade when `upgrade=true` is in the JDBC URL. Without it, connecting the new
engine does a reversible *soft* upgrade — the database remains openable by the
older engine, so a customer could still roll back to 26.6.x. Upstream Mirth
shipped `upgrade=true` by default, but **we already flipped it to `upgrade=false`
in PR #151 (IRT-776, May 2026)** — so first boot of 26.9 on an existing
embedded-Derby install will NOT irreversibly convert anyone's database.

**Asking you to confirm (rather than decide):**

1. We keep `upgrade=false` as the shipped default through 26.9; full-upgrade
   becomes a documented, deliberate customer action (backup first), not a side
   effect of upgrading BridgeLink
2. Phase 24 verifies both paths in the migration harness — soft upgrade
   (10.16 database under the 10.17 engine, including rollback) and explicit
   full upgrade
3. Release notes state the Java-21 requirement for embedded Derby and the
   soft-vs-full upgrade distinction

---

## Item 2 — itext/RTF replacement: needs your call, and your customer knowledge

IRT-1489 removes itext 2.1.7 + itext-rtf (2009, unpatchable — exactly the
14-day-SLA dead weight the epic targets). PDF output already moved to
pdfbox/openhtmltopdf in 26.6.0; the only remaining itext usage is **RTF output in
the Document Writer connector** (`DocumentDispatcher.createRTF()`).

**What verification showed:** the ticket's suggested drop-in, OpenPDF, does not
contain the RTF classes at all — those live in LibrePDF's separate **OpenRTF**
project. And the version lines split badly against our Java tiers:

| Option | Java | Maintained? |
| --- | --- | --- |
| OpenRTF 2.0.0/3.0.0 (current) | **21+ bytecode** — breaks the "17 supported" tier | Yes |
| OpenRTF 1.2.1 (2021) | Java 8+ | Dormant ~5 years; pairing with OpenPDF 2.0.x untested |
| Keep itext-rtf 2.1.7 | 17 ✓ | Dead since 2009 — the thing we're removing |

So for RTF specifically there is **no maintained, Java-17-compatible library**.
The honest options:

- **(a) Drop RTF output from Document Writer** — cleanest for the SLA surface;
  viable only if no customer uses it. *You have the customer visibility here —
  do we know of anyone producing RTF?*
- **(b) Gate RTF behind Java 21+** — ship current OpenRTF; RTF joins embedded
  Derby as a documented Java-21 feature. Caveat: unlike Derby there's no startup
  preflight — a 17-tier customer with an RTF channel fails at message-processing
  time, so we'd need a channel-deploy-time guard
- **(c) OpenPDF 2.0.5 + OpenRTF 1.2.1** — keeps RTF on Java 17, but swaps one
  unmaintained library for a mostly-dormant one; the SLA-surface gain is small
  and the pairing needs its own regression work

**My recommendation:** (a) if no known RTF usage, else (b) with the deploy-time
guard. I'd avoid (c) — it doesn't really buy the "patchable in 14 days" property
this track exists for.

---

## What happens next

Your confirmations/decision get logged in the project decision record; Phase 22
(library replacements) and Phase 24 (Derby) proceed accordingly. Neither blocks
the work starting this week (JDK 21 toolchain + CI matrix, SBOM/scanner, smoke
harness) — but both gate the CVE-track phases scheduled to start in ~2 weeks.
