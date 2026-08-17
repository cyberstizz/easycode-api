package com.easycode.api.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 speaks S3. Same bucket pattern as Unis media, private bucket,
 * presigned PUT for upload and presigned GET for viewing — the browser never sees a key.
 */
@Configuration
public class R2Config {

    private final AppProperties props;

    public R2Config(AppProperties props) {
        this.props = props;
    }

    private URI endpoint() {
        return URI.create("https://" + props.getR2().getAccountId() + ".r2.cloudflarestorage.com");
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        props.getR2().getAccessKeyId(), props.getR2().getSecretAccessKey()));
    }

    @Bean
    public S3Client r2Client() {
        return S3Client.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner r2Presigner() {
        return S3Presigner.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
