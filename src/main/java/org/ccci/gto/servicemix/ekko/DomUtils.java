package org.ccci.gto.servicemix.ekko;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public final class DomUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DomUtils.class);

    private static final XPath XPATH = XPathFactory.newInstance().newXPath();
    static {
        final NamespaceContextImpl nsContext = new NamespaceContextImpl();
        nsContext.registerNamespace("ekko", XMLNS_EKKO);
        XPATH.setNamespaceContext(nsContext);
    }

    public static final XPathExpression compileXPath(final String expression) throws XPathExpressionException {
        return XPATH.compile(expression);
    }

    public static final Document parse(final String rawXml) {
        if (rawXml != null) {
            try {
                final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(rawXml)));
            } catch (final Exception e) {
                LOG.error("xml parsing error", e);
            }
        }

        return null;
    }

    public static void output(final Document dom, final Writer writer) {
        try {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.transform(new DOMSource(dom), new StreamResult(writer));
        } catch (final Exception e) {
            LOG.error("xml DOM output error", e);
        }
    }

    public static String asString(final Document dom) {
        final StringWriter writer = new StringWriter();
        DomUtils.output(dom, writer);
        return writer.getBuffer().toString();
    }

    private static class NamespaceContextImpl implements NamespaceContext {
        private String defaultNs = XMLConstants.NULL_NS_URI;

        private final Map<String, String> namespaces = new HashMap<String, String>();
        private final Map<String, Set<String>> prefixes = new HashMap<String, Set<String>>();

        private NamespaceContextImpl() {
            this.registerNamespace(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getNamespaceURI(final String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("prefix cannot be null");
            }

            switch (prefix) {
            case XMLConstants.DEFAULT_NS_PREFIX:
                return this.defaultNs;
            case XMLConstants.XML_NS_PREFIX:
                return XMLConstants.XML_NS_URI;
            case XMLConstants.XMLNS_ATTRIBUTE:
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            default:
                final String namespaceURI = this.namespaces.get(prefix);
                return namespaceURI != null ? namespaceURI : XMLConstants.NULL_NS_URI;
            }
        }

        @Override
        public String getPrefix(final String namespaceURI) {
            if (namespaceURI == null) {
                throw new IllegalArgumentException("namespaceURI cannot be null");
            }

            switch (namespaceURI) {
            case XMLConstants.XML_NS_URI:
                return XMLConstants.XML_NS_PREFIX;
            case XMLConstants.XMLNS_ATTRIBUTE_NS_URI:
                return XMLConstants.XMLNS_ATTRIBUTE;
            default:
                // Handle default namespace
                if (namespaceURI.equals(this.defaultNs)) {
                    return XMLConstants.DEFAULT_NS_PREFIX;
                }

                // look for a prefix
                final Set<String> prefixes = this.prefixes.get(namespaceURI);
                return prefixes != null && prefixes.size() > 0 ? prefixes.iterator().next() : null;
            }
        }

        @Override
        public Iterator<String> getPrefixes(final String namespaceURI) {
            if (namespaceURI == null) {
                throw new IllegalArgumentException("namespaceURI cannot be null");
            }

            Collection<String> prefixes = new ArrayList<String>();
            switch (namespaceURI) {
            case XMLConstants.XML_NS_URI:
            case XMLConstants.XMLNS_ATTRIBUTE_NS_URI:
                prefixes.add(this.getPrefix(namespaceURI));
                break;
            default:
                if (namespaceURI.equals(this.defaultNs)) {
                    prefixes.add(this.defaultNs);
                } else {
                    prefixes = this.prefixes.get(namespaceURI);
                }
            }

            if (prefixes == null) {
                return Collections.emptyIterator();
            }
            return Collections.unmodifiableCollection(prefixes).iterator();
        }

        public void registerNamespace(final String prefix, final String namespaceURI) {
            // remove any old registration for the specified prefix
            if (this.namespaces.containsKey(prefix)) {
                final String oldNamespaceURI = this.namespaces.get(prefix);
                final Set<String> prefixes = this.prefixes.get(oldNamespaceURI);
                if (prefixes != null) {
                    prefixes.remove(prefix);
                    if (prefixes.size() <= 0) {
                        this.prefixes.remove(oldNamespaceURI);
                    }
                }
            }

            // store the new registration
            this.namespaces.put(prefix, namespaceURI);
            Set<String> prefixes = this.prefixes.get(namespaceURI);
            if (prefixes == null) {
                prefixes = new HashSet<String>();
            }
            prefixes.add(prefix);
            this.prefixes.put(namespaceURI, prefixes);
        }
    }
}
