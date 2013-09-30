package com.commercehub.jclouds.gridfs.blobstore;

import com.google.common.base.Supplier;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.ContainerNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.CreateContainerOptions;
import org.jclouds.blobstore.options.GetOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.blobstore.options.PutOptions;
import org.jclouds.blobstore.util.BlobUtils;
import org.jclouds.collect.Memoized;
import org.jclouds.domain.Location;
import org.jclouds.io.ContentMetadata;
import org.jclouds.io.Payload;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.providers.ProviderMetadata;

import javax.inject.Inject;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.commercehub.jclouds.gridfs.blobstore.Util.parseGridFSIdentifier;
import static com.commercehub.jclouds.gridfs.blobstore.Util.parseServerAddresses;
import static com.google.common.base.Preconditions.checkNotNull;

public class GridFSBlobStore implements BlobStore {
    // TODO: check if there's stuff from BaseBlobStore we want
    private final BlobStoreContext context;
    private final BlobUtils blobUtils;
    private final Supplier<Location> defaultLocation;
    private final Supplier<Set<? extends Location>> locations;
    private final MongoClient mongo;
    private final DBFileToBlob dbFileToBlob;
    private final DBFileToBlobMetadata dbFileToBlobMetadata;

    @Inject
    protected GridFSBlobStore(ProviderMetadata providerMetadata, BlobStoreContext context, BlobUtils blobUtils, Supplier<Location> defaultLocation, DBFileToBlob dbFileToBlob, DBFileToBlobMetadata dbFileToBlobMetadata, @Memoized Supplier<Set<? extends Location>> locations) throws UnknownHostException {
        // TODO: remove anything not needed
        this.context = checkNotNull(context, "context");
        this.blobUtils = checkNotNull(blobUtils, "blobUtils");
        this.defaultLocation = checkNotNull(defaultLocation, "defaultLocation");
        this.dbFileToBlob = dbFileToBlob;
        this.dbFileToBlobMetadata = dbFileToBlobMetadata;
        this.locations = checkNotNull(locations, "locations");

        List<ServerAddress> addresses = parseServerAddresses(providerMetadata.getEndpoint());
        List<MongoCredential> credentials = new ArrayList<>(); // TODO support credentials
        MongoClientOptions options = MongoClientOptions.builder().build(); // TODO support options configuration
        if (addresses.size() > 1) {
            this.mongo = new MongoClient(addresses, credentials, options);
        } else {
            // If only one address, assume we want single-node mode.
            // You should always use multiple seeds with a replica set.
            this.mongo = new MongoClient(addresses.get(0), credentials, options);
        }
    }

    @Override
    public BlobStoreContext getContext() {
        return context;
    }

    @Override
    public BlobBuilder blobBuilder(String name) {
        return blobUtils.blobBuilder().name(name);
    }

    @Override
    public Set<? extends Location> listAssignableLocations() {
        return locations.get();
    }

    @Override
    public PageSet<? extends StorageMetadata> list(String container) {
        return null;  // TODO: implement
    }

    @Override
    public void clearContainer(String container) {
        // TODO: implement
    }

    @Override
    public void clearContainer(String container, ListContainerOptions options) {
        // TODO: implement
    }

    @Override
    public boolean directoryExists(String container, String directory) {
        return false;  // TODO: implement
    }

    @Override
    public void createDirectory(String container, String directory) {
        // TODO: implement
    }

    @Override
    public void deleteDirectory(String containerName, String name) {
        // TODO: implement
    }

    @Override
    public long countBlobs(String container) {
        return 0;  // TODO: implement
    }

    @Override
    public long countBlobs(String container, ListContainerOptions options) {
        return 0;  // TODO: implement
    }

    @Override
    public void deleteContainer(String container) {
        parseGridFSIdentifier(container).dropStoreCollections(mongo);
    }

    @Override
    public PageSet<? extends StorageMetadata> list() {
        return null;  // TODO: implement
    }

    @Override
    public boolean containerExists(String container) {
        return parseGridFSIdentifier(container).storeExists(mongo);
    }

    @Override
    public boolean createContainerInLocation(@Nullable Location location, String container) {
        return createContainerInLocation(location, container, CreateContainerOptions.NONE);
    }

    @Override
    public boolean createContainerInLocation(@Nullable Location location, String container, CreateContainerOptions options) {
        // TODO: consider using locations
        if (options != null && options.isPublicRead()) {
            throw new IllegalArgumentException("public read is not supported by this provider");
        }
        GridFSIdentifier gridFSIdentifier = parseGridFSIdentifier(container);
        if (gridFSIdentifier.storeExists(mongo)) {
            return false;
        }
        // TODO: cache
        gridFSIdentifier.connect(mongo);
        return true;
    }

    @Override
    public PageSet<? extends StorageMetadata> list(String container, ListContainerOptions options) {
        return null;  // TODO: implement
    }

    @Override
    public boolean blobExists(String container, String name) {
        return parseGridFSIdentifier(container).connect(mongo).findOne(name) != null;
    }

    @Override
    public String putBlob(String container, Blob blob) {
        return putBlob(container, blob, PutOptions.Builder.multipart());
    }

    @Override
    public String putBlob(String container, Blob blob, PutOptions options) {
        if (options != null && !options.isMultipart()) {
            throw new IllegalArgumentException("only multipart is supported by this provider");
        }
        Payload payload = checkNotNull(blob.getPayload());
        BlobMetadata metadata = blob.getMetadata();
        ContentMetadata contentMetadata = metadata.getContentMetadata();
        GridFS gridFS = parseGridFSIdentifier(container).connect(mongo);
        GridFSInputFile inputFile = gridFS.createFile(payload.getInput(), metadata.getName(), true);
        inputFile.setContentType(contentMetadata.getContentType());
        DBObject fileMetadata = new BasicDBObject();
        fileMetadata.putAll(metadata.getUserMetadata());
        inputFile.setMetaData(fileMetadata);
        inputFile.save();
        return inputFile.getMD5();
    }

    @Override
    public BlobMetadata blobMetadata(String container, String name) {
        return null;  // TODO: implement
    }

    @Override
    public Blob getBlob(String container, String name) {
        return getBlob(container, name, GetOptions.NONE);
    }

    @Override
    public Blob getBlob(String container, String name, GetOptions options) {
        GridFSIdentifier identifier = parseGridFSIdentifier(container);
        if (!identifier.storeExists(mongo)) {
            throw new ContainerNotFoundException(container, "could not find expected collections in database");
        }
        GridFS gridFS = identifier.connect(mongo); // TODO: cache
        // TODO: handle options
        GridFSDBFile dbFile = gridFS.findOne(name);
        if (dbFile == null) {
            return null;
        }
        Blob blob = dbFileToBlob.apply(dbFile);
        blob.getMetadata().setContainer(container);
        return blob;
    }

    @Override
    public void removeBlob(String container, String name) {
        parseGridFSIdentifier(container).connect(mongo).remove(name);
    }
}