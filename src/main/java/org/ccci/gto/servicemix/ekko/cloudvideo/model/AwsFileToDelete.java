package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "AwsDeletionQueue")
@NamedQueries({
        @NamedQuery(name = "AwsFileToDelete.bucketsWithPendingDeletions", query = "SELECT DISTINCT f.bucket FROM AwsFileToDelete d JOIN d.file f"),
        @NamedQuery(name = "AwsFileToDelete.pendingDeletionsForBucket", query = "SELECT d FROM AwsFileToDelete d JOIN d.file f WHERE f.bucket = :bucket"), })
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
