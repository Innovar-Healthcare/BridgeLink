/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.client.core.api.servlets.ExtensionServletInterface;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.ServerEvent.Outcome;
import com.mirth.connect.server.api.DontCheckAuthorized;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.controllers.ExtensionController.InstallationResult;
import com.mirth.connect.server.util.ConnectorPropertiesUtil;

public class ExtensionServlet extends MirthServlet implements ExtensionServletInterface {

    private static final String WEBADMIN_MANIFEST_PATH = "webadmin" + File.separator + "webadmin.json";

    /*
     * Manifests larger than this are served as "manifest": null instead of being read. WebAdmin's
     * validator caps manifests at 64KB; this engine-side bound only exists to keep a pathological
     * file from being read into memory on every listing.
     */
    private static final long WEBADMIN_MANIFEST_MAX_BYTES = 1024 * 1024;

    private static final ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ExtensionServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    @DontCheckAuthorized
    public void installExtension(InputStream inputStream) {
        /*
         * Check whether user is authorized without auditing yet. If unauthorized, fail fast so that
         * files aren't allowed to be extracted on the server.
         */
        if (!isUserAuthorized(false)) {
            isUserAuthorized(true);
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        // Attempt to extract the extension data
        InstallationResult result = extensionController.extractExtension(inputStream);

        // Now we have the metadata, so we can place it in the parameter map and audit the request proper
        parameterMap.put("metadata", result.getMetaData());
        auditAuthorizationRequest(result.getCause() == null ? Outcome.SUCCESS : Outcome.FAILURE);

        // Throw an exception if anything bad happened
        if (result.getCause() != null) {
            throw new MirthApiException(result.getCause());
        }
    }

    @Override
    public void uninstallExtension(String extensionPath) {
        try {
            extensionController.prepareExtensionForUninstallation(extensionPath);
        } catch (ControllerException e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    public MetaData getExtensionMetaData(String extensionName) {
        MetaData metaData = extensionController.getPluginMetaData().get(extensionName);
        if (metaData == null) {
            metaData = extensionController.getConnectorMetaData().get(extensionName);
            if (metaData == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
        }
        return metaData;
    }

    @Override
    public Map<String, ConnectorMetaData> getConnectorMetaData() {
        return extensionController.getConnectorMetaData();
    }

    @Override
    public Map<String, PluginMetaData> getPluginMetaData() {
        return extensionController.getPluginMetaData();
    }

    @Override
    public RawContent getWebAdminManifests() {
        /*
         * Prefer plugin metadata when a plugin and a connector share an install path, so the
         * entry's name matches how the extension is keyed by GET /extensions/plugins/.
         */
        Map<String, MetaData> metaDataByPath = new LinkedHashMap<String, MetaData>();
        for (MetaData metaData : extensionController.getPluginMetaData().values()) {
            metaDataByPath.putIfAbsent(metaData.getPath(), metaData);
        }
        for (MetaData metaData : extensionController.getConnectorMetaData().values()) {
            metaDataByPath.putIfAbsent(metaData.getPath(), metaData);
        }

        File extensionsDir = new File(getExtensionsPath());
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode entriesNode = responseNode.putArray("entries");

        for (MetaData metaData : metaDataByPath.values()) {
            if (!extensionController.isExtensionEnabled(metaData.getName())) {
                continue;
            }

            File manifestFile = getGuardedWebAdminManifestFile(extensionsDir, metaData.getPath());
            if (manifestFile == null || !manifestFile.isFile()) {
                continue;
            }

            /*
             * The manifest is passed through verbatim; the engine does not validate its contents.
             * An unparseable file is served as "manifest": null.
             */
            JsonNode manifestNode;
            try {
                if (manifestFile.length() > WEBADMIN_MANIFEST_MAX_BYTES) {
                    manifestNode = NullNode.getInstance();
                } else {
                    manifestNode = objectMapper.readTree(FileUtils.readFileToString(manifestFile, StandardCharsets.UTF_8));
                    if (manifestNode == null || manifestNode.isMissingNode()) {
                        manifestNode = NullNode.getInstance();
                    }
                }
            } catch (IOException e) {
                manifestNode = NullNode.getInstance();
            }

            ObjectNode entryNode = entriesNode.addObject();
            entryNode.put("name", metaData.getName());
            entryNode.put("path", metaData.getPath());
            entryNode.put("version", metaData.getPluginVersion());
            entryNode.set("manifest", manifestNode);
        }

        try {
            return new RawContent(objectMapper.writeValueAsString(responseNode));
        } catch (IOException e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    public Response getWebAdminConnectorDefaults(String extensionName, String transportName) {
        MetaData extension = extensionController.getPluginMetaData().get(extensionName);
        if (extension == null) {
            extension = extensionController.getConnectorMetaData().get(extensionName);
        }
        if (extension == null || !extensionController.isExtensionEnabled(extension.getName())) {
            throw new MirthApiException(Status.NOT_FOUND);
        }

        File manifestFile = getGuardedWebAdminManifestFile(new File(getExtensionsPath()), extension.getPath());
        if (manifestFile == null || !manifestFile.isFile()) {
            throw new MirthApiException(Status.NOT_FOUND);
        }

        // The transport must be declared by the named extension itself
        ConnectorMetaData connectorMetaData = extensionController.getConnectorMetaDataByTransportName(transportName);
        if (connectorMetaData == null || !Objects.equals(connectorMetaData.getPath(), extension.getPath())) {
            throw new MirthApiException(Status.NOT_FOUND);
        }

        try {
            Class<?> sharedClass = Class.forName(connectorMetaData.getSharedClassName());
            if (!ConnectorProperties.class.isAssignableFrom(sharedClass)) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
            ConnectorProperties instance = (ConnectorProperties) sharedClass.getDeclaredConstructor().newInstance();
            /*
             * The explicit media type pins the response to application/xml even when the client
             * sent Accept: application/json (which the method's Produces admits to avoid a 406).
             */
            RawContent body = new RawContent(ConnectorPropertiesUtil.toConnectorPropertiesXml(instance));
            return Response.ok(body, MediaType.APPLICATION_XML_TYPE).build();
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    public boolean isExtensionEnabled(String extensionName) {
        return extensionController.isExtensionEnabled(extensionName);
    }

    @Override
    public void setExtensionEnabled(String extensionName, boolean enabled) {
        try {
            extensionController.setExtensionEnabled(extensionName, enabled);
        } catch (ControllerException e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    @DontCheckAuthorized
    public Properties getPluginProperties(String extensionName, Set<String> propertyKeys) {
        parameterMap.put("extensionName", extensionName);
        checkUserAuthorizedForExtension(extensionName);
        try {
            return extensionController.getPluginProperties(extensionName, propertyKeys);
        } catch (ControllerException e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    @DontCheckAuthorized
    public void setPluginProperties(String extensionName, Properties properties, boolean mergeProperties) {
        parameterMap.put("extensionName", extensionName);
        parameterMap.put("properties", properties);
        checkUserAuthorizedForExtension(extensionName);
        try {
            extensionController.setPluginProperties(extensionName, properties, mergeProperties);
            extensionController.updatePluginProperties(extensionName, properties);
        } catch (ControllerException e) {
            throw new MirthApiException(e);
        }
    }

    protected String getExtensionsPath() {
        return ExtensionController.getExtensionsPath();
    }

    /**
     * Resolves an extension's webadmin manifest file, guarding against paths that traverse outside
     * the extensions directory. Returns null when the resolved file escapes the directory.
     */
    private File getGuardedWebAdminManifestFile(File extensionsDir, String extensionPath) {
        if (StringUtils.isBlank(extensionPath)) {
            return null;
        }
        try {
            String canonicalExtensionsDir = extensionsDir.getCanonicalPath();
            File manifestFile = new File(new File(extensionsDir, extensionPath), WEBADMIN_MANIFEST_PATH);
            if (!manifestFile.getCanonicalPath().startsWith(canonicalExtensionsDir + File.separator)) {
                return null;
            }
            return manifestFile;
        } catch (IOException e) {
            return null;
        }
    }
}
