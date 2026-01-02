/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateContextSet;
import com.mirth.connect.model.codetemplates.CodeTemplateProperties.CodeTemplateType;
import com.mirth.connect.plugins.CodeTemplatePlugin;

public class LookupTableReferencePlugin extends CodeTemplatePlugin {
    private static final Logger logger = LogManager.getLogger(LookupTableReferencePlugin.class);

    public LookupTableReferencePlugin(String name) {
        super(name);
    }

    //@formatter:off
    @Override
    public Map<String, List<CodeTemplate>> getReferenceItems() {
        Map<String, List<CodeTemplate>> referenceItems = new HashMap<String, List<CodeTemplate>>();

        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();

        templates.add(new CodeTemplate(
                "Lookup Value by Key",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var value = LookupHelper.get(group, key);",
                "Retrieves a value from the specified lookup group using the given key. Returns null if no match is found."
        ));

        templates.add(new CodeTemplate(
        	    "Lookup Value by Key with TTL",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var value = LookupHelper.get(group, key, /*ttlHours*/ 0, /*ttlMinutes*/ 30);",
        	    "Retrieves a value from the specified lookup group using the given key and a TTL "
        	        + "expressed in hours and minutes. "
        	        + "If both values are 0, TTL is ignored. "
        	        + "If the cached or database value is older than the TTL, null is returned."
        	));


        templates.add(new CodeTemplate(
                "Lookup Value with Default Fallback",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var value = LookupHelper.get(group, key, defaultValue);",
                "Retrieves a value from a lookup group, or returns the default if the group or key is missing."
        ));

        templates.add(new CodeTemplate(
        	    "Lookup Value with TTL and Default Fallback",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var value = LookupHelper.get(group, key, /*ttlHours*/ 0, /*ttlMinutes*/ 30, /*defaultValue*/ 'N/A');",
        	    "Retrieves a value from the specified lookup group using the given key and a TTL "
        	        + "expressed in hours and minutes. "
        	        + "If both hours and minutes are 0, TTL is ignored. "
        	        + "If the value is missing or stale, the provided default value is returned instead."
        	));


        templates.add(new CodeTemplate(
                "Lookup Values Matching Pattern",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var values = LookupHelper.getMatching(group, keyPattern);",
                "Retrieves key-value pairs from the specified lookup group using the default limit (1000). "
                    + "Returns an empty map if the group does not exist or no matches are found."
            ));

        templates.add(new CodeTemplate(
                "Lookup Values Matching Pattern (Custom Limit)",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var values = LookupHelper.getMatching(group, keyPattern, limit);",
                "Retrieves key-value pairs from the specified lookup group using a custom limit. "
                    + "The limit is capped internally to prevent excessive memory usage. "
                    + "Returns an empty map if the group does not exist or no matches are found."
            ));

        templates.add(new CodeTemplate(
                "Lookup Values Count Matching Pattern",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var count = LookupHelper.getMatchingCount(group, keyPattern);",
                "Retrieves the number of entries in the specified lookup group whose keys match the given pattern. "
                    + "Returns 0 if the group does not exist or no matches are found."
            ));

        templates.add(new CodeTemplate(
                "Batch Lookup by Keys",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var keys = [\"key1\", \"key2\", \"key3\"];\nvar values = LookupHelper.getBatch(group, keys);",
                "Retrieves multiple key-value pairs from the specified lookup group in a single operation. Returns an empty map if the group is not found or none of the keys exist."
        ));

        templates.add(new CodeTemplate(
        	    "Batch Lookup by Keys with TTL",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var keys = [\"key1\", \"key2\", \"key3\"];\n"
        	        + "var batch = LookupHelper.getBatch(group, keys, /*ttlHours*/ 0, /*ttlMinutes*/ 30);",
        	    "Retrieves multiple key-value pairs from the specified lookup group using a TTL "
        	        + "expressed in hours and minutes. "
        	        + "If both hours and minutes are 0, TTL is ignored. "
        	        + "Only values updated within the TTL window are returned. "
        	        + "Returns an empty map if the group is not found, or if all values are stale or missing."
        	));


        templates.add(new CodeTemplate(
                "Lookup Key Existence in Group",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var found = LookupHelper.exists(group, key);",
                "Checks whether the specified key exists in the given lookup group. Returns true if found; false if the group or key does not exist, or if an error occurs."
        ));

        templates.add(new CodeTemplate(
                "Get Lookup Cache Statistics",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var stats = LookupHelper.getCacheStats(group);",
                "Retrieves cache and usage statistics for the specified lookup group, including hit/miss counts, hit rate, evictions, total lookups, and last accessed time. Returns an empty map if the group is not found or an error occurs."
        ));

        templates.add(new CodeTemplate(
                "Set Lookup Value by Key",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var success = LookupHelper.set(group, key, value);",
                "Sets a value in the specified lookup group using the given key. Returns true if successful, false otherwise."
        ));

        templates.add(new CodeTemplate(
                "Deletes a lookup value by group name and key",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var success = LookupHelper.deleteValue(group, key);",
                "Deletes a value in the specified lookup group by key. Returns true if successful, false otherwise."
        ));


        templates.add(new CodeTemplate(
                "Deletes all lookup values in the specified group",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var success = LookupHelper.deleteAllValues(group);",
                "Deletes all values in the given lookup group. Returns true if successful, false otherwise."
        ));

        templates.add(new CodeTemplate(
                "Imports multiple values into a lookup group",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var payload = {\n" +
                "  \"key1\": \"value1\",\n" +
                "  \"key2\": \"value2\"\n" +
                "};\n" +
                "var res = LookupHelper.importValues(group, payload, true);\n" +
                "if (!res || String(res.ok) !== 'true') {\n" +
                "  logger.error('Import values failed for group: ' + group + (res ? (' - ' + res.errorMessage) : ''));\n" +
                "} else {\n" +
                "  logger.info('Imported ' + res.importedCount + ' entries into groupId=' + res.groupId);\n" +
                "}",
                "Imports key-value pairs into the specified lookup group. " +
                "If clearExisting is true, all existing values are removed before import. " +
                "Returns { ok: 'true', groupId, importedCount } on success; otherwise { ok: 'false', errorCode, errorMessage }."
        ));

        templates.add(new CodeTemplate(
                "Put Lookup Value If Absent",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var success = LookupHelper.putIfAbsent(group, key, value);\n" +
                "if (!success) {\n" +
                "  logger.warn('Key already exists or insert failed in group: ' + group);\n" +
                "}",
                "Inserts a value into the specified lookup group only if the key does not already exist. " +
                "Returns true if the value was inserted, false otherwise."
        ));
        
        templates.add(new CodeTemplate(
        	    "Compare and Swap Lookup Value",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var success = LookupHelper.compareAndSwap(group, key, expectedValue, newValue);\n" +
	    		"if (!success) {\n" +
	    		"  logger.warn('Compare-and-swap failed for group: ' + group + ', key: ' + key + ', expectedValue: ' + expectedValue + ', newValue: ' + newValue);\n" +
	    		"}",
        	    "Atomically updates a lookup value only if its current value matches the expected value. " +
        	    "Returns true if the value was successfully updated; false if the key does not exist, the value does not match, or an error occurred."
       	));

        templates.add(new CodeTemplate(
        	    "Update Lookup Value by Delta",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var success = LookupHelper.updateValueByDelta(group, key, delta);\n" +
        	    "if (!success) {\n" +
        	    "  logger.warn('Failed to update lookup value by delta for group: ' + group + ', key: ' + key + ', delta: ' + delta);\n" +
        	    "}",
        	    "Atomically increments or decrements the numeric value of a lookup key by the specified delta. " +
        	    "Returns true if the value was successfully updated; false if the group or key was not found, or if an error occurred."
    	));

        templates.add(new CodeTemplate(
                "Search lookup values by JSON filter (Advanced)",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "// JavaScript snippet for Dynamic Lookup (Advanced Search)\n" +
                "var groupName = \"JSON - Search Test\";\n\n" +
                "// Optional KEY pattern filter (SQL LIKE)\n" +
                "var keyPattern = \"user_5%\";\n\n" +
                "// JSON field filters (array form; easy to edit)\n" +
                "// NOTE: The server normalizes \"value\" to text; valueType controls validation and casting (STRING/NUMBER/BOOLEAN).\n" +
                "var filterObj = [\n" +
                "  {\n" +
                "    \"field\": \"user.profile.age\",\n" +
                "    \"op\": \">=\",\n" +
                "    \"valueType\": \"NUMBER\",\n" +
                "    \"value\": \"40\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"field\": \"meta.role\",\n" +
                "    \"op\": \"=\",\n" +
                "    \"valueType\": \"STRING\",\n" +
                "    \"value\": \"support\"\n" +
                "  }\n" +
                "];\n" +
                "var filterJson = JSON.stringify(filterObj);\n\n" +
                "// NOTE:\n" +
                "// The lookup returns only the FIRST 1000 matching entries.\n" +
                "// This limit is applied to protect performance.\n\n" +
                "var start = new Date().getTime();\n" +
                "var results = LookupHelper.searchValuesByJsonFields(\n" +
                "    groupName,\n" +
                "    keyPattern,\n" +
                "    filterJson\n" +
                ");\n" +
                "var elapsed = new Date().getTime() - start;\n\n" +
                "// DEBUG OUTPUT (remove or comment out in production)\n" +
                "if (results == null) {\n" +
                "    logger.error(\"Lookup failed for group: \" + groupName);\n" +
                "} else if (results.isEmpty()) {\n" +
                "    logger.info(\"No matching entries (elapsed=\" + elapsed + \" ms) in group=\" + groupName);\n" +
                "} else {\n" +
                "    logger.info(\"Sample results (showing up to 2 entries):\");\n" +
                "    var iter = results.keySet().iterator();\n" +
                "    var count = 0;\n\n" +
                "    while (iter.hasNext() && count < 2) {\n" +
                "        var key = iter.next();\n" +
                "        var value = results.get(key);\n" +
                "        logger.info(\"  key=\" + key + \", value=\" + value);\n" +
                "        count++;\n" +
                "    }\n\n" +
                "    logger.info(\n" +
                "        \"Found \" + results.size() + \" matching entries (elapsed=\" + elapsed + \" ms) in group=\" + groupName\n" +
                "    );\n" +
                "}\n",
                "Retrieves lookup values using advanced JSON field filtering with an optional key pattern. " +
                "Filters are provided as an array of conditions (field, operator, valueType, value) and " +
                "converted to JSON before execution. Nested JSON paths are supported. " +
                "The key pattern uses SQL LIKE semantics. " +
                "For performance reasons, only the first 1000 matching entries are returned."
            ));


        templates.add(new CodeTemplate(
                "Creates a lookup group",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var payload = {\n" +
                "  name: 'MyGroup',\n" +
                "  description: 'optional',\n" +
                "  version: '1.0.0',\n" +
                "  cacheSize: '1000',\n" +
                "  cachePolicy: 'LRU', // or 'FIFO'\n" +
                "  statisticsEnabled: 'true',\n" +
                "\n" +
                "  // --- Optional JSON configuration ---\n" +
                "  // NOTE: Derby does NOT support JSON. On Derby, always set valueType = 'TEXT'.\n" +
                "  // Other databases may support JSON; the server will validate JSON capability automatically.\n" +
                "\n" +
                "  // valueType: 'TEXT' (default) or 'JSON'\n" +
                "  // jsonIndexMode (only when valueType = 'JSON'):\n" +
                "  //    'NONE'  - no index\n" +
                "  //    'FIELD' - per-field index (database-specific)\n" +
                "  // indexedJsonFields must be a JSON array string when jsonIndexMode = 'FIELD'\n" +
                "  //   e.g.: '[\"email\", \"address.city\"]'\n" +
                "\n" +
                "  // Example JSON group:\n" +
                "  // valueType: 'JSON',\n" +
                "  // jsonIndexMode: 'FIELD',\n" +
                "  // indexedJsonFields: '[\"email\", \"address.city\"]'\n" +
                "};\n" +
                "\n" +
                "var res = LookupHelper.createGroup(payload);\n" +
                "if (!res || String(res.ok) !== 'true') {\n" +
                "  logger.error('Create group failed: ' + (res ? (res.errorCode + ' - ' + res.errorMessage) : 'unknown'));\n" +
                "} else {\n" +
                "  logger.info('Created group id=' + res.group.id + ', name=' + res.group.name);\n" +
                "}\n",
                "Creates a lookup group. Required fields: name, description, version, cacheSize, cachePolicy. " +
                "Optional: valueType (TEXT/JSON), jsonIndexMode (NONE/FIELD), and indexedJsonFields (JSON array string) when FIELD mode is used. " +
                "JSON is not available on Derby; the server validates JSON support per database. " +
                "Returns { ok: 'true', group: {...} } on success; otherwise { ok: 'false', errorCode, errorMessage }."
            ));

        
        templates.add(new CodeTemplate(
                "Deletes a lookup group",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var success = LookupHelper.deleteGroup(group);\n",
                "Deletes the specified lookup group by name. Returns true if successful, false otherwise."
        ));

        // This defines the category
        referenceItems.put("Lookup Table Functions", templates);

        return referenceItems;
    }
    //@formatter:on

    @Override
    public String getPluginPointName() {
        return "Lookup Table Reference Plugin";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }
}
