/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.repository;

import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;

public class ChannelRepository extends BaseRepository<Channel> {

    private static final String DIRECTORY = "channels";
    private static final String TYPE_NAME = "Channel";

    public ChannelRepository(GitOperations gitOps, FileOperations fileOps, String serverId) {
        super(gitOps, fileOps, serverId);
    }

    @Override
    protected Channel deserializeAndVerify(String content, String filePath) {
        try {
            Channel channel = fileOps.deserializeXml(content, Channel.class);

            if (channel == null) {
                logger.warn("Deserialized channel is null: {}", filePath);
                return null;
            }

            // ✅ Channel specific: check InvalidChannel
            if (channel instanceof InvalidChannel) {
                logger.warn("Skipping invalid channel: {}", filePath);
                return null;
            }

            return channel;

        } catch (Exception e) {
            logger.warn("Failed to deserialize channel from: {}", filePath, e);
            return null;
        }
    }

    @Override
    protected void postCommit(Channel channel, String id, String name, String commitSha) {
//        // Channel-specific: Save revision mapping
//        logger.debug("Saving revision for channel {} ({})", name, id);
//
//        try {
//            ChannelRevision revision = new ChannelRevision();
//            revision.setChannelId(id);
//            revision.setChannelName(name);
//            revision.setCommitSha(commitSha);
//            revision.setRevisionNumber(channel.getRevision());
//            revision.setTimestamp(new Date());
//
//            revisionService.saveRevision(revision);
//
//            logger.info("Saved revision {} for channel {}",
//                    channel.getRevision(), name);
//
//        } catch (Exception e) {
//            // Log but don't fail the commit
//            logger.error("Failed to save revision metadata for channel {}: {}",
//                    id, e.getMessage(), e);
//            // Consider: Do we want to fail the whole operation?
//            // Or just log and continue since Git commit already succeeded?
//        }
    }

    @Override
    protected String extractId(Channel channel) {
        return channel.getId();
    }

    @Override
    protected String extractName(Channel channel) {
        return channel.getName();
    }

    @Override
    protected Class<Channel> getEntityClass() {
        return Channel.class;
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}