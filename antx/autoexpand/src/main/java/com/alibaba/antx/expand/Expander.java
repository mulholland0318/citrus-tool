/*
 * Copyright 2010 Alibaba Group Holding Limited.
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
 *
 */
package com.alibaba.antx.expand;

import com.alibaba.antx.util.FileObject;
import com.alibaba.antx.util.FileUtil;
import com.alibaba.antx.util.ZipUtil;

import org.apache.commons.digester.Digester;

import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ��ear�ļ�չ����tag��
 *
 * @author Michael Zhou
 */
public class Expander {
    private final ExpanderLogger log;
    private boolean              expandWar          = true;
    private boolean              expandRar          = true;
    private boolean              expandEjbjar       = false;
    private boolean              overwrite          = false;
    private boolean              keepRedundantFiles = false;
    private File                 srcfile;
    private File                 destdir;
    private Set                  expandedFiles;

    public Expander(ExpanderLogger log) {
        this.log                                    = log;
    }

    public boolean isExpandWar() {
        return expandWar;
    }

    public boolean isExpandRar() {
        return expandRar;
    }

    public boolean isExpandEjbjar() {
        return expandEjbjar;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public boolean isKeepRedundantFiles() {
        return keepRedundantFiles;
    }

    public File getSourceFile() {
        return srcfile;
    }

    public File getDestDir() {
        return destdir;
    }

    public void setExpandEjbjar(boolean expandEjbjar) {
        this.expandEjbjar = expandEjbjar;
    }

    public void setExpandRar(boolean expandRar) {
        this.expandRar = expandRar;
    }

    public void setExpandWar(boolean expandWar) {
        this.expandWar = expandWar;
    }

    public void setDestdir(String destdir) {
        String basedir = new File("").getAbsolutePath();

        this.destdir = new File(FileUtil.getPathBasedOn(basedir, destdir));
    }

    public void setSrcfile(String srcfile) {
        String basedir = new File("").getAbsolutePath();

        this.srcfile = new File(FileUtil.getPathBasedOn(basedir, srcfile));
    }

    /**
     * ���ø���ѡ�
     *
     * @param overwrite ���Ŀ��Ŀ¼�е��ļ���zip�ļ��е���Ҫ�£��Ƿ񸲸�֮
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * �����Ƿ񱣳ֶ�����ļ���
     *
     * @param keepRedundantFiles ���Ŀ��Ŀ¼���ж�����ļ����Ƿ񱣳ֶ���ɾ��
     */
    public void setKeepRedundantFiles(boolean keepRedundantFiles) {
        this.keepRedundantFiles = keepRedundantFiles;
    }

    private void init() {
        // srcfile
        if (srcfile == null) {
            throw new ExpanderException("missing source file to expand");
        }

        srcfile = srcfile.getAbsoluteFile();

        if (!srcfile.exists() || !srcfile.isFile()) {
            throw new ExpanderException(srcfile + " does not exist or is not a file");
        }

        // destdir
        if (destdir == null) {
            String dirname = srcfile.getName();
            int    index   = dirname.lastIndexOf(".");

            if (index < 0) {
                dirname += ".expanded";
            } else {
                dirname = dirname.substring(0, index);
            }

            destdir = new File(srcfile.getParentFile(), dirname);
        }

        destdir = destdir.getAbsoluteFile();

        if (destdir.exists() && !destdir.isDirectory()) {
            throw new ExpanderException(destdir + " is not a directory");
        }

        destdir.mkdirs();

        if (!destdir.exists()) {
            throw new ExpanderException("could not create directory: " + destdir);
        }
    }

    /**
     * ִ��չ��������
     */
    public void expand() {
        init();

        log.info("Expanding: " + srcfile + "\n       To: " + destdir.getAbsolutePath());

        // ����ļ��б�
        expandedFiles = new HashSet();

        // ��ʼչ��
        InputStream istream = null;

        try {
            istream = new BufferedInputStream(new FileInputStream(srcfile), 8192);
            getExpanderHandler(srcfile.toURI().toURL()).expand(istream, destdir);
            removeRedundantFiles(destdir);

            log.info("done.");
        } catch (IOException e) {
            throw new ExpanderException(e);
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (IOException e) {
                }
            }

            expandedFiles = null;
        }
    }

