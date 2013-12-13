package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
@Table(name = "AwsUploadQueue")
@NamedQueries({
        @NamedQuery(name = "AwsFileToUpload.pendingVideos", query = "SELECT DISTINCT v FROM AwsFileToUpload u JOIN u.video v WHERE v.locked = false"),
        @NamedQuery(name = "AwsFileToUpload.pendingUploads", query = "SELECT u FROM AwsFileToUpload u WHERE u.video = :video ORDER BY u.uploaded"), })
public class AwsFileToUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "videoId", referencedColumnName = "id", nullable = false, updatable = false)
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE)
    private Video video;

    @Column(insertable = true, updatable = false, nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date uploaded = new Date();

    @Embedded
    private AwsFile file;

    @Column(nullable = false)
    private boolean deleteSource = false;

    public AwsFileToUpload() {
    }

    public AwsFileToUpload(final Video video, final AwsFile file) {
        this.video = video;
        this.file = file;
    }

    public final Video getVideo() {
        return this.video;
    }

    public final AwsFile getFile() {
        return this.file;
    }

    public final void setDeleteSource(final boolean delete) {
        this.deleteSource = delete;
    }

    public final boolean isDeleteSource() {
        return this.deleteSource;
    }
}
