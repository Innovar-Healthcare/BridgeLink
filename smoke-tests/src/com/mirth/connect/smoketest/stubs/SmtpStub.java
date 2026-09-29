package com.mirth.connect.smoketest.stubs;

import java.util.List;

import javax.mail.internet.MimeMessage;

import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.store.StoredMessage;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * D-07 SMTP endpoint stub: wraps GreenMail 1.6.15 (18-RESEARCH.md Pattern 4).
 *
 * <p>Classpath note: GreenMail is paired with the shipped {@code javax.mail-1.6.2.jar}
 * (server/setup/server-lib/javax/) — NEVER {@code jakarta.mail} (Pitfall 5, incompatible
 * package namespace with GreenMail 1.6.15's javax.mail-based API).
 */
public class SmtpStub {

    private final int port;
    private GreenMail greenMail;

    public SmtpStub(int port) {
        this.port = port;
    }

    /** Starts an SMTP server bound explicitly to 127.0.0.1 (T-18-11). */
    public void start() {
        ServerSetup setup = new ServerSetup(port, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);
        greenMail = new GreenMail(setup);
        greenMail.start();
    }

    public void stop() {
        if (greenMail != null) {
            greenMail.stop();
            greenMail = null;
        }
    }

    public MimeMessage[] getReceivedMessages() {
        return greenMail.getReceivedMessages();
    }

    /**
     * Per-domain received-message accessor (18.5-01, NET-10).
     *
     * <p><b>18.5-03 correction — this accessor is header-based, NOT delivery-based, and
     * therefore CANNOT prove BCC delivery (empirically confirmed, not merely inferred from
     * reading GreenMail's source):</b> {@code GreenMail.getReceivedMessagesForDomain(domain)}
     * matches by parsing {@code MimeMessage.getAllRecipients()} — i.e. the STORED MESSAGE'S OWN
     * To/Cc/Bcc headers — not by which physical mailbox/recipient that particular copy was
     * actually delivered to. Since a well-behaved SMTP client (this project's
     * {@code javax.mail} {@code SMTPTransport}, via commons-email) strips the {@code Bcc}
     * header from every copy before the wire DATA phase (the entire point of BCC), EVERY stored
     * copy of a message with a BCC recipient has a {@code null} {@code Bcc} header — so
     * {@code getReceivedMessagesForDomain(bccDomain)} always returns an EMPTY array, even though
     * GreenMail genuinely delivered a copy to that recipient's own mailbox. Use
     * {@link #getReceivedMessagesForRecipient(String)} to prove delivery to a specific address
     * (including BCC addresses); reserve this domain-based accessor for header-content checks
     * only (e.g. "is this address visible in the Cc header of the copies addressed to this
     * domain").
     */
    public MimeMessage[] getReceivedMessagesForDomain(String domain) {
        return greenMail.getReceivedMessagesForDomain(domain);
    }

    /**
     * Per-recipient (per-mailbox) received-message accessor (18.5-03, NET-10) — the DELIVERY
     * proof {@link #getReceivedMessagesForDomain(String)} cannot provide for BCC recipients (see
     * that method's javadoc). GreenMail auto-creates a {@link GreenMailUser} for every distinct
     * SMTP envelope recipient (RCPT TO) and delivers that recipient's own copy into its own IMAP
     * mailbox, independent of what headers the copy's content carries — this is genuinely
     * delivery-target-based, not header-based, empirically confirmed via a standalone probe
     * against this project's exact {@code commons-email-1.6.0.jar} +
     * {@code javax.mail-1.6.2.jar} + {@code greenmail-1.6.15.jar} combination (see plan
     * 18.5-03's SUMMARY) before this method was written.
     *
     * @return the messages delivered to {@code emailAddress}'s own mailbox, or an empty array
     *         if GreenMail has not (yet) auto-created a user for that address (i.e. no message
     *         has been delivered to it).
     */
    public MimeMessage[] getReceivedMessagesForRecipient(String emailAddress) throws Exception {
        GreenMailUser user = greenMail.getUserManager().getUserByEmail(emailAddress);
        if (user == null) {
            return new MimeMessage[0];
        }
        MailFolder inbox = greenMail.getManagers().getImapHostManager().getInbox(user);
        List<StoredMessage> stored = inbox.getMessages();
        MimeMessage[] result = new MimeMessage[stored.size()];
        for (int i = 0; i < stored.size(); i++) {
            result[i] = stored.get(i).getMimeMessage();
        }
        return result;
    }

    /**
     * Delegates to GreenMail's own poll-with-timeout wait (plan 18-07's designated L2 API) —
     * blocks up to {@code timeoutMs} for {@code count} messages to arrive, returning as soon
     * as they do (Pitfall 7: no fixed sleeps).
     */
    public boolean waitForIncomingEmail(long timeoutMs, int count) {
        return greenMail.waitForIncomingEmail(timeoutMs, count);
    }

    /** Convenience accessor for a received message's text body. */
    public static String getBody(MimeMessage message) {
        return GreenMailUtil.getBody(message);
    }
}
