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
                "var value = LookupHelper.get(group, key, ttlHours);",
                "Retrieves a value from the specified lookup group using the given key and a TTL (in hours). "
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
                "var value = LookupHelper.get(group, key, ttlHours, defaultValue);",
                "Retrieves a value from a lookup group using the given key and TTL (in hours). "
                        + "If the value is missing or stale based on TTL, the default value is returned instead."
        ));

        templates.add(new CodeTemplate(
                "Lookup Values Matching Pattern",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var values = LookupHelper.getMatching(group, keyPattern);",
                "Retrieves key-value pairs from the specified lookup group where keys match a pattern. Returns an empty map if the group does not exist or no matches are found."
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
                "var keys = [\"key1\", \"key2\", \"key3\"];\nvar values = LookupHelper.getBatch(group, keys, ttlHours);",
                "Retrieves multiple key-value pairs from the specified lookup group using a TTL (in hours). "
                        + "Only values updated within the TTL window will be returned. "
                        + "Returns an empty map if the group is not found or all values are stale or missing."
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
        	    "Creates a lookup group",
        	    CodeTemplateType.DRAG_AND_DROP_CODE,
        	    CodeTemplateContextSet.getConnectorContextSet(),
        	    "var payload = {\n" +
        	    "  name: 'MyGroup',\n" +
        	    "  description: 'optional',\n" +
        	    "  version: '1.0.0',\n" +
        	    "  cacheSize: '1000',\n" +
        	    "  cachePolicy: 'LRU' // or 'FIFO'\n" +
        	    "};\n" +
        	    "var res = LookupHelper.createGroup(payload);\n" +
        	    "if (!res || String(res.ok) !== 'true') {\n" +
        	    "  logger.error('Create group failed: ' + (res ? (res.errorCode + ' - ' + res.errorMessage) : 'unknown'));\n" +
        	    "} else {\n" +
        	    "  logger.info('Created group id=' + res.group.id + ', name=' + res.group.name);\n" +
        	    "}\n",
        	    "Creates a lookup group with fields: name, description, version, cacheSize, cachePolicy. " +
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
