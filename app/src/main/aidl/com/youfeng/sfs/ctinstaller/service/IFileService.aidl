package com.youfeng.sfs.ctinstaller.service;

interface IFileService {

    void destroy(); // Destroy method defined by Shizuku server

    void exit(); // Exit method defined by user

    boolean isExists(String path);

    boolean isDirectory(String path);

    void mkdirs(String path);

    void copyFile(String srcPath, String destPath);  // 示例：复制文件
}