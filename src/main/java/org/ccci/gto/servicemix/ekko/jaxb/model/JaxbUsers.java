package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "users")
public class JaxbUsers {
    @XmlElementRef
    private List<JaxbUser> users = new ArrayList<JaxbUser>();

    public void addUser(final String guid) {
        this.users.add(new JaxbUser(guid));
    }

    public void setUsers(final Collection<String> guids) {
        this.users.clear();
        if (guids != null) {
            for (final String guid : guids) {
                this.addUser(guid);
            }
        }
    }
}
