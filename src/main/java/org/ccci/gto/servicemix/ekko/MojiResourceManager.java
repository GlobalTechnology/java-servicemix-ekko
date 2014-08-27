package org.ccci.gto.servicemix.ekko;

import fm.last.moji.Moji;
import fm.last.moji.MojiFile;
import fm.last.moji.tracker.KeyExistsAlreadyException;
import fm.last.moji.tracker.UnknownKeyException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityExistsException;
import javax.xml.bind.DatatypeConverter;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class MojiResourceManager implements ResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(MojiResourceManager.class);

    private static final SecureRandom RAND = new SecureRandom();

    private CourseManager courseManager;

    private Moji moji;

    public void setCourseManager(final CourseManager courseManager) {
        this.courseManager = courseManager;
    }

    public void setMoji(final Moji moji) {
        this.moji = moji;
    }

    @Override
    public FileResource storeResource(final Course course, String mimeType, final InputStream raw)
            throws ResourceAlreadyExistsException {
        try {
            // get temporary file
            final MojiFile file = this.getTmpMojiFile(course);

            // make the input stream buffered
            final BufferedInputStream in = new BufferedInputStream(raw);

            // get DigestOutputStream for temporary file
            final DigestOutputStream out = new DigestOutputStream(this.getMojiOutputStream(file),
                    MessageDigest.getInstance("SHA-1"));

            // try detecting the mimeType
            if (StringUtils.isBlank(mimeType)) {
                mimeType = URLConnection.guessContentTypeFromStream(in);
            }

            // copy InputStream to OutputStream
            long size = 0;
            try {
                try {
                    size = IOUtils.copyLarge(in, out);
                } finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                }
            } catch (final Exception e) {
                // there was an error copying the file, delete the temporary
                // MogileFS file before propagating the exception
                try {
                    new MojiDeleteCommand(file).execute();
                } catch (final Exception e2) {
                    LOG.error("Exception while deleting MogileFS file", e2);
                }

                throw e;
            }

            // create new resource object
            final String sha1 = DatatypeConverter.printHexBinary(out.getMessageDigest().digest());
            final FileResource resource = new FileResource(course, sha1);
            resource.setMimeType(mimeType);
            resource.setSize(size);

            // rename MojiFile
            try {
                this.renameMojiFile(file, resource);
            } catch (final KeyExistsAlreadyException e) {
                // this probably means the file has already been uploaded.
                // Suppress exception, and just use temporary key for now,
                // storing the resource object will address this case
            } catch (final Exception e) {
                try {
                    new MojiDeleteCommand(file).execute();
                } catch (final Exception e2) {
                    LOG.error("Exception while deleting MogileFS file", e2);
                }

                throw e;
            }

            // store the MogileFS key in the resource
            resource.setMogileFsKey(file.getKey());

            try {
                // attempt to add the resource to the course
                return this.courseManager.storeResource(course, resource);
            } catch (final Exception e) {
                // there was an exception, remove file
                try {
                    new MojiDeleteCommand(file).execute();
                } catch (final Exception e2) {
                    LOG.error("Exception while deleting MogileFS file", e2);
                }

                // check to see if the resource already exists
                if (e instanceof EntityExistsException) {
                    throw new ResourceAlreadyExistsException(e, resource.getKey());
                }
                // the spring framework transaction manager wraps JPA exceptions
                else if (e.getCause() instanceof EntityExistsException) {
                    throw new ResourceAlreadyExistsException(e.getCause(), resource.getKey());
                }

                // throw original exception
                throw e;
            }
        } catch (final ResourceAlreadyExistsException e) {
            // propagate exception to caller
            throw e;
        } catch (final Exception e) {
            LOG.error("Error storing resource in MogileFS", e);
            return null;
        }
    }

    @Override
    public InputStream loadResource(final FileResource resource) throws ResourceException {
        try {
            // return an InputStream for the specified resource
            return new MojiInputStreamCommand(this.moji.getFile(resource.getMogileFsKey())).execute();
        } catch (final UnknownKeyException e) {
            throw new ResourceNotFoundException(e);
        } catch (final Exception e) {
            LOG.error("Unexpected MogileFS exception", e);
            throw new ResourceException(e);
        }
    }

    @Override
    public void removeResource(final FileResource resource) {
        // get the MogileFS file for the specified resource
        final MojiFile file = this.moji.getFile(resource.getMogileFsKey());

        // attempt to delete the file
        try {
            new MojiDeleteCommand(file).execute();
        } catch (final IOException e) {
            // log exception, but suppress it
            LOG.error("Error deleting MogileFS file: {}", file, e);
        }

        // remove the resource from the database
        this.courseManager.removeResource(resource);
    }

    @Override
    public void removeUnpublishedResources(final Course course) {
        if (course != null) {
            for (final FileResource resource : course.getResources()) {
                if (!resource.isPublished()) {
                    this.removeResource(resource);
                }
            }
        }
    }

    private MojiFile getTmpMojiFile(final Course course) throws IOException {
        // get temporary file
        while (true) {
            final MojiFile file = this.moji.getFile("course/" + course.getId() + "/tmp/" + RAND.nextLong());
            if (new MojiExistsCommand(file).execute()) {
                continue;
            }

            return file;
        }
    }

    private OutputStream getMojiOutputStream(final MojiFile file) throws IOException {
        return new MojiCommand<OutputStream>(file) {
            @Override
            protected OutputStream command() throws IOException {
                return this.file.getOutputStream();
            }
        }.execute();
    }

    private void renameMojiFile(final MojiFile file, final FileResource resource) throws IOException {
        final FileResource.PrimaryKey key = resource.getKey();
        final String newName = "course/" + key.getCourseId() + "/resource/" + key.getSha1();
        new MojiCommand<Object>(file) {
            @Override
            protected Object command() throws IOException {
                this.file.rename(newName);
                return null;
            }
        }.execute();
    }

    private abstract class MojiCommand<T> {
        private int retryCount = 0;
        protected final MojiFile file;

        MojiCommand(final MojiFile file) {
            this.file = file;
        }

        protected abstract T command() throws IOException;

        public T execute() throws IOException {
            while (true) {
                try {
                    // execute the Moji command
                    return this.command();
                } catch (final IOException e) {
                    // throw the exception if we have retried too many times
                    if (retryCount++ > 3) {
                        throw e;
                    }

                    // re-attempt the command
                    LOG.debug("retrying command due to IOException", e);
                    continue;
                }
            }
        }
    }

    private final class MojiDeleteCommand extends MojiCommand<Object> {
        MojiDeleteCommand(final MojiFile file) {
            super(file);
        }

        @Override
        protected Object command() throws IOException {
            this.file.delete();
            return null;
        }
    }

    private final class MojiExistsCommand extends MojiCommand<Boolean> {
        MojiExistsCommand(final MojiFile file) {
            super(file);
        }

        @Override
        protected Boolean command() throws IOException {
            return this.file.exists();
        }
    }

    private final class MojiInputStreamCommand extends MojiCommand<InputStream> {
        MojiInputStreamCommand(final MojiFile file) {
            super(file);
        }

        @Override
        protected InputStream command() throws IOException {
            return this.file.getInputStream();
        }
    }
}
