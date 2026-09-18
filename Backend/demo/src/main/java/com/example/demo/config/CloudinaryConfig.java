package com.example.demo.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the singleton {@link Cloudinary} client from the {@code cloudinary.*}
 * properties (mapped from CLOUDINARY_* env vars in application.properties).
 *
 * The defaults are empty so the application context still starts when the
 * credentials are absent (e.g. during tests, which never call Cloudinary).
 * Real uploads require the values to be set in the environment / .env.
 */
@Configuration
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        // Startup visibility into whether the (rotated) credentials actually loaded.
        // Only the non-secret cloud name and presence booleans are logged — never the
        // API key or secret. Note: "present" here means "a value was injected", not
        // "accepted by Cloudinary" — an authenticated-but-unauthorized key still uploads-fails.
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("Cloudinary is NOT fully configured (cloud_name={}, apiKey={}, apiSecret={}); "
                            + "file uploads will fail. Set CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / "
                            + "CLOUDINARY_API_SECRET in the environment or .env.",
                    cloudName.isBlank() ? "missing" : "present",
                    apiKey.isBlank() ? "missing" : "present",
                    apiSecret.isBlank() ? "missing" : "present");
        } else {
            log.info("Cloudinary configured: cloud_name='{}', apiKey=present, apiSecret=present", cloudName);
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }
}
