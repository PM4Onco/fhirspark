package fhirspark;

import fhirspark.restmodel.Image;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class FileUploader implements AutoCloseable {
    private final String url;
    private final String bucket;
    private final MinioClient client;

    private static final String DATA_PREFIX = "base64,";

    public FileUploader(String url, String bucket, Credentials credentials) {
        this.url = url;
        this.bucket = bucket;
        this.client = MinioClient.builder()
            .endpoint(url)
            .credentials(credentials.accessKey(), credentials.secretKey())
            .build();


    }

    public String uploadImage(Image image) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!bucketExists()) {
            throw new RuntimeException("Bucket doesn't exist" );
        }

        var stream = this.convertImageToInputStream(image);

        var fileName = UUID.randomUUID() + "." + image.contentType().getExtension();
        this.client.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .contentType(image.contentType().display())
            .object(fileName)
            .stream(stream, stream.available(), -1)
            .build()
        );

        return this.url + "/" + this.bucket + "/" + fileName;
    }

    private InputStream convertImageToInputStream(Image image) {
        var dataPrefixStartIndex = image.data().indexOf(DATA_PREFIX);
        var imageDataStartIndex = dataPrefixStartIndex + DATA_PREFIX.length();
        var bytes = Base64.decodeBase64(image.data().substring(imageDataStartIndex));

        return new ByteArrayInputStream(bytes);
    }

    public boolean bucketExists() {
        try {
            return this.client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }
}
