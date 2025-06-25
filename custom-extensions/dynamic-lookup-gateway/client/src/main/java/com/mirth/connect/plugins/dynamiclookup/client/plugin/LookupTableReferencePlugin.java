package com.mirth.connect.plugins.dynamiclookup.client.plugin;

import com.mirth.connect.plugins.CodeTemplatePlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateContextSet;
import com.mirth.connect.model.codetemplates.CodeTemplateProperties.CodeTemplateType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LookupTableReferencePlugin extends CodeTemplatePlugin {
    private static final Logger logger = LogManager.getLogger(LookupTableReferencePlugin.class);

    public LookupTableReferencePlugin(String name) {
        super(name);
    }

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
                "Lookup Value with Default Fallback",
                CodeTemplateType.DRAG_AND_DROP_CODE,
                CodeTemplateContextSet.getConnectorContextSet(),
                "var value = LookupHelper.get(group, key, defaultValue);",
                "Retrieves a value from a lookup group, or returns the default if the group or key is missing."
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

        // This defines the category
        referenceItems.put("Lookup Table Functions", templates);

        return referenceItems;
    }

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
