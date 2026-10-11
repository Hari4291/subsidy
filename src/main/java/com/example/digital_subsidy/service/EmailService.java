package com.example.digital_subsidy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends transactional emails via the configured SMTP provider (Gmail).
 * SMTP credentials are injected from environment variables — never hardcoded.
 * The raw OTP is accepted here only to include it in the email body;
 * it is never logged and never persisted by this service.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an OTP email to an officer (Field Officer, District Officer, or Finance Approver).
     */
    public void sendOfficerOtpEmail(String toEmail, String roleTitle, String rawOtp, int expiryMin)
            throws MailException, MessagingException {

        logger.info("[EMAIL] Sending OTP email to {} ({})", toEmail, roleTitle);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setFrom(fromAddress);
        helper.setTo(toEmail);
        helper.setSubject("Digital Subsidy Portal - Login OTP (" + roleTitle + ")");

        String body = buildOfficerOtpEmailBody(roleTitle, rawOtp, expiryMin);
        helper.setText(body, false);

        mailSender.send(message);

        logger.info("[EMAIL] OTP email sent successfully to: {}", toEmail);
    }

    /**
     * Sends a Field Officer OTP email.
     */
    public void sendFieldOfficerOtpEmail(String toEmail, String rawOtp, int expiryMin)
            throws MailException, MessagingException {
        sendOfficerOtpEmail(toEmail, "Field Officer", rawOtp, expiryMin);
    }

    private String buildOfficerOtpEmailBody(String roleTitle, String rawOtp, int expiryMin) {
        return "Dear " + roleTitle + ",\n\n" +
               "Your one-time login OTP for the Digital Subsidy & Grant Administration Portal is:\n\n" +
               "    " + rawOtp + "\n\n" +
               "This OTP is valid for " + expiryMin + " minute" + (expiryMin == 1 ? "" : "s") +
               " and can be used only once.\n\n" +
               "Please do not share this OTP with anyone.\n\n" +
               "If you did not request this OTP, please ignore this email.\n\n" +
               "Regards,\n" +
               "Digital Subsidy & Grant Administration Portal";
    }
}
