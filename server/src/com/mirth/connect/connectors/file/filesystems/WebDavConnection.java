/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.filters.RegexFilenameFilter;

public class WebDavConnection implements FileSystemConnection {
    public class WebDavFileInfo implements FileInfo {
        private String thePath;
        private DavResource theFile;

        public WebDavFileInfo(String path, DavResource theFile) {
            this.thePath = path;
            this.theFile = theFile;
        }

        public long getLastModified() {
            Date modified = theFile.getModified();
            return modified != null ? modified.getTime() : 0L;
        }

        public String getName() {
            return theFile.getName();
        }

        /** Gets the absolute pathname of the file */
        public String getAbsolutePath() {
            return theFile.getPath();
        }

        public String getCanonicalPath() throws IOException {
            return theFile.getPath();
        }

        /** Gets the absolute pathname of the directory holding the file */
        public String getParent() {
            return this.thePath;
        }

        public long getSize() {
            Long length = theFile.getContentLength();
            return length != null ? length : 0L;
        }

        public boolean isDirectory() {
            return theFile.isDirectory();
        }

        public boolean isFile() {
            return !theFile.isDirectory();
        }

        public boolean isReadable() {
            return true;
        }

        @Override
        public void populateSourceMap(Map<String, Object> sourceMap) {}
    }

    private static transient Log logger = LogFactory.getLog(WebDavConnection.class);

    /** The WebDAV client instance */
    private Sardine sardine = null;
    private String baseUrl = null;
    private boolean secure = false;
    private String username = null;
    private String password = null;

    public WebDavConnection(String host, boolean secure, FileSystemConnectionOptions fileSystemOptions) throws Exception {
        this.secure = secure;
        username = fileSystemOptions.getUsername();
        password = fileSystemOptions.getPassword();
        baseUrl = (secure ? "https://" : "http://") + host;

        if (!username.equals("null")) {
            sardine = SardineFactory.begin(username, password);
        } else {
            sardine = SardineFactory.begin();
        }
    }

    @Override
    public List<FileInfo> listFiles(String fromDir, String filenamePattern, boolean isRegex, boolean ignoreDot) throws Exception {
        FilenameFilter filenameFilter;

        if (isRegex) {
            filenameFilter = new RegexFilenameFilter(filenamePattern);
        } else {
            filenameFilter = new WildcardFileFilter(filenamePattern.trim().split("\\s*,\\s*"));
        }

        return list(fromDir, true, filenameFilter, ignoreDot);
    }

    @Override
    public List<String> listDirectories(String fromDir) throws Exception {
        List<String> directories = new ArrayList<String>();
        for (FileInfo directory : list(fromDir, false, null, false)) {
            directories.add(directory.getCanonicalPath());
        }
        return directories;
    }

    private List<FileInfo> list(String fromDir, boolean files, FilenameFilter filenameFilter, boolean ignoreDot) throws Exception {
        String dirPath = getFullPath(fromDir, "");
        String dirUrl = baseUrl + dirPath;

        List<DavResource> resources;
        try {
            // Depth 1: the collection itself plus its immediate children (PROPFIND Depth: 1).
            resources = sardine.list(dirUrl, 1);
        } catch (IOException e) {
            logger.error("Unable to list directory: '" + fromDir + "'", e);
            throw e;
        }

        if (resources == null || resources.isEmpty()) {
            return new ArrayList<FileInfo>();
        }

        List<FileInfo> fileInfoList = new ArrayList<FileInfo>(resources.size());
        for (DavResource resource : resources) {
            // sardine.list(url, 1) returns the requested collection itself as the first entry
            // (depth 0) followed by its children (depth 1) -- skip the self-entry.
            if (isSelfEntry(resource, dirPath)) {
                continue;
            }

            String name = resource.getName();

            if (files) {
                if (!resource.isDirectory() && filenameFilter.accept(null, name) && !(ignoreDot && name.startsWith("."))) {
                    fileInfoList.add(new WebDavFileInfo(fromDir, resource));
                }
            } else if (resource.isDirectory()) {
                fileInfoList.add(new WebDavFileInfo(fromDir, resource));
            }
        }

        return fileInfoList;
    }

    private boolean isSelfEntry(DavResource resource, String dirPath) {
        String resourcePath = resource.getPath();
        if (resourcePath == null) {
            return false;
        }
        String normalizedResourcePath = resourcePath.endsWith("/") ? resourcePath : resourcePath + "/";
        String normalizedDirPath = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        return normalizedResourcePath.equals(normalizedDirPath);
    }

