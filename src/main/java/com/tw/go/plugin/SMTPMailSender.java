/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tw.go.plugin;

import com.thoughtworks.go.plugin.api.logging.Logger;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

import static jakarta.mail.Message.RecipientType.TO;

public class SMTPMailSender {
    private static final Logger LOGGER = Logger.getLoggerFor(EmailNotificationPluginImpl.class);

    public static final int DEFAULT_TIMEOUT = 60 * 1000;

    private final SMTPSettings smtpSettings;
    private final SessionFactory sessionFactory;

    public SMTPMailSender(SMTPSettings smtpSettings, SessionFactory sessionFactory) {
        this.smtpSettings = smtpSettings;
        this.sessionFactory = sessionFactory;
    }

    public void send(String subject, String body, String toEmailId) {
        Transport transport = null;
        try {
            Properties properties = mailProperties();
            SessionWrapper sessionWrapper = createSession(properties, smtpSettings.getSmtpUsername(), smtpSettings.getPassword());
            transport = sessionWrapper.getTransport();
            transport.connect(smtpSettings.getHostName(), smtpSettings.getPort(), nullIfEmpty(smtpSettings.getSmtpUsername()), nullIfEmpty(smtpSettings.getPassword()));
            MimeMessage message = sessionWrapper.createMessage(smtpSettings.getFromEmailId(), toEmailId, subject, body);
            transport.sendMessage(message, message.getRecipients(TO));
        } catch (Exception e) {
            LOGGER.error(String.format("Sending failed for email [%s] to [%s]", subject, toEmailId), e);
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException e) {
                    LOGGER.error("Failed to close transport", e);
                }
            }
        }
    }

    private Properties mailProperties() {
        Properties properties = new Properties();
        properties.put("mail.from", smtpSettings.getFromEmailId());

        if (!System.getProperties().containsKey("mail.smtp.connectiontimeout")) {
            properties.put("mail.smtp.connectiontimeout", DEFAULT_TIMEOUT);
        }

        if (!System.getProperties().containsKey("mail.smtp.timeout")) {
            properties.put("mail.smtp.timeout", DEFAULT_TIMEOUT);
        }

        if (smtpSettings.isTls()) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.ssl.enable", "true");
        }

        String mailProtocol = smtpSettings.isTls() ? "smtps" : "smtp";
        properties.put("mail.transport.protocol", mailProtocol);

        return properties;
    }

    private SessionWrapper createSession(Properties properties, String username, String password) {
        if (isEmpty(username) || isEmpty(password)) {
            return sessionFactory.getInstance(properties);
        } else {
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtps.auth", "true");
            return sessionFactory.getInstance(properties, new SMTPAuthenticator(username, password));
        }
    }

    private String nullIfEmpty(String str) {
        return isEmpty(str) ? null : str;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SMTPMailSender that = (SMTPMailSender) o;

        if (smtpSettings != null ? !smtpSettings.equals(that.smtpSettings) : that.smtpSettings != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return smtpSettings != null ? smtpSettings.hashCode() : 0;
    }
}
