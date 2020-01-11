package com.capturingserver.services.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Vector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.capturingserver.services.SCPService;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

/**
 * Service for SFTP handling.
 */
@Service
public class SCPServiceImpl implements SCPService {
    private Logger logger = LoggerFactory.getLogger(SCPServiceImpl.class);

    @Value("${sftp.host}")
    private String SFTP_HOST;
    @Value("${sftp.port}")
    private int SFTP_PORT;
    @Value("${sftp.channel}")
    private String SFTP_CHANNEL;
    @Value("${sftp.username}")
    private String SFTP_USERNAME;
    @Value("${sftp.password}")
    private String SFTP_PASSWORD;

    private ChannelSftp setupJsch() throws JSchException {
        JSch jsch = new JSch();
        Session jschSession = jsch.getSession(SFTP_USERNAME, SFTP_HOST, SFTP_PORT);
        jschSession.setPassword(SFTP_PASSWORD);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        jschSession.setConfig(config);
        jschSession.connect();
        ChannelSftp channelSftp = (ChannelSftp) jschSession.openChannel(SFTP_CHANNEL);
        channelSftp.connect();
        return channelSftp;
    }

    private ChannelExec setupJschExec() throws JSchException {
        JSch jsch = new JSch();
        Session jschSession = jsch.getSession(SFTP_USERNAME, SFTP_HOST, SFTP_PORT);
        jschSession.setPassword(SFTP_PASSWORD);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        jschSession.setConfig(config);
        jschSession.connect();
        ChannelExec channelExec = (ChannelExec) jschSession.openChannel("exec");
        return channelExec;
    }

