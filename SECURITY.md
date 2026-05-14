# Security Policy
 
## Supported Versions
 
We support the current minor version in regards to security updates. Versions are formatted \<major>.\<minor>.\<patch>. So, for example 4.5.3 is the current version; any security updates would be made in 4.5.4 or 4.6.0.
 
## Reporting a Vulnerability
 
If this is a critical vulnerability that you would like to contact us confidentially, email us here:
 
ossteam@innovarhealthcare.com

## Known residual dependencies

- `commons-httpclient-3.0.1.jar` remains on the server classpath for `WebDavConnection.java` runtime resolution (Apache Slide WebDAV). Tracked for removal under SEC-V2-01. No other callers reference 3.x APIs as of #140 merge.
