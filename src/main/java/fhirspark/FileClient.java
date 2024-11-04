package fhirspark;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import fhirspark.restmodel.Image;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

public class FileClient implements AutoCloseable {
    private static final String DATA_PREFIX = "base64,";
    private final String url;
    private final String bucket;
    private final MinioClient client;

    public FileClient(String url, String bucket, Credentials credentials) {
        this.url = url;
        this.bucket = bucket;
        this.client = MinioClient.builder()
            .endpoint(url)
            .credentials(credentials.accessKey(), credentials.secretKey())
            .build();


    }

    public String uploadImage(Image image, String patientId) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!bucketExists()) {
            throw new RuntimeException("Bucket doesn't exist");
        }

        var stream = this.convertImageToInputStream(image);

        var fileName = patientId + "/presentation/" + UUID.randomUUID() + "." + image.contentType().getExtension();
        this.client.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .contentType(image.contentType().display())
            .object(fileName)
            .stream(stream, stream.available(), -1)
            .build()
        );

        return fullFilePath(fileName);
    }

    private InputStream convertImageToInputStream(Image image) {
        var dataPrefixStartIndex = image.data().indexOf(DATA_PREFIX);
        var imageDataStartIndex = dataPrefixStartIndex + DATA_PREFIX.length();
        var bytes = Base64.decodeBase64(image.data().substring(imageDataStartIndex));

        return new ByteArrayInputStream(bytes);
    }

    public String uploadFile(String patientId, HttpServletRequest request) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!bucketExists()) {
            throw new RuntimeException("Bucket doesn't exist");
        }

        var upload = new JakartaServletFileUpload<>();
        var iterator = upload.getItemIterator(request);

        if (!iterator.hasNext()) {
            throw new IOException("no file");
        }

        var item = iterator.next();
        if (!item.getFieldName().equals("file")) {
            throw new IOException("invalid field");
        }

        // read all bytes, directly using item.getInputStream().available() does not work
        var bytes = item.getInputStream().readAllBytes();
        var stream = new ByteArrayInputStream(bytes);

        var fileName = patientId + "/resources/" + item.getName();

        this.client.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .contentType(item.getContentType())
            .object(fileName)
            .stream(stream, stream.available(), -1)
            .build());

        return fullFilePath(fileName);
    }

    public List<String> listFiles(String patientId) {
        if (!bucketExists()) {
            throw new RuntimeException("Bucket doesn't exist");
        }

        var objects = this.client.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(patientId + "/resources").recursive(true).build());

        return StreamSupport.stream(objects.spliterator(), false).map(itemResult -> {
            try {
                return itemResult.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).map(Item::objectName).map(this::fullFilePath).toList();
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

    public void removeUnusedImages(String patientId, List<String> imagePaths) {
        var serverPart = this.url + "/" + this.bucket + "/";
        var filesOnServerAndInBucket = imagePaths.stream().filter(path -> path.startsWith(serverPart)).map(path -> path.replace(serverPart, "")).toList();
        var uploadedFilesForPatientId = listImagesFor(patientId);
        var toRemove = new ArrayList<>(uploadedFilesForPatientId);
        toRemove.removeAll(filesOnServerAndInBucket);

        var objects = toRemove.stream().map(DeleteObject::new).toList();
        removeObjects(objects);
    }

    private List<String> listImagesFor(String patientId) {
        var filesForPatientId = this.client.listObjects(ListObjectsArgs.builder().bucket(this.bucket).prefix(patientId + "/presentation").recursive(true).build());
        return StreamSupport.stream(filesForPatientId.spliterator(), false).map(itemResult -> {
            try {
                return itemResult.get();
            } catch (XmlParserException | ServerException | InvalidResponseException | InternalException |
                     InsufficientDataException | ErrorResponseException | NoSuchAlgorithmException |
                     InvalidKeyException | IllegalArgumentException | IOException e) {
                throw new RuntimeException(e);
            }
        }).map(Item::objectName).toList();
    }

    private void removeObjects(List<DeleteObject> objects) {
        var results = this.client.removeObjects(RemoveObjectsArgs.builder().bucket(this.bucket).objects(objects).build());
        for (Result<DeleteError> result : results) {
            try {
                System.out.println(result.get());
            } catch (JsonParseException e) {
                System.out.println(e.getMessage());
            } catch (JsonMappingException e) {
                System.out.println(e.getMessage());
            } catch (ErrorResponseException | NoSuchAlgorithmException | InvalidKeyException |
                     IllegalArgumentException | IOException | XmlParserException | ServerException |
                     InvalidResponseException | InternalException | InsufficientDataException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private String fullFilePath(String fileName) {
        return "/" + this.bucket + "/" + fileName;
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }
}
