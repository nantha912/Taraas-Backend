package com.LocalService.lsp.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    @Autowired
    private Environment env;

    @Value("${aws.access-key:${AWS_ACCESS_KEY_ID:${AWS_ACCESS_KEY:}}}")
    private String accessKey;

    @Value("${aws.secret-key:${AWS_SECRET_ACCESS_KEY:${AWS_SECRET_KEY:}}}")
    private String secretKey;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @PostConstruct
    public void diagnosticCheck() {
        logger.info("======= S3 CREDENTIAL RESOLUTION CHECK =======");

        // Check which specific key was found in the environment
        String resolvedAccessKeyName = "NONE";
        if (env.containsProperty("aws.access-key")) resolvedAccessKeyName = "aws.access-key";
        else if (env.containsProperty("AWS_ACCESS_KEY_ID")) resolvedAccessKeyName = "AWS_ACCESS_KEY_ID";
        else if (env.containsProperty("AWS_ACCESS_KEY")) resolvedAccessKeyName = "AWS_ACCESS_KEY";

        if (accessKey != null && !accessKey.isBlank()) {
            logger.info("Access Key Status: LOADED");
            logger.info("Resolved From Source: {}", resolvedAccessKeyName);
            logger.info("Key Length: {}", accessKey.length());
        } else {
            logger.error("Access Key Status: NOT FOUND (Empty or Null)");
        }

        if (secretKey != null && !secretKey.isBlank()) {
            logger.info("Secret Key Status: LOADED");
            logger.info("Secret Key Length: {}", secretKey.length());
        } else {
            logger.error("Secret Key Status: NOT FOUND");
        }

        logger.info("Region: {}", region);
        logger.info("===============================================");
    }

    @Bean
    public S3Client s3Client() {
        try {
            if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
                logger.warn("Returning Anonymous S3 Client - Credentials missing.");
                return S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .build();
            }

            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        } catch (Exception e) {
            logger.error("S3 Initialization Error: {}", e.getMessage());
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(AnonymousCredentialsProvider.create())
                    .build();
        }
    }
}