package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.xpath.XPathConstants;

import org.ccci.gto.servicemix.ekko.DomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@XmlRootElement
public class JaxbDomElements {
    private static final Logger LOG = LoggerFactory.getLogger(JaxbDomElements.class);

    @XmlAnyElement
    private List<Element> elements = new ArrayList<Element>();

    public JaxbDomElements() {
    }

    public JaxbDomElements(final NodeList elements) {
        if (elements != null) {
            final int len = elements.getLength();
            for (int i = 0; i < len; i++) {
                final Node element = elements.item(i);
                if (element instanceof Element) {
                    this.elements.add((Element) element);
                }
            }
        }
    }

    public static JaxbDomElements fromXPath(final Document dom, final String xpath) {
        try {
            return new JaxbDomElements((NodeList) DomUtils.compileXPath(xpath).evaluate(dom, XPathConstants.NODESET));
        } catch (final Exception e) {
            LOG.error("XPath exception", e);
            return new JaxbDomElements();
        }
    }
}
