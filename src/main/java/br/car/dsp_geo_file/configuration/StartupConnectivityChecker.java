package br.car.dsp_geo_file.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Checks all 3 datasources and object storage before beans are created, logs every status, then
 * fails only after reporting. Runs as ApplicationContextInitializer so Spring Batch JobRepository
 * does not connect first.
 *
 * <p>Only the 3 datasources are fail-fast here (Spring Batch cannot even start without them).
 * Object storage is logged but never blocks startup — a missing bucket or unreachable S3 is a
 * recoverable condition the job already handles later via
 * {@code batch.tasklet.ObjectStorageReadinessTasklet}, which skips generation for this run
 * instead of crashing the container.
 */
public class StartupConnectivityChecker
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(StartupConnectivityChecker.class);
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();

        log.info("Checking connectivity to the 3 datasources...");

        List<CheckResult> results = List.of(
                check("batch", env, "spring.datasource.batch"),
                check("target", env, "spring.datasource.target"),
                check("geo-target", env, "spring.datasource.geo-target")
        );

        for (CheckResult result : results) {
            if (result.operational()) {
                log.info("Database status [{}] | url={} | OPERATIONAL", result.name(), result.url());
            } else {
                log.error("Database status [{}] | url={} | UNAVAILABLE | reason={}",
                        result.name(), result.url(), result.errorMessage());
            }
        }

        logObjectStorageStatus(env);

        List<String> unavailable = new ArrayList<>();
        for (CheckResult result : results) {
            if (!result.operational()) {
                unavailable.add(result.name());
            }
        }

        if (!unavailable.isEmpty()) {
            throw new IllegalStateException(
                    "Connectivity failed for database(s): " + String.join(", ", unavailable)
                            + ". See status above and fix the connection before running the job.");
        }

        log.info("All 3 datasources are operational.");
    }

    private void logObjectStorageStatus(Environment env) {
        log.info("Checking connectivity to object storage (S3 API)...");

        String endpoint = env.getProperty("dsp.object-storage.endpoint");
        String region = env.getProperty("dsp.object-storage.region", "us-east-1");
        String bucket = env.getProperty("dsp.object-storage.bucket");
        String accessKey = env.getProperty("dsp.object-storage.access-key");
        String secretKey = env.getProperty("dsp.object-storage.secret-key");
        boolean pathStyleAccess = env.getProperty("dsp.object-storage.path-style-access", Boolean.class, true);

        if (isBlank(endpoint)) {
            log.error("Object storage status [s3] | endpoint={} | bucket={} | UNAVAILABLE | reason={}",
                    endpoint, bucket, "dsp.object-storage.endpoint is not configured");
            return;
        }
        if (isBlank(bucket)) {
            log.error("Object storage status [s3] | endpoint={} | bucket={} | UNAVAILABLE | reason={}",
                    endpoint, bucket, "dsp.object-storage.bucket is not configured");
            return;
        }
        if (isBlank(accessKey)) {
            log.error("Object storage status [s3] | endpoint={} | bucket={} | UNAVAILABLE | reason={}",
                    endpoint, bucket, "dsp.object-storage.access-key is not configured");
            return;
        }
        if (isBlank(secretKey)) {
            log.error("Object storage status [s3] | endpoint={} | bucket={} | UNAVAILABLE | reason={}",
                    endpoint, bucket, "dsp.object-storage.secret-key is not configured");
            return;
        }

        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build())
                .build()) {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Object storage status [s3] | endpoint={} | bucket={} | OPERATIONAL", endpoint, bucket);
        } catch (Exception ex) {
            log.error("Object storage status [s3] | endpoint={} | bucket={} | UNAVAILABLE | reason={}",
                    endpoint, bucket, ex.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private CheckResult check(String name, Environment env, String prefix) {
        String url = env.getProperty(prefix + ".url");
        String username = env.getProperty(prefix + ".username");
        String password = env.getProperty(prefix + ".password");
        String driverClassName = env.getProperty(prefix + ".driver-class-name");

        if (url == null || url.isBlank()) {
            return CheckResult.unavailable(name, url, "JDBC URL is not configured (" + prefix + ".url)");
        }

        try {
            if (driverClassName != null && !driverClassName.isBlank()) {
                Class.forName(driverClassName);
            }

            Properties props = new Properties();
            if (username != null) {
                props.setProperty("user", username);
            }
            if (password != null) {
                props.setProperty("password", password);
            }
            props.setProperty("connectTimeout", String.valueOf(LOGIN_TIMEOUT_SECONDS));
            props.setProperty("loginTimeout", String.valueOf(LOGIN_TIMEOUT_SECONDS));
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);

            try (Connection connection = DriverManager.getConnection(url, props);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next()) {
                    return CheckResult.unavailable(name, url, "SELECT 1 returned no rows");
                }
                return CheckResult.operational(name, url);
            }
        } catch (Exception ex) {
            return CheckResult.unavailable(name, url, ex.getMessage());
        }
    }

    private record CheckResult(String name, String url, boolean operational, String errorMessage) {

        static CheckResult operational(String name, String url) {
            return new CheckResult(name, url, true, null);
        }

        static CheckResult unavailable(String name, String url, String errorMessage) {
            return new CheckResult(name, url, false, errorMessage);
        }
    }
}
