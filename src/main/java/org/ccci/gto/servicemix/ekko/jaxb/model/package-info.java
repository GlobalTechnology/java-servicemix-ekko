@XmlSchema(namespace = XMLNS_HUB, xmlns = { @XmlNs(namespaceURI = XMLNS_HUB, prefix = "hub"),
        @XmlNs(namespaceURI = XMLNS_EKKO, prefix = "ekko") }, elementFormDefault = XmlNsForm.QUALIFIED)
package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;
import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_HUB;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;
