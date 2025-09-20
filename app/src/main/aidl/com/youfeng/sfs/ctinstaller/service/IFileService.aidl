package com.youfeng.sfs.ctinstaller.service;

interface IFileService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    boolean isExists(String path) = 2;

    boolean isDirectory(String path) = 3;

    void mkdirs(String path) = 4;

    void copyFile(String srcPath, String destPath) = 5;  // 示例：复制文件
}