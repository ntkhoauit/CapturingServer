package com.capturingserver.services;

import java.io.FileNotFoundException;

public interface SCPService {
    void unzip(String remoteFilePath);
    void transferFileFromSftpServer(String remoteFilePath, String localFolderPath,
                                    String fileExtensionToTransfer,
                                    boolean keepFileAfterCopy);
    void transferFromSftpServer(String remoteFolderPath, String localFolderPath,
                                String fileExtensionToTransfer,
                                boolean keepFileAfterCopy);
    void transferToSftpServer(String localPath, String remoteFolderPath,
                              boolean keepFileAfterCopy);
    void removeRemoteFile(String remoteFilePath);

}
