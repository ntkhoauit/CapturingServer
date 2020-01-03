package com.searchengine.utils;

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
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

/**
 * Utilities for SFTP handling.
 */
public final class SCPUtils {
    private static Logger logger = LoggerFactory.getLogger(SCPUtils.class);
    private static final String SFTP_HOST = "192.168.1.30";
    private static final int SFTP_PORT = 22;
    private static final String SFTP_CHANNEL = "sftp";
    private static final String SFTP_USERNAME = "fusion";
    private static final String SFTP_PASSWORD = "Fus10nNewC0";

    private SCPUtils() {
        //To avoid initialzation.
    }

    private static ChannelSftp setupJsch() throws JSchException {
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

    private static ChannelExec setupJschExec() throws JSchException {
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

    public static void unzip(String remoteFilePath) {
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
    /**
     * Transfer all contents of a remote folder path to local
     * @param remoteFolderPath
     * @param localFolderPath
     * @param fileExtensionToTransfer transfer expected files with the given extension. If it's null, transfer all files
     * @param keepFileAfterCopy
     */
    public static void transferFromSftpServer(String remoteFolderPath, String localFolderPath,
                                              String fileExtensionToTransfer,
                                              boolean keepFileAfterCopy) throws FileNotFoundException {
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
    public static void transferToSftpServer(String localPath, String remoteFolderPath,
                                            boolean keepFileAfterCopy) throws FileNotFoundException {
        ChannelSftp channelSftp = null;
        try {
            channelSftp = setupJsch();
            File sourceFile = new File(localPath);
            channelSftp.cd(remoteFolderPath);
            if (sourceFile.isFile()) {
                channelSftp.put(new FileInputStream(sourceFile), sourceFile.getName());
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
    public static void removeRemoteFile(String remoteFilePath) throws FileNotFoundException {
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

    private static void closeAllConnections(ChannelSftp sftp) {
        try {
            if (sftp != null) {
                sftp.exit();
            }
        } catch (Exception e) {
            logger.error("Unexpected exception when closing connections", e);
        }
    }
}

