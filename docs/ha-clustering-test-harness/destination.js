// Destination script for the shared-server-ID test.
//
// A raw socket rather than URL.openConnection(): under JDK 17 module rules Rhino cannot
// reach sun.net.www.protocol.http.HttpURLConnection. java.net.Socket is exported and works.
//
// CRLF is built from char codes so the sequence survives the XML/CDATA round trip intact.
//
// The blocking readLine() is load-bearing: the sink withholds its response for HOLD_SECONDS,
// so this destination stays mid-send and the message stays PROCESSED = FALSE. That in-flight
// window is exactly what a peer node's RecoveryTask can see when server IDs are shared.
var CRLF = String.fromCharCode(13) + String.fromCharCode(10);
var mid = connectorMessage.getMessageId();
var node = java.net.InetAddress.getLocalHost().getHostName();

var sock = new java.net.Socket('sink', 9000);
sock.setSoTimeout(600000);
var out = new java.io.PrintWriter(sock.getOutputStream());
out.print('GET /deliver?id=' + mid + '&node=' + node + ' HTTP/1.1' + CRLF);
out.print('Host: sink' + CRLF);
out.print('Connection: close' + CRLF + CRLF);
out.flush();

var br = new java.io.BufferedReader(new java.io.InputStreamReader(sock.getInputStream()));
var line = br.readLine();
sock.close();
return 'sink:' + line;
