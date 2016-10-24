/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2016 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */

package org.dita.dost.module;

import org.apache.commons.io.FileUtils;
import org.dita.dost.exception.DITAOTException;
import org.dita.dost.pipeline.AbstractPipelineInput;
import org.dita.dost.pipeline.AbstractPipelineOutput;
import org.dita.dost.util.Job;
import org.dita.dost.util.Job.FileInfo;
import org.dita.dost.util.URLUtils;
import org.dita.dost.writer.AbstractXMLFilter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.dita.dost.util.Constants.*;
import static org.dita.dost.util.URLUtils.*;
import static org.dita.dost.util.XMLUtils.addOrSetAttribute;
import static org.dita.dost.util.XMLUtils.transform;

/**
 * Move temporary files not based on output URI to match output URI structure.
 *
 * @since 2.4
 */
public class CleanPreprocessModule extends AbstractPipelineModuleImpl {

    private final LinkFilter filter = new LinkFilter();
    private final MapCleanFilter mapFilter = new MapCleanFilter();

    @Override
    public AbstractPipelineOutput execute(final AbstractPipelineInput input) throws DITAOTException {
        init();

        final URI base = job.getInputDir();
        final Collection<FileInfo> fis = job.getFileInfo().stream()
                .collect(Collectors.toList());
        final Collection<FileInfo> res = new ArrayList<>(fis.size());
        for (final FileInfo fi : fis) {
            try {
                final FileInfo.Builder builder = new FileInfo.Builder(fi);
                final File srcFile = new File(job.tempDirURI.resolve(fi.uri));
                if (srcFile.exists()) {
                    final URI rel = base.relativize(fi.result);
                    final File destFile = new File(job.tempDirURI.resolve(rel));
                    final List<XMLFilter> processingPipe = getProcessingPipe(fi, srcFile, destFile);
                    if (fi.format != null && fi.format.equals("coderef")) {
                        logger.debug("Skip coderef");
                    } else if (!processingPipe.isEmpty()) {
                        logger.info("Processing " + srcFile.toURI() + " to " + destFile.toURI());
                        transform(srcFile.toURI(), destFile.toURI(), processingPipe);
                        if (!srcFile.equals(destFile)) {
                            logger.debug("Deleting " + srcFile.toURI());
                            FileUtils.deleteQuietly(srcFile);
                        }
                    } else if (!srcFile.equals(destFile)) {
                        logger.info("Moving " + srcFile.toURI() + " to " + destFile.toURI());
                        FileUtils.moveFile(srcFile, destFile);
                    }
                    builder.uri(rel);

                    // start map
                    if (fi.src.equals(job.getInputFile())) {
                        job.setProperty(INPUT_DITAMAP_URI, rel.toString());
                        job.setProperty(INPUT_DITAMAP, toFile(rel).getPath());
                    }
                }
                res.add(builder.build());
            } catch (final IOException e) {
                logger.error("Failed to clean " + job.tempDirURI.resolve(fi.uri) + ": " + e.getMessage(), e);
            }
        }

        fis.stream().forEach(fi -> job.remove(fi));
        res.stream().forEach(fi -> job.add(fi));

        try {
            job.write();
        } catch (IOException e) {
            throw new DITAOTException();
        }

        return null;
    }

    private void init() {
        filter.setJob(job);
        filter.setLogger(logger);

        mapFilter.setJob(job);
        mapFilter.setLogger(logger);
    }

    private List<XMLFilter> getProcessingPipe(final FileInfo fi, final File srcFile, final File destFile) {
        final List<XMLFilter> res = new ArrayList<>();

        if (fi.format == null || fi.format.equals(ATTR_FORMAT_VALUE_DITA) || fi.format.equals(ATTR_FORMAT_VALUE_DITAMAP)) {
            filter.setCurrentFile(srcFile.toURI());
            filter.setDestFile(destFile.toURI());
            res.add(filter);
        }

        if (fi.format != null && fi.format.equals(ATTR_FORMAT_VALUE_DITAMAP)) {
            res.add(mapFilter);
        }

        return res;
    }

    private class LinkFilter extends AbstractXMLFilter {

