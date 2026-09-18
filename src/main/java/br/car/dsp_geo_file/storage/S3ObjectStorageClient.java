package br.car.dsp_geo_file.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ObjectStorageClient} over the S3 API.
 */
@Slf4j
public class S3ObjectStorageClient implements ObjectStorageClient {

    private static final int NOT_FOUND = 404;

    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStorageClient(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public boolean bucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND) {
                return false;
            }
            throw new ObjectStorageException("Failed to check bucket " + bucket, ex);
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to check bucket " + bucket, ex);
        }
    }

    @Override
    public void putFile(String key, Path file, String contentType, Map<String, String> userMetadata) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .metadata(userMetadata == null ? Map.of() : userMetadata)
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(file));
            log.info("Published {} ({} bytes) from staging to bucket {}", key, file.toFile().length(), bucket);
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to publish " + key, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to read " + key, ex);
        }
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(
                    key,
                    response.contentLength() == null ? 0L : response.contentLength(),
                    response.lastModified(),
                    response.metadata() == null ? Map.of() : Map.copyOf(response.metadata())
            ));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw new ObjectStorageException("Failed to head " + key, ex);
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to head " + key, ex);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try {
            List<StoredObject> objects = new ArrayList<>();
            s3Client.listObjectsV2Paginator(
                            ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                    .contents()
                    .forEach(object -> objects.add(new StoredObject(
                            object.key(),
                            object.size() == null ? 0L : object.size(),
                            object.lastModified(),
                            Map.of()
                    )));
            return objects;
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to list prefix " + prefix, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("Deleted {} from bucket {}", key, bucket);
        } catch (RuntimeException ex) {
            throw new ObjectStorageException("Failed to delete " + key, ex);
        }
    }
}
