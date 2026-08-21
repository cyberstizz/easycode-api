package com.easycode.api.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 speaks S3. Private bucket, presigned PUT for upload and presigned GET
 * for viewing — the browser never sees a key.
 *
 * <p>These beans only exist when R2 credentials are actually present. Without that
 * guard the AWS SDK throws "Access key ID cannot be blank" during bean creation and
 * takes the whole application context down, so an unconfigured bucket blocks login,
 * projects, requests, and billing — none of which touch file storage.
 *
 * <p>{@code AssetService} injects these through {@code ObjectProvider}, so the app
 * starts fine without them and only file operations fail, with a message that says so.
 */
@Configuration
@ConditionalOnExpression("!'${app.r2.access-key-id:}'.isEmpty() && !'${app.r2.account-id:}'.isEmpty()")
public class R2Config {

    private static final Logger log = LoggerFactory.getLogger(R2Config.class);

    private final AppProperties props;

    public R2Config(AppProperties props) {
        this.props = props;
        log.info("R2 configured for account {}, bucket {}",
                props.getR2().getAccountId(), props.getR2().getBucket());
    }

    private URI endpoint() {
        return URI.create("https://" + props.getR2().getAccountId() + ".r2.cloudflarestorage.com");
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        props.getR2().getAccessKeyId(), props.getR2().getSecretAccessKey()));
    }

    private S3Configuration serviceConfig() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    @Bean
    public S3Client r2Client() {
        return S3Client.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credentials())
                .serviceConfiguration(serviceConfig())
                .build();
    }

    @Bean
    public S3Presigner r2Presigner() {
        return S3Presigner.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credentials())
                .serviceConfiguration(serviceConfig())
                .build();
    }
}