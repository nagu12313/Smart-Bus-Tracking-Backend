package com.smartbus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username:bollanagu6868@gmail.com}")
    private String fromEmail;

    @Value("${brevo.api.key:${BREVO_API_KEY:}}")
    private String brevoApiKey;

    @Value("${resend.api.key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String resolveSender() {
        return (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "bollanagu6868@gmail.com";
    }

    private boolean sendViaBrevo(String toEmail, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = Map.of(
                "sender", Map.of("name", "EduTrack Campus Transport", "email", resolveSender()),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlContent
            );

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", brevoApiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email delivered successfully via Brevo HTTPS API (status {}) to {}", response.statusCode(), toEmail);
                return true;
            } else {
                log.warn("Brevo API responded with status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Brevo API dispatch failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendViaResend(String toEmail, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = Map.of(
                "from", "EduTrack <onboarding@resend.dev>",
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlContent
            );

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email delivered successfully via Resend HTTPS API (status {}) to {}", response.statusCode(), toEmail);
                return true;
            } else {
                log.warn("Resend API responded with status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Resend API dispatch failed: {}", e.getMessage());
            return false;
        }
    }

    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "EduTrack Verification Code: " + otp;
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f8fafc; color: #1e293b; padding: 20px; }
                .card { max-width: 500px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; }
                .brand { font-size: 20px; font-weight: 800; color: #1d6de5; display: flex; align-items: center; margin-bottom: 24px; }
                h2 { font-size: 22px; font-weight: 800; color: #0f172a; margin-top: 0; }
                p { font-size: 14px; line-height: 1.6; color: #64748b; margin-bottom: 24px; }
                .otp-box { background: #f0f7ff; border: 2px dashed #93c5fd; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0; }
                .otp-code { font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #1d6de5; font-family: monospace; }
                .footer { font-size: 12px; color: #94a3b8; text-align: center; margin-top: 28px; border-top: 1px solid #f1f5f9; padding-top: 16px; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="brand">🚌 EduTrack Transport</div>
                <h2>Verify your email address</h2>
                <p>Thank you for registering on EduTrack. Please use the following 6-digit verification code to complete your account setup:</p>
                <div class="otp-box">
                  <div class="otp-code">%s</div>
                </div>
                <p style="font-size: 13px; color: #64748b;">This verification code is valid for <strong>10 minutes</strong>. If you did not request this, please disregard this email.</p>
                <div class="footer">
                  EduTrack · Smart School &amp; College Bus Tracking Platform
                </div>
              </div>
            </body>
            </html>
            """.formatted(otp);

        // 1. Try Brevo HTTPS API (Port 443 - works on Render Free Tier)
        if (sendViaBrevo(toEmail, subject, html)) {
            return;
        }

        // 2. Try Resend HTTPS API (Port 443)
        if (sendViaResend(toEmail, subject, html)) {
            return;
        }

        // 3. Fallback to JavaMail SMTPS
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveSender(), "EduTrack Campus Transport");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("OTP verification email sent successfully via SMTP to {}", toEmail);
        } catch (Exception e) {
            log.warn("SMTP email dispatch to {} failed (cloud port block or timeout): {}. OTP code [{}] is available in logs and client response.", toEmail, e.getMessage(), otp);
        }
    }

    public void sendPasswordResetOtpEmail(String toEmail, String otp) {
        String subject = "EduTrack Password Reset Code: " + otp;
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f8fafc; color: #1e293b; padding: 20px; }
                .card { max-width: 500px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; }
                .brand { font-size: 20px; font-weight: 800; color: #1d6de5; display: flex; align-items: center; margin-bottom: 24px; }
                h2 { font-size: 22px; font-weight: 800; color: #0f172a; margin-top: 0; }
                p { font-size: 14px; line-height: 1.6; color: #64748b; margin-bottom: 24px; }
                .otp-box { background: #fff1f2; border: 2px dashed #f43f5e; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0; }
                .otp-code { font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #e11d48; font-family: monospace; }
                .footer { font-size: 12px; color: #94a3b8; text-align: center; margin-top: 28px; border-top: 1px solid #f1f5f9; padding-top: 16px; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="brand">🚌 EduTrack Transport</div>
                <h2>Reset your password</h2>
                <p>We received a request to reset your password for your EduTrack account. Use this 6-digit verification code to set your new password:</p>
                <div class="otp-box">
                  <div class="otp-code">%s</div>
                </div>
                <p style="font-size: 13px; color: #64748b;">This verification code is valid for <strong>10 minutes</strong>. If you did not request a password reset, you can safely ignore this email.</p>
                <div class="footer">
                  EduTrack · Smart School &amp; College Bus Tracking Platform
                </div>
              </div>
            </body>
            </html>
            """.formatted(otp);

        // 1. Try Brevo HTTPS API (Port 443 - works on Render Free Tier)
        if (sendViaBrevo(toEmail, subject, html)) {
            return;
        }

        // 2. Try Resend HTTPS API (Port 443)
        if (sendViaResend(toEmail, subject, html)) {
            return;
        }

        // 3. Fallback to JavaMail SMTPS
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveSender(), "EduTrack Campus Transport");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset OTP email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.warn("SMTP email dispatch to {} failed (cloud port block or timeout): {}. Password reset OTP code [{}] is available in logs and client response.", toEmail, e.getMessage(), otp);
        }
    }

    /**
     * Send Administrator Support Tickets Summary Report.
     */
    public void sendAdminTicketsReport(String toEmail, java.util.List<com.smartbus.dto.response.SupportTicketResponse> tickets) {
        String subject = "📋 EduTrack Admin Support Tickets Report [" + tickets.size() + " Active Tickets]";
        long openCount = tickets.stream().filter(t -> t.getStatus() != null && t.getStatus().name().equals("OPEN")).count();
        long urgentCount = tickets.stream().filter(t -> t.getPriority() != null && (t.getPriority().name().equals("URGENT") || t.getPriority().name().equals("HIGH"))).count();
        long resolvedCount = tickets.stream().filter(t -> t.getStatus() != null && t.getStatus().name().equals("RESOLVED")).count();

        StringBuilder rows = new StringBuilder();
        for (com.smartbus.dto.response.SupportTicketResponse t : tickets) {
            String statusBg = switch (t.getStatus() != null ? t.getStatus().name() : "") {
                case "OPEN" -> "#eff6ff; color: #2563eb;";
                case "IN_PROGRESS" -> "#fef3c7; color: #d97706;";
                case "WAITING_FOR_USER" -> "#ede9fe; color: #7c3aed;";
                case "RESOLVED" -> "#dcfce7; color: #16a34a;";
                default -> "#f1f5f9; color: #475569;";
            };

            String prioBg = switch (t.getPriority() != null ? t.getPriority().name() : "") {
                case "URGENT" -> "#fee2e2; color: #dc2626;";
                case "HIGH" -> "#fef3c7; color: #d97706;";
                default -> "#f1f5f9; color: #475569;";
            };

            rows.append("""
                <tr style="border-bottom: 1px solid #e2e8f0;">
                  <td style="padding: 12px; font-family: monospace; font-weight: 700; color: #0f172a;">%s</td>
                  <td style="padding: 12px;">
                    <div style="font-weight: 700; color: #0f172a; font-size: 13px;">%s</div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 2px;">%s</div>
                  </td>
                  <td style="padding: 12px; font-size: 12px; color: #334155;">%s (%s)</td>
                  <td style="padding: 12px; text-align: center;">
                    <span style="display: inline-block; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; background: %s">%s</span>
                  </td>
                  <td style="padding: 12px; text-align: center;">
                    <span style="display: inline-block; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; background: %s">%s</span>
                  </td>
                </tr>
                """.formatted(
                    t.getTicketNumber(),
                    t.getSubject(),
                    t.getCategory() != null ? t.getCategory().name() : "GENERAL",
                    t.getUserName() != null ? t.getUserName() : "User",
                    t.getUserRole() != null ? t.getUserRole() : "",
                    prioBg,
                    t.getPriority() != null ? t.getPriority().name() : "MEDIUM",
                    statusBg,
                    t.getStatus() != null ? t.getStatus().name() : "OPEN"
                ));
        }

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #1e293b; padding: 24px; margin: 0; }
                .container { max-width: 700px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 25px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }
                .header { background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%); padding: 24px 30px; color: #ffffff; }
                .header h1 { margin: 0; font-size: 20px; font-weight: 800; }
                .header p { margin: 4px 0 0 0; color: #94a3b8; font-size: 12px; }
                .body { padding: 24px; }
                table { width: 100%%; border-collapse: collapse; text-align: left; }
                th { font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; padding: 10px 12px; border-bottom: 2px solid #e2e8f0; }
                .footer { background: #f8fafc; padding: 16px 24px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>🚌 EduTrack Transport Administration</h1>
                  <p>Campus Support Tickets Status Report · Generated on %s</p>
                </div>

                <table style="width: 100%%; background: #f8fafc; border-bottom: 1px solid #e2e8f0;">
                  <tr>
                    <td style="text-align: center; padding: 16px;">
                      <div style="font-size: 22px; font-weight: 800; color: #0f172a;">%d</div>
                      <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase;">Total Tickets</div>
                    </td>
                    <td style="text-align: center; padding: 16px;">
                      <div style="font-size: 22px; font-weight: 800; color: #2563eb;">%d</div>
                      <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase;">Open</div>
                    </td>
                    <td style="text-align: center; padding: 16px;">
                      <div style="font-size: 22px; font-weight: 800; color: #dc2626;">%d</div>
                      <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase;">Urgent / High</div>
                    </td>
                    <td style="text-align: center; padding: 16px;">
                      <div style="font-size: 22px; font-weight: 800; color: #16a34a;">%d</div>
                      <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase;">Resolved</div>
                    </td>
                  </tr>
                </table>

                <div class="body">
                  <h3 style="font-size: 15px; font-weight: 800; color: #0f172a; margin-top: 0; margin-bottom: 14px;">Recent Support Requests</h3>
                  <table>
                    <thead>
                      <tr>
                        <th>Ticket</th>
                        <th>Subject & Category</th>
                        <th>Requester</th>
                        <th style="text-align: center;">Priority</th>
                        <th style="text-align: center;">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                </div>

                <div class="footer">
                  EduTrack · Smart School &amp; College Bus Tracking System · Transport Operations
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                tickets.size(),
                openCount,
                urgentCount,
                resolvedCount,
                rows.toString()
            );

        if (sendViaBrevo(toEmail, subject, html)) return;
        if (sendViaResend(toEmail, subject, html)) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveSender(), "EduTrack Transport Administration");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Admin support tickets report email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send admin support tickets report to {}: {}", toEmail, e.getMessage());
        }
    }
}
