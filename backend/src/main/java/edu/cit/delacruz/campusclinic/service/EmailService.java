package edu.cit.delacruz.campusclinic.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name:BudgetMate}")
    private String appName;

    @Async
    public void sendWelcomeEmail(String toEmail, String name) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + appName + "!");
            helper.setText(buildWelcomeHtml(name), true);

            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendBudgetAlertEmail(String toEmail, String name, String category,
                                     double percentUsed) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("[" + appName + "] Budget Alert - " + category);
            message.setText(String.format(
                    "Hi %s,\n\nYou have used %.1f%% of your %s budget.\n\n"
                    + "Log in to %s to review your spending.\n\nBest regards,\nThe %s Team",
                    name, percentUsed, category, appName, appName));
            mailSender.send(message);
            log.info("Budget alert email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send budget alert to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[" + appName + "] Password Reset Request");
            helper.setText(buildPasswordResetHtml(resetLink), true);

            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildWelcomeHtml(String name) {
        return """
                <html>
                  <body style="font-family:Arial,sans-serif;color:#333;padding:20px;">
                    <h2>Welcome to BudgetMate, %s!</h2>
                    <p>We're excited to have you on board. Start tracking your expenses and budgets today.</p>
                    <p>With BudgetMate you can:</p>
                    <ul>
                      <li>Track daily expenses by category</li>
                      <li>Set monthly budgets and receive alerts</li>
                      <li>View live exchange rates</li>
                      <li>Gain insights via the real-time dashboard</li>
                    </ul>
                    <p>Happy budgeting!<br><strong>The BudgetMate Team</strong></p>
                  </body>
                </html>
                """.formatted(name);
    }

    private String buildPasswordResetHtml(String resetLink) {
        return """
                <html>
                  <body style="font-family:Arial,sans-serif;color:#333;padding:20px;">
                    <h2>Password Reset</h2>
                    <p>Click the button below to reset your password. This link expires in 1 hour.</p>
                    <a href="%s" style="background:#4CAF50;color:white;padding:12px 24px;
                       text-decoration:none;border-radius:4px;">Reset Password</a>
                    <p>If you did not request a password reset, please ignore this email.</p>
                  </body>
                </html>
                """.formatted(resetLink);
    }
}
