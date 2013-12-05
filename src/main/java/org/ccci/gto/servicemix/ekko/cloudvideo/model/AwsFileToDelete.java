package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "AwsDeletionQueue")
public class AwsFileToDelete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Embedded
    private AwsFile file;

    public AwsFileToDelete() {
    }

    public AwsFileToDelete(final AwsFile file) {
        this.file = file;
    }

    public final AwsFile getFile() {
        return this.file;
    }
}
