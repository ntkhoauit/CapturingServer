package com.capturingserver.services.impl;

import static ch.qos.logback.core.util.EnvUtil.isWindows;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.capturingserver.services.ImageService;
import com.capturingserver.utils.CommonRESTRegistry;
import com.capturingserver.utils.Constants;

@Service
public class ImageServiceImpl implements ImageService {
    Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);
    @Value("${capturingserver.configuration.uncompressedLocation}")
    private String SERVER_UNCOMPRESSED_LOCATION;
    @Value("${capturingserver.configuration.3dsCommand}")
    private String SERVER_3DS_COMMAND;
    @Value("${capturingserver.configuration.rootFolderLocation}")
    private String ROOT_FOLDER_LOCATION;
    @Value("${searchengine.serverUrl}")
    private String SEARCH_ENGINE_SERVER_URL;


    @Override
    public void handle3dZipFolder(List<String> modelPaths) {
        List<String> invalidModelPaths = new ArrayList<>();
        modelPaths.forEach(modelPath -> {
            try {
                unzip3dFolder(modelPath);

                ProcessBuilder builder = new ProcessBuilder();
                String uncompressedDirectory = SERVER_UNCOMPRESSED_LOCATION + ROOT_FOLDER_LOCATION;
                System.out.println(uncompressedDirectory);
                String modelDirectory = ROOT_FOLDER_LOCATION + modelPath;
                String command = SERVER_3DS_COMMAND + " -mxsString root:'" + uncompressedDirectory.replace("/","")
                        + "' -mxsString baseFolder:'" + modelDirectory + "' -listenerLog \"test.log\"";
                System.out.println(command);
                if (isWindows()){
                    builder.command("cmd.exe", "/c", command);
                }
                Process process = builder.start();
                int exitCode = process.waitFor();
                if (exitCode == 0){
                    FileSystemUtils.deleteRecursively(Paths.get(uncompressedDirectory));
                }
                else{
                    BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    invalidModelPaths.add(modelPath);
                    logger.error(stdError.readLine());

                }
            } catch (IOException | InterruptedException e) {
                logger.error("Something wrong during capturing image: " + e.getMessage());
                invalidModelPaths.add(modelPath);
            }
        });
        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add(Constants.DATA, modelPaths);
        requestBody.add(Constants.INVALID_DATA, modelPaths);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody);
        new RestTemplate().postForObject(SEARCH_ENGINE_SERVER_URL + CommonRESTRegistry.CAPTURING_IMAGE_STATUS_RESPONSE, requestEntity, Boolean.class);
    }

    private void unzip3dFolder(String modelPath) throws IOException {
        byte[] buffer = new byte[1024];
        int bufferSize = 1024;
        InputStream is = new FileInputStream(modelPath);
        ZipInputStream zipInputStream = new ZipInputStream(is);
        ZipEntry entry;
        boolean isRootFolder = true;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                if (isRootFolder) {
                    isRootFolder = false;
                }
                Files.createDirectories(Paths.get(SERVER_UNCOMPRESSED_LOCATION + entry.getName()));
            } else {
                String uncompressedFilename = SERVER_UNCOMPRESSED_LOCATION + entry.getName();
                Files.createFile(Paths.get(uncompressedFilename));
                FileOutputStream fos = new FileOutputStream(uncompressedFilename);
                BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
                int count;
                while ((count = zipInputStream.read(buffer, 0, bufferSize)) != -1) {
                    bos.write(buffer, 0, count);
                }
                bos.flush();
                bos.close();
            }
        }
    }
}