    /**
     * ɾ��������ļ���
     *
     * @param todir չ������Ŀ¼
     * @param zipStream ѹ����
     * @param zipEntry zip���
     * @param url �ݹ�չ����urlǰ׺
     * @param output XML���
     *
     * @throws IOException ��д�ļ�ʧ�ܣ���Zip��ʽ����
     */
    protected void removeRedundantFiles(File fileOrDir) throws IOException {
        if (isKeepRedundantFiles()) {
            return;
        }

        if (!fileOrDir.exists()) {
            return;
        }

        // ������ļ���������expandedFiles�в����ڸ��ļ�����ɾ��֮
        if (!fileOrDir.isDirectory()) {
            if (!expandedFiles.contains(fileOrDir.getCanonicalPath())) {
                log.info("- " + getPathRelativeToDestdir(fileOrDir) + " - "
                         + (fileOrDir.delete() ? "deleted"
                                               : "can't delete"));
            }

            return;
        }

        // ������ȵ�ɾ��Ŀ¼�е��ļ�����Ŀ¼
        File[] files = fileOrDir.listFiles();

        for (int i = 0; i < files.length; i++) {
            removeRedundantFiles(files[i]);
        }

        // ���Ŀ¼Ϊ�գ�������expandedFiles�в����ڸ�Ŀ¼����ɾ��Ŀ¼����
        if (!expandedFiles.contains(fileOrDir.getCanonicalPath()) && fileOrDir.delete()) {
            log.info("- " + getPathRelativeToDestdir(fileOrDir) + " - success");
        }
    }

    private ExpanderHandler getExpanderHandler(URL url) {
        String name = url.getFile();

        name = name.substring(name.lastIndexOf("/") + 1);

        if (name.endsWith(".war")) {
            return new WarExpanderHandler();
        } else {
            return new EarExpanderHandler(url);
        }
    }

    private String getPathRelativeToDestdir(File file) {
        return new FileObject(destdir).newFileObject(file).getRelativePath();
    }

