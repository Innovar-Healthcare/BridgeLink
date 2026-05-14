# Security Policy
 
## Supported Versions
 
We support the current minor version in regards to security updates. Versions are formatted \<major>.\<minor>.\<patch>. So, for example 4.5.3 is the current version; any security updates would be made in 4.5.4 or 4.6.0.
 
## Reporting a Vulnerability
 
If this is a critical vulnerability that you would like to contact us confidentially, email us here:
 
ossteam@innovarhealthcare.com

## Known residual dependencies

- `commons-httpclient-3.0.1.jar` remains on the server classpath for runtime
  resolution of two deferred call sites:
  - `WebDavConnection.java` (`HttpURL` / `HttpsURL` — blocked on Apache Slide
    replacement; tracked under SEC-V2-01).
  - `HTTPUtil.java` (`Header` / `HttpParser` — blocked on `HttpParser.parseHeaders`
    equivalent in `org.apache.http`; tracked under SEC-V2-01 companion).

  All other constant-only callers (`HttpReceiver.java`, `MirthWebServer.java`,
  `ConnectServiceUtil.java`) were migrated in #140. The jar carries unfixed
  CVE-2012-5783, CVE-2014-3577, and CVE-2015-5262 (MITM / hostname-verification);
  exposure is bounded to the two WebDAV/HTTP utility call sites above.
