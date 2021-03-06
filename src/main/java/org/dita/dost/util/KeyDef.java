/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2013 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */
package org.dita.dost.util;

import static org.dita.dost.util.Constants.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import net.sf.saxon.s9api.XdmNode;
import org.dita.dost.exception.DITAOTException;
import org.w3c.dom.Element;

/**
 * Key definition.
 */
public class KeyDef {

    public static final String ELEMENT_STUB = "stub";
    private static final String ATTRIBUTE_SOURCE = "source";
    private static final String ATTRIBUTE_HREF = "href";
    private static final String ATTRIBUTE_SCOPE = "scope";
    private static final String ATTRIBUTE_FORMAT = "format";
    private static final String ATTRIBUTE_KEYS = "keys";
    private static final String ELEMENT_KEYDEF = "keydef";

    /** Space delimited list of key names */
    public final String keys;
    public URI href;
    public final String scope;
    public final URI source;
    public final XdmNode element;
    public final String format;

    /**
     * Construct new key definition.
     *
     * @param keys key name
     * @param href href URI, may be {@code null}
     * @param scope link scope, may be {@code null}
     * @param source key definition source, may be {@code null}
     */
    public KeyDef(final String keys, final URI href, final String scope, final String format, final URI source, final XdmNode element) {
        //assert href.isAbsolute();
        this.keys = keys;
        this.href = href == null || href.toString().isEmpty() ? null : href;
        this.scope = scope == null ? ATTR_SCOPE_VALUE_LOCAL : scope;
        this.format = format == null ? ATTR_FORMAT_VALUE_DITA : format;
        this.source = source;
        this.element = element;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder().append(keys).append(EQUAL);
        if (href != null) {
            buf.append(href.toString());
        }
        if (scope != null) {
            buf.append(LEFT_BRACKET).append(scope).append(RIGHT_BRACKET);
        }
        if (source != null) {
            buf.append(LEFT_BRACKET).append(source.toString()).append(RIGHT_BRACKET);
        }
        return buf.toString();
    }

    /**
     * Write key definition XML configuration file
     *
     * @param keydefFile key definition file
     * @param keydefs list of key definitions
     * @throws DITAOTException if writing configuration file failed
     */
    public static void writeKeydef(final File keydefFile, final Collection<KeyDef> keydefs) throws DITAOTException {
        XMLStreamWriter keydef = null;
        try (OutputStream out = new FileOutputStream(keydefFile)) {
            keydef = XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8");
            keydef.writeStartDocument();
            keydef.writeStartElement(ELEMENT_STUB);
            for (final KeyDef k : keydefs) {
                keydef.writeStartElement(ELEMENT_KEYDEF);
                keydef.writeAttribute(ATTRIBUTE_KEYS, k.keys);
                if (k.href != null) {
                    keydef.writeAttribute(ATTRIBUTE_HREF, k.href.toString());
                }
                if (k.scope != null) {
                    keydef.writeAttribute(ATTRIBUTE_SCOPE, k.scope);
                }
                if (k.format != null) {
                    keydef.writeAttribute(ATTRIBUTE_FORMAT, k.format);
                }
                if (k.source != null) {
                    keydef.writeAttribute(ATTRIBUTE_SOURCE, k.source.toString());
                }
                keydef.writeEndElement();
            }
            keydef.writeEndDocument();
        } catch (final XMLStreamException | IOException e) {
            throw new DITAOTException("Failed to write key definition file " + keydefFile + ": " + e.getMessage(), e);
        } finally {
            if (keydef != null) {
                try {
                    keydef.close();
                } catch (final XMLStreamException e) {
                }
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((href == null) ? 0 : href.hashCode());
        result = prime * result + ((keys == null) ? 0 : keys.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        result = prime * result + ((format == null) ? 0 : format.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof KeyDef)) {
            return false;
        }
        KeyDef other = (KeyDef) obj;
        if (href == null) {
            if (other.href != null) {
                return false;
            }
        } else if (!href.equals(other.href)) {
            return false;
        }
        if (keys == null) {
            if (other.keys != null) {
                return false;
            }
        } else if (!keys.equals(other.keys)) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        if (format == null) {
            if (other.format != null) {
                return false;
            }
        } else if (!format.equals(other.format)) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        return true;
    }

}
