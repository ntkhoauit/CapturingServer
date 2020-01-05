package com.capturingserver.utils;

public class SCPUtilsTest {
    public static void main(String[] args) {
        try {
            SCPUtils.transferToSftpServer("D:\\data\\arm chair\\1.zip", "/mywork/leedviz", true);
        } catch(Exception e) {
            System.err.println(e.getStackTrace());
        }
    }
}
