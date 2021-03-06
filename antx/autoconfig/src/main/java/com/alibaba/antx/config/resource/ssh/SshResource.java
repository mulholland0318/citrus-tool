/*
 * Copyright (c) 2002-2012 Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.antx.config.resource.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.alibaba.antx.config.ConfigException;
import com.alibaba.antx.config.resource.Resource;
import com.alibaba.antx.config.resource.ResourceURI;
import com.alibaba.antx.config.resource.Session;
import com.alibaba.antx.util.StreamUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SshResource extends Resource {
    private final ChannelSftp channel;
    private final SftpATTRS   attrs;

    public SshResource(Session session, ChannelSftp channel, ResourceURI uri, SftpATTRS attrs) {
        super(session, uri);

        this.channel = channel;
        this.attrs = attrs;
    }

    @Override
    public Resource getRelatedResource(String suburi) {
        return new SshResource(getSession(), channel, getURI().getSubURI(suburi), null);
    }

    @Override
    public byte[] getContent() {
        assertFile();

        try {
            return StreamUtil.readBytes(getInputStream(), true).toByteArray();
        } catch (IOException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public InputStream getInputStream() {
        assertFile();

        try {
            return channel.get(getURI().getPath());
        } catch (SftpException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        assertFile();

        try {
            return channel.put(getURI().getPath());
        } catch (SftpException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public String getCharset() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isDirectory() {
        if (attrs == null) {
            return getURI().guessDirectory();
        } else {
            return attrs.isDir();
        }
    }

    @Override
    public List list() {
        assertDirectory();

        List entries;

        try {
            entries = channel.ls(getURI().getPath());
        } catch (SftpException e) {
            throw new ConfigException(e);
        }

        List result = new ArrayList(entries.size());

        for (Iterator i = entries.iterator(); i.hasNext(); ) {
            LsEntry entry = (LsEntry) i.next();
            String name = entry.getFilename();

            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            result.add(new SshResource(getSession(), channel, getURI().getSubURI(name, entry.getAttrs().isDir()), entry
                    .getAttrs()));
        }

        Collections.sort(result);

        return result;
    }
}