    /**
     * ������ͬ���͵�jar���Ľӿڡ�
     */
    private abstract class ExpanderHandler {
        /**
         * չ��ear�ļ���ָ��Ŀ¼
         *
         * @param istream ������
         * @param todir չ��Ŀ¼
         *
         * @throws IOException ��д�ļ�ʧ�ܣ���Zip��ʽ����
         */
        protected void expand(InputStream istream, File todir)
                throws IOException {
            ZipInputStream zipStream = null;

            try {
                zipStream = new ZipInputStream(istream);

                ZipEntry zipEntry = null;

                while ((zipEntry = zipStream.getNextEntry()) != null) {
                    extractFile(todir, zipStream, zipEntry, null);
                }
            } finally {
                if (zipStream != null) {
                    try {
                        zipStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        /**
         * չ��һ���ļ���
         *
         * @param todir չ������Ŀ¼
         * @param zipStream ѹ����
         * @param zipEntry zip���
         * @param url �ݹ�չ����urlǰ׺
         *
         * @throws IOException ��д�ļ�ʧ�ܣ���Zip��ʽ����
         */
        protected void extractFile(File todir, InputStream zipStream, ZipEntry zipEntry, String url)
                throws IOException {
            String  entryName   = zipEntry.getName();
            Date    entryDate   = new Date(zipEntry.getTime());
            boolean isDirectory = zipEntry.isDirectory();
            File    targetFile  = FileUtil.getFile(todir, entryName);
            boolean expandFile  = false;

            // �ж��Ƿ���Ҫ��һ��չ��
            if (!isDirectory && (url == null)) {
                expandFile = needToExpand(zipEntry.getName());
            }

            if (!expandFile && !overwrite && targetFile.exists()
                        && (targetFile.lastModified() >= entryDate.getTime())) {
                log.debug(". " + getPathRelativeToDestdir(targetFile) + " - up-to-date");
                expandedFiles.add(targetFile.getCanonicalPath());
                return;
            }

            if (isDirectory) {
                expandedFiles.add(targetFile.getCanonicalPath());
                targetFile.mkdirs();
            } else {
                File dir = targetFile.getParentFile();

                dir.mkdirs();

                // �����war��rar�ļ�����չ����ͬ����Ŀ¼��
                if (expandFile) {
                    log.info("X " + getPathRelativeToDestdir(targetFile));

                    if (targetFile.exists() && !targetFile.isDirectory()) {
                        targetFile.delete();
                    }

                    targetFile.mkdirs();

                    if (!targetFile.exists() || !targetFile.isDirectory()) {
                        throw new ExpanderException("could not create directory: " + targetFile);
                    }

                    ZipInputStream zis      = new ZipInputStream(zipStream);
                    ZipEntry       subEntry = null;

                    while ((subEntry = zis.getNextEntry()) != null) {
                        extractFile(targetFile, zis, subEntry, entryName);
                    }
                } else {
                    log.debug("+ " + getPathRelativeToDestdir(targetFile));

                    if (targetFile.exists() && targetFile.isDirectory()) {
                        FileUtil.deleteDirectory(targetFile);
                    }

                    if (targetFile.exists() && !targetFile.isFile()) {
                        throw new ExpanderException("could not create file: " + targetFile
                                                    + ", it's a directory");
                    }

                    byte[]       buffer  = new byte[8192];
                    int          length  = 0;
                    OutputStream ostream = null;

                    try {
                        expandedFiles.add(targetFile.getCanonicalPath());

                        ostream = new BufferedOutputStream(new FileOutputStream(targetFile), 8192);

                        while ((length = zipStream.read(buffer)) >= 0) {
                            ostream.write(buffer, 0, length);
                        }
                    } finally {
                        if (ostream != null) {
                            try {
                                ostream.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                }
            }

            targetFile.setLastModified(entryDate.getTime());
        }

        /**
         * �ж��Ƿ���Ҫ��һ��չ����
         */
        protected boolean needToExpand(String name) {
            if (expandWar && name.endsWith(".war")) {
                return true;
            }

            if (expandRar && name.endsWith(".rar")) {
                return true;
            }

            return false;
        }
    }

    private class WarExpanderHandler extends ExpanderHandler {
    }

    private class EarExpanderHandler extends ExpanderHandler {
        private URL earURL;
        private URL applicationXmlURL;
        private Set ejbJars;

        public EarExpanderHandler(URL earURL) {
            this.earURL = earURL;

            try {
                this.applicationXmlURL = ZipUtil.getJarURL(this.earURL, "META-INF/application.xml");
            } catch (MalformedURLException e) {
                IllegalArgumentException iae = new IllegalArgumentException();

                iae.initCause(e);
                throw iae;
            }

            ejbJars = new HashSet();

            if (expandEjbjar) {
                InputStream istream = null;

                try {
                    istream = applicationXmlURL.openStream();
                    log.info("  Loading: " + applicationXmlURL);
                    readApplicationXml(istream);
                } catch (Exception e) {
                    log.warn("   failed - EJB-jars will not be expanded");
                } finally {
                    if (istream != null) {
                        try {
                            istream.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }

        private void readApplicationXml(InputStream istream)
                throws IOException, SAXException {
            Digester digester = new Digester();

            digester.addCallMethod("application/module/ejb", "add", 1);
            digester.addCallParam("application/module/ejb", 0);

            digester.push(ejbJars);
            digester.parse(istream);
        }

        protected boolean needToExpand(String name) {
            return super.needToExpand(name) || ejbJars.contains(name);
        }
    }
}