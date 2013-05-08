package org.ccci.gto.servicemix.ekko;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.IOUtils;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fm.last.moji.Moji;
import fm.last.moji.MojiFile;
import fm.last.moji.tracker.KeyExistsAlreadyException;

public class MojiResourceManager implements ResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(MojiResourceManager.class);

    private static final SecureRandom RAND = new SecureRandom();

    private CourseManager courseManager;

    @PersistenceContext
    private EntityManager em;

    private Moji moji;

    public void setCourseManager(final CourseManager courseManager) {
        this.courseManager = courseManager;
    }

    public void setEntityManager(final EntityManager em) {
        this.em = em;
    }

    public void setMoji(final Moji moji) {
        this.moji = moji;
    }

    @Override
    public Resource storeResource(final Course course, final InputStream in) throws ResourceAlreadyExistsException {
        try {
            // get temporary file
            final MojiFile file;
            while (true) {
                final MojiFile tmp = this.moji.getFile("course/" + course.getId() + "/tmp/" + RAND.nextLong());
                if (new MojiExistsCommand(tmp).execute()) {
                    continue;
                }

                file = tmp;
                break;
            }

            // get DigestOutputStream for temporary file
            final DigestOutputStream out = new DigestOutputStream(new MojiCommand<OutputStream>(file) {
                @Override
                protected OutputStream command() throws IOException {
                    return this.file.getOutputStream();
                }
            }.execute(), MessageDigest.getInstance("SHA-1"));

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
            final Resource resource = new Resource(course, sha1);
            resource.setSize(size);

            // rename MojiFile
            final String newName = resource.generateMogileFsKey();
            try {
                new MojiCommand<Object>(file) {
                    @Override
                    protected Object command() throws IOException {
                        this.file.rename(newName);
                        return null;
                    }
                }.execute();
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

            // attempt to add the resource to the course
            try {
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
                // the spring framework transaction manager may wrap JPA
                // exceptions
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
}