    @Override
    public boolean exists(String file, String path) {
        try {
            return sardine.exists(baseUrl + getFullPath(path, file));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public InputStream readFile(String file, String fromDir, Map<String, Object> sourceMap) throws Exception {
        String fullPath = getFullPath(fromDir, file);
        String url = baseUrl + fullPath;

        if (isCollection(url)) {
            logger.error("Invalid filepath: " + fullPath);
            throw new Exception("Invalid Path");
        }

        return sardine.get(url);
    }

    @Override
    public void closeReadFile() throws Exception {
        // irrelevant
    }

    @Override
    public boolean canAppend() {
        return false;
    }

    @Override
    public void writeFile(String file, String toDir, boolean append, InputStream is, long contentLength, Map<String, Object> connectorMap) throws Exception {
        String fullPath = getFullPath(toDir, file);
        String dirUrl = baseUrl + getFullPath(toDir, "");
        String fileUrl = baseUrl + fullPath;

        // first check if the toDir exists.
        if (!sardine.exists(dirUrl)) {

            // create the directory.
            sardine.createDirectory(dirUrl);
            logger.info("Destination directory does not exist. Creating directory: '" + toDir + "'");

        } else if (!isCollection(dirUrl)) {
            // make sure it's a directory, not a file.
            throw new Exception("The destination directory path is invalid: '" + toDir + "'");
        }

        // valid directory. now write the file.
        try {
            sardine.put(fileUrl, is);
        } catch (IOException e) {
            logger.error("Unable to write file: '" + fullPath + "'", e);
            throw e;
        }
    }

    @Override
    public void delete(String file, String fromDir, boolean mayNotExist) throws Exception {
        String fullPath = getFullPath(fromDir, file);
        String url = baseUrl + fullPath;

        try {
            sardine.delete(url);
        } catch (IOException e) {
            if (mayNotExist) {
                logger.debug("Unable to delete file (may not exist): '" + fullPath + "'", e);
            } else {
                logger.error("Unable to delete file: '" + fullPath + "'", e);
                throw e;
            }
        }
    }

    @Override
    public void move(String fromName, String fromDir, String toName, String toDir) throws Exception {
        String sourcePath = getFullPath(fromDir, fromName);
        String targetPath = getFullPath(toDir, toName);
        String sourceUrl = baseUrl + sourcePath;
        String targetUrl = baseUrl + targetPath;
        String toDirUrl = baseUrl + getFullPath(toDir, "");

        // first check if the toDir exists.
        if (!sardine.exists(toDirUrl)) {

            // create the directory. and then move.
            sardine.createDirectory(toDirUrl);
            logger.info("Move-To directory does not exist. Creating directory: '" + toDir + "'");

        } else if (!isCollection(toDirUrl)) {
            // make sure it's a directory, not a file.
            throw new Exception("The move-to directory path is invalid: '" + toDir + "'");
        }

        // valid directory. now move the file.
        try {
            sardine.move(sourceUrl, targetUrl);
        } catch (IOException e) {
            logger.error("Unable to move file: '" + sourcePath + "' to '" + targetPath + "'", e);
            throw e;
        }
    }

    @Override
    public boolean isConnected() {
        return checkConnection();
    }

    @Override
    public void disconnect() {}

    @Override
    public void activate() {
        // irrelevant
    }

    @Override
    public void passivate() {
        // irrelevant
    }

    @Override
    public void destroy() {
        try {
            if (sardine != null) {
                sardine.shutdown();
            }
        } catch (IOException e) {
            logger.debug(e);
        }
    }

    @Override
    public boolean isValid() {
        return checkConnection();
    }

    private boolean checkConnection() {
        if (sardine == null) {
            return false;
        }
        try {
            return sardine.exists(baseUrl + "/");
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    @Override
    public boolean canRead(String readDir) {
        try {
            String url = baseUrl + getFullPath(readDir, "");
            return sardine.exists(url) && isCollection(url);
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    @Override
    public boolean canWrite(String writeDir) {
        try {
            String url = baseUrl + getFullPath(writeDir, "");
            return sardine.exists(url) && isCollection(url);
        } catch (IOException e) {
            logger.debug(e);
            return false;
        }
    }

    /**
     * Tests whether the resource at the given URL is a WebDAV collection (directory). Uses a
     * Depth: 0 PROPFIND (via {@code sardine.list(url, 0)}) so only the resource itself is
     * described, not its children.
     */
    private boolean isCollection(String url) throws IOException {
        List<DavResource> resources = sardine.list(url, 0);
        return !resources.isEmpty() && resources.get(0).isDirectory();
    }

    private String getFullPath(String dir, String file) {
        return ("/" + dir + "/" + file).replaceAll("//", "/");
    }
}
