/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles all file operations for the version history plugin.
 * Provides methods for reading/writing XML files and directory management.
 */
public class FileOperations {

    private static final Logger logger = LogManager.getLogger(FileOperations.class);

    private final File baseDirectory;
    private final ObjectXMLSerializer serializer;

    /**
     * Creates a new FileOperations instance
     *
     * @param baseDirectory Base directory for repository (e.g., repo root)
     * @param serializer    XML serializer for object conversion
     */
    public FileOperations(File baseDirectory, ObjectXMLSerializer serializer) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory cannot be null");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }

        this.baseDirectory = baseDirectory;
        this.serializer = serializer;

        logger.debug("FileOperations initialized with base directory: {}", baseDirectory.getAbsolutePath());
    }

    /**
     * Writes an object to XML file
     *
     * @param directory Directory within base (e.g., "Channels", "Libraries")
     * @param filename  Filename (e.g., "channel-123.xml")
     * @param object    Object to serialize
     * @return Relative path from base directory (e.g., "Channels/channel-123.xml")
     * @throws IOException if write fails
     */
    public String writeXml(String directory, String filename, Object object) throws IOException {
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory cannot be null or empty");
        }
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }

        // Serialize object to XML
        String xml;
        try {
            xml = serializer.serialize(object);
        } catch (Exception e) {
            throw new IOException("Failed to serialize object: " + e.getMessage(), e);
        }

        // Create directory path
        File dir = new File(baseDirectory, directory);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
            }
            logger.info("Created directory: {}", dir.getAbsolutePath());
        }

        // Write to file
        File file = new File(dir, filename);
        String relativePath = directory + "/" + filename;

        Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);

        logger.debug("Wrote XML file: {}", relativePath);

        return relativePath;
    }

    /**
     * Reads an object from XML file
     *
     * @param <T>          Type of object to deserialize
     * @param relativePath Relative path from base directory
     * @param clazz        Class of object to deserialize
     * @return Deserialized object
     * @throws IOException if read or deserialization fails
     */
    public <T> T readXml(String relativePath, Class<T> clazz) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }

        File file = new File(baseDirectory, relativePath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }

        String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        // Reuse deserializeXml()
        return deserializeXml(xml, clazz);
    }

    /**
     * Deserializes XML string content to object
     *
     * @param <T>        Type of object to deserialize
     * @param xmlContent XML content as string
     * @param clazz      Class of object to deserialize
     * @return Deserialized object
     * @throws IOException if deserialization fails
     */
    public <T> T deserializeXml(String xmlContent, Class<T> clazz) throws IOException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("XML content cannot be null or empty");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        try {
            @SuppressWarnings("unchecked") T object = (T) serializer.deserialize(xmlContent, clazz);
            logger.debug("Deserialized XML content to {}", clazz.getSimpleName());
            return object;
        } catch (Exception e) {
            throw new IOException("Failed to deserialize XML content: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures a directory exists, creating it if necessary
     *
     * @param directory Directory path relative to base
     * @return Absolute path of the directory
     * @throws IOException if directory creation fails
     */
    public Path ensureDirectoryExists(String directory) throws IOException {
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory cannot be null or empty");
        }

        File dir = new File(baseDirectory, directory);
        Path dirPath = dir.toPath();

        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            logger.info("Created directory: {}", dirPath);
        }

        return dirPath;
    }

    /**
     * Lists all files in a directory matching a pattern
     *
     * @param directory Directory path relative to base
     * @param pattern   Glob pattern (e.g., "*.xml")
     * @return List of relative file paths
     * @throws IOException if directory read fails
     */
    public List<String> listFiles(String directory, String pattern) throws IOException {
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory cannot be null or empty");
        }

        File dir = new File(baseDirectory, directory);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.debug("Directory does not exist: {}", dir.getAbsolutePath());
            return new ArrayList<>();
        }

        Path dirPath = dir.toPath();

        try (Stream<Path> stream = Files.list(dirPath)) {
            List<String> files = stream.filter(Files::isRegularFile).filter(path -> pattern == null || path.getFileName().toString().matches(pattern.replace("*", ".*"))).map(path -> directory + "/" + path.getFileName().toString()).collect(Collectors.toList());

            logger.debug("Found {} files in directory: {}", files.size(), directory);
            return files;
        }
    }

    /**
     * Lists all files in a directory (no pattern filter)
     *
     * @param directory Directory path relative to base
     * @return List of relative file paths
     * @throws IOException if directory read fails
     */
    public List<String> listFiles(String directory) throws IOException {
        return listFiles(directory, null);
    }

    /**
     * Deletes a file
     *
     * @param relativePath Relative path from base directory
     * @return true if file was deleted, false if file didn't exist
     * @throws IOException if deletion fails
     */
    public boolean deleteFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }

        File file = new File(baseDirectory, relativePath);

        if (!file.exists()) {
            logger.debug("File does not exist, nothing to delete: {}", relativePath);
            return false;
        }

        boolean deleted = Files.deleteIfExists(file.toPath());

        if (deleted) {
            logger.info("Deleted file: {}", relativePath);
        }

        return deleted;
    }

    /**
     * Checks if a file exists
     *
     * @param relativePath Relative path from base directory
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }

        File file = new File(baseDirectory, relativePath);
        return file.exists() && file.isFile();
    }

    /**
     * Gets the absolute path for a relative path
     *
     * @param relativePath Relative path from base directory
     * @return Absolute path
     */
    public Path getAbsolutePath(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }

        return Paths.get(baseDirectory.getAbsolutePath(), relativePath);
    }

    /**
     * Reads raw file content from the working tree as a UTF-8 string.
     *
     * @param relativePath Relative path from base directory (e.g., "Channels/abc-123.xml")
     * @return File content as string
     * @throws IOException if file does not exist or read fails
     */
    public String readFileContent(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }

        File file = new File(baseDirectory, relativePath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + relativePath);
        }

        logger.debug("Reading file content: {}", relativePath);
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Gets the base directory
     *
     * @return Base directory
     */
    public File getBaseDirectory() {
        return baseDirectory;
    }
}
