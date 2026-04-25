/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */
package com.innovarhealthcare.channelHistory.shared.interfaces;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.Permissions;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataParam;
//@formatter:off
@Path("/plugins/version-history")
@Tag(name = "Version History Plugin")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public interface VersionHistoryServletInterface extends BaseServletInterface {
    @GET
    @Path("/history")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved all commit revisions of the file",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getHistory",
            display = "Get all commit revisions of a file",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.ASYNC,
            auditable = false
    )
    public String getHistory(
            @Param("fileName")
            @Parameter(description = "The file name (UUID) of the channel or code template", required = true)
            @QueryParam("fileName") String fileName,
            @Param("mode")
            @Parameter(description = "The type of item: 'channel' or 'codetemplate'", required = true)
            @QueryParam("mode") String mode
    ) throws ClientException;
    
    @GET
    @Path("/content")
    @ApiResponse(
            responseCode = "200",
            description = "Retrieved entity content from repository at specific revision",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getContentAtRevision",
            display = "Get entity content from repository at specific revision",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getContentAtRevision(
            @Param("id")
            @Parameter(description = "The entity ID (channel, library, or code template)", required = true)
            @QueryParam("id") String id,
            @Param("revision")
            @Parameter(description = "The Git revision/commit hash or ref (e.g., 'HEAD', commit SHA)", required = true)
            @QueryParam("revision") String revision,
            @Param("mode")
            @Parameter(description = "The entity type: 'channel', 'library', or 'codetemplate'", required = true)
            @QueryParam("mode") String mode
    ) throws ClientException;
    
    @POST
    @Path("/validateSetting")
    @ApiResponse(
            responseCode = "200",
            description = "Validate git repository settings",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "validateSetting",
            display = "Validate git repository settings",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String validateSetting(
            @Param("properties")
            @RequestBody(
                    description = "Git repository connection properties",
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Properties.class), examples = {@ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_xml")}),
                            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Properties.class), examples = {@ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_json")})
                    }
            )
            Properties properties
    ) throws ClientException;
    
    @POST
    @Path("/commitAndPushChannel")
    @ApiResponse(
            responseCode = "200",
            description = "Commit and push channel to repository",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "commitAndPushChannel",
            display = "Commit and push channel",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String commitAndPushChannel(
            @Param("channel")
            @RequestBody(
                    description = "The Channel object to commit.",
                    required = true,
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_xml")}),
                            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_json")})
                    }
            )
            Channel channel,
            @Param("message")
            @Parameter(description = "Commit message", required = true)
            @QueryParam("message") String message,
            @Param("userId")
            @Parameter(description = "User ID", required = true)
            @QueryParam("userId") String userId,
            @Param("overwrite")
            @Parameter(description = "true = auto-commit (pullWithOverwrite), false = manual commit (rebase+conflict detection)")
            @QueryParam("overwrite") @DefaultValue("true") boolean overwrite
    ) throws ClientException;
    
    @POST
    @Path("/writeChannel")
    @ApiResponse(responseCode = "204", description = "Write channel to working tree without committing")
    @MirthOperation(
            name = "writeChannel",
            display = "Write channel to working tree",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public void writeChannel(
            @Param("channel")
            @RequestBody(
                    description = "The Channel object to write.",
                    required = true,
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_xml")}),
                            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_json")})
                    }
            )
            Channel channel
    ) throws ClientException;
    
    @GET
    @Path("/channel_on_repo")
    @ApiResponse(
            responseCode = "200",
            description = "Load channels on repo",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "loadChannelsMetadata",
            display = "Load channels on repo",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String loadChannelsMetadata() throws ClientException;
    
    @GET
    @Path("/code_template_on_repo")
    @ApiResponse(
            responseCode = "200",
            description = "Load code templates on repo",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "loadCodeTemplatesMetadata",
            display = "Load code templates on repo",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String loadCodeTemplatesMetadata() throws ClientException;
    
    @POST
    @Path("/commitAndPushCodeTemplate")
    @ApiResponse(
            responseCode = "200",
            description = "Commit and push code template to repository",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "commitAndPushCodeTemplate",
            display = "Commit and push code template",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String commitAndPushCodeTemplate(
            @Param("codeTemplateId")
            @Parameter(description = "Code template ID", required = true)
            @QueryParam("codeTemplateId") String codeTemplateId,
            @Param("message")
            @Parameter(description = "Commit message", required = true)
            @QueryParam("message") String message,
            @Param("userId")
            @Parameter(description = "User ID", required = true)
            @QueryParam("userId") String userId,
            @Param("overwrite")
            @Parameter(description = "true = auto-commit (pullWithOverwrite), false = manual commit (rebase+conflict detection)")
            @QueryParam("overwrite") @DefaultValue("true") boolean overwrite
    ) throws ClientException;
    
    @GET
    @Path("/libraries_and_templates")
    @ApiResponse(
            responseCode = "200",
            description = "Load libraries and code template metadata",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "loadLibrariesAndTemplateMetadata",
            display = "Load libraries and code template metadata",
            permission = Permissions.CODE_TEMPLATES_VIEW,
            auditable = false
    )
    public String loadLibrariesAndTemplateMetadata() throws ClientException;
    
    @POST
    @Path("/saveLibraries")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ApiResponse(
            responseCode = "200",
            description = "Save code template libraries to repository",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(name = "saveLibraries", display = "Save libraries to repository", permission = Permissions.CODE_TEMPLATES_MANAGE)
    public String saveLibraries(
            @Param("libraries")
            @Parameter(
                    description = "The list of code template libraries to save to repository. Each library will be exported as an XML file in the Libraries folder.",
                    schema = @Schema(
                            description = "List of CodeTemplateLibrary objects containing library structure and template references"
                    ),
                    required = true
            )
            @FormDataParam("libraries")  List<CodeTemplateLibrary> libraries,
            @Param("message")
            @Parameter(
                    description = "message",
                    required = true)
            @QueryParam("message") String message,
            @Param("userId")
            @Parameter(
                    description = "user id",
                    required = true)
            @QueryParam("userId") String userId
            ) throws ClientException;
    
    @POST
    @Path("/commitAndPushGlobalScripts")
    @ApiResponse(responseCode = "200", description = "commit and push global scripts", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
            @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
    })
    @MirthOperation(
            name = "commitAndPushGlobalScripts",
            display = "commit and push global scripts",
            permission = Permissions.GLOBAL_SCRIPTS_EDIT,
            auditable = false
    )
    public String commitAndPushGlobalScripts(
            @Param("globalScripts")
            @RequestBody(
                    description = "The Global Scripts map to commit.",
                    required = true,
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_XML,
                                    schema = @Schema(implementation = Map.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "globalScripts",
                                                    summary = "Global Scripts XML Map",
                                                    value = "<map>\n" +
                                                            "  <entry>\n" +
                                                            "    <string>Deploy</string>\n" +
                                                            "    <string>// Deploy script content\nreturn;</string>\n" +
                                                            "  </entry>\n" +
                                                            "  <entry>\n" +
                                                            "    <string>Undeploy</string>\n" +
                                                            "    <string>// Undeploy script content\nreturn;</string>\n" +
                                                            "  </entry>\n" +
                                                            "  <entry>\n" +
                                                            "    <string>Preprocessor</string>\n" +
                                                            "    <string>// Preprocessor script content\nreturn message;</string>\n" +
                                                            "  </entry>\n" +
                                                            "  <entry>\n" +
                                                            "    <string>Postprocessor</string>\n" +
                                                            "    <string>// Postprocessor script content\nreturn;</string>\n" +
                                                            "  </entry>\n" +
                                                            "</map>"
                                            )
                                    }
                            ),
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = Map.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "globalScripts",
                                                    summary = "Global Scripts JSON Map",
                                                    value = "{\n" +
                                                            "  \"Deploy\": \"// Deploy script content\\nreturn;\",\n" +
                                                            "  \"Undeploy\": \"// Undeploy script content\\nreturn;\",\n" +
                                                            "  \"Preprocessor\": \"// Preprocessor script content\\nreturn message;\",\n" +
                                                            "  \"Postprocessor\": \"// Postprocessor script content\\nreturn;\"\n" +
                                                            "}"
                                            )
                                    }
                            )
                    }
            )
            Map<String, String> globalScripts,
            @Param("message")
            @Parameter(description = "commit message", required = true)
            @QueryParam("message")
            String message,
            @Param("userId")
            @Parameter(description = "user id", required = true)
            @QueryParam("userId")
            String userId
    ) throws ClientException;
    
    @GET
    @Path("/repoInfo")
    @ApiResponse(
            responseCode = "200",
            description = "Get local repository structure and size",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getRepoInfo",
            display = "Get local repository info",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getRepoInfo() throws ClientException;
    
    @GET
    @Path("/repoChanges")
    @ApiResponse(
            responseCode = "200",
            description = "Get current working tree changes",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getRepoChanges",
            display = "Get working tree changes",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getRepoChanges() throws ClientException;
    
    @GET
    @Path("/fileContent")
    @ApiResponse(
            responseCode = "200",
            description = "Get file content from working tree",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getFileContent",
            display = "Get file content from working tree",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getFileContent(
            @Param("filePath")
            @Parameter(description = "Relative file path from repository root (e.g., 'Channels/abc-123.xml')", required = true)
            @QueryParam("filePath") String filePath
    ) throws ClientException;
    
    @GET
    @Path("/fileContentAtHead")
    @ApiResponse(
            responseCode = "200",
            description = "Get file content at HEAD revision",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getFileContentAtHead",
            display = "Get file content at HEAD revision",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getFileContentAtHead(
            @Param("filePath")
            @Parameter(description = "Relative file path from repository root (e.g., 'Channels/abc-123.xml')", required = true)
            @QueryParam("filePath") String filePath
    ) throws ClientException;
    
    @GET
    @Path("/fileHistory")
    @ApiResponse(
            responseCode = "200",
            description = "Get commit history for a specific file path",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getFileHistory",
            display = "Get File Commit History",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getFileHistory(
            @Param("filePath")
            @Parameter(description = "Relative file path from repository root (e.g., 'Channels/abc-123.xml')", required = true)
            @QueryParam("filePath") String filePath
    ) throws ClientException;
    
    @GET
    @Path("/fileContentAtRevision")
    @ApiResponse(
            responseCode = "200",
            description = "Get file content at a specific commit revision",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getFileContentAtRevision",
            display = "Get File Content At Revision",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getFileContentAtRevision(
            @Param("filePath")
            @Parameter(description = "Relative file path from repository root (e.g., 'Channels/abc-123.xml')", required = true)
            @QueryParam("filePath") String filePath,
            @Param("commitHash")
            @Parameter(description = "The commit hash to read the file at", required = true)
            @QueryParam("commitHash") String commitHash
    ) throws ClientException;
    
    @GET
    @Path("/repoLog")
    @ApiResponse(
            responseCode = "200",
            description = "Get commit log for the entire repository",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getRepoLog",
            display = "Get Repository Commit Log",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getRepoLog(
            @Param("maxCount")
            @Parameter(description = "Maximum number of commits to return", required = false)
            @QueryParam("maxCount") @DefaultValue("" + VersionControlConstants.REPO_LOG_MAX_COUNT) int maxCount
    ) throws ClientException;
    
    @GET
    @Path("/commitChanges")
    @ApiResponse(
            responseCode = "200",
            description = "Get files changed in a specific commit",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getCommitChanges",
            display = "Get Files Changed in Commit",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getCommitChanges(
            @Param("commitHash")
            @Parameter(description = "The commit hash to inspect", required = true)
            @QueryParam("commitHash") String commitHash
    ) throws ClientException;
    
    @POST
    @Path("/commitAndPushFiles")
    @ApiResponse(
            responseCode = "200",
            description = "Commit and push selected files",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "commitAndPushFiles",
            display = "Commit and Push Selected Files",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String commitAndPushFiles(
            @Param("requestJson")
            @RequestBody(
                    description = "JSON-serialized CommitFilesRequest containing file paths, message, and user ID",
                    required = true,
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                            @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
                    }
            )
            String requestJson
    ) throws ClientException;
    
    @POST
    @Path("/restoreFiles")
    @ApiResponse(
            responseCode = "200",
            description = "Restore backed-up file content to working tree (no commit)",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "restoreFiles",
            display = "Restore files to working tree",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String restoreFiles(
            @Param("requestJson")
            @RequestBody(description = "JSON-serialized Map<String,String> of relative file paths to their content", required = true, content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class))})
            String requestJson
    ) throws ClientException;
    
    @GET
    @Path("/remoteStatus")
    @ApiResponse(
            responseCode = "200",
            description = "Fetch from origin and return ahead/behind commit counts",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getRemoteStatus",
            display = "Get remote sync status",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getRemoteStatus() throws ClientException;
    
    @POST
    @Path("/pull")
    @ApiResponse(
            responseCode = "200",
            description = "Pull from remote and merge; conflicts resolved using remote version"
    )
    @MirthOperation(
            name = "pull",
            display = "Pull from remote",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public void pull() throws ClientException;
    
    @POST
    @Path("/push")
    @ApiResponse(
            responseCode = "200",
            description = "Push already-committed local work to remote (fetch + rebase + push)",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "push",
            display = "Push local commits to remote",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String push() throws ClientException;
}
//@formatter:on
