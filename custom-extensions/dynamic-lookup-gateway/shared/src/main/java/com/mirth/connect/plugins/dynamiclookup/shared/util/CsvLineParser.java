/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.util;

import com.opencsv.CSVReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.StringReader;
import java.util.AbstractMap;
import java.util.Map;

public class CsvLineParser {
    private static final Logger logger = LogManager.getLogger(CsvLineParser.class);

    /**
     * Parses a CSV line assuming the first column is the key, and the second is always a string value.
     * Handles quoted CSV fields and embedded commas properly.
     *
     * @param line       A CSV line
     * @param lineNumber The line number (optional for caller context)
     * @return A Map.Entry of key to raw value string, or null if line is malformed
     */
    public static Map.Entry<String, String> parseLine(String line, int lineNumber) {
        try (CSVReader csvReader = new CSVReader(new StringReader(line))) {
            String[] parts = csvReader.readNext();

            if (parts == null || parts.length < 2) {
                logger.debug("Skipping malformed CSV line at {}: '{}'", lineNumber, line);
                return null; // malformed
            }

            String key = parts[0].trim();
            String rawValue = parts[1].trim();

            if (key.isEmpty() || rawValue.isEmpty()) {
                logger.debug("Skipping line {} due to empty key or value: '{}'", lineNumber, line);
                return null; // empty key or value
            }

            return new AbstractMap.SimpleEntry<>(key, rawValue);
        } catch (Exception ex) {
            logger.debug("Failed to parse line {}: '{}'. Error: {}", lineNumber, line, ex.getMessage());
            return null; // error parsing
        }
    }
}

