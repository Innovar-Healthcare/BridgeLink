package com.mirth.connect.plugins.dynamiclookup.client.util;

import com.mirth.connect.client.ui.Frame;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import java.io.File;

public class FileChooser {
    public File createFileForExport(Frame parent, String defaultFileName, String fileExtension) {
        JFileChooser exportFileChooser = new JFileChooser();
        if (defaultFileName != null) {
            exportFileChooser.setSelectedFile(new File(defaultFileName));
        }

        if (fileExtension != null) {
            exportFileChooser.setFileFilter(new CustomFileFilter(fileExtension));
        }

        File currentDir = new File(Frame.userPreferences.get("currentDirectory", ""));
        if (currentDir.exists()) {
            exportFileChooser.setCurrentDirectory(currentDir);
        }

        if (exportFileChooser.showSaveDialog(parent) != 0) {
            return null;
        } else {
            Frame.userPreferences.put("currentDirectory", exportFileChooser.getCurrentDirectory().getPath());
            File exportFile = exportFileChooser.getSelectedFile();
            if (exportFile.getName().length() < 4 || !FilenameUtils.getExtension(exportFile.getName()).equalsIgnoreCase(fileExtension)) {
                exportFile = new File(exportFile.getAbsolutePath() + "." + fileExtension.toLowerCase());
            }

            return exportFile.exists() && !parent.alertOption(parent, "This file already exists.  Would you like to overwrite it?") ? null : exportFile;
        }
    }

    private class CustomFileFilter extends FileFilter {
        private String fileExtension;

        public CustomFileFilter(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public boolean accept(File file) {
            return file.isDirectory() || FilenameUtils.getExtension(file.getName()).equalsIgnoreCase(this.fileExtension);
        }

        public String getDescription() {
            if (this.fileExtension.equalsIgnoreCase("csv")) {
                return "CSV files";
            }

            if (this.fileExtension.equalsIgnoreCase("json")) {
                return "JSON files";
            }

            return "All Files";
        }
    }
}
