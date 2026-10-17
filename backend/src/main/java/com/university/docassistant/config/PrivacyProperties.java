package com.university.docassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * app.privacy: masking of personal data in document texts (US-09).
 *
 * @param enabled            false stores the text unmasked as the "masked" copy too (nothing hidden); default true
 * @param publicEmailDomains e-mails on these domains and their subdomains are not personal (e.g. sdu.edu.kz)
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacyProperties(Boolean enabled, List<String> publicEmailDomains) {

    public PrivacyProperties {
        enabled = enabled == null || enabled; // Absent means on: personal data must not leak by default.
        publicEmailDomains = publicEmailDomains == null ? List.of() : publicEmailDomains.stream()
                .filter(Objects::nonNull)
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .distinct().sorted()
                .toList();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
