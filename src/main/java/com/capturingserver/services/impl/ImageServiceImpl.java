package com.capturingserver.services.impl;

import static ch.qos.logback.core.util.EnvUtil.isWindows;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.capturingserver.services.ImageService;
import com.capturingserver.utils.CommonRESTRegistry;
import com.capturingserver.utils.Constants;
import com.capturingserver.utils.SCPUtils;

@Service
public class ImageServiceImpl implements ImageService {
    Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);

    @Value("${capturingserver.3dsCommand}")
    private String SERVER_3DS_COMMAND;
    @Value("${capturingserver.rootFolderLocation}")
    private String ROOT_FOLDER_LOCATION;
    @Value("${remoteserver.serverUrl}")
    private String SEARCH_ENGINE_SERVER_URL;
    @Value("${remoteserver.rootFolderLocation}")
    private String REMOTE_ROOT_FOLDER_LOCATION;

    @Override
    public void handle3dZipFolder(List<String> modelPaths) {
        modelPaths.forEach(modelPath -> CompletableFuture
                .supplyAsync(() -> { System.err.println(modelPath); return null; }));
        modelPaths.forEach(modelPath -> CompletableFuture
                .supplyAsync(() -> {
                            try {
                                String localModelFolderPath = ROOT_FOLDER_LOCATION + modelPath;
                                String remoteModelFolderPath = REMOTE_ROOT_FOLDER_LOCATION + modelPath;
                                String remoteModelFileNamePath = remoteModelFolderPath + Constants.FRONT_SLASH + modelPath;
                                SCPUtils.transferFileFromSftpServer(remoteModelFileNamePath, ROOT_FOLDER_LOCATION, "zip", true);
                                unzip3dFolder(localModelFolderPath + ".zip");

                                String capturedImageFolderPath = localModelFolderPath + Constants.BACKSLASH + "capturedImages";
                                ProcessBuilder builder = new ProcessBuilder().redirectErrorStream(true);
                                String command = SERVER_3DS_COMMAND + " -mxsString root:\"" + localModelFolderPath.replace("\\","\\\\")
                                        + "\" -mxsString baseFolder:\"" + capturedImageFolderPath.replace("\\","\\\\") + "\" -listenerLog \"test.log\"";
                                if (isWindows()) {
                                    builder.command("cmd.exe", "/c", command);
                                }
                                Process process = builder.start();

                                int exitCode = process.waitFor();
                                if (exitCode != 0) {
                                    throw new InterruptedException("Cannot capture 3d model");
                                }
                                String zipPath = capturedImageFolderPath + ".zip";
                                zipDirectory(capturedImageFolderPath, zipPath);
                                SCPUtils.transferToSftpServer(zipPath, remoteModelFolderPath, false);
                                FileUtils.deleteDirectory(new File(localModelFolderPath));
                                MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
                                requestBody.add(Constants.DATA, modelPath);
                                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody);
                                ResponseEntity<String> responseEntity = new RestTemplate().exchange(SEARCH_ENGINE_SERVER_URL + CommonRESTRegistry.CAPTURING_IMAGE_STATUS_RESPONSE, HttpMethod.POST, requestEntity, String.class);
                                if(responseEntity.getStatusCode() != HttpStatus.OK) {
                                    throw new InterruptedException("Something wrong during response sending");
                                }
                            } catch (Throwable e) {
                                logger.error("Something wrong during capturing image: " + e.getMessage());
                                MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
                                requestBody.add(Constants.DATA, modelPath);
                                requestBody.add(Constants.ERROR, e.getMessage());
                                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody);
                                new RestTemplate().postForObject(SEARCH_ENGINE_SERVER_URL + CommonRESTRegistry.CAPTURING_IMAGE_STATUS_RESPONSE, requestEntity, String.class);
                            }
                            return null;
                        }
                ));
    }

    private void unzip3dFolder(String modelPath) throws IOException {
        byte[] buffer = new byte[1024];
        int bufferSize = 1024;
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(modelPath));
        String extractedModelDirectory = modelPath.substring(0, modelPath.lastIndexOf('.'));
        Files.createDirectories(Paths.get(extractedModelDirectory));
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                Files.createDirectories(Paths.get(extractedModelDirectory + Constants.BACKSLASH + entry.getName()));
            } else {
                String entryName = entry.getName();
                if (Paths.get(entryName).getParent() != null) {
                    if (Paths.get(entryName).getParent().getParent() != null) {
                        createFolder(extractedModelDirectory + Constants.BACKSLASH + Paths.get(entryName).getParent().getParent());
                    }
                    createFolder(extractedModelDirectory + Constants.BACKSLASH + Paths.get(entryName).getParent());
                }
                String uncompressedFilename = extractedModelDirectory + Constants.BACKSLASH + entryName;
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
        zipInputStream.close();
        Files.delete(Paths.get(modelPath));
    }


    public static void zipDirectory(String sourceDirectoryPath, String zipPath) throws IOException {

        Path zipFilePath = Files.createFile(Paths.get(zipPath));

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            File dir = new File(sourceDirectoryPath);

            String[] files = dir.list();
            for (String file: files){
                String path = sourceDirectoryPath + Constants.BACKSLASH + file;
                ZipEntry zipEntry = new ZipEntry(file);
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    zipOutputStream.write(Files.readAllBytes(Paths.get(path)));
                    zipOutputStream.closeEntry();
                } catch (Exception e) {
                    System.err.println(e);
                }
            }
        }

    }

    private void createFolder(String serverPath) {
        File newFolder = new File(serverPath);
        if (!newFolder.exists()) {
            newFolder.mkdirs();
        }
    }
}
