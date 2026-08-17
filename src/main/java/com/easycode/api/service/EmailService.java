package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Resend — same provider already configured and debugged for Unis. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppProperties props;
    private final RestClient client = RestClient.create("https://api.resend.com");

    public EmailService(AppProperties props) {
        this.props = props;
    }

    public void sendInvite(String to, String name, String orgName, String link) {
        send(to, "Set up your EasyCode portal",
                html("Welcome" + (name == null ? "" : ", " + name),
                        "Your EasyCode client portal for <strong>" + escape(orgName)
                                + "</strong> is ready. Set a password to see your project tracker, "
                                + "send requests and view invoices in one place.",
                        "Set up my portal", link,
                        "This link expires in " + props.getInvite().getTtlHours() / 24 + " days."));
    }

    public void sendPasswordReset(String to, String link) {
        send(to, "Reset your EasyCode password",
                html("Password reset",
                        "Click below to choose a new password. If you didn't ask for this, ignore this email "
                                + "and nothing changes.",
                        "Choose a new password", link,
                        "This link expires in " + props.getReset().getTtlMinutes() + " minutes."));
    }

    public void sendRequestReceived(String to, String title, String link) {
        send(to, "We got your request: " + title,
                html("Request received",
                        "We've logged <strong>" + escape(title) + "</strong> and you'll get a reply here "
                                + "as soon as it's picked up.",
                        "View the request", link, null));
    }

    public void sendNewRequestAlert(String to, String orgName, String title, String link) {
        send(to, "New request from " + orgName,
                html("New request", escape(orgName) + " filed: <strong>" + escape(title) + "</strong>",
                        "Open in the console", link, null));
    }

    public void sendInvoiceSent(String to, String number, String amount, String link) {
        send(to, "Invoice " + number + " from EasyCode",
                html("Invoice " + number, "Amount due: <strong>" + amount + "</strong>",
                        "View and pay", link, null));
    }

    public void send(String to, String subject, String html) {
        if (!props.getResend().isEnabled()) {
            log.info("[email disabled] to={} subject={}\n{}", to, subject, html);
            return;
        }
        try {
            client.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + props.getResend().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", props.getResend().getFrom(),
                            "to", List.of(to),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // a failed email must not fail the operation that triggered it
            log.error("Resend send failed to={} subject={}", to, subject, e);
        }
    }

    private String html(String heading, String body, String cta, String link, String footnote) {
        return """
            <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#09090b;padding:32px">
              <div style="max-width:520px;margin:0 auto;background:#111113;border:1px solid #27272a;border-radius:14px;padding:32px">
                <div style="color:#10b981;font-size:13px;letter-spacing:.14em;text-transform:uppercase;margin-bottom:18px">EasyCode</div>
                <h1 style="color:#fafafa;font-size:22px;margin:0 0 12px">%s</h1>
                <p style="color:#a1a1aa;font-size:15px;line-height:1.6;margin:0 0 26px">%s</p>
                <a href="%s" style="display:inline-block;background:#10b981;color:#06281d;font-weight:600;text-decoration:none;padding:12px 22px;border-radius:9px">%s</a>
                <p style="color:#52525b;font-size:12px;margin:26px 0 0">%s</p>
              </div>
            </div>
            """
                .formatted(heading, body, link, cta, footnote == null ? "" : footnote);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
