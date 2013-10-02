package com.mongodb;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

class ResultsCursor implements MongoCursor {
    private ServerAddress serverAddress;
    private Iterator<DBObject> iterator;
    private boolean done = false;
    private DBCollection collection;
    private int batchSize;
    private Long cursorId;

    @SuppressWarnings("unchecked")
    public ResultsCursor(CommandResult res, DBCollection collection, int batchSize) {
        this.collection = collection;
        this.batchSize = batchSize;
        String[] split = ((String) res.get("serverUsed")).split(":");
        try {
            serverAddress = split.length == 2
                    ? new ServerAddress(split[0], Integer.parseInt(split[1]))
                    : new ServerAddress(split[0]);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        
        Map cursor = (Map) res.get("cursor");
        if (cursor != null) {
            cursorId = (Long) cursor.get("id");
            List<DBObject> firstBatch = (List<DBObject>) cursor.get("firstBatch");
            iterator = firstBatch.iterator();
        } else {
            List<DBObject> result = (List<DBObject>) res.get("result");
            iterator = result.iterator();
            done = true;
        }
    }

    public long getCursorId() {
        return cursorId;
    }

    public ServerAddress getServerAddress() {
        return serverAddress;
    }

    public void close() throws IOException {
    }

    public boolean hasNext() {
        if (!iterator.hasNext() && done) {
            return false;
        }
        if (!iterator.hasNext()) {
            advance();
        }
        return iterator.hasNext();
    }

    private void advance() {
        if (getCursorId() <= 0) {
            throw new RuntimeException("can't advance a cursor <= 0");
        }

        OutMessage m = OutMessage.getMore(collection, getCursorId(), batchSize);

        DBApiLayer db = (DBApiLayer) collection.getDB();
        Response res = db._connector.call(db, collection, m, getServerAddress(), getDecoder());
        DBApiLayer.throwOnQueryFailure(res, getCursorId());

        iterator = res.iterator();

        done = !iterator.hasNext();
    }

    public DBObject next() {
        if (hasNext()) {
            return iterator.next();
        }
        throw new NoSuchElementException("no more");
    }

    public void remove() {
        throw new UnsupportedClassVersionError("Removes are not supported with cursors");
    }

    private DBDecoder getDecoder() {
        return collection.getDBDecoderFactory() != null ? collection.getDBDecoderFactory().create() : null;
    }

}