    public void unzip(String remoteFilePath) {
        try{
            //String unzipCommand = String.format( "unzip %s", remoteFilePath) ;
            String unzipCommand = String.format( "unzip %s -d armchair") ;
            // create the execution channel over the session
            ChannelExec channelExec = setupJschExec();
            // Set the command to execute on the channel and execute the command
            channelExec.setCommand(unzipCommand);
            channelExec.connect();

            // Get an InputStream from this channel and read messages, generated
            // by the executing command, from the remote side.
            InputStream in = channelExec.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Command execution completed here.

            // Retrieve the exit status of the executed command
            int exitStatus = channelExec.getExitStatus();
            if (exitStatus > 0) {
                System.out.println("Remote script exec error! " + exitStatus);
            }
            //Disconnect the Session
            channelExec.disconnect();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void transferFileFromSftpServer(String remoteFilePath, String localFolderPath,
                                                  String fileExtensionToTransfer,
                                                  boolean keepFileAfterCopy) {
        ChannelSftp channelSftp = null;
        String remoteFilePathWithExtension = remoteFilePath + FilenameUtils.EXTENSION_SEPARATOR + fileExtensionToTransfer;
        String localFileWithExtensionPath = localFolderPath + FilenameUtils.getName(remoteFilePath) + FilenameUtils.EXTENSION_SEPARATOR + fileExtensionToTransfer;

        try {
            channelSftp = setupJsch();

            File localFile = new File(localFileWithExtensionPath);
            if (!localFile.exists()) {
                channelSftp.get(remoteFilePathWithExtension , localFileWithExtensionPath);
                if (!keepFileAfterCopy) {
                    channelSftp.rm(remoteFilePath);
                }
            } else {
                String message =
                        "Problem when transfering file " + remoteFilePathWithExtension +
                                "  from sftp due to file already existed in " + localFolderPath;
                logger.error(message);
                throw new RuntimeException(message);
            }

        } catch (JSchException e) {
            String message =
                    "Problem while creating session to connect SFTP " + remoteFilePathWithExtension + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (SftpException e) {
            String message = "Problem while connecting to " + remoteFilePathWithExtension + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (Exception e) {
            String message =
                    "Problem while doing transfer from sftp " + remoteFilePathWithExtension + " to local " + localFolderPath + " " +
                            e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } finally {
            closeAllConnections(channelSftp);
        }
    }
    /**
     * Transfer all contents of a remote folder path to local
     * @param remoteFolderPath
     * @param localFolderPath
     * @param fileExtensionToTransfer transfer expected files with the given extension. If it's null, transfer all files
     * @param keepFileAfterCopy
     */
    public void transferFromSftpServer(String remoteFolderPath, String localFolderPath,
                                              String fileExtensionToTransfer,
                                              boolean keepFileAfterCopy) {
        ChannelSftp channelSftp = null;
        try {
            channelSftp = setupJsch();

            String remoteFolder = remoteFolderPath;
            channelSftp.cd(remoteFolder);

            Vector<ChannelSftp.LsEntry> files =
                    fileExtensionToTransfer == null ? channelSftp.ls("*") :
                            channelSftp.ls("*" + FilenameUtils.EXTENSION_SEPARATOR_STR + fileExtensionToTransfer);
            for (ChannelSftp.LsEntry file : files) {
                if (!file.getAttrs().isDir()) { // just transfer file, not folder
                    String localFilePath = localFolderPath + File.separator + file.getFilename();
                    File localFile = new File(localFilePath);
                    if (!localFile.exists()) {
                        String remoteFilePath = remoteFolder + "/" + file.getFilename();
                        channelSftp.get(remoteFilePath, localFilePath);
                        if (!keepFileAfterCopy) {
                            channelSftp.rm(remoteFilePath);
                        }
                    } else {
                        String message =
                                "Problem when transfering file " + file.getFilename() +
                                        "  from sftp due to file already existed in " + localFolderPath;
                        logger.error(message);
                        throw new RuntimeException(message);
                    }
                }
            }
        } catch (JSchException e) {
            String message =
                    "Problem while creating session to connect SFTP " + remoteFolderPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (SftpException e) {
            String message = "Problem while connecting to " + remoteFolderPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (Exception e) {
            String message =
                    "Problem while doing transfer from sftp " + remoteFolderPath + " to local " + localFolderPath + " " +
                            e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } finally {
            closeAllConnections(channelSftp);
        }
    }

    /**
     * Transfer file/folder to a remote folder path
     * @param localPath
     * @param remoteFolderPath
     */
    public void transferToSftpServer(String localPath, String remoteFolderPath,
                                            boolean keepFileAfterCopy) {
        ChannelSftp channelSftp = null;
        try {
            channelSftp = setupJsch();
            File sourceFile = new File(localPath);
            channelSftp.cd(remoteFolderPath);
            if (sourceFile.isFile()) {
                FileInputStream fis = new FileInputStream(sourceFile);
                channelSftp.put(fis, sourceFile.getName());
                fis.close();
                if (!keepFileAfterCopy) {
                    FileUtils.deleteQuietly(sourceFile);
                }
            } else {
                File[] files = sourceFile.listFiles();
                if (files != null) {
                    SftpATTRS attrs = null;
                    // check if the directory is already existing
                    try {
                        attrs = channelSftp.stat(remoteFolderPath + "/" + sourceFile.getName());
                    } catch (SftpException e) {
                        // Create a directory if it were not existed
                        channelSftp.mkdir(sourceFile.getName());
                    }

                    for (File f: files) {
                        transferToSftpServer(f.getAbsolutePath(), remoteFolderPath + "/" + sourceFile.getName(), keepFileAfterCopy);
                    }
                }
            }
        } catch (JSchException e) {
            String message =
                    "Problem while creating session to connect SFTP " + remoteFolderPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (SftpException e) {
            String message = "Problem while connecting to " + remoteFolderPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (FileNotFoundException e) {
            String message = "Problem with input file " + localPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (Exception e) {
            String message =
                    "Problem while doing transfer " + localPath + " to sftp " + remoteFolderPath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } finally {
            closeAllConnections(channelSftp);
        }
    }

    /**
     * Remove a file from remote path
     * @param remoteFilePath
     */
    public void removeRemoteFile(String remoteFilePath) {
        ChannelSftp channelSftp = null;
        try {
            channelSftp = setupJsch();
            channelSftp.rm(remoteFilePath);
        } catch (JSchException e) {
            String message = "Problem while creating session to connect SFTP " + remoteFilePath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } catch (SftpException e) {
            String message = "Remote file " + remoteFilePath + " does not exist";
            logger.warn(message, e);
        } catch (Exception e) {
            String message = "Problem while doing removeRemoteFile " + remoteFilePath + " " + e.getMessage();
            logger.error(message, e);
            throw new RuntimeException(message, e);
        } finally {
            closeAllConnections(channelSftp);
        }
    }

    private void closeAllConnections(ChannelSftp sftp) {
        try {
            if (sftp != null) {
                sftp.exit();
            }
        } catch (Exception e) {
            logger.error("Unexpected exception when closing connections", e);
        }
    }
}