        private URI destFile;
        private URI base;

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
                throws SAXException {
            Attributes res = atts;

            if (hasLocalDitaLink(atts)) {
                final AttributesImpl resAtts = new AttributesImpl(atts);
                final URI href = toURI(atts.getValue(ATTRIBUTE_NAME_HREF));
                final URI resHref = getHref(href);
                addOrSetAttribute(resAtts, ATTRIBUTE_NAME_HREF, resHref.toString());
                res = resAtts;
            }

            getContentHandler().startElement(uri, localName, qName, res);
        }

        private boolean hasLocalDitaLink(final Attributes atts) {
            final boolean hasHref = atts.getIndex(ATTRIBUTE_NAME_HREF) != -1;
            final boolean notExternal = !ATTR_SCOPE_VALUE_EXTERNAL.equals(atts.getValue(ATTRIBUTE_NAME_SCOPE));
            if (hasHref && notExternal) {
                return true;
            }
            final URI data = toURI(atts.getValue(ATTRIBUTE_NAME_DATA));
            if (data != null && !data.isAbsolute()) {
                return true;
            }
            return false;
        }

        private URI getHref(final URI href) {
            if (href.getFragment() != null && (href.getPath() == null || href.getPath().equals(""))) {
                return href;
            }
            final URI targetAbs = stripFragment(currentFile.resolve(href));
            final FileInfo targetFileInfo = job.getFileInfo(targetAbs);
            if (targetFileInfo != null) {
                final URI rel = base.relativize(targetFileInfo.result);
                final URI targetDestFile = job.tempDirURI.resolve(rel);
                final URI relTarget = URLUtils.getRelativePath(destFile, targetDestFile);
                return setFragment(relTarget, href.getFragment());
            } else {
                return href;
            }
        }

        public void setDestFile(final URI destFile) {
            this.destFile = destFile;
        }

        @Override
        public void setJob(final Job job) {
            super.setJob(job);
            base = job.getInputDir();
        }
    }

    private enum Keep {
        RETAIN, DISCARD, DISCARD_BRANCH
    }

    private class MapCleanFilter extends AbstractXMLFilter {

        private final Queue<Keep> stack = new ArrayDeque<>();

        @Override
        public void startDocument() throws SAXException {
            stack.clear();
            getContentHandler().startDocument();
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
                throws SAXException {
            final String cls = atts.getValue(ATTRIBUTE_NAME_CLASS);
            if (!stack.isEmpty() && stack.element() == Keep.DISCARD_BRANCH) {
                stack.add(Keep.DISCARD_BRANCH);
            } else if (SUBMAP.matches(cls)) {
                stack.add(Keep.DISCARD);
            } else if (MAPGROUP_D_KEYDEF.matches(cls)) {
                stack.add(Keep.DISCARD_BRANCH);
            } else {
                stack.add(Keep.RETAIN);
            }

            if (stack.isEmpty() || stack.element() == Keep.RETAIN) {
                getContentHandler().startElement(uri, localName, qName, atts);
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName)
                throws SAXException {
            if (stack.element() == Keep.RETAIN) {
                getContentHandler().endElement(uri, localName, qName);
            }
        }

        @Override
        public void characters(char ch[], int start, int length)
                throws SAXException {
            if (stack.element() != Keep.DISCARD_BRANCH) {
                getContentHandler().characters(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char ch[], int start, int length)
                throws SAXException {
            if (stack.element() != Keep.DISCARD_BRANCH) {
                getContentHandler().ignorableWhitespace(ch, start, length);
            }
        }

        @Override
        public void processingInstruction(String target, String data)
                throws SAXException {
            if (stack.isEmpty() || stack.element() != Keep.DISCARD_BRANCH) {
                getContentHandler().processingInstruction(target, data);
            }
        }

        @Override
        public void skippedEntity(String name)
                throws SAXException {
            if (stack.element() != Keep.DISCARD_BRANCH) {
                getContentHandler().skippedEntity(name);
            }
        }

//    <xsl:template match="*[contains(@class, ' mapgroup-d/topicgroup ')]/*/*[contains(@class, ' topic/navtitle ')]">
//      <xsl:call-template name="output-message">
//        <xsl:with-param name="id" select="'DOTX072I'"/>
//      </xsl:call-template>
//    </xsl:template>

    }

}
