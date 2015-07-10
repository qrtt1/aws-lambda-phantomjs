package qty.aws.lambda;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.IOUtils;

public class ScreenCapture {

    private static final String ENGINE_PATH = "/var/task/phantomjs";
    private static final String TEMPLATE = loadScriptTemplate();
    private static final String PNG_EXTENSION = "png";
    private static final String JS_EXTENSION = "js";
    private String session = UUID.randomUUID().toString();

    public String execute(String bucket, String url) {

        String imagePath = preparePath(PNG_EXTENSION);
        String scriptPath = preparePath(JS_EXTENSION);
        try {
            runScript(url, imagePath, scriptPath);
            return uploadFile(bucket, imagePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            removeFile(imagePath, scriptPath);
        }
    }

    protected String uploadFile(String bucket, String imagePath) {
        AmazonS3 client = new AmazonS3Client();
        String key = "images/" + preparePath(PNG_EXTENSION, false);
        PutObjectRequest putObjectRequest = 
                new PutObjectRequest(bucket, key, 
                new File(imagePath)).withCannedAcl(CannedAccessControlList.PublicRead);
        client.putObject(putObjectRequest);
        return String.format("https://%s.s3.amazonaws.com/%s", bucket, key);
    }

    private void removeFile(String... path) {
        for (String p : path) {
            new File(p).delete();
        }
    }

    protected void runScript(String url, String imagePath, String scriptPath) {
        write(scriptPath, String.format(TEMPLATE, url, imagePath));

        try {
            ApplicationExecutor executor = new ApplicationExecutor(ENGINE_PATH, scriptPath);
            executor.execute(25000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void write(String scriptPath, String script) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(scriptPath);
            writer.write(script);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                IOUtils.closeQuietly(writer, null);
            }
        }
    }

    protected String preparePath(String extension) {
        return preparePath(extension, true);
    }

    protected String preparePath(String extension, boolean isTempfile) {
        return touchFile((isTempfile ? "/tmp/" : "") + session + "." + extension);
    }

    protected String touchFile(String path) {
        try {
            new File(path).createNewFile();
        } catch (IOException ignored) {
        }
        return path;
    }

    protected static String loadScriptTemplate() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream input = null;
        try {
            input = ScreenCapture.class.getResourceAsStream("/executor.tpl.js");
            IOUtils.copy(input, output);
            return new String(output.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(input, null);
        }
    }
}
